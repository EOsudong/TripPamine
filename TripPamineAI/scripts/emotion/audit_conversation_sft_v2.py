import argparse
from pathlib import Path
from typing import Any

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTSample,
)
from trippamine_ai.datasets.emotion.conversation_sft_v2_integrity import (
    ConversationSFTV2IntegrityAnalyzer,
)


def read_json(
    file_path: Path,
) -> dict[str, Any]:
    if not file_path.exists():
        raise FileNotFoundError(
            f"JSON file not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            f"JSON file is empty: "
            f"{file_path}"
        )

    result = orjson.loads(
        file_path.read_bytes()
    )

    if not isinstance(
        result,
        dict,
    ):
        raise ValueError(
            f"JSON root must be an "
            f"object: {file_path}"
        )

    return result


def read_sft_dataset(
    file_path: Path,
) -> list[
    ConversationSFTSample
]:
    if not file_path.exists():
        raise FileNotFoundError(
            f"SFT dataset not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            f"SFT dataset is empty: "
            f"{file_path}"
        )

    samples = []

    with file_path.open(
        "rb"
    ) as file:
        for line_number, line in enumerate(
            tqdm(
                file,
                desc=(
                    f"Loading "
                    f"{file_path.name}"
                ),
                unit="sample",
            ),
            start=1,
        ):
            if not line.strip():
                continue

            try:
                raw = orjson.loads(
                    line
                )
            except (
                orjson.JSONDecodeError
            ) as exc:
                raise ValueError(
                    "Invalid JSONL at "
                    f"{file_path}:"
                    f"{line_number}"
                ) from exc

            samples.append(
                ConversationSFTSample
                .model_validate(raw)
            )

    return samples


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Audit TripPamine "
            "Conversation SFT v2 "
            "three-way integrity."
        )
    )

    parser.add_argument(
        "--training",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--test",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--manifest",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--build-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    training = read_sft_dataset(
        args.training
    )

    validation = read_sft_dataset(
        args.validation
    )

    test = read_sft_dataset(
        args.test
    )

    manifest = read_json(
        args.manifest
    )

    build_report = read_json(
        args.build_report
    )

    report = (
        ConversationSFTV2IntegrityAnalyzer()
        .analyze(
            training=training,
            validation=validation,
            test=test,
            manifest=manifest,
            build_report=build_report,
        )
    )

    args.output.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    args.output.write_bytes(
        orjson.dumps(
            report.model_dump(
                mode="json"
            ),
            option=orjson.OPT_INDENT_2,
        )
    )

    print()
    print(
        "Conversation SFT v2 "
        "integrity audit completed"
    )

    print()
    print(
        f"Total samples: "
        f"{report.total_samples}"
    )

    print(
        "Unique record IDs: "
        f"{report.total_unique_record_ids}"
    )

    print(
        "Unique profiles: "
        f"{report.total_unique_profile_ids}"
    )

    print(
        "Unique content hashes: "
        f"{report.total_unique_content_hashes}"
    )

    print()

    for summary in (
        report.training,
        report.validation,
        report.test,
    ):
        print(
            f"{summary.split}: "
            f"samples="
            f"{summary.total_samples}, "
            f"records="
            f"{summary.unique_record_ids}, "
            f"profiles="
            f"{summary.unique_profile_ids}, "
            f"turns="
            f"{summary.target_turns}"
        )

    print()
    print("Pairwise overlaps")

    for overlap in (
        report.pairwise_overlaps
    ):
        print(
            f"{overlap.left_split}"
            f" <-> "
            f"{overlap.right_split}: "
            f"samples="
            f"{overlap.sample_id_count}, "
            f"records="
            f"{overlap.record_id_count}, "
            f"profiles="
            f"{overlap.profile_id_count}, "
            f"talk_ids="
            f"{overlap.talk_id_count}, "
            f"content_hashes="
            f"{overlap.content_hash_count}"
        )

    print()

    print(
        "Sample IDs isolated: "
        f"{report.all_sample_ids_isolated}"
    )

    print(
        "Record IDs isolated: "
        f"{report.all_record_ids_isolated}"
    )

    print(
        "Profiles isolated: "
        f"{report.all_profiles_isolated}"
    )

    print(
        "Content hashes unique: "
        f"{report.all_content_hashes_unique}"
    )

    print(
        "Source splits correct: "
        f"{report.all_source_splits_correct}"
    )

    print(
        "Target sequences valid: "
        f"{report.all_target_sequences_valid}"
    )

    print(
        "Messages non-empty: "
        f"{report.all_messages_non_empty}"
    )

    print(
        "Manifest matches: "
        f"{report.manifest.all_match}"
    )

    print(
        "Build report matches: "
        f"{report.build_report.all_match}"
    )

    print()
    print(
        "INTEGRITY PASS: "
        f"{report.integrity_pass}"
    )

    print()
    print(f"Report: {args.output}")


if __name__ == "__main__":
    main()