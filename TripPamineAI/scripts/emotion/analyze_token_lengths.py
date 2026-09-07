import argparse
import hashlib
from pathlib import Path
from typing import Any

import orjson
from tqdm import tqdm
from transformers import AutoTokenizer

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.tokenizer_statistics import (
    TokenizerStatisticsAnalyzer,
)


DEFAULT_MODEL = (
    "monologg/"
    "koelectra-base-v3-discriminator"
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


def read_dataset(
    file_path: Path,
    expected_split: str,
) -> list[
    EmotionClassificationSample
]:
    if not file_path.exists():
        raise FileNotFoundError(
            "Classification dataset "
            "not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            "Classification dataset "
            "is empty: "
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
                    f"{expected_split}"
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

            sample = (
                EmotionClassificationSample
                .model_validate(raw)
            )

            if (
                sample.source.split
                != expected_split
            ):
                raise ValueError(
                    "Classification split "
                    "mismatch: "
                    f"expected "
                    f"'{expected_split}', "
                    f"found "
                    f"'{sample.source.split}'"
                )

            samples.append(
                sample
            )

    return samples


def calculate_token_lengths(
    samples: list[
        EmotionClassificationSample
    ],
    tokenizer: Any,
    batch_size: int,
    description: str,
) -> list[int]:
    if batch_size <= 0:
        raise ValueError(
            "Batch size must be "
            "greater than zero."
        )

    lengths: list[int] = []

    for start in tqdm(
        range(
            0,
            len(samples),
            batch_size,
        ),
        desc=description,
        unit="batch",
    ):
        batch = samples[
            start:
            start + batch_size
        ]

        texts = [
            sample.text
            for sample in batch
        ]

        encoded = tokenizer(
            texts,
            add_special_tokens=True,
            truncation=False,
            padding=False,
        )

        input_ids = encoded[
            "input_ids"
        ]

        lengths.extend(
            len(token_ids)
            for token_ids
            in input_ids
        )

    if len(lengths) != len(
        samples
    ):
        raise RuntimeError(
            "Tokenizer output count "
            "does not match sample count."
        )

    return lengths


def save_report(
    report: dict[str, Any],
    output_path: Path,
) -> None:
    if output_path.exists():
        raise FileExistsError(
            "Report already exists: "
            f"{output_path}"
        )

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
            "Analyze KoELECTRA token "
            "length distribution for "
            "TripPamine emotion "
            "classification data."
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
        "--report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
    )

    parser.add_argument(
        "--batch-size",
        type=int,
        default=256,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.report.exists():
        raise FileExistsError(
            "Report already exists: "
            f"{args.report}"
        )

    if args.batch_size <= 0:
        raise ValueError(
            "Batch size must be "
            "greater than zero."
        )

    training = read_dataset(
        args.training,
        "training",
    )

    validation = read_dataset(
        args.validation,
        "validation",
    )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model
        )
    )

    training_lengths = (
        calculate_token_lengths(
            samples=training,
            tokenizer=tokenizer,
            batch_size=(
                args.batch_size
            ),
            description=(
                "Tokenizing training"
            ),
        )
    )

    validation_lengths = (
        calculate_token_lengths(
            samples=validation,
            tokenizer=tokenizer,
            batch_size=(
                args.batch_size
            ),
            description=(
                "Tokenizing validation"
            ),
        )
    )

    combined_lengths = (
        training_lengths
        + validation_lengths
    )

    analyzer = (
        TokenizerStatisticsAnalyzer()
    )

    training_statistics = (
        analyzer.analyze(
            training_lengths
        )
    )

    validation_statistics = (
        analyzer.analyze(
            validation_lengths
        )
    )

    combined_statistics = (
        analyzer.analyze(
            combined_lengths
        )
    )

    report = {
        "experiment": (
            "T0-koelectra-"
            "token-length-analysis"
        ),
        "tokenizer": {
            "model": args.model,
            "class": (
                type(tokenizer).__name__
            ),
            "vocab_size": (
                tokenizer.vocab_size
            ),
            "model_max_length": (
                tokenizer
                .model_max_length
            ),
        },
        "configuration": {
            "add_special_tokens": True,
            "truncation": False,
            "padding": False,
            "batch_size": (
                args.batch_size
            ),
            "thresholds": list(
                TokenizerStatisticsAnalyzer
                .DEFAULT_THRESHOLDS
            ),
            "test_split_used": False,
        },
        "dataset": {
            "training": {
                "file": str(
                    args.training
                ),
                "samples": len(
                    training
                ),
                "sha256": (
                    calculate_sha256(
                        args.training
                    )
                ),
            },
            "validation": {
                "file": str(
                    args.validation
                ),
                "samples": len(
                    validation
                ),
                "sha256": (
                    calculate_sha256(
                        args.validation
                    )
                ),
            },
            "combined_samples": len(
                combined_lengths
            ),
        },
        "statistics": {
            "training": (
                training_statistics
                .model_dump(
                    mode="json"
                )
            ),
            "validation": (
                validation_statistics
                .model_dump(
                    mode="json"
                )
            ),
            "combined": (
                combined_statistics
                .model_dump(
                    mode="json"
                )
            ),
        },
    }

    save_report(
        report,
        args.report,
    )

    print()
    print(
        "KoELECTRA token length "
        "analysis completed"
    )

    print()
    print(
        "Training samples: "
        f"{len(training)}"
    )

    print(
        "Validation samples: "
        f"{len(validation)}"
    )

    print(
        "Combined samples: "
        f"{len(combined_lengths)}"
    )

    print(
        "Test split used: False"
    )

    print()
    print(
        "Combined token lengths"
    )

    print(
        "Minimum: "
        f"{combined_statistics.minimum}"
    )

    print(
        "Mean: "
        f"{combined_statistics.mean:.2f}"
    )

    print(
        "P50: "
        f"{combined_statistics.p50:.2f}"
    )

    print(
        "P90: "
        f"{combined_statistics.p90:.2f}"
    )

    print(
        "P95: "
        f"{combined_statistics.p95:.2f}"
    )

    print(
        "P99: "
        f"{combined_statistics.p99:.2f}"
    )

    print(
        "Maximum: "
        f"{combined_statistics.maximum}"
    )

    print()
    print(
        "Truncation candidates"
    )

    for item in (
        combined_statistics
        .thresholds
    ):
        print(
            f"> {item.threshold}: "
            f"{item.exceeded_count} "
            f"({item.exceeded_rate:.4%})"
        )

    print()
    print(
        f"Report: "
        f"{args.report}"
    )


if __name__ == "__main__":
    main()