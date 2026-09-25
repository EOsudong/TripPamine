import argparse
import math
import sys
import time
from pathlib import Path
from typing import Any

import numpy as np
import torch
import transformers
from sklearn.metrics import (
    accuracy_score,
    f1_score,
)
from transformers import (
    AutoConfig,
    AutoTokenizer,
    Trainer,
    TrainingArguments,
)

from scripts.emotion.train_transformer_classifier import (
    DEFAULT_DATA_SEED,
    DEFAULT_EPOCHS,
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
from trippamine_ai.evaluation.emotion.classification_metrics import (
    EmotionClassificationEvaluator,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.multitask_transformer import (
    MultiTaskElectraForSequenceClassification,
)
from trippamine_ai.models.emotion.multitask_transformer_dataset import (
    CoarseEmotionLabelMapping,
    MultiTaskDataCollatorWithPadding,
    MultiTaskTransformerEmotionDataset,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_fine_to_coarse,
)


DEFAULT_LEARNING_RATE = 4e-5

DEFAULT_MULTITASK_EPOCHS = 4.0

DEFAULT_COARSE_LOSS_WEIGHT = 0.3


def build_multitask_compute_metrics(
        label_mapping: EmotionLabelMapping,
        coarse_label_mapping: (
            CoarseEmotionLabelMapping
        ),
        fine_to_coarse: dict[
            str,
            str,
        ],
):
    evaluator = (
        EmotionClassificationEvaluator()
    )

    def compute_metrics(
            eval_prediction: Any,
    ) -> dict[str, float]:
        predictions = (
            eval_prediction.predictions
        )

        if not isinstance(
                predictions,
                (tuple, list),
        ):
            raise ValueError(
                "Multi-task predictions "
                "must contain fine and "
                "coarse logits."
            )

        if len(predictions) < 2:
            raise ValueError(
                "Multi-task predictions "
                "must contain both fine "
                "and coarse logits."
            )

        fine_logits = np.asarray(
            predictions[0]
        )

        coarse_logits = np.asarray(
            predictions[1]
        )

        label_ids = (
            eval_prediction.label_ids
        )

        if isinstance(
                label_ids,
                (tuple, list),
        ):
            if len(label_ids) != 1:
                raise ValueError(
                    "Expected exactly one "
                    "evaluation label set."
                )

            label_ids = label_ids[0]

        label_ids = np.asarray(
            label_ids
        )

        if (
                fine_logits.ndim != 2
                or fine_logits.shape[1]
                != label_mapping.num_labels
        ):
            raise ValueError(
                "Fine prediction logits "
                "have an unexpected shape."
            )

        if (
                coarse_logits.ndim != 2
                or coarse_logits.shape[1]
                != coarse_label_mapping.num_labels
        ):
            raise ValueError(
                "Coarse prediction logits "
                "have an unexpected shape."
            )

        if (
                fine_logits.shape[0]
                != label_ids.shape[0]
                or coarse_logits.shape[0]
                != label_ids.shape[0]
        ):
            raise ValueError(
                "Prediction/label sample "
                "count mismatch."
            )

        predicted_fine_ids = (
            fine_logits.argmax(
                axis=-1
            )
        )

        predicted_coarse_ids = (
            coarse_logits.argmax(
                axis=-1
            )
        )

        predicted_fine_labels = [
            label_mapping.decode(
                int(label_id)
            )
            for label_id
            in predicted_fine_ids
        ]

        true_fine_labels = [
            label_mapping.decode(
                int(label_id)
            )
            for label_id
            in label_ids
        ]

        evaluation = (
            evaluator.evaluate(
                true_labels=(
                    true_fine_labels
                ),
                predicted_labels=(
                    predicted_fine_labels
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

        true_aux_coarse_labels = [
            fine_to_coarse[
                fine_label
            ]
            for fine_label
            in true_fine_labels
        ]

        predicted_aux_coarse_labels = [
            coarse_label_mapping.decode(
                int(label_id)
            )
            for label_id
            in predicted_coarse_ids
        ]

        aux_coarse_accuracy = (
            accuracy_score(
                true_aux_coarse_labels,
                predicted_aux_coarse_labels,
            )
        )

        aux_coarse_macro_f1 = (
            f1_score(
                true_aux_coarse_labels,
                predicted_aux_coarse_labels,
                labels=list(
                    coarse_label_mapping.labels
                ),
                average="macro",
                zero_division=0,
            )
        )

        return {
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
            "aux_coarse_accuracy": float(
                aux_coarse_accuracy
            ),
            "aux_coarse_macro_f1": float(
                aux_coarse_macro_f1
            ),
        }

    return compute_metrics


def capture_classifier_parameters(
        model: (
            MultiTaskElectraForSequenceClassification
        ),
) -> dict[
    str,
    list[torch.Tensor],
]:
    return {
        "fine": [
            parameter
            .detach()
            .cpu()
            .clone()
            for parameter
            in model
            .fine_classifier
            .parameters()
        ],
        "coarse": [
            parameter
            .detach()
            .cpu()
            .clone()
            for parameter
            in model
            .coarse_classifier
            .parameters()
        ],
    }


def classifier_parameters_changed(
        before: dict[
            str,
            list[torch.Tensor],
        ],
        model: (
            MultiTaskElectraForSequenceClassification
        ),
) -> dict[str, bool]:
    result = {}

    classifiers = {
        "fine": (
            model.fine_classifier
        ),
        "coarse": (
            model.coarse_classifier
        ),
    }

    for name, classifier in (
            classifiers.items()
    ):
        previous_parameters = (
            before.get(
                name
            )
        )

        if previous_parameters is None:
            raise RuntimeError(
                "Missing classifier "
                "parameter snapshot: "
                f"{name}"
            )

        current_parameters = [
            parameter
            .detach()
            .cpu()
            for parameter
            in classifier.parameters()
        ]

        if len(
                previous_parameters
        ) != len(
                current_parameters
        ):
            raise RuntimeError(
                "Classifier parameter "
                "count changed during "
                f"training: {name}"
            )

        result[name] = any(
            not torch.equal(
                previous,
                current,
            )
            for previous, current
            in zip(
                previous_parameters,
                current_parameters,
                strict=True,
            )
        )

    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Train the TripPamine "
            "KoELECTRA multi-task "
            "emotion classifier."
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
        default=(
            DEFAULT_LEARNING_RATE
        ),
    )

    parser.add_argument(
        "--epochs",
        type=float,
        default=(
            DEFAULT_MULTITASK_EPOCHS
        ),
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
        "--coarse-loss-weight",
        type=float,
        default=(
            DEFAULT_COARSE_LOSS_WEIGHT
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

    validate_positive_float(
        args.coarse_loss_weight,
        "Coarse loss weight",
    )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. "
            "Multi-task transformer "
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

    coarse_label_mapping = (
        CoarseEmotionLabelMapping()
    )

    validate_label_sets(
        training=training,
        validation=validation,
        label_mapping=label_mapping,
    )

    fine_to_coarse = (
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
            fine_to_coarse
            != validation_fine_to_coarse
    ):
        raise ValueError(
            "Training/validation fine "
            "to coarse mappings differ."
        )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model
        )
    )

    train_dataset = (
        MultiTaskTransformerEmotionDataset(
            samples=training,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            coarse_label_mapping=(
                coarse_label_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    eval_dataset = (
        MultiTaskTransformerEmotionDataset(
            samples=validation,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            coarse_label_mapping=(
                coarse_label_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    data_collator = (
        MultiTaskDataCollatorWithPadding(
            tokenizer=tokenizer
        )
    )

    config = (
        AutoConfig
        .from_pretrained(
            args.model
        )
    )

    config.num_labels = (
        label_mapping.num_labels
    )

    config.label2id = dict(
        label_mapping.label2id
    )

    config.id2label = dict(
        label_mapping.id2label
    )

    config.coarse_num_labels = (
        coarse_label_mapping.num_labels
    )

    config.coarse_loss_weight = (
        args.coarse_loss_weight
    )

    model = (
        MultiTaskElectraForSequenceClassification
        .from_pretrained(
            args.model,
            config=config,
        )
    )

    classifier_before = (
        capture_classifier_parameters(
            model
        )
    )

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
            label_names=[
                "labels"
            ],
        )
    )

    compute_metrics = (
        build_multitask_compute_metrics(
            label_mapping=(
                label_mapping
            ),
            coarse_label_mapping=(
                coarse_label_mapping
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

    parameter_changes = (
        classifier_parameters_changed(
            classifier_before,
            trainer.model,
        )
    )

    if not all(
            parameter_changes.values()
    ):
        raise RuntimeError(
            "One or more multi-task "
            "classifier heads did not "
            "change during training: "
            f"{parameter_changes}"
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

    final_aux_coarse_accuracy = (
        validate_finite_metric(
            final_validation,
            "eval_aux_coarse_accuracy",
        )
    )

    final_aux_coarse_macro_f1 = (
        validate_finite_metric(
            final_validation,
            "eval_aux_coarse_macro_f1",
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
            "architecture": (
                "shared-electra-dual-head"
            ),
            "fine_num_labels": (
                label_mapping.num_labels
            ),
            "coarse_num_labels": (
                coarse_label_mapping
                .num_labels
            ),
            "coarse_loss_weight": (
                args.coarse_loss_weight
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
            "hard_coarse_gating": False,
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
            "coarse_loss_weight": (
                args.coarse_loss_weight
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
                parameter_changes
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
            "aux_coarse_accuracy": (
                final_aux_coarse_accuracy
            ),
            "aux_coarse_macro_f1": (
                final_aux_coarse_macro_f1
            ),
            "metrics": (
                final_validation
            ),
        },
        "protocol": {
            "primary_prediction_head": (
                "fine"
            ),
            "auxiliary_prediction_head": (
                "coarse"
            ),
            "hard_coarse_gating": False,
            "checkpoint_selection_metric": (
                "eval_fine_macro_f1"
            ),
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
        "KoELECTRA multi-task "
        "training completed"
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

    print(
        "Hard coarse gating: False"
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
        "Coarse loss weight: "
        f"{args.coarse_loss_weight}"
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
        "Final derived Coarse "
        "Macro F1: "
        f"{final_coarse_macro_f1:.6f}"
    )

    print(
        "Final auxiliary Coarse "
        "accuracy: "
        f"{final_aux_coarse_accuracy:.6f}"
    )

    print(
        "Final auxiliary Coarse "
        "Macro F1: "
        f"{final_aux_coarse_macro_f1:.6f}"
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
        "MULTI-TASK TRAINING: PASS"
    )


if __name__ == "__main__":
    main()