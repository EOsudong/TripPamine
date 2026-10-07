import argparse
import hashlib
from collections import Counter
from pathlib import Path
from typing import Any

import orjson
from pydantic import ValidationError
from tqdm import tqdm

from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)
from trippamine_ai.datasets.emotion.validator import (
    EmotionDatasetValidator,
    ValidationStatus,
)


def calculate_sha256(file_path: Path) -> str:
    digest = hashlib.sha256()

    with file_path.open("rb") as file:
        while chunk := file.read(1024 * 1024):
            digest.update(chunk)

    return digest.hexdigest()


def load_dataset(
    file_path: Path,
) -> list[dict[str, Any]]:
    if not file_path.exists():
        raise FileNotFoundError(
            f"Dataset not found: {file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            f"Dataset is empty: {file_path}"
        )

    raw_data = file_path.read_bytes()

    try:
        data = orjson.loads(raw_data)
    except orjson.JSONDecodeError as exc:
        raise ValueError(
            f"Invalid JSON dataset: {file_path}"
        ) from exc

    if not isinstance(data, list):
        raise ValueError(
            "Dataset root must be a JSON array."
        )

    return data


def normalize_dataset(
    input_path: Path,
    output_path: Path,
    split: str,
    dataset_version: str,
) -> dict[str, Any]:
    dataset = load_dataset(input_path)

    validator = EmotionDatasetValidator()
    normalizer = EmotionDialogueNormalizer(
        dataset_version=dataset_version
    )

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    status_counts: Counter[str] = Counter()
    emotion_counts: Counter[str] = Counter()
    coarse_emotion_counts: Counter[str] = Counter()

    schema_rejected = 0
    normalized_records = 0
    duplicate_record_ids = 0

    record_ids: set[str] = set()

    with output_path.open("wb") as output_file:
        for raw_record in tqdm(
            dataset,
            desc=f"Normalizing {split}",
            unit="record",
        ):
            try:
                record = (
                    EmotionDialogueRecord.model_validate(
                        raw_record
                    )
                )
            except ValidationError:
                schema_rejected += 1

                status_counts[
                    ValidationStatus.REJECTED.value
                ] += 1

                continue

            validation_result = validator.validate(
                record
            )

            status_counts[
                validation_result.status.value
            ] += 1

            if (
                validation_result.status
                == ValidationStatus.REJECTED
            ):
                continue

            normalized = normalizer.normalize(
                record=record,
                split=split,
            )

            if normalized.record_id in record_ids:
                duplicate_record_ids += 1
                continue

            record_ids.add(
                normalized.record_id
            )

            emotion_counts[
                normalized.emotion.emotion_code
            ] += 1

            coarse_emotion_counts[
                normalized.emotion.coarse_emotion
            ] += 1

            json_line = orjson.dumps(
                normalized.model_dump(
                    mode="json"
                ),
                option=orjson.OPT_APPEND_NEWLINE,
            )

            output_file.write(json_line)

            normalized_records += 1

    return {
        "source": {
            "file": str(input_path),
            "file_size_bytes": (
                input_path.stat().st_size
            ),
            "sha256": calculate_sha256(
                input_path
            ),
            "split": split,
            "dataset_version": dataset_version,
        },
        "output": {
            "file": str(output_path),
            "file_size_bytes": (
                output_path.stat().st_size
            ),
            "sha256": calculate_sha256(
                output_path
            ),
        },
        "summary": {
            "total_source_records": len(dataset),
            "normalized_records": normalized_records,
            "schema_rejected_records": (
                schema_rejected
            ),
            "duplicate_record_ids": (
                duplicate_record_ids
            ),
            "status": {
                "VALID": status_counts[
                    ValidationStatus.VALID.value
                ],
                "WARNING": status_counts[
                    ValidationStatus.WARNING.value
                ],
                "REJECTED": status_counts[
                    ValidationStatus.REJECTED.value
                ],
            },
        },
        "distribution": {
            "emotion": dict(
                sorted(emotion_counts.items())
            ),
            "coarse_emotion": dict(
                sorted(
                    coarse_emotion_counts.items()
                )
            ),
        },
    }


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
            "Normalize AI-Hub emotional dialogue "
            "dataset to TripPamine JSONL format."
        )
    )

    parser.add_argument(
        "--input",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--split",
        required=True,
        choices=[
            "training",
            "validation",
        ],
    )

    parser.add_argument(
        "--dataset-version",
        default="v1",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    report = normalize_dataset(
        input_path=args.input,
        output_path=args.output,
        split=args.split,
        dataset_version=args.dataset_version,
    )

    save_report(
        report=report,
        output_path=args.report,
    )

    summary = report["summary"]

    print()
    print("Normalization completed")
    print(f"Split: {args.split}")

    print(
        "Source records: "
        f"{summary['total_source_records']}"
    )

    print(
        "Normalized records: "
        f"{summary['normalized_records']}"
    )

    print(
        "Valid: "
        f"{summary['status']['VALID']}"
    )

    print(
        "Warning: "
        f"{summary['status']['WARNING']}"
    )

    print(
        "Rejected: "
        f"{summary['status']['REJECTED']}"
    )

    print(
        "Schema rejected: "
        f"{summary['schema_rejected_records']}"
    )

    print(
        "Duplicate record IDs: "
        f"{summary['duplicate_record_ids']}"
    )

    print(f"Output: {args.output}")
    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()