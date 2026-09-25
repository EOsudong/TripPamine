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


def load_dataset(file_path: Path) -> list[dict[str, Any]]:
    raw_data = file_path.read_bytes()

    data = orjson.loads(raw_data)

    if not isinstance(data, list):
        raise ValueError(
            "Dataset root must be a JSON array."
        )

    return data


def build_schema_issue_key(
        error: dict[str, Any],
) -> str:
    location = ".".join(
        str(part)
        for part in error["loc"]
    )

    return f"{error['type']}:{location}"


def validate_dataset(
        file_path: Path,
        split: str,
) -> dict[str, Any]:
    dataset = load_dataset(file_path)

    validator = EmotionDatasetValidator()

    status_counts: Counter[str] = Counter()
    issue_counts: Counter[str] = Counter()
    schema_issue_counts: Counter[str] = Counter()

    emotion_counts: Counter[str] = Counter()
    situation_counts: Counter[str] = Counter()
    age_counts: Counter[str] = Counter()
    gender_counts: Counter[str] = Counter()

    parsed_records = 0
    schema_rejected_records = 0

    for raw_record in tqdm(
            dataset,
            desc=f"Validating {split}",
            unit="record",
    ):
        try:
            record = EmotionDialogueRecord.model_validate(
                raw_record
            )
        except ValidationError as exc:
            schema_rejected_records += 1
            status_counts[
                ValidationStatus.REJECTED.value
            ] += 1

            for error in exc.errors():
                schema_issue_counts[
                    build_schema_issue_key(error)
                ] += 1

            continue

        parsed_records += 1

        emotion = record.profile.emotion
        persona = record.profile.persona

        emotion_counts[emotion.type] += 1

        if emotion.situation:
            situation_counts[
                emotion.situation[0]
            ] += 1

        if len(persona.human) >= 1:
            age_counts[
                persona.human[0]
            ] += 1

        if len(persona.human) >= 2:
            gender_counts[
                persona.human[1]
            ] += 1

        result = validator.validate(record)

        status_counts[result.status.value] += 1

        for issue in result.issues:
            issue_counts[issue.code] += 1

    total_records = len(dataset)

    return {
        "dataset": {
            "split": split,
            "source_file": file_path.name,
            "source_path": str(file_path),
            "file_size_bytes": file_path.stat().st_size,
            "sha256": calculate_sha256(file_path),
        },
        "summary": {
            "total_records": total_records,
            "parsed_records": parsed_records,
            "schema_rejected_records": (
                schema_rejected_records
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
        "issues": {
            "validation": dict(
                sorted(issue_counts.items())
            ),
            "schema": dict(
                sorted(schema_issue_counts.items())
            ),
        },
        "distribution": {
            "emotion": dict(
                sorted(emotion_counts.items())
            ),
            "situation": dict(
                sorted(situation_counts.items())
            ),
            "age": dict(
                sorted(age_counts.items())
            ),
            "gender": dict(
                sorted(gender_counts.items())
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
            "Validate AI-Hub emotional dialogue dataset."
        )
    )

    parser.add_argument(
        "--input",
        required=True,
        type=Path,
        help="Path to the source JSON dataset.",
    )

    parser.add_argument(
        "--split",
        required=True,
        choices=["training", "validation"],
        help="Dataset split name.",
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
        help="Path to validation report JSON.",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if not args.input.exists():
        raise FileNotFoundError(
            f"Dataset not found: {args.input}"
        )

    report = validate_dataset(
        file_path=args.input,
        split=args.split,
    )

    save_report(
        report=report,
        output_path=args.output,
    )

    summary = report["summary"]

    print()
    print("Validation completed")
    print(f"Split: {args.split}")
    print(
        f"Total: {summary['total_records']}"
    )
    print(
        f"Valid: {summary['status']['VALID']}"
    )
    print(
        f"Warning: {summary['status']['WARNING']}"
    )
    print(
        f"Rejected: {summary['status']['REJECTED']}"
    )
    print(
        f"Report: {args.output}"
    )


if __name__ == "__main__":
    main()
