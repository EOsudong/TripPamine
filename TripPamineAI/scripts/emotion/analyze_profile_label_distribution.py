import argparse
from pathlib import Path

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.profile_distribution import (
    ProfileLabelDistributionAnalyzer,
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


def save_report(
    report,
    output_path: Path,
) -> None:
    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    output_path.write_bytes(
        orjson.dumps(
            report.model_dump(
                mode="json"
            ),
            option=orjson.OPT_INDENT_2,
        )
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Analyze profile-level label "
            "distribution for TripPamine "
            "emotion dataset."
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
        "--output",
        required=True,
        type=Path,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    training_records = read_dataset(
        args.training
    )

    validation_records = read_dataset(
        args.validation
    )

    records = (
        training_records
        + validation_records
    )

    report = (
        ProfileLabelDistributionAnalyzer()
        .analyze(records)
    )

    save_report(
        report=report,
        output_path=args.output,
    )

    print()
    print(
        "Profile label distribution "
        "analysis completed"
    )

    print(
        f"Total records: "
        f"{report.total_records}"
    )

    print(
        f"Total profiles: "
        f"{report.total_profiles}"
    )

    print(
        "Duplicate record IDs: "
        f"{report.duplicate_record_ids}"
    )

    print(
        "Records per profile: "
        f"min={report.min_records_per_profile}, "
        f"median="
        f"{report.median_records_per_profile}, "
        f"max={report.max_records_per_profile}"
    )

    print(
        "Labels per profile: "
        f"min={report.min_labels_per_profile}, "
        f"median="
        f"{report.median_labels_per_profile}, "
        f"max={report.max_labels_per_profile}"
    )

    print(
        "Profiles with one label: "
        f"{report.profiles_with_single_label}"
    )

    print()
    print("Lowest profile support labels")

    lowest_support = sorted(
        report.label_support,
        key=lambda item: (
            item.profile_count,
            item.total_records,
        ),
    )[:10]

    for item in lowest_support:
        print(
            f"{item.label} "
            f"{item.label_name}: "
            f"records={item.total_records}, "
            f"profiles={item.profile_count}, "
            f"max_profile_share="
            f"{item.max_profile_share:.4%}"
        )

    print()
    print(
        "Highest single-profile "
        "concentration"
    )

    highest_concentration = sorted(
        report.label_support,
        key=lambda item: (
            item.max_profile_share
        ),
        reverse=True,
    )[:10]

    for item in highest_concentration:
        print(
            f"{item.label} "
            f"{item.label_name}: "
            f"records={item.total_records}, "
            f"profiles={item.profile_count}, "
            f"max_profile_share="
            f"{item.max_profile_share:.4%}"
        )

    print()
    print(f"Report: {args.output}")


if __name__ == "__main__":
    main()