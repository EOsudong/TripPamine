import argparse
from pathlib import Path
from typing import Any

import orjson

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.statistics import (
    ClassificationStatisticsAnalyzer,
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
            file,
            start=1,
        ):
            if not line.strip():
                continue

            try:
                raw = orjson.loads(line)
            except orjson.JSONDecodeError as exc:
                raise ValueError(
                    "Invalid JSONL at "
                    f"{file_path}:{line_number}"
                ) from exc

            records.append(
                EmotionClassificationSample
                .model_validate(raw)
            )

    return records


def build_comparison(
    training_stats: dict[str, Any],
    validation_stats: dict[str, Any],
) -> dict[str, Any]:
    training_by_label = {
        item["label"]: item
        for item in training_stats[
            "fine_emotion"
        ]
    }

    validation_by_label = {
        item["label"]: item
        for item in validation_stats[
            "fine_emotion"
        ]
    }

    comparison = {}

    for label in sorted(training_by_label):
        training = training_by_label[label]
        validation = validation_by_label[label]

        comparison[label] = {
            "label_name": (
                training["label_name"]
            ),
            "training_count": (
                training["count"]
            ),
            "training_share": (
                training["share"]
            ),
            "validation_count": (
                validation["count"]
            ),
            "validation_share": (
                validation["share"]
            ),
            "share_difference": (
                validation["share"]
                - training["share"]
            ),
        }

    return comparison


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
            "Analyze TripPamine emotion "
            "classification datasets."
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

    analyzer = ClassificationStatisticsAnalyzer()

    training = read_dataset(
        args.training
    )

    validation = read_dataset(
        args.validation
    )

    training_stats = analyzer.analyze(
        training
    )

    validation_stats = analyzer.analyze(
        validation
    )

    training_data = training_stats.model_dump(
        mode="json"
    )

    validation_data = (
        validation_stats.model_dump(
            mode="json"
        )
    )

    report = {
        "training": training_data,
        "validation": validation_data,
        "comparison": build_comparison(
            training_stats=training_data,
            validation_stats=validation_data,
        ),
    }

    save_report(
        report=report,
        output_path=args.output,
    )

    print()
    print("Classification analysis completed")

    print(
        "Training records: "
        f"{training_stats.total_records}"
    )

    print(
        "Validation records: "
        f"{validation_stats.total_records}"
    )

    print(
        "Training missing labels: "
        f"{len(training_stats.missing_labels)}"
    )

    print(
        "Validation missing labels: "
        f"{len(validation_stats.missing_labels)}"
    )

    print(
        "Training imbalance ratio: "
        f"{training_stats.imbalance_ratio}"
    )

    print(
        "Validation imbalance ratio: "
        f"{validation_stats.imbalance_ratio}"
    )

    print(f"Report: {args.output}")


if __name__ == "__main__":
    main()