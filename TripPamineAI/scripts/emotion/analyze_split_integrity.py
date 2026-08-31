import argparse
from pathlib import Path

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.split_integrity import (
    SplitIntegrityAnalyzer,
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
                    f"{file_path}:{line_number}"
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
            "Audit TripPamine emotion "
            "classification split integrity."
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

    training = read_dataset(
        args.training
    )

    validation = read_dataset(
        args.validation
    )

    report = SplitIntegrityAnalyzer().analyze(
        training_records=training,
        validation_records=validation,
    )

    save_report(
        report,
        args.output,
    )

    print()
    print("Split integrity audit completed")

    print(
        "Training records: "
        f"{report.training.total_records}"
    )

    print(
        "Validation records: "
        f"{report.validation.total_records}"
    )

    print(
        "Training unique profiles: "
        f"{report.training.unique_profile_ids}"
    )

    print(
        "Validation unique profiles: "
        f"{report.validation.unique_profile_ids}"
    )

    print(
        "Record ID overlap: "
        f"{report.overlap.record_id_count}"
    )

    print(
        "Talk ID overlap: "
        f"{report.overlap.talk_id_count}"
    )

    print(
        "Profile ID overlap: "
        f"{report.overlap.profile_id_count}"
    )

    print(
        "Training profile overlap rate: "
        f"{report.overlap.training_profile_overlap_rate:.4%}"
    )

    print(
        "Validation profile overlap rate: "
        f"{report.overlap.validation_profile_overlap_rate:.4%}"
    )

    print(
        "Training duplicate record IDs: "
        f"{report.training.duplicate_record_ids}"
    )

    print(
        "Validation duplicate record IDs: "
        f"{report.validation.duplicate_record_ids}"
    )

    print(
        "Training duplicate talk IDs: "
        f"{report.training.duplicate_talk_ids}"
    )

    print(
        "Validation duplicate talk IDs: "
        f"{report.validation.duplicate_talk_ids}"
    )

    print()
    print("Top fine-emotion share differences")

    for item in (
        report.top_fine_emotion_differences
    ):
        print(
            f"{item.label} "
            f"{item.label_name}: "
            f"train={item.training_share:.4%}, "
            f"validation={item.validation_share:.4%}, "
            f"diff={item.share_difference:+.4%}"
        )

    print()
    print(f"Report: {args.output}")


if __name__ == "__main__":
    main()