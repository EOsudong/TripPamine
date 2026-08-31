import argparse
from pathlib import Path

import orjson

from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)
from trippamine_ai.datasets.emotion.profile_support import (
    ProfileLabelSupportAnalyzer,
)


def load_records(
    file_paths: list[Path],
) -> list[NormalizedEmotionDialogue]:
    records = []

    seen_record_ids: set[str] = set()

    for file_path in file_paths:
        if not file_path.exists():
            raise FileNotFoundError(
                f"Dataset not found: {file_path}"
            )

        if file_path.stat().st_size == 0:
            raise ValueError(
                f"Dataset is empty: {file_path}"
            )

        with file_path.open("rb") as file:
            for line_number, line in enumerate(
                file,
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
                        "Invalid JSONL: "
                        f"{file_path}:"
                        f"{line_number}"
                    ) from exc

                record = (
                    NormalizedEmotionDialogue
                    .model_validate(raw)
                )

                if (
                    record.record_id
                    in seen_record_ids
                ):
                    raise ValueError(
                        "Duplicate record_id "
                        "across input datasets: "
                        f"{record.record_id}"
                    )

                seen_record_ids.add(
                    record.record_id
                )

                records.append(
                    record
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
            "Analyze profile-level emotion "
            "support for TripPamine dataset."
        )
    )

    parser.add_argument(
        "--input",
        required=True,
        type=Path,
        nargs="+",
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--top-profiles",
        type=int,
        default=10,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    records = load_records(
        args.input
    )

    analyzer = (
        ProfileLabelSupportAnalyzer()
    )

    report = analyzer.analyze(
        records,
        top_profile_limit=(
            args.top_profiles
        ),
    )

    save_report(
        report,
        args.output,
    )

    summary = report.summary

    print()
    print(
        "Profile label support "
        "analysis completed"
    )

    print(
        "Total records: "
        f"{summary.total_records}"
    )

    print(
        "Unique profiles: "
        f"{summary.unique_profiles}"
    )

    print(
        "Profile record size "
        "(min / median / max): "
        f"{summary.minimum_records_per_profile}"
        " / "
        f"{summary.median_records_per_profile}"
        " / "
        f"{summary.maximum_records_per_profile}"
    )

    print(
        "Labels with no records: "
        f"{summary.labels_with_no_records}"
    )

    print(
        "Labels with fewer than "
        "3 profiles: "
        f"{summary.labels_with_fewer_than_three_profiles}"
    )

    print()
    print(
        "Lowest profile support labels"
    )

    risky_labels = sorted(
        report.labels,
        key=lambda item: (
            item.unique_profiles,
            -item.max_profile_share,
            item.label,
        ),
    )

    for item in risky_labels[:15]:
        print(
            f"{item.label} "
            f"records={item.total_records} "
            f"profiles={item.unique_profiles} "
            f"max_profile="
            f"{item.max_profile_id} "
            f"max_count="
            f"{item.max_records_in_profile} "
            f"max_share="
            f"{item.max_profile_share:.4f}"
        )

    print()
    print(
        f"Report: {args.output}"
    )


if __name__ == "__main__":
    main()