import argparse
import math
import tempfile
from pathlib import Path

import torch
from transformers import (
    AutoConfig,
    AutoTokenizer,
    Trainer,
    TrainingArguments,
)

from scripts.emotion.train_multitask_transformer_classifier import (
    build_multitask_compute_metrics,
)
from scripts.emotion.train_transformer_classifier import (
    DEFAULT_MODEL,
    read_dataset,
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


DEFAULT_MAX_LENGTH = 96

DEFAULT_EVAL_BATCH_SIZE = 64

DEFAULT_COARSE_LOSS_WEIGHT = 0.3


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run a CUDA preflight for "
            "the TripPamine multi-task "
            "KoELECTRA classifier."
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
        default=(
            DEFAULT_EVAL_BATCH_SIZE
        ),
    )

    parser.add_argument(
        "--coarse-loss-weight",
        type=float,
        default=(
            DEFAULT_COARSE_LOSS_WEIGHT
        ),
    )

    return parser.parse_args()


def has_finite_nonzero_gradient(
        module: torch.nn.Module,
) -> bool:
    found_gradient = False

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
            found_gradient = True

    return found_gradient


def main() -> None:
    args = parse_args()

    if (
            isinstance(
                args.max_length,
                bool,
            )
            or args.max_length <= 0
    ):
        raise ValueError(
            "Maximum token length "
            "must be positive."
        )

    if (
            isinstance(
                args.eval_batch_size,
                bool,
            )
            or args.eval_batch_size <= 0
    ):
        raise ValueError(
            "Evaluation batch size "
            "must be positive."
        )

    if (
            not math.isfinite(
                args.coarse_loss_weight
            )
            or args.coarse_loss_weight
            <= 0.0
    ):
        raise ValueError(
            "Coarse loss weight must "
            "be a finite positive "
            "value."
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

    fine_mapping = (
        EmotionLabelMapping()
    )

    coarse_mapping = (
        CoarseEmotionLabelMapping()
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            samples=validation,
            label_mapping=(
                fine_mapping
            ),
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
        MultiTaskTransformerEmotionDataset(
            samples=preflight_samples,
            tokenizer=tokenizer,
            label_mapping=(
                fine_mapping
            ),
            coarse_label_mapping=(
                coarse_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    collator = (
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
        fine_mapping.num_labels
    )

    config.label2id = dict(
        fine_mapping.label2id
    )

    config.id2label = dict(
        fine_mapping.id2label
    )

    config.coarse_num_labels = (
        coarse_mapping.num_labels
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

    device = torch.device(
        "cuda"
    )

    model.to(
        device
    )

    batch = collator(
        [
            dataset[index]
            for index in range(
                len(dataset)
            )
        ]
    )

    cuda_batch = {
        key: (
            value.to(
                device
            )
            if torch.is_tensor(
                value
            )
            else value
        )
        for key, value
        in batch.items()
    }

    model.train()

    model.zero_grad(
        set_to_none=True
    )

    output = model(
        **cuda_batch
    )

    if output.loss is None:
        raise RuntimeError(
            "Multi-task forward pass "
            "did not produce a loss."
        )

    if not torch.isfinite(
            output.loss
    ):
        raise RuntimeError(
            "Multi-task loss is "
            "not finite."
        )

    expected_fine_shape = (
        args.eval_batch_size,
        fine_mapping.num_labels,
    )

    expected_coarse_shape = (
        args.eval_batch_size,
        coarse_mapping.num_labels,
    )

    if (
            tuple(
                output.logits.shape
            )
            != expected_fine_shape
    ):
        raise RuntimeError(
            "Unexpected fine logits "
            "shape: "
            f"{tuple(output.logits.shape)}"
        )

    if (
            tuple(
                output.coarse_logits.shape
            )
            != expected_coarse_shape
    ):
        raise RuntimeError(
            "Unexpected coarse logits "
            "shape: "
            f"{tuple(output.coarse_logits.shape)}"
        )

    output.loss.backward()

    fine_gradient_ok = (
        has_finite_nonzero_gradient(
            model.fine_classifier
        )
    )

    coarse_gradient_ok = (
        has_finite_nonzero_gradient(
            model.coarse_classifier
        )
    )

    if not fine_gradient_ok:
        raise RuntimeError(
            "Fine classifier did not "
            "receive a non-zero "
            "gradient."
        )

    if not coarse_gradient_ok:
        raise RuntimeError(
            "Coarse classifier did not "
            "receive a non-zero "
            "gradient."
        )

    model.zero_grad(
        set_to_none=True
    )

    compute_metrics = (
        build_multitask_compute_metrics(
            label_mapping=(
                fine_mapping
            ),
            coarse_label_mapping=(
                coarse_mapping
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

        trainer = Trainer(
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
        )

        trainer_metrics = (
            trainer.evaluate()
        )

    required_metrics = (
        "eval_loss",
        "eval_fine_accuracy",
        "eval_fine_macro_f1",
        "eval_coarse_macro_f1",
        "eval_aux_coarse_accuracy",
        "eval_aux_coarse_macro_f1",
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
        "Multi-task KoELECTRA "
        "CUDA preflight completed"
    )

    print()
    print(
        "Device: "
        f"{torch.cuda.get_device_name(0)}"
    )

    print(
        "Samples: "
        f"{len(dataset)}"
    )

    print(
        "Fine logits: "
        f"{tuple(output.logits.shape)}"
    )

    print(
        "Coarse logits: "
        f"{tuple(output.coarse_logits.shape)}"
    )

    print(
        "Loss: "
        f"{float(output.loss.detach().cpu()):.6f}"
    )

    print(
        "Fine head gradient: "
        f"{fine_gradient_ok}"
    )

    print(
        "Coarse head gradient: "
        f"{coarse_gradient_ok}"
    )

    print()
    print(
        "Trainer eval loss: "
        f"{trainer_metrics['eval_loss']:.6f}"
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

    print(
        "Trainer auxiliary Coarse "
        "Macro F1: "
        f"{trainer_metrics['eval_aux_coarse_macro_f1']:.6f}"
    )

    print()
    print(
        "MULTI-TASK CUDA PREFLIGHT: PASS"
    )


if __name__ == "__main__":
    main()