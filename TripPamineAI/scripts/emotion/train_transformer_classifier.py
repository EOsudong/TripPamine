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
from tqdm import tqdm
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    DataCollatorWithPadding,
    Trainer,
    TrainerCallback,
    TrainingArguments,
)

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_dataset import (
    TransformerEmotionDataset,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_compute_metrics,
    build_fine_to_coarse,
)

DEFAULT_MODEL = (
    "monologg/"
    "koelectra-base-v3-discriminator"
)

DEFAULT_MAX_LENGTH = 96

DEFAULT_TRAIN_BATCH_SIZE = 32

DEFAULT_EVAL_BATCH_SIZE = 64

DEFAULT_LEARNING_RATE = 2e-5

DEFAULT_EPOCHS = 3.0

DEFAULT_WARMUP_RATIO = 0.1

DEFAULT_WEIGHT_DECAY = 0.01

DEFAULT_MAX_GRAD_NORM = 1.0

DEFAULT_LOGGING_STEPS = 50

DEFAULT_SEED = 42

DEFAULT_DATA_SEED = 42


class ExperimentHistoryCallback(
    TrainerCallback
):

    def __init__(self) -> None:
        self.history: list[
            dict[str, Any]
        ] = []

        self._epoch_started_at: (
                float | None
        ) = None

    def on_epoch_begin(
            self,
            args: TrainingArguments,
            state,
            control,
            **kwargs,
    ):
        self._epoch_started_at = (
            time.perf_counter()
        )

        if torch.cuda.is_available():
            torch.cuda.reset_peak_memory_stats()

        return control

    def on_evaluate(
            self,
            args: TrainingArguments,
            state,
            control,
            metrics=None,
            **kwargs,
    ):
        evaluation_metrics = dict(
            metrics or {}
        )

        epoch_seconds = None

        if self._epoch_started_at is not None:
            epoch_seconds = (
                    time.perf_counter()
                    - self._epoch_started_at
            )

        peak_allocated = None
        peak_reserved = None

        if torch.cuda.is_available():
            device_index = (
                torch.cuda.current_device()
            )

            peak_allocated = (
                bytes_to_gib(
                    torch.cuda
                    .max_memory_allocated(
                        device_index
                    )
                )
            )

            peak_reserved = (
                bytes_to_gib(
                    torch.cuda
                    .max_memory_reserved(
                        device_index
                    )
                )
            )

        self.history.append(
            {
                "epoch": (
                    float(state.epoch)
                    if state.epoch is not None
                    else None
                ),
                "global_step": int(
                    state.global_step
                ),
                "seconds": (
                    epoch_seconds
                ),
                "peak_allocated_gib": (
                    peak_allocated
                ),
                "peak_reserved_gib": (
                    peak_reserved
                ),
                "metrics": (
                    evaluation_metrics
                ),
            }
        )

        return control


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
            "Saved model artifact "
            "directory is empty: "
            f"{directory}"
        )

    return artifacts


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


def validate_label_sets(
        training: list[
            EmotionClassificationSample
        ],
        validation: list[
            EmotionClassificationSample
        ],
        label_mapping: EmotionLabelMapping,
) -> None:
    expected_labels = set(
        label_mapping.labels
    )

    training_labels = {
        sample.label
        for sample in training
    }

    validation_labels = {
        sample.label
        for sample in validation
    }

    if (
            training_labels
            != expected_labels
    ):
        raise ValueError(
            "Training label set "
            "does not match the "
            "60-label codebook."
        )

    if (
            validation_labels
            != expected_labels
    ):
        raise ValueError(
            "Validation label set "
            "does not match the "
            "60-label codebook."
        )


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


def validate_non_negative_integer(
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
            or value < 0
    ):
        raise ValueError(
            f"{name} must be "
            "a non-negative integer."
        )


def validate_positive_float(
        value: float,
        name: str,
) -> None:
    if (
            not math.isfinite(value)
            or value <= 0.0
    ):
        raise ValueError(
            f"{name} must be "
            "a finite positive value."
        )


def validate_non_negative_float(
        value: float,
        name: str,
) -> None:
    if (
            not math.isfinite(value)
            or value < 0.0
    ):
        raise ValueError(
            f"{name} must be "
            "a finite non-negative "
            "value."
        )


def classifier_parameters_changed(
        before: list[
            torch.Tensor
        ],
        model: torch.nn.Module,
) -> bool:
    after = [
        parameter
        .detach()
        .cpu()
        for parameter
        in model.classifier.parameters()
    ]

    if len(before) != len(
            after
    ):
        raise RuntimeError(
            "Classifier parameter "
            "count changed during "
            "training."
        )

    return any(
        not torch.equal(
            previous,
            current,
        )
        for previous, current
        in zip(
            before,
            after,
            strict=True,
        )
    )


def bytes_to_gib(
        value: int,
) -> float:
    return (
            value
            / 1024
            / 1024
            / 1024
    )


def validate_finite_metric(
        metrics: dict[str, Any],
        key: str,
) -> float:
    value = metrics.get(key)

    if value is None:
        raise RuntimeError(
            "Required metric is "
            "missing: "
            f"{key}"
        )

    numeric_value = float(value)

    if not math.isfinite(
            numeric_value
    ):
        raise RuntimeError(
            "Metric is not finite: "
            f"{key}={value}"
        )

    return numeric_value


def find_best_epoch(
        history: list[
            dict[str, Any]
        ],
) -> dict[str, Any]:
    if not history:
        raise RuntimeError(
            "No epoch evaluation "
            "history was recorded."
        )

    best_entry = None
    best_metric = None

    for entry in history:
        metrics = entry.get(
            "metrics",
            {},
        )

        metric = (
            validate_finite_metric(
                metrics,
                "eval_fine_macro_f1",
            )
        )

        if (
                best_metric is None
                or metric > best_metric
        ):
            best_metric = metric
            best_entry = entry

    if best_entry is None:
        raise RuntimeError(
            "Best epoch could not "
            "be determined."
        )

    return best_entry


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
            "Train the TripPamine "
            "KoELECTRA fine emotion "
            "classifier."
        )
    )

    parser.add_argument(
        "--experiment",
        required=True,
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
        "--output-dir",
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
        "--max-length",
        type=int,
        default=DEFAULT_MAX_LENGTH,
    )

    parser.add_argument(
        "--train-batch-size",
        type=int,
        default=(
            DEFAULT_TRAIN_BATCH_SIZE
        ),
    )

    parser.add_argument(
        "--eval-batch-size",
        type=int,
        default=(
            DEFAULT_EVAL_BATCH_SIZE
        ),
    )

    parser.add_argument(
        "--learning-rate",
        type=float,
        default=DEFAULT_LEARNING_RATE,
    )

    parser.add_argument(
        "--epochs",
        type=float,
        default=DEFAULT_EPOCHS,
    )

    parser.add_argument(
        "--warmup-ratio",
        type=float,
        default=DEFAULT_WARMUP_RATIO,
    )

    parser.add_argument(
        "--weight-decay",
        type=float,
        default=DEFAULT_WEIGHT_DECAY,
    )

    parser.add_argument(
        "--max-grad-norm",
        type=float,
        default=DEFAULT_MAX_GRAD_NORM,
    )

    parser.add_argument(
        "--logging-steps",
        type=int,
        default=DEFAULT_LOGGING_STEPS,
    )

    parser.add_argument(
        "--seed",
        type=int,
        default=DEFAULT_SEED,
    )

    parser.add_argument(
        "--data-seed",
        type=int,
        default=DEFAULT_DATA_SEED,
    )

    parser.add_argument(
        "--precision",
        choices=[
            "fp32",
            "fp16",
        ],
        default="fp32",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output_dir.exists():
        raise FileExistsError(
            "Training output directory "
            "already exists: "
            f"{args.output_dir}"
        )

    if args.report.exists():
        raise FileExistsError(
            "Training report already "
            "exists: "
            f"{args.report}"
        )

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.train_batch_size,
        "Training batch size",
    )

    validate_positive_integer(
        args.eval_batch_size,
        "Evaluation batch size",
    )

    validate_positive_integer(
        args.logging_steps,
        "Logging steps",
    )

    validate_non_negative_integer(
        args.seed,
        "Seed",
    )

    validate_non_negative_integer(
        args.data_seed,
        "Data seed",
    )

    validate_positive_float(
        args.learning_rate,
        "Learning rate",
    )

    validate_positive_float(
        args.epochs,
        "Epoch count",
    )

    validate_non_negative_float(
        args.weight_decay,
        "Weight decay",
    )

    validate_positive_float(
        args.max_grad_norm,
        "Maximum gradient norm",
    )

    validate_non_negative_float(
        args.warmup_ratio,
        "Warmup ratio",
    )

    if args.warmup_ratio >= 1.0:
        raise ValueError(
            "Warmup ratio must be "
            "less than 1.0."
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. "
            "Transformer training "
            "cannot run."
        )

    device_index = (
        torch.cuda.current_device()
    )

    device_properties = (
        torch.cuda.get_device_properties(
            device_index
        )
    )

    training = read_dataset(
        args.training,
        "training",
    )

    validation = read_dataset(
        args.validation,
        "validation",
    )

    label_mapping = (
        EmotionLabelMapping()
    )

    validate_label_sets(
        training=training,
        validation=validation,
        label_mapping=label_mapping,
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            samples=training,
            label_mapping=label_mapping,
        )
    )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model
        )
    )

    train_dataset = (
        TransformerEmotionDataset(
            samples=training,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    eval_dataset = (
        TransformerEmotionDataset(
            samples=validation,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    data_collator = (
        DataCollatorWithPadding(
            tokenizer=tokenizer,
            padding=True,
            return_tensors="pt",
        )
    )

    model = (
        AutoModelForSequenceClassification
        .from_pretrained(
            args.model,
            num_labels=(
                label_mapping.num_labels
            ),
            label2id=dict(
                label_mapping.label2id
            ),
            id2label=dict(
                label_mapping.id2label
            ),
        )
    )

    classifier_before = [
        parameter
        .detach()
        .cpu()
        .clone()
        for parameter
        in model.classifier.parameters()
    ]

    checkpoints_dir = (
            args.output_dir
            / "checkpoints"
    )

    best_model_dir = (
            args.output_dir
            / "best-model"
    )

    training_arguments = (
        TrainingArguments(
            output_dir=str(
                checkpoints_dir
            ),
            per_device_train_batch_size=(
                args.train_batch_size
            ),
            per_device_eval_batch_size=(
                args.eval_batch_size
            ),
            num_train_epochs=(
                args.epochs
            ),
            learning_rate=(
                args.learning_rate
            ),
            lr_scheduler_type="linear",
            warmup_steps=(
                args.warmup_ratio
            ),
            optim="adamw_torch",
            weight_decay=(
                args.weight_decay
            ),
            gradient_accumulation_steps=1,
            max_grad_norm=(
                args.max_grad_norm
            ),
            logging_strategy="steps",
            logging_steps=(
                args.logging_steps
            ),
            logging_first_step=True,
            eval_strategy="epoch",
            save_strategy="epoch",
            save_total_limit=3,
            load_best_model_at_end=True,
            metric_for_best_model=(
                "fine_macro_f1"
            ),
            greater_is_better=True,
            report_to="none",
            run_name=(
                args.experiment
            ),
            seed=(
                args.seed
            ),
            data_seed=(
                args.data_seed
            ),
            use_cpu=False,
            fp16=(
                    args.precision
                    == "fp16"
            ),
            bf16=False,
            dataloader_pin_memory=True,
            dataloader_num_workers=0,
            do_train=True,
            do_eval=True,
        )
    )

    compute_metrics = (
        build_compute_metrics(
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    history_callback = (
        ExperimentHistoryCallback()
    )

    trainer = Trainer(
        model=model,
        args=training_arguments,
        data_collator=(
            data_collator
        ),
        train_dataset=(
            train_dataset
        ),
        eval_dataset=(
            eval_dataset
        ),
        processing_class=(
            tokenizer
        ),
        compute_metrics=(
            compute_metrics
        ),
        callbacks=[
            history_callback
        ],
    )

    model_device = (
        next(
            trainer.model.parameters()
        )
        .device
    )

    if model_device.type != "cuda":
        raise RuntimeError(
            "Trainer model is not "
            "on a CUDA device: "
            f"{model_device}"
        )

    torch.cuda.empty_cache()

    torch.cuda.synchronize(
        device_index
    )

    training_started_at = (
        time.perf_counter()
    )

    train_result = (
        trainer.train()
    )

    torch.cuda.synchronize(
        device_index
    )

    training_seconds = (
            time.perf_counter()
            - training_started_at
    )

    parameters_changed = (
        classifier_parameters_changed(
            classifier_before,
            trainer.model,
        )
    )

    if not parameters_changed:
        raise RuntimeError(
            "Classifier parameters "
            "did not change during "
            "full training."
        )

    train_loss = (
        train_result.metrics.get(
            "train_loss"
        )
    )

    if (
            train_loss is None
            or not math.isfinite(
        float(train_loss)
    )
    ):
        raise RuntimeError(
            "Training loss is not "
            "finite."
        )

    final_validation = (
        trainer.evaluate()
    )

    final_eval_loss = (
        validate_finite_metric(
            final_validation,
            "eval_loss",
        )
    )

    final_fine_macro_f1 = (
        validate_finite_metric(
            final_validation,
            "eval_fine_macro_f1",
        )
    )

    final_fine_accuracy = (
        validate_finite_metric(
            final_validation,
            "eval_fine_accuracy",
        )
    )

    final_coarse_macro_f1 = (
        validate_finite_metric(
            final_validation,
            "eval_coarse_macro_f1",
        )
    )

    best_epoch = (
        find_best_epoch(
            history_callback.history
        )
    )

    trainer.save_model(
        str(best_model_dir)
    )

    tokenizer.save_pretrained(
        best_model_dir
    )

    artifact_files = (
        calculate_artifact_files(
            best_model_dir
        )
    )

    best_metric = (
        trainer.state.best_metric
    )

    if (
            best_metric is None
            or not math.isfinite(
        float(best_metric)
    )
    ):
        raise RuntimeError(
            "Trainer best metric is "
            "missing or not finite."
        )

    report = {
        "experiment": (
            args.experiment
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
                bytes_to_gib(
                    device_properties
                    .total_memory
                )
            ),
        },
        "model": {
            "name": args.model,
            "num_labels": (
                label_mapping.num_labels
            ),
            "max_length": (
                args.max_length
            ),
            "padding": "dynamic",
            "precision": (
                args.precision
            ),
            "fp16": (
                    args.precision
                    == "fp16"
            ),
            "bf16": False,
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
            "test_split_used": False,
        },
        "hyperparameters": {
            "train_batch_size": (
                args.train_batch_size
            ),
            "eval_batch_size": (
                args.eval_batch_size
            ),
            "gradient_accumulation_steps": 1,
            "effective_batch_size": (
                args.train_batch_size
            ),
            "learning_rate": (
                args.learning_rate
            ),
            "epochs": (
                args.epochs
            ),
            "warmup_ratio": (
                args.warmup_ratio
            ),
            "weight_decay": (
                args.weight_decay
            ),
            "max_grad_norm": (
                args.max_grad_norm
            ),
            "lr_scheduler_type": "linear",
            "optimizer": "adamw_torch",
            "seed": (
                args.seed
            ),
            "data_seed": (
                args.data_seed
            ),
        },
        "training": {
            "seconds": (
                training_seconds
            ),
            "global_step": (
                train_result.global_step
            ),
            "classifier_parameters_changed": (
                parameters_changed
            ),
            "metrics": (
                train_result.metrics
            ),
            "best_metric_name": (
                "eval_fine_macro_f1"
            ),
            "best_metric": float(
                best_metric
            ),
            "best_model_checkpoint": (
                trainer.state
                .best_model_checkpoint
            ),
            "best_epoch": (
                best_epoch
            ),
            "epoch_history": (
                history_callback.history
            ),
        },
        "validation": {
            "best_model_loaded": True,
            "eval_loss": (
                final_eval_loss
            ),
            "fine_accuracy": (
                final_fine_accuracy
            ),
            "fine_macro_f1": (
                final_fine_macro_f1
            ),
            "coarse_macro_f1": (
                final_coarse_macro_f1
            ),
            "metrics": (
                final_validation
            ),
        },
        "artifact": {
            "directory": str(
                best_model_dir
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
        "KoELECTRA full training "
        "completed"
    )

    print()
    print(
        "Experiment: "
        f"{args.experiment}"
    )

    print(
        "Device: "
        f"{torch.cuda.get_device_name(device_index)}"
    )

    print(
        "Precision: "
        f"{args.precision}"
    )

    print(
        "Padding: dynamic"
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
        "Test split used: False"
    )

    print()
    print(
        "Train batch size: "
        f"{args.train_batch_size}"
    )

    print(
        "Eval batch size: "
        f"{args.eval_batch_size}"
    )

    print(
        "Learning rate: "
        f"{args.learning_rate}"
    )

    print(
        "Epochs: "
        f"{args.epochs}"
    )

    print(
        "Seed: "
        f"{args.seed}"
    )

    print(
        "Data seed: "
        f"{args.data_seed}"
    )

    print(
        "Optimizer steps: "
        f"{train_result.global_step}"
    )

    print(
        "Training seconds: "
        f"{training_seconds:.2f}"
    )

    print()
    print(
        "Best Fine Macro F1: "
        f"{float(best_metric):.6f}"
    )

    print(
        "Final validation loss: "
        f"{final_eval_loss:.6f}"
    )

    print(
        "Final Fine accuracy: "
        f"{final_fine_accuracy:.6f}"
    )

    print(
        "Final Fine Macro F1: "
        f"{final_fine_macro_f1:.6f}"
    )

    print(
        "Final Coarse Macro F1: "
        f"{final_coarse_macro_f1:.6f}"
    )

    print()
    print(
        "Best checkpoint: "
        f"{trainer.state.best_model_checkpoint}"
    )

    print(
        "Best model: "
        f"{best_model_dir}"
    )

    print(
        "Report: "
        f"{args.report}"
    )

    print()
    print(
        "FULL TRAINING: PASS"
    )


if __name__ == "__main__":
    main()
