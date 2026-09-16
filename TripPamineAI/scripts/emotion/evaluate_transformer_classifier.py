import argparse
import hashlib
import math
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import orjson
import torch
import transformers
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
from trippamine_ai.evaluation.emotion.classification_metrics import (
    EmotionClassificationEvaluator,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


DEFAULT_MAX_LENGTH = 96

DEFAULT_EVAL_BATCH_SIZE = 64


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


def calculate_artifact_files(
        directory: Path,
) -> list[
    dict[str, Any]
]:
    if not directory.exists():
        raise FileNotFoundError(
            "Model artifact directory "
            "not found: "
            f"{directory}"
        )

    if not directory.is_dir():
        raise ValueError(
            "Model artifact path must "
            "be a directory: "
            f"{directory}"
        )

    artifacts = []

    for file_path in sorted(
            path
            for path in directory.rglob("*")
            if path.is_file()
    ):
        artifacts.append(
            {
                "file": str(
                    file_path.relative_to(
                        directory
                    )
                ),
                "bytes": (
                    file_path.stat().st_size
                ),
                "sha256": (
                    calculate_sha256(
                        file_path
                    )
                ),
            }
        )

    if not artifacts:
        raise RuntimeError(
            "Model artifact directory "
            "is empty: "
            f"{directory}"
        )

    return artifacts


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


def read_test_dataset(
        file_path: Path,
) -> list[
    EmotionClassificationSample
]:
    if not file_path.exists():
        raise FileNotFoundError(
            "Classification test "
            "dataset not found: "
            f"{file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            "Classification test "
            "dataset is empty: "
            f"{file_path}"
        )

    samples = []

    with file_path.open(
            "rb"
    ) as file:
        for line_number, line in enumerate(
                tqdm(
                    file,
                    desc="Loading test",
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
                    != "test"
            ):
                raise ValueError(
                    "Classification split "
                    "mismatch: expected "
                    "'test', found "
                    f"'{sample.source.split}'"
                )

            samples.append(
                sample
            )

    if not samples:
        raise ValueError(
            "Classification test "
            "dataset contains no samples."
        )

    return samples


def build_codebook_fine_to_coarse(
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


def validate_test_samples(
        samples: list[
            EmotionClassificationSample
        ],
        label_mapping: EmotionLabelMapping,
) -> None:
    if not samples:
        raise ValueError(
            "Test samples must not "
            "be empty."
        )

    for sample in samples:
        if sample.source.split != "test":
            raise ValueError(
                "Classification split "
                "mismatch: expected "
                "'test', found "
                f"'{sample.source.split}'"
            )

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


def validate_finite_float(
        value: float,
        name: str,
) -> float:
    numeric_value = float(
        value
    )

    if not math.isfinite(
            numeric_value
    ):
        raise RuntimeError(
            f"{name} is not finite: "
            f"{value}"
        )

    return numeric_value


def evaluate_model(
        model: torch.nn.Module,
        tokenizer: Any,
        samples: list[
            EmotionClassificationSample
        ],
        label_mapping: EmotionLabelMapping,
        fine_to_coarse: dict[str, str],
        max_length: int,
        eval_batch_size: int,
        device: torch.device,
) -> dict[str, Any]:
    model.eval()

    true_labels = [
        sample.label
        for sample in samples
    ]

    predicted_labels = []

    total_loss = 0.0
    total_samples = 0

    with torch.inference_mode():
        for start in tqdm(
                range(
                    0,
                    len(samples),
                    eval_batch_size,
                ),
                desc=(
                    "Evaluating sealed test"
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

            labels = torch.tensor(
                [
                    label_mapping.encode(
                        sample.label
                    )
                    for sample in batch
                ],
                dtype=torch.long,
                device=device,
            )

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
                **model_inputs,
                labels=labels,
            )

            if outputs.loss is None:
                raise RuntimeError(
                    "Model did not return "
                    "evaluation loss."
                )

            batch_loss = (
                validate_finite_float(
                    outputs.loss.item(),
                    "Evaluation loss",
                )
            )

            logits = outputs.logits

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

            predicted_ids = (
                logits
                .argmax(
                    dim=-1
                )
                .detach()
                .cpu()
                .tolist()
            )

            predicted_labels.extend(
                label_mapping.decode(
                    int(label_id)
                )
                for label_id
                in predicted_ids
            )

            batch_size = len(
                batch
            )

            total_loss += (
                batch_loss
                * batch_size
            )

            total_samples += (
                batch_size
            )

    if total_samples != len(
            samples
    ):
        raise RuntimeError(
            "Evaluation sample count "
            "mismatch."
        )

    if len(
            predicted_labels
    ) != len(
            true_labels
    ):
        raise RuntimeError(
            "Prediction count mismatch."
        )

    evaluation = (
        EmotionClassificationEvaluator()
        .evaluate(
            true_labels=true_labels,
            predicted_labels=(
                predicted_labels
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    fine = (
        evaluation
        .fine
        .aggregate
    )

    coarse = (
        evaluation
        .coarse
        .aggregate
    )

    return {
        "loss": (
            validate_finite_float(
                total_loss
                / total_samples,
                "Evaluation loss",
            )
        ),
        "fine_accuracy": (
            fine.accuracy
        ),
        "fine_macro_precision": (
            fine.macro_precision
        ),
        "fine_macro_recall": (
            fine.macro_recall
        ),
        "fine_macro_f1": (
            fine.macro_f1
        ),
        "fine_weighted_f1": (
            fine.weighted_f1
        ),
        "coarse_accuracy": (
            coarse.accuracy
        ),
        "coarse_macro_f1": (
            coarse.macro_f1
        ),
        "true_fine_label_count": (
            len(
                set(true_labels)
            )
        ),
        "predicted_fine_label_count": (
            len(
                set(predicted_labels)
            )
        ),
        "metric_fine_label_count": (
            len(
                evaluation.fine.labels
            )
        ),
        "metric_coarse_label_count": (
            len(
                evaluation.coarse.labels
            )
        ),
    }


def save_report(
        report: dict[str, Any],
        output_path: Path,
) -> None:
    if output_path.exists():
        raise FileExistsError(
            "Evaluation report already "
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
            "Evaluate the final "
            "TripPamine KoELECTRA "
            "classifier on the sealed "
            "test split."
        )
    )

    parser.add_argument(
        "--model-dir",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--test",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--report",
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

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.report.exists():
        raise FileExistsError(
            "Evaluation report already "
            "exists: "
            f"{args.report}"
        )

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.eval_batch_size,
        "Evaluation batch size",
    )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. "
            "Sealed test evaluation "
            "cannot run."
        )

    device_index = (
        torch.cuda.current_device()
    )

    device = torch.device(
        "cuda",
        device_index,
    )

    device_properties = (
        torch.cuda.get_device_properties(
            device_index
        )
    )

    artifact_files = (
        calculate_artifact_files(
            args.model_dir
        )
    )

    label_mapping = (
        EmotionLabelMapping()
    )

    fine_to_coarse = (
        build_codebook_fine_to_coarse(
            label_mapping
        )
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
            "Evaluation model is not "
            "on a CUDA device: "
            f"{model_device}"
        )

    samples = read_test_dataset(
        args.test
    )

    validate_test_samples(
        samples=samples,
        label_mapping=label_mapping,
    )

    torch.cuda.empty_cache()

    torch.cuda.synchronize(
        device_index
    )

    evaluation_started_at = (
        time.perf_counter()
    )

    metrics = evaluate_model(
        model=model,
        tokenizer=tokenizer,
        samples=samples,
        label_mapping=label_mapping,
        fine_to_coarse=(
            fine_to_coarse
        ),
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

    evaluation_seconds = (
        time.perf_counter()
        - evaluation_started_at
    )

    report = {
        "evaluation": (
            "sealed-test"
        ),
        "evaluated_at_utc": (
            datetime.now(
                timezone.utc
            ).isoformat()
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
        "hardware": {
            "device_index": (
                device_index
            ),
            "device_name": (
                torch.cuda.get_device_name(
                    device_index
                )
            ),
            "compute_capability": list(
                torch.cuda
                .get_device_capability(
                    device_index
                )
            ),
            "total_vram_gib": (
                device_properties
                .total_memory
                / 1024
                / 1024
                / 1024
            ),
        },
        "model": {
            "directory": str(
                args.model_dir
            ),
            "num_labels": (
                label_mapping.num_labels
            ),
            "max_length": (
                args.max_length
            ),
            "padding": "dynamic",
            "precision": "fp32",
            "local_files_only": True,
        },
        "dataset": {
            "test": {
                "file": str(
                    args.test
                ),
                "samples": len(
                    samples
                ),
                "sha256": (
                    calculate_sha256(
                        args.test
                    )
                ),
            },
            "training_used": False,
            "validation_used": False,
            "test_split_used": True,
        },
        "protocol": {
            "eval_batch_size": (
                args.eval_batch_size
            ),
            "training_performed": False,
            "checkpoint_selection_performed": False,
            "optimizer_used": False,
            "scheduler_used": False,
            "metric_label_policy": (
                "union_true_predicted"
            ),
            "expected_fine_label_count": (
                label_mapping.num_labels
            ),
            "true_fine_label_count": (
                metrics[
                    "true_fine_label_count"
                ]
            ),
            "predicted_fine_label_count": (
                metrics[
                    "predicted_fine_label_count"
                ]
            ),
            "metric_fine_label_count": (
                metrics[
                    "metric_fine_label_count"
                ]
            ),
            "metric_coarse_label_count": (
                metrics[
                    "metric_coarse_label_count"
                ]
            ),
        },
        "metrics": {
            "test_loss": (
                metrics[
                    "loss"
                ]
            ),
            "fine_accuracy": (
                metrics[
                    "fine_accuracy"
                ]
            ),
            "fine_macro_precision": (
                metrics[
                    "fine_macro_precision"
                ]
            ),
            "fine_macro_recall": (
                metrics[
                    "fine_macro_recall"
                ]
            ),
            "fine_macro_f1": (
                metrics[
                    "fine_macro_f1"
                ]
            ),
            "fine_weighted_f1": (
                metrics[
                    "fine_weighted_f1"
                ]
            ),
            "coarse_accuracy": (
                metrics[
                    "coarse_accuracy"
                ]
            ),
            "coarse_macro_f1": (
                metrics[
                    "coarse_macro_f1"
                ]
            ),
        },
        "timing": {
            "evaluation_seconds": (
                evaluation_seconds
            ),
            "samples_per_second": (
                len(samples)
                / evaluation_seconds
            ),
        },
        "artifact": {
            "directory": str(
                args.model_dir
            ),
            "files": (
                artifact_files
            ),
        },
    }

    save_report(
        report=report,
        output_path=(
            args.report
        ),
    )

    print()
    print(
        "KoELECTRA sealed test "
        "evaluation completed"
    )

    print()
    print(
        "Model: "
        f"{args.model_dir}"
    )

    print(
        "Device: "
        f"{torch.cuda.get_device_name(device_index)}"
    )

    print(
        "Precision: fp32"
    )

    print(
        "Padding: dynamic"
    )

    print()
    print(
        "Test samples: "
        f"{len(samples)}"
    )

    print(
        "Training performed: False"
    )

    print(
        "Validation used: False"
    )

    print(
        "Test split used: True"
    )

    print()
    print(
        "Evaluation seconds: "
        f"{evaluation_seconds:.2f}"
    )

    print(
        "Test loss: "
        f"{metrics['loss']:.6f}"
    )

    print(
        "Fine accuracy: "
        f"{metrics['fine_accuracy']:.6f}"
    )

    print(
        "Fine Macro F1: "
        f"{metrics['fine_macro_f1']:.6f}"
    )

    print(
        "Coarse Macro F1: "
        f"{metrics['coarse_macro_f1']:.6f}"
    )

    print()
    print(
        "Report: "
        f"{args.report}"
    )

    print()
    print(
        "SEALED TEST EVALUATION: PASS"
    )


if __name__ == "__main__":
    main()