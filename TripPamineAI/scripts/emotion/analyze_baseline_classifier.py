import argparse
import hashlib
import pickle
from pathlib import Path
from typing import Any

import orjson
from sklearn.feature_extraction.text import (
    TfidfVectorizer,
)
from sklearn.linear_model import (
    LogisticRegression,
)
from sklearn.pipeline import (
    Pipeline,
)
from tqdm import tqdm

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.evaluation.emotion.classification_error_analysis import (
    EmotionClassificationErrorAnalyzer,
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


def read_validation_dataset(
    file_path: Path,
) -> list[
    EmotionClassificationSample
]:
    if not file_path.exists():
        raise FileNotFoundError(
            "Validation dataset "
            "not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            "Validation dataset "
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
                    "Loading validation"
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
                != "validation"
            ):
                raise ValueError(
                    "Expected validation "
                    "sample, found split "
                    f"'{sample.source.split}'."
                )

            samples.append(
                sample
            )

    return samples


def read_b2_model(
    file_path: Path,
) -> Pipeline:
    if not file_path.exists():
        raise FileNotFoundError(
            "Baseline model not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            "Baseline model is empty: "
            f"{file_path}"
        )

    with file_path.open(
        "rb"
    ) as file:
        model = pickle.load(
            file
        )

    if not isinstance(
        model,
        Pipeline,
    ):
        raise ValueError(
            "Baseline artifact is not "
            "a sklearn Pipeline."
        )

    vectorizer = (
        model.named_steps.get(
            "tfidf"
        )
    )

    classifier = (
        model.named_steps.get(
            "classifier"
        )
    )

    if not isinstance(
        vectorizer,
        TfidfVectorizer,
    ):
        raise ValueError(
            "B2 model must use a "
            "TfidfVectorizer."
        )

    if (
        vectorizer.analyzer
        != "char"
    ):
        raise ValueError(
            "B2 model must use "
            "character TF-IDF."
        )

    if (
        vectorizer.ngram_range
        != (2, 5)
    ):
        raise ValueError(
            "B2 model must use "
            "character n-gram 2-5."
        )

    if not isinstance(
        classifier,
        LogisticRegression,
    ):
        raise ValueError(
            "B2 model must use "
            "LogisticRegression."
        )

    if (
        classifier.class_weight
        is not None
    ):
        raise ValueError(
            "B2 model must use "
            "class_weight=None."
        )

    return model


def build_fine_to_coarse(
    samples: list[
        EmotionClassificationSample
    ],
) -> dict[str, str]:
    mapping: dict[
        str,
        str,
    ] = {}

    for sample in samples:
        existing = mapping.get(
            sample.label
        )

        if (
            existing is not None
            and existing
            != sample.coarse_label
        ):
            raise ValueError(
                "Inconsistent coarse "
                "mapping for "
                f"{sample.label}."
            )

        mapping[
            sample.label
        ] = sample.coarse_label

    return mapping


def save_report(
    report: dict[str, Any],
    output_path: Path,
) -> None:
    if output_path.exists():
        raise FileExistsError(
            "Analysis report "
            "already exists: "
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
            "Analyze TripPamine B2 "
            "character TF-IDF "
            "emotion baseline errors."
        )
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--model",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--top-k",
        type=int,
        default=10,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output.exists():
        raise FileExistsError(
            "Analysis report "
            "already exists: "
            f"{args.output}"
        )

    validation = (
        read_validation_dataset(
            args.validation
        )
    )

    model = read_b2_model(
        args.model
    )

    texts = [
        sample.text
        for sample in validation
    ]

    true_labels = [
        sample.label
        for sample in validation
    ]

    fine_to_coarse = (
        build_fine_to_coarse(
            validation
        )
    )

    raw_predictions = (
        model.predict(
            texts
        )
    )

    predicted_labels = [
        str(label)
        for label
        in raw_predictions
    ]

    analysis = (
        EmotionClassificationErrorAnalyzer()
        .analyze(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                predicted_labels
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
            top_k=args.top_k,
        )
    )

    report = {
        "experiment": (
            "B2-tfidf-char-"
            "logistic-regression"
        ),
        "source": {
            "validation": {
                "file": str(
                    args.validation
                ),
                "sha256": calculate_sha256(
                    args.validation
                ),
                "samples": len(
                    validation
                ),
            },
            "model": {
                "file": str(
                    args.model
                ),
                "sha256": calculate_sha256(
                    args.model
                ),
            },
        },
        "analysis": (
            analysis.model_dump(
                mode="json"
            )
        ),
    }

    save_report(
        report=report,
        output_path=(
            args.output
        ),
    )

    fine = (
        analysis
        .evaluation
        .fine
        .aggregate
    )

    coarse = (
        analysis
        .evaluation
        .coarse
        .aggregate
    )

    boundary = (
        analysis.error_boundary
    )

    print()
    print(
        "B2 baseline error "
        "analysis completed"
    )

    print()
    print(
        f"Samples: "
        f"{analysis.total_samples}"
    )

    print(
        f"Correct: "
        f"{analysis.correct_predictions}"
    )

    print(
        f"Incorrect: "
        f"{analysis.incorrect_predictions}"
    )

    print()
    print(
        f"Fine accuracy: "
        f"{fine.accuracy:.6f}"
    )

    print(
        f"Fine Macro F1: "
        f"{fine.macro_f1:.6f}"
    )

    print(
        f"Coarse Macro F1: "
        f"{coarse.macro_f1:.6f}"
    )

    print()
    print(
        "Same-coarse errors: "
        f"{boundary.same_coarse_errors} "
        f"("
        f"{boundary.same_coarse_error_rate:.2%}"
        f")"
    )

    print(
        "Cross-coarse errors: "
        f"{boundary.cross_coarse_errors} "
        f"("
        f"{boundary.cross_coarse_error_rate:.2%}"
        f")"
    )

    print()
    print("Best fine labels")

    for metric in (
        analysis
        .label_ranking
        .best_labels
    ):
        print(
            f"{metric.label}: "
            f"F1={metric.f1:.6f}, "
            f"P={metric.precision:.6f}, "
            f"R={metric.recall:.6f}, "
            f"support={metric.support}"
        )

    print()
    print("Worst fine labels")

    for metric in (
        analysis
        .label_ranking
        .worst_labels
    ):
        print(
            f"{metric.label}: "
            f"F1={metric.f1:.6f}, "
            f"P={metric.precision:.6f}, "
            f"R={metric.recall:.6f}, "
            f"support={metric.support}"
        )

    print()
    print("Top fine confusions")

    for confusion in (
        analysis.top_fine_confusions
    ):
        print(
            f"{confusion.true_label}"
            f" -> "
            f"{confusion.predicted_label}: "
            f"{confusion.count}, "
            f"same_coarse="
            f"{confusion.same_coarse}"
        )

    print()
    print("Cross-coarse confusions")

    for confusion in (
        analysis
        .cross_coarse_confusions
    ):
        print(
            f"{confusion.true_coarse}"
            f" -> "
            f"{confusion.predicted_coarse}: "
            f"{confusion.count}"
        )

    print()
    print(
        f"Report: "
        f"{args.output}"
    )


if __name__ == "__main__":
    main()