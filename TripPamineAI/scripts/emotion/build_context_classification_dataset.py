import argparse
import hashlib
from collections import Counter
from pathlib import Path
from typing import Any

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.context_classification import (
    EmotionContextClassificationBuilder,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


DATASET_SPLITS = (
    "training",
    "validation",
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
        description: str,
) -> None:
    if not file_path.exists():
        raise FileNotFoundError(
            f"{description} not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            f"{description} is empty: "
            f"{file_path}"
        )


def ensure_outputs_absent(
        output_paths: list[Path],
) -> None:
    existing_paths = [
        path
        for path in output_paths
        if path.exists()
    ]

    if existing_paths:
        raise FileExistsError(
            "Output already exists: "
            + ", ".join(
                str(path)
                for path in existing_paths
            )
        )


def read_reference_dataset(
        file_path: Path,
        expected_split: str,
) -> list[EmotionClassificationSample]:
    validate_input_file(
        file_path,
        "Reference classification dataset",
    )

    samples = []
    seen_ids: set[str] = set()

    with file_path.open("rb") as file:
        for line_number, line in enumerate(
                tqdm(
                    file,
                    desc=(
                        f"Loading reference "
                        f"{expected_split}"
                    ),
                    unit="sample",
                ),
                start=1,
        ):
            if not line.strip():
                continue

            try:
                raw = orjson.loads(line)
            except orjson.JSONDecodeError as exc:
                raise ValueError(
                    "Invalid JSONL at "
                    f"{file_path}:"
                    f"{line_number}"
                ) from exc

            sample = (
                EmotionClassificationSample
                .model_validate(raw)
            )

            if (
                    sample.source.split
                    != expected_split
            ):
                raise ValueError(
                    "Reference split mismatch: "
                    f"expected '{expected_split}', "
                    "found "
                    f"'{sample.source.split}'."
                )

            if sample.id in seen_ids:
                raise ValueError(
                    "Duplicate reference record ID: "
                    f"{sample.id}"
                )

            seen_ids.add(sample.id)
            samples.append(sample)

    if not samples:
        raise ValueError(
            "Reference classification dataset "
            "contains no samples: "
            f"{file_path}"
        )

    return samples


def load_normalized_records_for_ids(
        file_paths: list[Path],
        required_ids: set[str],
) -> dict[str, NormalizedEmotionDialogue]:
    if not required_ids:
        raise ValueError(
            "required_ids must not be empty."
        )

    for file_path in file_paths:
        validate_input_file(
            file_path,
            "Normalized dataset",
        )

    matched_records: dict[
        str,
        NormalizedEmotionDialogue,
    ] = {}

    for file_path in file_paths:
        with file_path.open("rb") as file:
            for line_number, line in enumerate(
                    tqdm(
                        file,
                        desc=(
                            "Matching normalized "
                            f"{file_path.name}"
                        ),
                        unit="record",
                    ),
                    start=1,
            ):
                if not line.strip():
                    continue

                try:
                    raw = orjson.loads(line)
                except orjson.JSONDecodeError as exc:
                    raise ValueError(
                        "Invalid JSONL at "
                        f"{file_path}:"
                        f"{line_number}"
                    ) from exc

                record_id = raw.get(
                    "record_id"
                )

                if not isinstance(
                        record_id,
                        str,
                ):
                    raise ValueError(
                        "Normalized record_id is "
                        "missing or invalid at "
                        f"{file_path}:"
                        f"{line_number}"
                    )

                if record_id not in required_ids:
                    continue

                if record_id in matched_records:
                    raise ValueError(
                        "Duplicate normalized "
                        "record ID: "
                        f"{record_id}"
                    )

                matched_records[
                    record_id
                ] = (
                    NormalizedEmotionDialogue
                    .model_validate(raw)
                )

    missing_ids = (
        required_ids
        - set(matched_records)
    )

    if missing_ids:
        preview = sorted(
            missing_ids
        )[:10]

        raise ValueError(
            "Normalized records are missing "
            "for reference IDs. "
            f"Missing count: {len(missing_ids)}, "
            f"examples: {preview}"
        )

    return matched_records


def validate_reference_match(
        reference: EmotionClassificationSample,
        record: NormalizedEmotionDialogue,
) -> None:
    comparisons = {
        "record_id": (
            reference.id,
            record.record_id,
        ),
        "profile_id": (
            reference.source.profile_id,
            record.source.profile_id,
        ),
        "talk_id": (
            reference.source.talk_id,
            record.source.talk_id,
        ),
        "label": (
            reference.label,
            record.emotion.emotion_code,
        ),
        "label_name": (
            reference.label_name,
            record.emotion.emotion,
        ),
        "coarse_label": (
            reference.coarse_label,
            record.emotion.coarse_emotion,
        ),
        "situation_code": (
            reference.situation_code,
            record.emotion.situation_code,
        ),
        "situation": (
            reference.situation,
            record.emotion.situation,
        ),
    }

    for field_name, (
            reference_value,
            normalized_value,
    ) in comparisons.items():
        if reference_value != normalized_value:
            raise ValueError(
                "Reference metadata mismatch "
                f"for {reference.id}: "
                f"{field_name} "
                f"reference={reference_value!r}, "
                f"normalized={normalized_value!r}"
            )


def build_context_samples(
        references: list[
            EmotionClassificationSample
        ],
        normalized_by_id: dict[
            str,
            NormalizedEmotionDialogue,
        ],
        split: str,
) -> tuple[
    list[EmotionClassificationSample],
    dict[str, Any],
]:
    if split not in DATASET_SPLITS:
        raise ValueError(
            "Context build supports only "
            "training and validation splits."
        )

    builder = (
        EmotionContextClassificationBuilder()
    )

    samples = []
    turn_counts: Counter[int] = Counter()
    changed_text_count = 0

    for reference in references:
        record = normalized_by_id.get(
            reference.id
        )

        if record is None:
            raise ValueError(
                "Normalized record missing "
                "during context build: "
                f"{reference.id}"
            )

        validate_reference_match(
            reference,
            record,
        )

        sample = builder.build(
            record=record,
            split=split,
        )

        if sample.id != reference.id:
            raise RuntimeError(
                "Context sample record ID "
                "does not match reference."
            )

        human_turn_count = sum(
            1
            for turn in record.turns
            if turn.human.strip()
        )

        turn_counts[
            human_turn_count
        ] += 1

        if sample.text != reference.text:
            changed_text_count += 1

        samples.append(sample)

    return (
        samples,
        {
            "samples": len(samples),
            "changed_text_samples": (
                changed_text_count
            ),
            "unchanged_text_samples": (
                len(samples)
                - changed_text_count
            ),
            "human_turn_count": {
                str(turn_count): count
                for turn_count, count
                in sorted(
                    turn_counts.items()
                )
            },
        },
    )


def write_jsonl(
        samples: list[
            EmotionClassificationSample
        ],
        output_path: Path,
) -> None:
    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with output_path.open("wb") as file:
        for sample in samples:
            file.write(
                orjson.dumps(
                    sample.model_dump(
                        mode="json"
                    ),
                    option=(
                        orjson.OPT_APPEND_NEWLINE
                    ),
                )
            )


def save_report(
        report: dict[str, Any],
        output_path: Path,
) -> None:
    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    output_path.write_bytes(
        orjson.dumps(
            report,
            option=orjson.OPT_INDENT_2,
        )
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build TripPamine context-aware "
            "emotion classification training "
            "and validation datasets while "
            "preserving the existing v2 split."
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
        "--reference-training",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--reference-validation",
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

    output_training = (
        args.output_dir
        / "training.jsonl"
    )

    output_validation = (
        args.output_dir
        / "validation.jsonl"
    )

    ensure_outputs_absent(
        [
            output_training,
            output_validation,
            args.report,
        ]
    )

    training_references = (
        read_reference_dataset(
            args.reference_training,
            "training",
        )
    )

    validation_references = (
        read_reference_dataset(
            args.reference_validation,
            "validation",
        )
    )

    all_references = (
        training_references
        + validation_references
    )

    required_ids = {
        sample.id
        for sample in all_references
    }

    if len(required_ids) != len(
            all_references
    ):
        raise ValueError(
            "Reference training/validation "
            "record overlap detected."
        )

    normalized_by_id = (
        load_normalized_records_for_ids(
            file_paths=[
                args.normalized_training,
                args.normalized_validation,
            ],
            required_ids=required_ids,
        )
    )

    (
        training_samples,
        training_summary,
    ) = build_context_samples(
        references=training_references,
        normalized_by_id=normalized_by_id,
        split="training",
    )

    (
        validation_samples,
        validation_summary,
    ) = build_context_samples(
        references=validation_references,
        normalized_by_id=normalized_by_id,
        split="validation",
    )

    write_jsonl(
        training_samples,
        output_training,
    )

    write_jsonl(
        validation_samples,
        output_validation,
    )

    report = {
        "experiment": (
            "T0-Q8-B-context-"
            "classification-dataset"
        ),
        "protocol": {
            "context": (
                "all_non_empty_human_turns"
            ),
            "assistant_turns_included": False,
            "turn_separator": "newline",
            "reference_split_preserved": True,
            "test_split_used": False,
            "test_output_created": False,
        },
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
            "reference_training": {
                "file": str(
                    args.reference_training
                ),
                "samples": len(
                    training_references
                ),
                "sha256": calculate_sha256(
                    args.reference_training
                ),
            },
            "reference_validation": {
                "file": str(
                    args.reference_validation
                ),
                "samples": len(
                    validation_references
                ),
                "sha256": calculate_sha256(
                    args.reference_validation
                ),
            },
        },
        "output": {
            "training": {
                "file": str(
                    output_training
                ),
                "sha256": calculate_sha256(
                    output_training
                ),
                **training_summary,
            },
            "validation": {
                "file": str(
                    output_validation
                ),
                "sha256": calculate_sha256(
                    output_validation
                ),
                **validation_summary,
            },
        },
    }

    save_report(
        report,
        args.report,
    )

    print()
    print(
        "Context classification dataset "
        "completed"
    )

    print()
    print(
        "Training samples: "
        f"{len(training_samples)}"
    )
    print(
        "Validation samples: "
        f"{len(validation_samples)}"
    )
    print(
        "Test split used: False"
    )
    print(
        "Test output created: False"
    )

    print()
    print(
        "Training changed text: "
        f"{training_summary['changed_text_samples']}"
    )
    print(
        "Validation changed text: "
        f"{validation_summary['changed_text_samples']}"
    )

    print()
    print(
        "Output directory: "
        f"{args.output_dir}"
    )
    print(
        "Report: "
        f"{args.report}"
    )

    print()
    print(
        "CONTEXT DATASET BUILD: PASS"
    )


if __name__ == "__main__":
    main()
