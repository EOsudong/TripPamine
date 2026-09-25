import argparse
import math
import tempfile
from pathlib import Path

import torch
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    DataCollatorWithPadding,
    TrainingArguments,
)

from scripts.emotion.train_transformer_classifier import (
    DEFAULT_MODEL,
    read_dataset,
)
from trippamine_ai.models.emotion.hierarchical_transformer_training import (
    HierarchicalEmotionTrainer,
    aggregate_fine_logits_to_coarse,
    build_fine_to_coarse_ids,
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


DEFAULT_MAX_LENGTH = 96
DEFAULT_EVAL_BATCH_SIZE = 64
DEFAULT_COARSE_LOSS_WEIGHT = 0.1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run a CUDA preflight for "
            "fine-logit hierarchical "
            "KoELECTRA training."
        )
    )

    parser.add_argument(
        "--validation",
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
        "--eval-batch-size",
        type=int,
        default=DEFAULT_EVAL_BATCH_SIZE,
    )

    parser.add_argument(
        "--coarse-loss-weight",
        type=float,
        default=DEFAULT_COARSE_LOSS_WEIGHT,
    )

    return parser.parse_args()


def has_finite_nonzero_gradient(
        module: torch.nn.Module,
) -> bool:
    found_nonzero = False

    for parameter in module.parameters():
        gradient = parameter.grad

        if gradient is None:
            continue

        if not torch.isfinite(
                gradient
        ).all():
            raise RuntimeError(
                "Non-finite classifier "
                "gradient detected."
            )

        if torch.count_nonzero(
                gradient
        ).item() > 0:
            found_nonzero = True

    return found_nonzero


def main() -> None:
    args = parse_args()

    if (
            isinstance(
                args.max_length,
                bool,
            )
            or not isinstance(
                args.max_length,
                int,
            )
            or args.max_length <= 0
    ):
        raise ValueError(
            "Maximum token length "
            "must be a positive integer."
        )

    if (
            isinstance(
                args.eval_batch_size,
                bool,
            )
            or not isinstance(
                args.eval_batch_size,
                int,
            )
            or args.eval_batch_size <= 0
    ):
        raise ValueError(
            "Evaluation batch size "
            "must be a positive integer."
        )

    if (
            not math.isfinite(
                args.coarse_loss_weight
            )
            or args.coarse_loss_weight <= 0.0
    ):
        raise ValueError(
            "Coarse loss weight must be "
            "a finite positive value."
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available."
        )

    validation = read_dataset(
        args.validation,
        "validation",
    )

    if (
            len(validation)
            < args.eval_batch_size
    ):
        raise ValueError(
            "Validation dataset is "
            "smaller than the requested "
            "preflight batch."
        )

    label_mapping = (
        EmotionLabelMapping()
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            samples=validation,
            label_mapping=(
                label_mapping
            ),
        )
    )

    fine_to_coarse_ids = (
        build_fine_to_coarse_ids(
            label_mapping
        )
    )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model
        )
    )

    preflight_samples = (
        validation[
            :args.eval_batch_size
        ]
    )

    dataset = (
        TransformerEmotionDataset(
            samples=preflight_samples,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    collator = (
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

    with tempfile.TemporaryDirectory() as (
            temporary_directory
    ):
        training_arguments = (
            TrainingArguments(
                output_dir=(
                    temporary_directory
                ),
                per_device_eval_batch_size=(
                    args.eval_batch_size
                ),
                report_to="none",
                use_cpu=False,
                fp16=False,
                bf16=False,
                dataloader_num_workers=0,
                dataloader_pin_memory=True,
                do_train=False,
                do_eval=True,
                label_names=[
                    "labels"
                ],
            )
        )

        trainer = (
            HierarchicalEmotionTrainer(
                model=model,
                args=training_arguments,
                data_collator=(
                    collator
                ),
                eval_dataset=(
                    dataset
                ),
                processing_class=(
                    tokenizer
                ),
                compute_metrics=(
                    compute_metrics
                ),
                fine_to_coarse_ids=(
                    fine_to_coarse_ids
                ),
                coarse_loss_weight=(
                    args.coarse_loss_weight
                ),
            )
        )

        model_device = next(
            trainer.model.parameters()
        ).device

        if model_device.type != "cuda":
            raise RuntimeError(
                "Trainer model is not "
                "on CUDA: "
                f"{model_device}"
            )

        batch = collator(
            [
                dataset[index]
                for index in range(
                    len(dataset)
                )
            ]
        )

        device_batch = {
            key: (
                value.to(
                    model_device
                )
                if torch.is_tensor(
                    value
                )
                else value
            )
            for key, value
            in batch.items()
        }

        trainer.model.train()

        trainer.model.zero_grad(
            set_to_none=True
        )

        loss, outputs = (
            trainer.compute_loss(
                trainer.model,
                device_batch,
                return_outputs=True,
                num_items_in_batch=(
                    len(dataset)
                ),
            )
        )

        if not torch.isfinite(
                loss
        ):
            raise RuntimeError(
                "Hierarchical loss "
                "is not finite."
            )

        fine_logits = (
            outputs.logits
        )

        expected_fine_shape = (
            args.eval_batch_size,
            label_mapping.num_labels,
        )

        if (
                tuple(
                    fine_logits.shape
                )
                != expected_fine_shape
        ):
            raise RuntimeError(
                "Unexpected fine logits "
                "shape: "
                f"{tuple(fine_logits.shape)}"
            )

        coarse_logits = (
            aggregate_fine_logits_to_coarse(
                fine_logits=(
                    fine_logits
                ),
                fine_to_coarse_ids=(
                    fine_to_coarse_ids
                ),
            )
        )

        expected_coarse_shape = (
            args.eval_batch_size,
            6,
        )

        if (
                tuple(
                    coarse_logits.shape
                )
                != expected_coarse_shape
        ):
            raise RuntimeError(
                "Unexpected derived coarse "
                "logits shape: "
                f"{tuple(coarse_logits.shape)}"
            )

        if not torch.isfinite(
                coarse_logits
        ).all():
            raise RuntimeError(
                "Derived coarse logits "
                "contain non-finite values."
            )

        loss.backward()

        classifier_gradient_ok = (
            has_finite_nonzero_gradient(
                trainer.model.classifier
            )
        )

        if not classifier_gradient_ok:
            raise RuntimeError(
                "Fine classifier did not "
                "receive a non-zero "
                "gradient."
            )

        trainer.model.zero_grad(
            set_to_none=True
        )

        trainer_metrics = (
            trainer.evaluate()
        )

    required_metrics = (
        "eval_loss",
        "eval_fine_accuracy",
        "eval_fine_macro_f1",
        "eval_coarse_accuracy",
        "eval_coarse_macro_f1",
    )

    for metric_name in (
            required_metrics
    ):
        value = trainer_metrics.get(
            metric_name
        )

        if value is None:
            raise RuntimeError(
                "Trainer preflight metric "
                "is missing: "
                f"{metric_name}"
            )

        if not math.isfinite(
                float(value)
        ):
            raise RuntimeError(
                "Trainer preflight metric "
                "is not finite: "
                f"{metric_name}={value}"
            )

    print()
    print(
        "Hierarchical KoELECTRA "
        "CUDA preflight completed"
    )

    print()
    print(
        "Device: "
        f"{model_device}"
    )

    print(
        "Device name: "
        f"{torch.cuda.get_device_name(model_device)}"
    )

    print(
        "Samples: "
        f"{len(dataset)}"
    )

    print(
        "Fine logits: "
        f"{tuple(fine_logits.shape)}"
    )

    print(
        "Derived coarse logits: "
        f"{tuple(coarse_logits.shape)}"
    )

    print(
        "Hierarchical loss: "
        f"{float(loss.detach().cpu()):.6f}"
    )

    print(
        "Fine classifier gradient: "
        f"{classifier_gradient_ok}"
    )

    print()
    print(
        "Trainer eval loss: "
        f"{trainer_metrics['eval_loss']:.6f}"
    )

    print(
        "Trainer Fine accuracy: "
        f"{trainer_metrics['eval_fine_accuracy']:.6f}"
    )

    print(
        "Trainer Fine Macro F1: "
        f"{trainer_metrics['eval_fine_macro_f1']:.6f}"
    )

    print(
        "Trainer derived Coarse "
        "Macro F1: "
        f"{trainer_metrics['eval_coarse_macro_f1']:.6f}"
    )

    print()
    print(
        "HIERARCHICAL CUDA PREFLIGHT: PASS"
    )


if __name__ == "__main__":
    main()