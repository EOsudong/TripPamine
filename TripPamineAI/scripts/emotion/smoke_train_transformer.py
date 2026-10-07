import argparse
import hashlib
import math
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
    select_balanced_samples,
)


DEFAULT_MODEL = (
    "monologg/"
    "koelectra-base-v3-discriminator"
)

DEFAULT_MAX_LENGTH = 96

DEFAULT_TRAIN_SAMPLES_PER_LABEL = 2

DEFAULT_VALIDATION_SAMPLES_PER_LABEL = 1


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


def calculate_sample_ids_sha256(
        samples: list[
            EmotionClassificationSample
        ],
) -> str:
    digest = hashlib.sha256()

    for sample in samples:
        digest.update(
            sample.id.encode(
                "utf-8"
            )
        )

        digest.update(
            b"\n"
        )

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
            "Run a deterministic "
            "TripPamine KoELECTRA "
            "CPU training smoke test."
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
        "--train-samples-per-label",
        type=int,
        default=(
            DEFAULT_TRAIN_SAMPLES_PER_LABEL
        ),
    )

    parser.add_argument(
        "--validation-samples-per-label",
        type=int,
        default=(
            DEFAULT_VALIDATION_SAMPLES_PER_LABEL
        ),
    )

    parser.add_argument(
        "--max-steps",
        type=int,
        default=1,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output_dir.exists():
        raise FileExistsError(
            "Smoke output directory "
            "already exists: "
            f"{args.output_dir}"
        )

    if args.report.exists():
        raise FileExistsError(
            "Smoke report already "
            "exists: "
            f"{args.report}"
        )

    if args.max_length <= 0:
        raise ValueError(
            "Maximum token length "
            "must be greater than zero."
        )

    if args.max_steps <= 0:
        raise ValueError(
            "Maximum training steps "
            "must be greater than zero."
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

    smoke_training = (
        select_balanced_samples(
            samples=training,
            label_mapping=label_mapping,
            samples_per_label=(
                args
                .train_samples_per_label
            ),
        )
    )

    smoke_validation = (
        select_balanced_samples(
            samples=validation,
            label_mapping=label_mapping,
            samples_per_label=(
                args
                .validation_samples_per_label
            ),
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
            samples=smoke_training,
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
            samples=smoke_validation,
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

    training_arguments = (
        TrainingArguments(
            output_dir=str(
                args.output_dir
            ),
            per_device_train_batch_size=2,
            per_device_eval_batch_size=8,
            max_steps=(
                args.max_steps
            ),
            learning_rate=5e-5,
            lr_scheduler_type="linear",
            warmup_steps=0,
            optim="adamw_torch",
            weight_decay=0.0,
            gradient_accumulation_steps=1,
            max_grad_norm=1.0,
            logging_strategy="steps",
            logging_steps=1,
            logging_first_step=True,
            eval_strategy="no",
            save_strategy="no",
            report_to="none",
            seed=42,
            data_seed=42,
            use_cpu=True,
            dataloader_pin_memory=False,
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
    )

    start_time = (
        time.perf_counter()
    )

    train_result = (
        trainer.train()
    )

    training_seconds = (
        time.perf_counter()
        - start_time
    )

    parameters_changed = (
        classifier_parameters_changed(
            classifier_before,
            model,
        )
    )

    if not parameters_changed:
        raise RuntimeError(
            "Classifier parameters "
            "did not change during "
            "the smoke training step."
        )

    evaluation_metrics = (
        trainer.evaluate()
    )

    eval_loss = (
        evaluation_metrics.get(
            "eval_loss"
        )
    )

    if (
            eval_loss is None
            or not math.isfinite(
                float(
                    eval_loss
                )
            )
    ):
        raise RuntimeError(
            "Smoke evaluation loss "
            "is not finite."
        )

    report = {
        "experiment": (
            "T0-koelectra-"
            "cpu-training-smoke"
        ),
        "versions": {
            "torch": (
                torch.__version__
            ),
            "transformers": (
                transformers.__version__
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
            "dynamic_padding": True,
        },
        "dataset": {
            "training": {
                "file": str(
                    args.training
                ),
                "full_samples": len(
                    training
                ),
                "sha256": (
                    calculate_sha256(
                        args.training
                    )
                ),
                "smoke_samples": len(
                    smoke_training
                ),
                "samples_per_label": (
                    args
                    .train_samples_per_label
                ),
                "sample_ids_sha256": (
                    calculate_sample_ids_sha256(
                        smoke_training
                    )
                ),
            },
            "validation": {
                "file": str(
                    args.validation
                ),
                "full_samples": len(
                    validation
                ),
                "sha256": (
                    calculate_sha256(
                        args.validation
                    )
                ),
                "smoke_samples": len(
                    smoke_validation
                ),
                "samples_per_label": (
                    args
                    .validation_samples_per_label
                ),
                "sample_ids_sha256": (
                    calculate_sample_ids_sha256(
                        smoke_validation
                    )
                ),
            },
            "test_split_used": False,
        },
        "training": {
            "max_steps": (
                args.max_steps
            ),
            "seconds": (
                training_seconds
            ),
            "classifier_parameters_changed": (
                parameters_changed
            ),
            "metrics": (
                train_result.metrics
            ),
        },
        "evaluation": (
            evaluation_metrics
        ),
    }

    save_report(
        report=report,
        output_path=(
            args.report
        ),
    )

    print()
    print(
        "KoELECTRA CPU training "
        "smoke completed"
    )

    print()
    print(
        "Training subset: "
        f"{len(smoke_training)}"
    )

    print(
        "Validation subset: "
        f"{len(smoke_validation)}"
    )

    print(
        "Test split used: False"
    )

    print()
    print(
        "Optimizer steps: "
        f"{train_result.global_step}"
    )

    print(
        "Classifier parameters "
        "changed: "
        f"{parameters_changed}"
    )

    print(
        "Training seconds: "
        f"{training_seconds:.2f}"
    )

    print()
    print(
        "Validation loss: "
        f"{evaluation_metrics['eval_loss']:.6f}"
    )

    print(
        "Fine accuracy: "
        f"{evaluation_metrics['eval_fine_accuracy']:.6f}"
    )

    print(
        "Fine Macro F1: "
        f"{evaluation_metrics['eval_fine_macro_f1']:.6f}"
    )

    print(
        "Coarse accuracy: "
        f"{evaluation_metrics['eval_coarse_accuracy']:.6f}"
    )

    print(
        "Coarse Macro F1: "
        f"{evaluation_metrics['eval_coarse_macro_f1']:.6f}"
    )

    print()
    print(
        f"Report: "
        f"{args.report}"
    )

    print()
    print(
        "CPU TRAINING SMOKE: PASS"
    )


if __name__ == "__main__":
    main()