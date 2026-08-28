import argparse
import hashlib
from collections import Counter
from pathlib import Path
from typing import Any

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationBuilder,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


def calculate_sha256(file_path: Path) -> str:
    digest = hashlib.sha256()

    with file_path.open("rb") as file:
        while chunk := file.read(1024 * 1024):
            digest.update(chunk)

    return digest.hexdigest()


def build_dataset(
        input_path: Path,
        output_path: Path,
) -> dict[str, Any]:
    if not input_path.exists():
        raise FileNotFoundError(
            f"Input dataset not found: {input_path}"
        )

    if input_path.stat().st_size == 0:
        raise ValueError(
            f"Input dataset is empty: {input_path}"
        )

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    builder = EmotionClassificationBuilder()

    total_records = 0
    written_records = 0

    label_counts: Counter[str] = Counter()
    coarse_label_counts: Counter[str] = Counter()
    quality_counts: Counter[str] = Counter()
    issue_counts: Counter[str] = Counter()

    with (
        input_path.open("rb") as input_file,
        output_path.open("wb") as output_file,
    ):
        for line in tqdm(
                input_file,
                desc=f"Building {input_path.name}",
                unit="record",
        ):
            if not line.strip():
                continue

            total_records += 1

            raw_record = orjson.loads(line)

            record = (
                NormalizedEmotionDialogue.model_validate(
                    raw_record
                )
            )

            sample = builder.build(record)

            output_file.write(
                orjson.dumps(
                    sample.model_dump(mode="json"),
                    option=orjson.OPT_APPEND_NEWLINE,
                )
            )

            written_records += 1

            label_counts[
                sample.label
            ] += 1

            coarse_label_counts[
                sample.coarse_label
            ] += 1

            quality_counts[
                sample.quality_status
            ] += 1

            for issue_code in (
                    sample.quality_issue_codes
            ):
                issue_counts[
                    issue_code
                ] += 1

    return {
        "source": {
            "file": str(input_path),
            "file_size_bytes": (
                input_path.stat().st_size
            ),
            "sha256": calculate_sha256(
                input_path
            ),
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
            "total_records": total_records,
            "written_records": written_records,
        },
        "distribution": {
            "fine_emotion": dict(
                sorted(label_counts.items())
            ),
            "coarse_emotion": dict(
                sorted(coarse_label_counts.items())
            ),
            "quality": dict(
                sorted(quality_counts.items())
            ),
            "issues": dict(
                sorted(issue_counts.items())
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
            "Build TripPamine emotion "
            "classification dataset."
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

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    report = build_dataset(
        input_path=args.input,
        output_path=args.output,
    )

    save_report(
        report=report,
        output_path=args.report,
    )

    summary = report["summary"]

    print()
    print("Classification dataset completed")
    print(
        f"Source records: "
        f"{summary['total_records']}"
    )
    print(
        f"Written records: "
        f"{summary['written_records']}"
    )
    print(f"Output: {args.output}")
    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()
