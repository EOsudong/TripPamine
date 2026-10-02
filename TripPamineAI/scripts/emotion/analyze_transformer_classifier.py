import argparse
import hashlib
import math
import sys
import time
from pathlib import Path
from typing import Any

import orjson
import torch
import transformers
from sklearn.metrics import (
    precision_recall_fscore_support,
)
from tqdm import tqdm
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
)

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    get_coarse_emotion,
)
from trippamine_ai.evaluation.emotion.classification_error_analysis import (
    EmotionClassificationErrorAnalyzer,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


DEFAULT_MAX_LENGTH = 96

DEFAULT_EVAL_BATCH_SIZE = 64

DEFAULT_TOP_K = 15


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


def validate_positive_integer(
        value: int,
        name: str,
) -> None:
    if (
            isinstance(
                value,
                bool,
            )
            or not isinstance(
                value,
                int,
            )
            or value <= 0
    ):
        raise ValueError(
            f"{name} must be "
            "a positive integer."
        )


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
                    desc="Loading validation",
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

    if not samples:
        raise ValueError(
            "Validation dataset "
            "contains no samples."
        )

    return samples


def build_fine_to_coarse(
        label_mapping: EmotionLabelMapping,
) -> dict[str, str]:
    return {
        label: get_coarse_emotion(
            label
        )
        for label in (
            label_mapping.labels
        )
    }


def validate_validation_samples(
        samples: list[
            EmotionClassificationSample
        ],
        label_mapping: EmotionLabelMapping,
) -> None:
    for sample in samples:
        label_mapping.encode(
            sample.label
        )

        expected_coarse = (
            get_coarse_emotion(
                sample.label
            )
        )

        if (
                sample.coarse_label
                != expected_coarse
        ):
            raise ValueError(
                "Inconsistent coarse "
                "emotion mapping for "
                f"{sample.label}: "
                f"expected "
                f"'{expected_coarse}', "
                f"found "
                f"'{sample.coarse_label}'"
            )


def validate_model_config(
        config: Any,
        label_mapping: EmotionLabelMapping,
) -> None:
    if int(
            config.num_labels
    ) != label_mapping.num_labels:
        raise ValueError(
            "Model label count does "
            "not match the 60-label "
            "codebook: "
            f"{config.num_labels}"
        )

    raw_label2id = getattr(
        config,
        "label2id",
        None,
    )

    raw_id2label = getattr(
        config,
        "id2label",
        None,
    )

    if not isinstance(
            raw_label2id,
            dict,
    ):
        raise ValueError(
            "Model label2id mapping "
            "is missing."
        )

    if not isinstance(
            raw_id2label,
            dict,
    ):
        raise ValueError(
            "Model id2label mapping "
            "is missing."
        )

    actual_label2id = {
        str(label): int(label_id)
        for label, label_id
        in raw_label2id.items()
    }

    actual_id2label = {
        int(label_id): str(label)
        for label_id, label
        in raw_id2label.items()
    }

    if actual_label2id != dict(
            label_mapping.label2id
    ):
        raise ValueError(
            "Model label2id mapping "
            "does not match the "
            "emotion codebook."
        )

    if actual_id2label != dict(
            label_mapping.id2label
    ):
        raise ValueError(
            "Model id2label mapping "
            "does not match the "
            "emotion codebook."
        )


def predict_validation(
        model: torch.nn.Module,
        tokenizer: Any,
        samples: list[
            EmotionClassificationSample
        ],
        label_mapping: EmotionLabelMapping,
        max_length: int,
        eval_batch_size: int,
        device: torch.device,
) -> tuple[
    list[str],
    list[str],
    list[float],
]:
    model.eval()

    true_labels = [
        sample.label
        for sample in samples
    ]

    predicted_labels = []
    top1_confidences = []

    with torch.inference_mode():
        for start in tqdm(
                range(
                    0,
                    len(samples),
                    eval_batch_size,
                ),
                desc=(
                    "Analyzing validation"
                ),
                unit="batch",
        ):
            batch = samples[
                start:
                start + eval_batch_size
            ]

            texts = [
                sample.text
                for sample in batch
            ]

            encoded = tokenizer(
                texts,
                padding=True,
                truncation=True,
                max_length=max_length,
                return_tensors="pt",
            )

            model_inputs = {
                key: value.to(
                    device
                )
                for key, value
                in encoded.items()
            }

            outputs = model(
                **model_inputs
            )

            logits = (
                outputs.logits
            )

            if logits.ndim != 2:
                raise RuntimeError(
                    "Model logits must be "
                    "two-dimensional."
                )

            if (
                    logits.shape[-1]
                    != label_mapping.num_labels
            ):
                raise RuntimeError(
                    "Model logits label "
                    "dimension mismatch: "
                    f"{logits.shape[-1]}"
                )

            if not torch.isfinite(
                    logits
            ).all():
                raise RuntimeError(
                    "Model logits contain "
                    "non-finite values."
                )

            probabilities = (
                torch.softmax(
                    logits,
                    dim=-1,
                )
            )

            (
                batch_confidences,
                predicted_ids,
            ) = probabilities.max(
                dim=-1
            )

            predicted_labels.extend(
                label_mapping.decode(
                    int(label_id)
                )
                for label_id
                in (
                    predicted_ids
                    .detach()
                    .cpu()
                    .tolist()
                )
            )

            top1_confidences.extend(
                float(value)
                for value
                in (
                    batch_confidences
                    .detach()
                    .cpu()
                    .tolist()
                )
            )

    if len(
            predicted_labels
    ) != len(
            true_labels
    ):
        raise RuntimeError(
            "Prediction count mismatch."
        )

    if len(
            top1_confidences
    ) != len(
            true_labels
    ):
        raise RuntimeError(
            "Confidence count mismatch."
        )

    return (
        true_labels,
        predicted_labels,
        top1_confidences,
    )


def build_all_label_metrics(
        true_labels: list[str],
        predicted_labels: list[str],
        label_mapping: EmotionLabelMapping,
) -> list[
    dict[str, Any]
]:
    labels = list(
        label_mapping.labels
    )

    (
        precision,
        recall,
        f1,
        support,
    ) = precision_recall_fscore_support(
        true_labels,
        predicted_labels,
        labels=labels,
        average=None,
        zero_division=0,
    )

    return [
        {
            "label": label,
            "label_name": (
                label
            ),
            "coarse_label": (
                get_coarse_emotion(
                    label
                )
            ),
            "precision": float(
                precision[index]
            ),
            "recall": float(
                recall[index]
            ),
            "f1": float(
                f1[index]
            ),
            "support": int(
                support[index]
            ),
        }
        for index, label
        in enumerate(labels)
    ]


def mean_or_none(
        values: list[float],
) -> float | None:
    if not values:
        return None

    value = (
        math.fsum(
            values
        )
        / len(values)
    )

    if not math.isfinite(
            value
    ):
        raise RuntimeError(
            "Confidence mean is "
            "not finite."
        )

    return value


def build_confidence_summary(
        true_labels: list[str],
        predicted_labels: list[str],
        confidences: list[float],
) -> dict[str, Any]:
    if not (
            len(true_labels)
            == len(predicted_labels)
            == len(confidences)
    ):
        raise ValueError(
            "Prediction confidence "
            "input lengths must match."
        )

    correct_confidences = []
    incorrect_confidences = []

    for (
            true_label,
            predicted_label,
            confidence,
    ) in zip(
            true_labels,
            predicted_labels,
            confidences,
            strict=True,
    ):
        if not math.isfinite(
                confidence
        ):
            raise ValueError(
                "Prediction confidence "
                "must be finite."
            )

        if (
                confidence < 0.0
                or confidence > 1.0
        ):
            raise ValueError(
                "Prediction confidence "
                "must be between "
                "0 and 1."
            )

        if (
                true_label
                == predicted_label
        ):
            correct_confidences.append(
                confidence
            )

        else:
            incorrect_confidences.append(
                confidence
            )

    return {
        "mean_top1_confidence": (
            mean_or_none(
                confidences
            )
        ),
        "correct_mean_top1_confidence": (
            mean_or_none(
                correct_confidences
            )
        ),
        "incorrect_mean_top1_confidence": (
            mean_or_none(
                incorrect_confidences
            )
        ),
        "correct_samples": len(
            correct_confidences
        ),
        "incorrect_samples": len(
            incorrect_confidences
        ),
    }


def save_report(
        report: dict[str, Any],
        output_path: Path,
) -> None:
    if output_path.exists():
        raise FileExistsError(
            "Analysis report already "
            "exists: "
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
            "Analyze validation errors "
            "for the TripPamine "
            "KoELECTRA fine emotion "
            "classifier."
        )
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--model-dir",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--max-length",
        type=int,
        default=DEFAULT_MAX_LENGTH,
    )

    parser.add_argument(
        "--eval-batch-size",
        type=int,
        default=DEFAULT_EVAL_BATCH_SIZE,
    )

    parser.add_argument(
        "--top-k",
        type=int,
        default=DEFAULT_TOP_K,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output.exists():
        raise FileExistsError(
            "Analysis report already "
            "exists: "
            f"{args.output}"
        )

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.eval_batch_size,
        "Evaluation batch size",
    )

    validate_positive_integer(
        args.top_k,
        "Top K",
    )

    if not args.model_dir.exists():
        raise FileNotFoundError(
            "Model directory not found: "
            f"{args.model_dir}"
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. "
            "Transformer error analysis "
            "cannot run."
        )

    device_index = (
        torch.cuda.current_device()
    )

    device = torch.device(
        "cuda",
        device_index,
    )

    label_mapping = (
        EmotionLabelMapping()
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            label_mapping
        )
    )

    validation = (
        read_validation_dataset(
            args.validation
        )
    )

    validate_validation_samples(
        samples=validation,
        label_mapping=label_mapping,
    )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model_dir,
            local_files_only=True,
        )
    )

    model = (
        AutoModelForSequenceClassification
        .from_pretrained(
            args.model_dir,
            local_files_only=True,
        )
    )

    validate_model_config(
        model.config,
        label_mapping,
    )

    model.float()

    model.to(
        device
    )

    model_device = (
        next(
            model.parameters()
        )
        .device
    )

    if model_device.type != "cuda":
        raise RuntimeError(
            "Analysis model is not "
            "on a CUDA device: "
            f"{model_device}"
        )

    torch.cuda.empty_cache()

    torch.cuda.synchronize(
        device_index
    )

    started_at = (
        time.perf_counter()
    )

    (
        true_labels,
        predicted_labels,
        confidences,
    ) = predict_validation(
        model=model,
        tokenizer=tokenizer,
        samples=validation,
        label_mapping=label_mapping,
        max_length=(
            args.max_length
        ),
        eval_batch_size=(
            args.eval_batch_size
        ),
        device=device,
    )

    torch.cuda.synchronize(
        device_index
    )

    analysis_seconds = (
        time.perf_counter()
        - started_at
    )

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
            top_k=(
                args.top_k
            ),
        )
    )

    all_label_metrics = (
        build_all_label_metrics(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                predicted_labels
            ),
            label_mapping=(
                label_mapping
            ),
        )
    )

    confidence_summary = (
        build_confidence_summary(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                predicted_labels
            ),
            confidences=(
                confidences
            ),
        )
    )

    true_label_set = set(
        true_labels
    )

    predicted_label_set = set(
        predicted_labels
    )

    missing_true_labels = [
        label
        for label in (
            label_mapping.labels
        )
        if label not in true_label_set
    ]

    never_predicted_labels = [
        label
        for label in (
            label_mapping.labels
        )
        if label not in predicted_label_set
    ]

    report = {
        "experiment": (
            "T0-Q7-A-"
            "validation-error-analysis"
        ),
        "versions": {
            "python": (
                sys.version
            ),
            "torch": (
                torch.__version__
            ),
            "transformers": (
                transformers.__version__
            ),
            "cuda_runtime": (
                torch.version.cuda
            ),
        },
        "source": {
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
            "model": {
                "directory": str(
                    args.model_dir
                ),
            },
            "test_split_used": False,
        },
        "protocol": {
            "max_length": (
                args.max_length
            ),
            "eval_batch_size": (
                args.eval_batch_size
            ),
            "precision": "fp32",
            "padding": "dynamic",
            "expected_label_count": (
                label_mapping.num_labels
            ),
            "top_k": (
                args.top_k
            ),
        },
        "coverage": {
            "true_label_count": len(
                true_label_set
            ),
            "predicted_label_count": len(
                predicted_label_set
            ),
            "missing_true_labels": (
                missing_true_labels
            ),
            "never_predicted_labels": (
                never_predicted_labels
            ),
        },
        "confidence": (
            confidence_summary
        ),
        "all_label_metrics": (
            all_label_metrics
        ),
        "analysis": (
            analysis.model_dump(
                mode="json"
            )
        ),
        "timing": {
            "analysis_seconds": (
                analysis_seconds
            ),
            "samples_per_second": (
                len(validation)
                / analysis_seconds
            ),
        },
    }

    save_report(
        report=report,
        output_path=args.output,
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
        "KoELECTRA validation "
        "error analysis completed"
    )

    print()
    print(
        "Validation samples: "
        f"{analysis.total_samples}"
    )

    print(
        "Test split used: False"
    )

    print()
    print(
        "Fine accuracy: "
        f"{fine.accuracy:.6f}"
    )

    print(
        "Fine Macro F1: "
        f"{fine.macro_f1:.6f}"
    )

    print(
        "Coarse Macro F1: "
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
    print(
        "Validation true labels: "
        f"{len(true_label_set)}"
    )

    print(
        "Predicted labels: "
        f"{len(predicted_label_set)}"
    )

    print(
        "Missing true labels: "
        f"{missing_true_labels}"
    )

    print(
        "Never predicted labels: "
        f"{never_predicted_labels}"
    )

    print()
    print(
        "Mean confidence: "
        f"{confidence_summary['mean_top1_confidence']:.6f}"
    )

    correct_confidence = (
        confidence_summary[
            "correct_mean_top1_confidence"
        ]
    )

    incorrect_confidence = (
        confidence_summary[
            "incorrect_mean_top1_confidence"
        ]
    )

    if correct_confidence is not None:
        print(
            "Correct mean confidence: "
            f"{correct_confidence:.6f}"
        )

    if incorrect_confidence is not None:
        print(
            "Incorrect mean confidence: "
            f"{incorrect_confidence:.6f}"
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
            analysis
            .top_fine_confusions
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
    print(
        "Analysis seconds: "
        f"{analysis_seconds:.2f}"
    )

    print(
        "Report: "
        f"{args.output}"
    )

    print()
    print(
        "VALIDATION ERROR "
        "ANALYSIS: PASS"
    )


if __name__ == "__main__":
    main()