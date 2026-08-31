import argparse
import hashlib
from collections import Counter
from pathlib import Path
from typing import Any

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.conversation_sft_v2 import (
    SPLIT_NAMES,
    ConversationSFTV2Builder,
    build_profile_split_map,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


def calculate_sha256(
    file_path: Path,
) -> str:
    digest = hashlib.sha256()

    with file_path.open("rb") as file:
        while chunk := file.read(
            1024 * 1024
        ):
            digest.update(chunk)

    return digest.hexdigest()


def validate_input_file(
    file_path: Path,
) -> None:
    if not file_path.exists():
        raise FileNotFoundError(
            "Input dataset not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            "Input dataset is empty: "
            f"{file_path}"
        )


def read_manifest(
    file_path: Path,
) -> dict[str, Any]:
    if not file_path.exists():
        raise FileNotFoundError(
            "Manifest not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            "Manifest is empty: "
            f"{file_path}"
        )

    try:
        manifest = orjson.loads(
            file_path.read_bytes()
        )
    except (
        orjson.JSONDecodeError
    ) as exc:
        raise ValueError(
            "Invalid manifest JSON: "
            f"{file_path}"
        ) from exc

    if not isinstance(
        manifest,
        dict,
    ):
        raise ValueError(
            "Manifest root must be "
            "a JSON object."
        )

    return manifest


def validate_output_paths(
    output_dir: Path,
    report_path: Path,
) -> dict[str, Path]:
    output_paths = {
        split_name: (
            output_dir
            / f"{split_name}.jsonl"
        )
        for split_name
        in SPLIT_NAMES
    }

    existing = [
        path
        for path
        in [
            *output_paths.values(),
            report_path,
        ]
        if path.exists()
    ]

    if existing:
        formatted = ", ".join(
            str(path)
            for path in existing
        )

        raise FileExistsError(
            "Output already exists. "
            "Existing files will not "
            "be overwritten: "
            f"{formatted}"
        )

    return output_paths


def process_source_dataset(
    input_path: Path,
    expected_source_split: str,
    builder: ConversationSFTV2Builder,
    output_files: dict[
        str,
        Any,
    ],
    record_ids: set[str],
    sample_ids: set[str],
    content_hashes: set[str],
    seen_profile_ids: set[str],
    source_dialogue_counts: Counter[str],
    generated_sample_counts: Counter[str],
    target_turn_counts: dict[
        str,
        Counter[int],
    ],
    quality_counts: dict[
        str,
        Counter[str],
    ],
) -> tuple[
    int,
    int,
]:
    source_dialogues = 0
    duplicate_content_hashes = 0

    with input_path.open(
        "rb"
    ) as input_file:
        for line_number, line in enumerate(
            tqdm(
                input_file,
                desc=(
                    "Building SFT v2 "
                    f"from {expected_source_split}"
                ),
                unit="dialogue",
            ),
            start=1,
        ):
            if not line.strip():
                continue

            try:
                raw_record = (
                    orjson.loads(line)
                )
            except (
                orjson.JSONDecodeError
            ) as exc:
                raise ValueError(
                    "Invalid JSONL at "
                    f"{input_path}:"
                    f"{line_number}"
                ) from exc

            record = (
                NormalizedEmotionDialogue
                .model_validate(
                    raw_record
                )
            )

            if (
                record.source.split
                != expected_source_split
            ):
                raise ValueError(
                    "Normalized source "
                    "split mismatch: "
                    f"expected "
                    f"'{expected_source_split}', "
                    f"found "
                    f"'{record.source.split}' "
                    f"at {input_path}:"
                    f"{line_number}"
                )

            if (
                record.record_id
                in record_ids
            ):
                raise ValueError(
                    "Duplicate normalized "
                    "record ID detected: "
                    f"{record.record_id}"
                )

            record_ids.add(
                record.record_id
            )

            seen_profile_ids.add(
                record.source.profile_id
            )

            (
                target_split,
                samples,
            ) = builder.build(
                record
            )

            source_dialogues += 1

            source_dialogue_counts[
                target_split
            ] += 1

            for sample in samples:
                if (
                    sample.id
                    in sample_ids
                ):
                    raise ValueError(
                        "Duplicate SFT "
                        "sample ID detected: "
                        f"{sample.id}"
                    )

                sample_ids.add(
                    sample.id
                )

                if (
                    sample.content_hash
                    in content_hashes
                ):
                    duplicate_content_hashes += 1
                else:
                    content_hashes.add(
                        sample.content_hash
                    )

                output_files[
                    target_split
                ].write(
                    orjson.dumps(
                        sample.model_dump(
                            mode="json"
                        ),
                        option=(
                            orjson
                            .OPT_APPEND_NEWLINE
                        ),
                    )
                )

                generated_sample_counts[
                    target_split
                ] += 1

                target_turn_counts[
                    target_split
                ][
                    sample.target_turn
                ] += 1

                quality_counts[
                    target_split
                ][
                    sample
                    .source_quality_status
                ] += 1

    return (
        source_dialogues,
        duplicate_content_hashes,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build TripPamine "
            "Conversation SFT v2 using "
            "the Classification v2 "
            "profile split manifest."
        )
    )

    parser.add_argument(
        "--normalized-training",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--normalized-validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--manifest",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output-dir",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    validate_input_file(
        args.normalized_training
    )

    validate_input_file(
        args.normalized_validation
    )

    manifest = read_manifest(
        args.manifest
    )

    profile_split_map = (
        build_profile_split_map(
            manifest
        )
    )

    builder = (
        ConversationSFTV2Builder(
            profile_split_map
        )
    )

    output_paths = (
        validate_output_paths(
            output_dir=(
                args.output_dir
            ),
            report_path=args.report,
        )
    )

    args.output_dir.mkdir(
        parents=True,
        exist_ok=True,
    )

    record_ids: set[str] = set()
    sample_ids: set[str] = set()
    content_hashes: set[str] = set()
    seen_profile_ids: set[str] = set()

    source_dialogue_counts: Counter[
        str
    ] = Counter()

    generated_sample_counts: Counter[
        str
    ] = Counter()

    target_turn_counts = {
        split_name: Counter()
        for split_name in SPLIT_NAMES
    }

    quality_counts = {
        split_name: Counter()
        for split_name in SPLIT_NAMES
    }

    duplicate_content_hashes = 0

    output_files = {
        split_name: (
            output_paths[
                split_name
            ].open("wb")
        )
        for split_name in SPLIT_NAMES
    }

    try:
        (
            training_source_dialogues,
            training_duplicates,
        ) = process_source_dataset(
            input_path=(
                args.normalized_training
            ),
            expected_source_split=(
                "training"
            ),
            builder=builder,
            output_files=output_files,
            record_ids=record_ids,
            sample_ids=sample_ids,
            content_hashes=content_hashes,
            seen_profile_ids=(
                seen_profile_ids
            ),
            source_dialogue_counts=(
                source_dialogue_counts
            ),
            generated_sample_counts=(
                generated_sample_counts
            ),
            target_turn_counts=(
                target_turn_counts
            ),
            quality_counts=(
                quality_counts
            ),
        )

        (
            validation_source_dialogues,
            validation_duplicates,
        ) = process_source_dataset(
            input_path=(
                args.normalized_validation
            ),
            expected_source_split=(
                "validation"
            ),
            builder=builder,
            output_files=output_files,
            record_ids=record_ids,
            sample_ids=sample_ids,
            content_hashes=content_hashes,
            seen_profile_ids=(
                seen_profile_ids
            ),
            source_dialogue_counts=(
                source_dialogue_counts
            ),
            generated_sample_counts=(
                generated_sample_counts
            ),
            target_turn_counts=(
                target_turn_counts
            ),
            quality_counts=(
                quality_counts
            ),
        )

        duplicate_content_hashes = (
            training_duplicates
            + validation_duplicates
        )

    finally:
        for output_file in (
            output_files.values()
        ):
            output_file.close()

    total_source_dialogues = (
        training_source_dialogues
        + validation_source_dialogues
    )

    expected_source_records = (
        manifest.get(
            "source_records"
        )
    )

    if not isinstance(
        expected_source_records,
        int,
    ):
        raise ValueError(
            "Manifest does not contain "
            "a valid source_records value."
        )

    if (
        total_source_dialogues
        != expected_source_records
    ):
        raise RuntimeError(
            "Source dialogue count does "
            "not match manifest: "
            f"expected "
            f"{expected_source_records}, "
            f"found "
            f"{total_source_dialogues}"
        )

    expected_profile_ids = set(
        profile_split_map
    )

    missing_profiles = (
        expected_profile_ids
        - seen_profile_ids
    )

    unexpected_profiles = (
        seen_profile_ids
        - expected_profile_ids
    )

    if missing_profiles:
        raise RuntimeError(
            "Profiles from manifest "
            "were not found in the "
            "normalized dataset: "
            f"{len(missing_profiles)}"
        )

    if unexpected_profiles:
        raise RuntimeError(
            "Normalized dataset contains "
            "profiles not found in "
            "manifest: "
            f"{len(unexpected_profiles)}"
        )

    expected_profile_counts = {
        split_name: sum(
            1
            for target_split
            in profile_split_map.values()
            if (
                target_split
                == split_name
            )
        )
        for split_name in SPLIT_NAMES
    }

    seen_profiles_by_split = {
        split_name: {
            profile_id
            for profile_id, target_split
            in profile_split_map.items()
            if (
                target_split
                == split_name
            )
        }
        for split_name in SPLIT_NAMES
    }

    total_generated_samples = sum(
        generated_sample_counts.values()
    )

    report = {
        "version": "v2",
        "strategy": (
            "classification-v2-"
            "profile-manifest"
        ),
        "source": {
            "normalized_training": {
                "file": str(
                    args.normalized_training
                ),
                "sha256": calculate_sha256(
                    args.normalized_training
                ),
            },
            "normalized_validation": {
                "file": str(
                    args.normalized_validation
                ),
                "sha256": calculate_sha256(
                    args.normalized_validation
                ),
            },
            "split_manifest": {
                "file": str(
                    args.manifest
                ),
                "sha256": calculate_sha256(
                    args.manifest
                ),
            },
        },
        "summary": {
            "source_dialogues": (
                total_source_dialogues
            ),
            "generated_samples": (
                total_generated_samples
            ),
            "unique_record_ids": len(
                record_ids
            ),
            "unique_sample_ids": len(
                sample_ids
            ),
            "unique_content_hashes": len(
                content_hashes
            ),
            "duplicate_content_hashes": (
                duplicate_content_hashes
            ),
            "profiles": len(
                seen_profile_ids
            ),
        },
        "splits": {
            split_name: {
                "source_dialogues": (
                    source_dialogue_counts[
                        split_name
                    ]
                ),
                "generated_samples": (
                    generated_sample_counts[
                        split_name
                    ]
                ),
                "profiles": len(
                    seen_profiles_by_split[
                        split_name
                    ]
                ),
                "expected_profiles": (
                    expected_profile_counts[
                        split_name
                    ]
                ),
                "target_turns": {
                    str(turn): count
                    for turn, count
                    in sorted(
                        target_turn_counts[
                            split_name
                        ].items()
                    )
                },
                "quality": dict(
                    sorted(
                        quality_counts[
                            split_name
                        ].items()
                    )
                ),
                "output": {
                    "file": str(
                        output_paths[
                            split_name
                        ]
                    ),
                    "sha256": (
                        calculate_sha256(
                            output_paths[
                                split_name
                            ]
                        )
                    ),
                },
            }
            for split_name
            in SPLIT_NAMES
        },
    }

    args.report.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    args.report.write_bytes(
        orjson.dumps(
            report,
            option=orjson.OPT_INDENT_2,
        )
    )

    print()
    print(
        "Conversation SFT v2 "
        "dataset completed"
    )

    print(
        "Source dialogues: "
        f"{total_source_dialogues}"
    )

    print(
        "Generated samples: "
        f"{total_generated_samples}"
    )

    print(
        "Duplicate content hashes: "
        f"{duplicate_content_hashes}"
    )

    for split_name in SPLIT_NAMES:
        print(
            f"{split_name}: "
            f"dialogues="
            f"{source_dialogue_counts[split_name]}, "
            f"samples="
            f"{generated_sample_counts[split_name]}, "
            f"profiles="
            f"{len(seen_profiles_by_split[split_name])}"
        )

    print()
    print(
        f"Output: "
        f"{args.output_dir}"
    )

    print(
        f"Report: "
        f"{args.report}"
    )


if __name__ == "__main__":
    main()