import argparse
import math
import sys
import time
from pathlib import Path

import torch
import transformers
from transformers import (
    AutoTokenizer,
    DataCollatorWithPadding,
    EarlyStoppingCallback,
    TrainingArguments,
)

from scripts.emotion.train_transformer_classifier import (
    DEFAULT_DATA_SEED,
    DEFAULT_EVAL_BATCH_SIZE,
    DEFAULT_LOGGING_STEPS,
    DEFAULT_MAX_GRAD_NORM,
    DEFAULT_MAX_LENGTH,
    DEFAULT_MODEL,
    DEFAULT_SEED,
    DEFAULT_TRAIN_BATCH_SIZE,
    DEFAULT_WARMUP_RATIO,
    DEFAULT_WEIGHT_DECAY,
    ExperimentHistoryCallback,
    bytes_to_gib,
    calculate_artifact_files,
    calculate_sha256,
    classifier_parameters_changed,
    find_best_epoch,
    read_dataset,
    save_report,
    validate_finite_metric,
    validate_label_sets,
    validate_non_negative_float,
    validate_non_negative_integer,
    validate_positive_float,
    validate_positive_integer,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.layernorm_transformer import (
    LayerNormElectraForSequenceClassification,
)
from trippamine_ai.models.emotion.transformer_dataset import (
    TransformerEmotionDataset,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_compute_metrics,
    build_fine_to_coarse,
)


DEFAULT_LEARNING_RATE = 4e-5
DEFAULT_EPOCHS = 4.0
DEFAULT_REVISION = "main"
DEFAULT_LABEL_SMOOTHING = 0.0

DEFAULT_EARLY_STOPPING_PATIENCE = 1
DEFAULT_EARLY_STOPPING_THRESHOLD = 0.0005


def validate_early_stopping_patience(
        value: int,
) -> int:
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
            "Early stopping patience "
            "must be a positive integer."
        )

    return value


def validate_early_stopping_threshold(
        value: float,
) -> float:
    if (
            isinstance(
                value,
                bool,
            )
            or not isinstance(
                value,
                (int, float),
            )
    ):
        raise ValueError(
            "Early stopping threshold "
            "must be a finite "
            "non-negative value."
        )

    numeric_value = float(
        value
    )

    if (
            not math.isfinite(
                numeric_value
            )
            or numeric_value < 0.0
    ):
        raise ValueError(
            "Early stopping threshold "
            "must be a finite "
            "non-negative value."
        )

    return numeric_value


def build_early_stopping_callback(
        patience: int,
        threshold: float,
) -> EarlyStoppingCallback:
    validated_patience = (
        validate_early_stopping_patience(
            patience
        )
    )

    validated_threshold = (
        validate_early_stopping_threshold(
            threshold
        )
    )

    return EarlyStoppingCallback(
        early_stopping_patience=(
            validated_patience
        ),
        early_stopping_threshold=(
            validated_threshold
        ),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Train the TripPamine "
            "ELECTRA fine emotion "
            "classifier with a "
            "LayerNorm classification "
            "head."
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
        "--revision",
        default=DEFAULT_REVISION,
    )

    parser.add_argument(
        "--label-smoothing",
        type=float,
        default=DEFAULT_LABEL_SMOOTHING,
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
        default=(
            DEFAULT_LEARNING_RATE
        ),
    )

    parser.add_argument(
        "--epochs",
        type=float,
        default=DEFAULT_EPOCHS,
    )

    parser.add_argument(
        "--warmup-ratio",
        type=float,
        default=(
            DEFAULT_WARMUP_RATIO
        ),
    )

    parser.add_argument(
        "--weight-decay",
        type=float,
        default=(
            DEFAULT_WEIGHT_DECAY
        ),
    )

    parser.add_argument(
        "--max-grad-norm",
        type=float,
        default=(
            DEFAULT_MAX_GRAD_NORM
        ),
    )

    parser.add_argument(
        "--logging-steps",
        type=int,
        default=(
            DEFAULT_LOGGING_STEPS
        ),
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
        "--early-stopping-patience",
        type=int,
        default=(
            DEFAULT_EARLY_STOPPING_PATIENCE
        ),
    )

    parser.add_argument(
        "--early-stopping-threshold",
        type=float,
        default=(
            DEFAULT_EARLY_STOPPING_THRESHOLD
        ),
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

    validate_non_negative_float(
        args.label_smoothing,
        "Label smoothing",
    )

    if args.label_smoothing >= 1.0:
        raise ValueError(
            "Label smoothing must be "
            "less than 1.0."
        )

    early_stopping_patience = (
        validate_early_stopping_patience(
            args.early_stopping_patience
        )
    )

    early_stopping_patience = (
        validate_early_stopping_patience(
            args.early_stopping_patience
        )
    )

    early_stopping_threshold = (
        validate_early_stopping_threshold(
            args.early_stopping_threshold
        )
    )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. "
            "LayerNorm transformer "
            "training cannot run."
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

    training_fine_to_coarse = (
        build_fine_to_coarse(
            samples=training,
            label_mapping=(
                label_mapping
            ),
        )
    )

    validation_fine_to_coarse = (
        build_fine_to_coarse(
            samples=validation,
            label_mapping=(
                label_mapping
            ),
        )
    )

    if (
            training_fine_to_coarse
            != validation_fine_to_coarse
    ):
        raise ValueError(
            "Training/validation fine "
            "to coarse mappings differ."
        )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model,
            revision=args.revision,
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
        LayerNormElectraForSequenceClassification
        .from_pretrained(
            args.model,
            revision=args.revision,
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

    model_commit_hash = getattr(
        model.config,
        "_commit_hash",
        None,
    )

    tokenizer_commit_hash = (
        getattr(
            tokenizer,
            "init_kwargs",
            {},
        )
        .get(
            "_commit_hash"
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
            label_smoothing_factor=(
                args.label_smoothing
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
                training_fine_to_coarse
            ),
        )
    )

    history_callback = (
        ExperimentHistoryCallback()
    )

    early_stopping_callback = (
        build_early_stopping_callback(
            patience=(
                early_stopping_patience
            ),
            threshold=(
                early_stopping_threshold
            ),
        )
    )

    trainer = transformers.Trainer(
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
            history_callback,
            early_stopping_callback,
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
            "LayerNorm classifier "
            "parameters did not change "
            "during training."
        )

    train_loss = (
        train_result.metrics.get(
            "train_loss"
        )
    )

    if (
            train_loss is None
            or not math.isfinite(
                float(
                    train_loss
                )
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
        str(
            best_model_dir
        )
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
                float(
                    best_metric
                )
            )
    ):
        raise RuntimeError(
            "Trainer best metric is "
            "missing or not finite."
        )

    actual_epochs = (
        float(
            trainer.state.epoch
        )
        if trainer.state.epoch
        is not None
        else None
    )

    stopped_early = (
        actual_epochs is not None
        and actual_epochs
        < float(
            args.epochs
        )
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
            "requested_revision": (
                args.revision
            ),
            "model_commit_hash": (
                model_commit_hash
            ),
            "tokenizer_commit_hash": (
                tokenizer_commit_hash
            ),
            "architecture": (
                "electra-layernorm-"
                "classification-head"
            ),
            "encoder_hidden_layers": 12,
            "hidden_size": 768,
            "intermediate_size": 3072,
            "attention_heads": 12,
            "fine_num_labels": (
                label_mapping.num_labels
            ),
            "classification_head": [
                "cls",
                "layer_norm",
                "dropout",
                "dense",
                "gelu",
                "dropout",
                "out_proj",
            ],
            "layer_norm_position": (
                "before_classifier_dropout"
            ),
            "layer_norm_eps": (
                trainer.model
                .classifier
                .layer_norm
                .eps
            ),
            "dropout": (
                trainer.model
                .classifier
                .dropout
                .p
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
            "max_epochs": (
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
            "class_weighting": False,
            "label_smoothing": (
                args.label_smoothing
            ),
        },
        "early_stopping": {
            "enabled": True,
            "patience": (
                early_stopping_patience
            ),
            "threshold": (
                early_stopping_threshold
            ),
            "metric": (
                "eval_fine_macro_f1"
            ),
            "greater_is_better": True,
            "stopped_early": (
                stopped_early
            ),
            "actual_final_epoch": (
                actual_epochs
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
        "protocol": {
            "primary_metric": (
                "eval_fine_macro_f1"
            ),
            "class_weighting": False,
            "label_smoothing": (
                args.label_smoothing
            ),
            "hierarchical_loss": False,
            "separate_coarse_head": False,
            "test_split_used": False,
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
        "ELECTRA LayerNorm "
        "training completed"
    )

    print()

    print(
        "Experiment: "
        f"{args.experiment}"
    )

    print(
        "Model: "
        f"{args.model}"
    )

    print(
        "Revision: "
        f"{args.revision}"
    )

    print(
        "Device: "
        f"{torch.cuda.get_device_name(device_index)}"
    )

    print(
        "Encoder layers: 12"
    )

    print(
        "Hidden size: 768"
    )

    print(
        "LayerNorm eps: "
        f"{trainer.model.classifier.layer_norm.eps}"
    )

    print(
        "Dropout: "
        f"{trainer.model.classifier.dropout.p}"
    )

    print(
        "Precision: "
        f"{args.precision}"
    )

    print(
        "Label smoothing: "
        f"{args.label_smoothing}"
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
        "Maximum epochs: "
        f"{args.epochs}"
    )

    print(
        "Actual final epoch: "
        f"{actual_epochs}"
    )

    print(
        "Early stopped: "
        f"{stopped_early}"
    )

    print(
        "Early stopping patience: "
        f"{early_stopping_patience}"
    )

    print(
        "Early stopping threshold: "
        f"{early_stopping_threshold}"
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
        "Final derived Coarse "
        "Macro F1: "
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
        "LAYERNORM TRAINING: PASS"
    )


if __name__ == "__main__":
    main()