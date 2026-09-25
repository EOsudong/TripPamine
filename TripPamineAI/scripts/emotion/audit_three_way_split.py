import argparse
from pathlib import Path

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.three_way_integrity import (
    ThreeWaySplitIntegrityAnalyzer,
)


def read_dataset(
    file_path: Path,
) -> list[EmotionClassificationSample]:
    if not file_path.exists():
        raise FileNotFoundError(
            f"Dataset not found: {file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            f"Dataset is empty: {file_path}"
        )

    records = []

    with file_path.open("rb") as file:
        for line_number, line in enumerate(
            tqdm(
                file,
                desc=f"Loading {file_path.name}",
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

            records.append(
                EmotionClassificationSample
                .model_validate(raw)
            )

    return records


def read_manifest(
    file_path: Path,
) -> dict:
    if not file_path.exists():
        raise FileNotFoundError(
            f"Manifest not found: {file_path}"
        )

    return orjson.loads(
        file_path.read_bytes()
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Audit TripPamine emotion "
            "classification v2 three-way split."
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
        "--output",
        required=True,
        type=Path,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    training = read_dataset(
        args.training
    )

    validation = read_dataset(
        args.validation
    )

    test = read_dataset(
        args.test
    )

    manifest = read_manifest(
        args.manifest
    )

    report = (
        ThreeWaySplitIntegrityAnalyzer()
        .analyze(
            training=training,
            validation=validation,
            test=test,
            manifest=manifest,
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
    print("Three-way integrity audit completed")

    print()
    print(
        f"Total records: "
        f"{report.total_records}"
    )

    print(
        "Unique record IDs: "
        f"{report.total_unique_record_ids}"
    )

    print(
        "Unique profiles: "
        f"{report.total_unique_profile_ids}"
    )

    print()

    for summary in (
        report.training,
        report.validation,
        report.test,
    ):
        print(
            f"{summary.split}: "
            f"records={summary.total_records}, "
            f"profiles={summary.unique_profile_ids}, "
            f"missing_labels="
            f"{len(summary.missing_labels)}, "
            f"profile_target_mismatches="
            f"{len(summary.profile_target_mismatches)}"
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
            f"records="
            f"{overlap.record_id_count}, "
            f"profiles="
            f"{overlap.profile_id_count}, "
            f"talk_ids="
            f"{overlap.talk_id_count}"
        )

    print()
    print(
        "Records isolated: "
        f"{report.all_records_isolated}"
    )

    print(
        "Profiles isolated: "
        f"{report.all_profiles_isolated}"
    )

    print(
        "All labels present: "
        f"{report.all_labels_present}"
    )

    print(
        "Profile targets met: "
        f"{report.all_profile_targets_met}"
    )

    print(
        "Source splits correct: "
        f"{report.all_source_splits_correct}"
    )

    print(
        "Manifest matches: "
        f"{report.manifest.all_match}"
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