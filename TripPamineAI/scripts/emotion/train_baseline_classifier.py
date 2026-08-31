import argparse
import hashlib
import time
import warnings
from pathlib import Path
from typing import Any

import orjson
import sklearn
from sklearn.exceptions import (
    ConvergenceWarning,
)
from tqdm import tqdm

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.evaluation.emotion.classification_metrics import (
    EmotionClassificationEvaluator,
)
from trippamine_ai.models.emotion.baseline_classifier import (
    BaselineEmotionClassifier,
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
                "emotion mapping for "
                f"{sample.label}: "
                f"'{existing}' vs "
                f"'{sample.coarse_label}'"
            )

        mapping[
            sample.label
        ] = sample.coarse_label

    return mapping


def validate_label_sets(
        training: list[
            EmotionClassificationSample
        ],
        validation: list[
            EmotionClassificationSample
        ],
) -> None:
    training_labels = {
        sample.label
        for sample in training
    }

    validation_labels = {
        sample.label
        for sample in validation
    }

    if len(training_labels) != 60:
        raise ValueError(
            "Training dataset must "
            "contain 60 fine emotion "
            "labels, found "
            f"{len(training_labels)}."
        )

    if (
            training_labels
            != validation_labels
    ):
        raise ValueError(
            "Training/validation "
            "label sets do not match."
        )


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
            "Train TripPamine "
            "TF-IDF Logistic Regression "
            "emotion baseline."
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
        "--model-output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--class-weight",
        choices=[
            "none",
            "balanced",
        ],
        default="none",
    )

    parser.add_argument(
        "--vectorizer",
        choices=[
            "word",
            "char",
            "hybrid",
        ],
        default="word",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.model_output.exists():
        raise FileExistsError(
            "Model output already exists: "
            f"{args.model_output}"
        )

    if args.report.exists():
        raise FileExistsError(
            "Report already exists: "
            f"{args.report}"
        )

    training = read_dataset(
        args.training,
        "training",
    )

    validation = read_dataset(
        args.validation,
        "validation",
    )

    validate_label_sets(
        training,
        validation,
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            training
        )
    )

    training_texts = [
        sample.text
        for sample in training
    ]

    training_labels = [
        sample.label
        for sample in training
    ]

    validation_texts = [
        sample.text
        for sample in validation
    ]

    validation_labels = [
        sample.label
        for sample in validation
    ]

    class_weight = (
        None
        if args.class_weight == "none"
        else "balanced"
    )

    if (
            args.vectorizer == "word"
            and class_weight is None
    ):
        experiment = (
            "B0-tfidf-word-"
            "logistic-regression"
        )

    elif (
            args.vectorizer == "word"
            and class_weight == "balanced"
    ):
        experiment = (
            "B1-tfidf-word-"
            "logistic-regression-balanced"
        )

    elif (
            args.vectorizer == "char"
            and class_weight is None
    ):
        experiment = (
            "B2-tfidf-char-"
            "logistic-regression"
        )

    elif (
            args.vectorizer == "hybrid"
            and class_weight is None
    ):
        experiment = (
            "B3-tfidf-word-char-"
            "logistic-regression"
        )

    else:
        experiment = (
            "experimental-tfidf-"
            f"{args.vectorizer}-"
            "logistic-regression-"
            f"{args.class_weight}"
        )

    classifier = (
        BaselineEmotionClassifier(
            class_weight=class_weight,
            vectorizer_type=(
                args.vectorizer
            ),
        )
    )

    start_time = (
        time.perf_counter()
    )

    with warnings.catch_warnings(
            record=True
    ) as caught_warnings:
        warnings.simplefilter(
            "always",
            ConvergenceWarning,
        )

        classifier.fit(
            training_texts,
            training_labels,
        )

    training_seconds = (
            time.perf_counter()
            - start_time
    )

    convergence_warnings = [
        str(warning.message)
        for warning in caught_warnings
        if issubclass(
            warning.category,
            ConvergenceWarning,
        )
    ]

    predictions = (
        classifier.predict(
            validation_texts
        )
    )

    evaluation = (
        EmotionClassificationEvaluator()
        .evaluate(
            true_labels=(
                validation_labels
            ),
            predicted_labels=(
                predictions
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    classifier.save(
        args.model_output
    )

    report = {
        "experiment": experiment,
        "scikit_learn_version": (
            sklearn.__version__
        ),
        "dataset": {
            "training": {
                "file": str(
                    args.training
                ),
                "samples": len(
                    training
                ),
                "sha256": calculate_sha256(
                    args.training
                ),
            },
            "validation": {
                "file": str(
                    args.validation
                ),
                "samples": len(
                    validation
                ),
                "sha256": calculate_sha256(
                    args.validation
                ),
            },
            "fine_labels": len(
                fine_to_coarse
            ),
            "coarse_labels": len(
                set(
                    fine_to_coarse.values()
                )
            ),
        },
        "model": (
            classifier.get_parameters()
        ),
        "training": {
            "seconds": (
                training_seconds
            ),
            "convergence_warnings": (
                convergence_warnings
            ),
        },
        "validation": (
            evaluation.model_dump(
                mode="json"
            )
        ),
        "artifact": {
            "file": str(
                args.model_output
            ),
            "sha256": calculate_sha256(
                args.model_output
            ),
        },
    }

    save_report(
        report,
        args.report,
    )

    fine = (
        evaluation.fine.aggregate
    )

    coarse = (
        evaluation.coarse.aggregate
    )

    print()
    print(
        "Baseline emotion classifier "
        "training completed"
    )

    print()
    print(
        f"Training samples: "
        f"{len(training)}"
    )

    print(
        f"Validation samples: "
        f"{len(validation)}"
    )

    print(
        "Fine labels: "
        f"{len(fine_to_coarse)}"
    )

    print(
        "Coarse labels: "
        f"{len(set(fine_to_coarse.values()))}"
    )

    print()
    print(
        "Fine emotion validation"
    )

    print(
        f"Accuracy: "
        f"{fine.accuracy:.6f}"
    )

    print(
        f"Macro precision: "
        f"{fine.macro_precision:.6f}"
    )

    print(
        f"Macro recall: "
        f"{fine.macro_recall:.6f}"
    )

    print(
        f"Macro F1: "
        f"{fine.macro_f1:.6f}"
    )

    print(
        f"Weighted F1: "
        f"{fine.weighted_f1:.6f}"
    )

    print()
    print(
        "Coarse emotion validation"
    )

    print(
        f"Accuracy: "
        f"{coarse.accuracy:.6f}"
    )

    print(
        f"Macro F1: "
        f"{coarse.macro_f1:.6f}"
    )

    print()
    print(
        "Training seconds: "
        f"{training_seconds:.2f}"
    )

    print(
        "Convergence warnings: "
        f"{len(convergence_warnings)}"
    )

    print()
    print(
        f"Model: "
        f"{args.model_output}"
    )

    print(
        f"Report: "
        f"{args.report}"
    )


if __name__ == "__main__":
    main()
