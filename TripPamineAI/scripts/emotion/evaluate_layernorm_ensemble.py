import argparse
import json
import math
import sys
import time
from pathlib import Path
from typing import Any

import torch
import transformers
from torch.utils.data import DataLoader
from transformers import (
    AutoTokenizer,
    DataCollatorWithPadding,
)

from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    read_dataset,
    save_report,
    validate_positive_integer,
)
from trippamine_ai.evaluation.emotion.classification_metrics import (
    EmotionClassificationEvaluator,
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
    build_fine_to_coarse,
)


DEFAULT_BATCH_SIZE = 64
DEFAULT_ALPHA_STEP = 0.05
DEFAULT_METRIC_TOLERANCE = 1e-6


def load_report(
        path: Path,
) -> dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(
            f"Report not found: {path}"
        )

    with path.open(
        "r",
        encoding="utf-8",
    ) as handle:
        report = json.load(
            handle
        )

    if not isinstance(
        report,
        dict,
    ):
        raise ValueError(
            "Training report must "
            "contain a JSON object."
        )

    return report


def validate_report_dataset(
        report: dict[str, Any],
        validation_path: Path,
) -> None:
    actual_sha256 = (
        calculate_sha256(
            validation_path
        )
    )

    try:
        report_sha256 = (
            report[
                "dataset"
            ][
                "validation"
            ][
                "sha256"
            ]
        )
    except KeyError as error:
        raise ValueError(
            "Training report does not "
            "contain validation SHA256."
        ) from error

    if (
            report_sha256
            != actual_sha256
    ):
        raise ValueError(
            "Training report validation "
            "SHA256 differs from the "
            "requested validation file."
        )

    if report[
        "dataset"
    ].get(
        "test_split_used"
    ) is not False:
        raise ValueError(
            "Training report must state "
            "test_split_used=False."
        )


def validate_model_contract(
        tokenizer: Any,
        model: torch.nn.Module,
) -> dict[str, Any]:
    tokenizer_vocab = len(
        tokenizer
    )

    config_vocab = int(
        model.config.vocab_size
    )

    embedding_vocab = int(
        model
        .get_input_embeddings()
        .weight
        .shape[
            0
        ]
    )

    if not (
            tokenizer_vocab
            == config_vocab
            == embedding_vocab
    ):
        raise ValueError(
            "Tokenizer/model vocabulary "
            "mismatch: "
            f"tokenizer={tokenizer_vocab}, "
            f"config={config_vocab}, "
            f"embedding={embedding_vocab}."
        )

    special_token_count = (
        tokenizer
        .num_special_tokens_to_add(
            pair=False
        )
    )

    if special_token_count != 2:
        raise ValueError(
            "Expected exactly 2 special "
            "tokens for a single input, "
            f"found {special_token_count}."
        )

    if (
            model.config.num_labels
            != 60
    ):
        raise ValueError(
            "Expected 60 fine labels, "
            f"found {model.config.num_labels}."
        )

    if (
            model.config.hidden_size
            != 768
    ):
        raise ValueError(
            "Expected hidden size 768, "
            f"found {model.config.hidden_size}."
        )

    return {
        "tokenizer_class": (
            type(
                tokenizer
            ).__name__
        ),
        "tokenizer_vocab": (
            tokenizer_vocab
        ),
        "config_vocab": (
            config_vocab
        ),
        "embedding_vocab": (
            embedding_vocab
        ),
        "special_tokens_per_input": (
            special_token_count
        ),
        "hidden_size": (
            model.config.hidden_size
        ),
        "num_labels": (
            model.config.num_labels
        ),
    }


def extract_validation_logits(
        model_dir: Path,
        validation: list[Any],
        label_mapping: EmotionLabelMapping,
        max_length: int,
        batch_size: int,
        device: torch.device,
        description: str,
) -> tuple[
    torch.Tensor,
    torch.Tensor,
    dict[str, Any],
]:
    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            model_dir
        )
    )

    model = (
        LayerNormElectraForSequenceClassification
        .from_pretrained(
            model_dir
        )
    )

    contract = (
        validate_model_contract(
            tokenizer=tokenizer,
            model=model,
        )
    )

    dataset = (
        TransformerEmotionDataset(
            samples=validation,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            max_length=max_length,
        )
    )

    collator = (
        DataCollatorWithPadding(
            tokenizer=tokenizer,
            padding=True,
            return_tensors="pt",
        )
    )

    data_loader = DataLoader(
        dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=0,
        pin_memory=True,
        drop_last=False,
        collate_fn=collator,
    )

    model.eval()
    model.to(
        device
    )

    logits_batches = []
    label_batches = []

    device_index = (
        device.index
        if device.index is not None
        else torch.cuda.current_device()
    )

    torch.cuda.reset_peak_memory_stats(
        device_index
    )

    torch.cuda.synchronize(
        device_index
    )

    started_at = (
        time.perf_counter()
    )

    with torch.inference_mode():
        for batch in data_loader:
            labels = batch.pop(
                "labels"
            )

            device_batch = {
                key: value.to(
                    device,
                    non_blocking=True,
                )
                for key, value
                in batch.items()
            }

            outputs = model(
                **device_batch,
            )

            logits = outputs.logits

            if (
                    logits.ndim != 2
                    or logits.shape[
                        1
                    ] != 60
            ):
                raise RuntimeError(
                    "Unexpected logits "
                    f"shape: {tuple(logits.shape)}"
                )

            if not torch.isfinite(
                    logits
            ).all():
                raise RuntimeError(
                    "Non-finite logits "
                    f"detected for {description}."
                )

            logits_batches.append(
                logits
                .detach()
                .float()
                .cpu()
            )

            label_batches.append(
                labels
                .detach()
                .long()
                .cpu()
            )

    torch.cuda.synchronize(
        device_index
    )

    elapsed = (
        time.perf_counter()
        - started_at
    )

    logits = torch.cat(
        logits_batches,
        dim=0,
    )

    labels = torch.cat(
        label_batches,
        dim=0,
    )

    if (
            logits.shape[
                0
            ]
            != len(
                validation
            )
    ):
        raise RuntimeError(
            "Logit sample count does "
            "not match validation size."
        )

    if (
            labels.shape[
                0
            ]
            != len(
                validation
            )
    ):
        raise RuntimeError(
            "Label sample count does "
            "not match validation size."
        )

    metadata = {
        "model_dir": str(
            model_dir
        ),
        "contract": contract,
        "logits_shape": list(
            logits.shape
        ),
        "labels_shape": list(
            labels.shape
        ),
        "seconds": elapsed,
        "samples_per_second": (
            len(validation)
            / elapsed
        ),
        "peak_allocated_gib": (
            torch.cuda
            .max_memory_allocated(
                device_index
            )
            / (
                1024 ** 3
            )
        ),
        "peak_reserved_gib": (
            torch.cuda
            .max_memory_reserved(
                device_index
            )
            / (
                1024 ** 3
            )
        ),
    }

    del model
    del tokenizer

    torch.cuda.empty_cache()

    return (
        logits,
        labels,
        metadata,
    )


def evaluate_logits(
        logits: torch.Tensor,
        labels: torch.Tensor,
        label_mapping: EmotionLabelMapping,
        fine_to_coarse: dict[
            str,
            str,
        ],
) -> dict[str, Any]:
    if (
            logits.ndim != 2
            or logits.shape[
                0
            ] != labels.shape[
                0
            ]
    ):
        raise ValueError(
            "Logits/labels shape "
            "mismatch."
        )

    predicted_ids = (
        torch.argmax(
            logits,
            dim=-1,
        )
        .tolist()
    )

    true_ids = labels.tolist()

    true_labels = [
        label_mapping.id2label[
            int(
                label_id
            )
        ]
        for label_id
        in true_ids
    ]

    predicted_labels = [
        label_mapping.id2label[
            int(
                label_id
            )
        ]
        for label_id
        in predicted_ids
    ]

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

    return {
        "fine_accuracy": (
            evaluation
            .fine
            .aggregate
            .accuracy
        ),
        "fine_macro_precision": (
            evaluation
            .fine
            .aggregate
            .macro_precision
        ),
        "fine_macro_recall": (
            evaluation
            .fine
            .aggregate
            .macro_recall
        ),
        "fine_macro_f1": (
            evaluation
            .fine
            .aggregate
            .macro_f1
        ),
        "fine_weighted_f1": (
            evaluation
            .fine
            .aggregate
            .weighted_f1
        ),
        "coarse_accuracy": (
            evaluation
            .coarse
            .aggregate
            .accuracy
        ),
        "coarse_macro_f1": (
            evaluation
            .coarse
            .aggregate
            .macro_f1
        ),
        "predicted_ids": (
            predicted_ids
        ),
    }


def validate_reproduced_metrics(
        name: str,
        result: dict[str, Any],
        report: dict[str, Any],
        tolerance: float,
) -> None:
    expected = report[
        "validation"
    ]

    checks = {
        "fine_accuracy": (
            "fine_accuracy"
        ),
        "fine_macro_f1": (
            "fine_macro_f1"
        ),
        "coarse_macro_f1": (
            "coarse_macro_f1"
        ),
    }

    for (
            result_key,
            report_key,
    ) in checks.items():
        actual = float(
            result[
                result_key
            ]
        )

        expected_value = float(
            expected[
                report_key
            ]
        )

        if not math.isclose(
                actual,
                expected_value,
                rel_tol=0.0,
                abs_tol=tolerance,
        ):
            raise RuntimeError(
                f"{name} validation "
                f"{result_key} did not "
                "reproduce the training "
                "report: "
                f"expected={expected_value}, "
                f"actual={actual}."
            )


def build_alpha_values(
        step: float,
) -> list[float]:
    if (
            not math.isfinite(
                step
            )
            or step <= 0.0
            or step > 1.0
    ):
        raise ValueError(
            "Alpha step must be finite "
            "and in (0, 1]."
        )

    count = int(
        round(
            1.0 / step
        )
    )

    if not math.isclose(
            count * step,
            1.0,
            rel_tol=0.0,
            abs_tol=1e-9,
    ):
        raise ValueError(
            "Alpha step must divide "
            "1.0 exactly."
        )

    return [
        round(
            index * step,
            10,
        )
        for index
        in range(
            count + 1
        )
    ]


def calculate_complementarity(
        ko_predictions: list[int],
        kc_predictions: list[int],
        labels: torch.Tensor,
) -> dict[str, Any]:
    true_ids = labels.tolist()

    if not (
            len(
                ko_predictions
            )
            == len(
                kc_predictions
            )
            == len(
                true_ids
            )
    ):
        raise ValueError(
            "Complementarity input "
            "length mismatch."
        )

    both_correct = 0
    ko_only_correct = 0
    kc_only_correct = 0
    both_wrong = 0
    prediction_disagreements = 0

    for (
            true_id,
            ko_prediction,
            kc_prediction,
    ) in zip(
            true_ids,
            ko_predictions,
            kc_predictions,
            strict=True,
    ):
        ko_correct = (
            ko_prediction
            == true_id
        )

        kc_correct = (
            kc_prediction
            == true_id
        )

        if (
                ko_prediction
                != kc_prediction
        ):
            prediction_disagreements += 1

        if (
                ko_correct
                and kc_correct
        ):
            both_correct += 1

        elif ko_correct:
            ko_only_correct += 1

        elif kc_correct:
            kc_only_correct += 1

        else:
            both_wrong += 1

    total = len(
        true_ids
    )

    return {
        "total_samples": total,
        "both_correct": (
            both_correct
        ),
        "ko_only_correct": (
            ko_only_correct
        ),
        "kc_only_correct": (
            kc_only_correct
        ),
        "both_wrong": (
            both_wrong
        ),
        "prediction_disagreements": (
            prediction_disagreements
        ),
        "disagreement_rate": (
            prediction_disagreements
            / total
        ),
        "ko_rescue_opportunities": (
            ko_only_correct
        ),
        "kc_rescue_opportunities": (
            kc_only_correct
        ),
    }


def save_logits(
        path: Path,
        logits: torch.Tensor,
        labels: torch.Tensor,
        sample_ids: list[str],
        model_key: str,
) -> dict[str, Any]:
    if path.exists():
        raise FileExistsError(
            f"Logit artifact exists: {path}"
        )

    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    torch.save(
        {
            "model_key": (
                model_key
            ),
            "logits": (
                logits.contiguous()
            ),
            "labels": (
                labels.contiguous()
            ),
            "sample_ids": list(
                sample_ids
            ),
        },
        path,
    )

    return {
        "file": str(
            path
        ),
        "bytes": (
            path
            .stat()
            .st_size
        ),
        "sha256": (
            calculate_sha256(
                path
            )
        ),
        "logits_shape": list(
            logits.shape
        ),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Evaluate KoELECTRA and "
            "KcELECTRA LayerNorm "
            "validation-logit ensemble."
        )
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--ko-model",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--ko-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc-model",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc-report",
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
        "--max-length",
        type=int,
        default=96,
    )

    parser.add_argument(
        "--batch-size",
        type=int,
        default=(
            DEFAULT_BATCH_SIZE
        ),
    )

    parser.add_argument(
        "--alpha-step",
        type=float,
        default=(
            DEFAULT_ALPHA_STEP
        ),
    )

    parser.add_argument(
        "--metric-tolerance",
        type=float,
        default=(
            DEFAULT_METRIC_TOLERANCE
        ),
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output_dir.exists():
        raise FileExistsError(
            "Ensemble output directory "
            "already exists: "
            f"{args.output_dir}"
        )

    if args.report.exists():
        raise FileExistsError(
            "Ensemble report already "
            "exists: "
            f"{args.report}"
        )

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.batch_size,
        "Evaluation batch size",
    )

    if (
            not math.isfinite(
                args.metric_tolerance
            )
            or args.metric_tolerance
            < 0.0
    ):
        raise ValueError(
            "Metric tolerance must be "
            "finite and non-negative."
        )

    alpha_values = (
        build_alpha_values(
            args.alpha_step
        )
    )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available."
        )

    ko_report = load_report(
        args.ko_report
    )

    kc_report = load_report(
        args.kc_report
    )

    validate_report_dataset(
        ko_report,
        args.validation,
    )

    validate_report_dataset(
        kc_report,
        args.validation,
    )

    validation = read_dataset(
        args.validation,
        "validation",
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

    device_index = (
        torch.cuda.current_device()
    )

    device = torch.device(
        "cuda",
        device_index,
    )

    (
        ko_logits,
        ko_labels,
        ko_extraction,
    ) = extract_validation_logits(
        model_dir=args.ko_model,
        validation=validation,
        label_mapping=(
            label_mapping
        ),
        max_length=(
            args.max_length
        ),
        batch_size=(
            args.batch_size
        ),
        device=device,
        description="KoELECTRA",
    )

    ko_metrics = evaluate_logits(
        logits=ko_logits,
        labels=ko_labels,
        label_mapping=(
            label_mapping
        ),
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    validate_reproduced_metrics(
        name="KoELECTRA",
        result=ko_metrics,
        report=ko_report,
        tolerance=(
            args.metric_tolerance
        ),
    )

    (
        kc_logits,
        kc_labels,
        kc_extraction,
    ) = extract_validation_logits(
        model_dir=args.kc_model,
        validation=validation,
        label_mapping=(
            label_mapping
        ),
        max_length=(
            args.max_length
        ),
        batch_size=(
            args.batch_size
        ),
        device=device,
        description="KcELECTRA-v2022",
    )

    if not torch.equal(
            ko_labels,
            kc_labels,
    ):
        raise RuntimeError(
            "Ko/Kc validation labels "
            "are not aligned."
        )

    kc_metrics = evaluate_logits(
        logits=kc_logits,
        labels=kc_labels,
        label_mapping=(
            label_mapping
        ),
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    validate_reproduced_metrics(
        name="KcELECTRA-v2022",
        result=kc_metrics,
        report=kc_report,
        tolerance=(
            args.metric_tolerance
        ),
    )

    complementarity = (
        calculate_complementarity(
            ko_predictions=(
                ko_metrics[
                    "predicted_ids"
                ]
            ),
            kc_predictions=(
                kc_metrics[
                    "predicted_ids"
                ]
            ),
            labels=ko_labels,
        )
    )

    grid_results = []

    for alpha in alpha_values:
        ensemble_logits = (
            (
                1.0 - alpha
            )
            * ko_logits
            + alpha
            * kc_logits
        )

        metrics = evaluate_logits(
            logits=ensemble_logits,
            labels=ko_labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )

        grid_results.append(
            {
                "kc_weight": alpha,
                "ko_weight": (
                    1.0 - alpha
                ),
                "fine_accuracy": (
                    metrics[
                        "fine_accuracy"
                    ]
                ),
                "fine_macro_f1": (
                    metrics[
                        "fine_macro_f1"
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
            }
        )

    best = sorted(
        grid_results,
        key=lambda result: (
            -result[
                "fine_macro_f1"
            ],
            -result[
                "coarse_macro_f1"
            ],
            -result[
                "fine_accuracy"
            ],
            abs(
                result[
                    "kc_weight"
                ]
                - 0.5
            ),
            result[
                "kc_weight"
            ],
        ),
    )[0]

    kc_single_f1 = (
        kc_metrics[
            "fine_macro_f1"
        ]
    )

    ko_single_f1 = (
        ko_metrics[
            "fine_macro_f1"
        ]
    )

    best_f1 = (
        best[
            "fine_macro_f1"
        ]
    )

    sample_ids = [
        sample.id
        for sample
        in validation
    ]

    args.output_dir.mkdir(
        parents=True,
        exist_ok=False,
    )

    ko_artifact = save_logits(
        path=(
            args.output_dir
            / "ko-validation-logits.pt"
        ),
        logits=ko_logits,
        labels=ko_labels,
        sample_ids=sample_ids,
        model_key="koelectra-v3",
    )

    kc_artifact = save_logits(
        path=(
            args.output_dir
            / "kc-validation-logits.pt"
        ),
        logits=kc_logits,
        labels=kc_labels,
        sample_ids=sample_ids,
        model_key="kcelectra-v2022",
    )

    report = {
        "experiment": (
            "T0-Q14-B-"
            "ko-kc-validation-"
            "logit-ensemble"
        ),
        "versions": {
            "python": sys.version,
            "torch": (
                torch.__version__
            ),
            "transformers": (
                transformers.__version__
            ),
        },
        "hardware": {
            "device_index": (
                device_index
            ),
            "device_name": (
                torch.cuda
                .get_device_name(
                    device_index
                )
            ),
        },
        "dataset": {
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
        "protocol": {
            "ensemble_type": (
                "weighted_raw_logits"
            ),
            "selection_split": (
                "validation"
            ),
            "primary_metric": (
                "fine_macro_f1"
            ),
            "alpha_definition": (
                "kc_weight"
            ),
            "alpha_step": (
                args.alpha_step
            ),
            "max_length": (
                args.max_length
            ),
            "batch_size": (
                args.batch_size
            ),
            "test_split_used": False,
        },
        "models": {
            "koelectra_v3": {
                "model_dir": str(
                    args.ko_model
                ),
                "training_report": str(
                    args.ko_report
                ),
                "extraction": (
                    ko_extraction
                ),
                "metrics": {
                    key: value
                    for key, value
                    in ko_metrics.items()
                    if key
                    != "predicted_ids"
                },
                "artifact": (
                    ko_artifact
                ),
            },
            "kcelectra_v2022": {
                "model_dir": str(
                    args.kc_model
                ),
                "training_report": str(
                    args.kc_report
                ),
                "extraction": (
                    kc_extraction
                ),
                "metrics": {
                    key: value
                    for key, value
                    in kc_metrics.items()
                    if key
                    != "predicted_ids"
                },
                "artifact": (
                    kc_artifact
                ),
            },
        },
        "complementarity": (
            complementarity
        ),
        "grid": (
            grid_results
        ),
        "best": (
            best
        ),
        "comparison": {
            "best_minus_ko_f1": (
                best_f1
                - ko_single_f1
            ),
            "best_minus_kc_f1": (
                best_f1
                - kc_single_f1
            ),
            "kc_minus_ko_f1": (
                kc_single_f1
                - ko_single_f1
            ),
        },
    }

    save_report(
        report=report,
        output_path=args.report,
    )

    print()
    print(
        "Ko/Kc validation logit "
        "ensemble completed"
    )

    print()
    print(
        "Validation samples: "
        f"{len(validation)}"
    )

    print(
        "Test split used: False"
    )

    print()
    print(
        "Ko reproduced Fine Macro F1: "
        f"{ko_single_f1:.6f}"
    )

    print(
        "Kc reproduced Fine Macro F1: "
        f"{kc_single_f1:.6f}"
    )

    print()
    print(
        "Prediction disagreement rate: "
        f"{complementarity['disagreement_rate']:.2%}"
    )

    print(
        "Ko-only correct: "
        f"{complementarity['ko_only_correct']}"
    )

    print(
        "Kc-only correct: "
        f"{complementarity['kc_only_correct']}"
    )

    print()
    print(
        "Best Kc weight: "
        f"{best['kc_weight']:.2f}"
    )

    print(
        "Best Ko weight: "
        f"{best['ko_weight']:.2f}"
    )

    print(
        "Best ensemble Fine Macro F1: "
        f"{best['fine_macro_f1']:.6f}"
    )

    print(
        "Best ensemble Fine accuracy: "
        f"{best['fine_accuracy']:.6f}"
    )

    print(
        "Best ensemble Coarse Macro F1: "
        f"{best['coarse_macro_f1']:.6f}"
    )

    print()
    print(
        "Ensemble - Kc Fine F1: "
        f"{best_f1 - kc_single_f1:+.6f}"
    )

    print(
        "Ensemble - Ko Fine F1: "
        f"{best_f1 - ko_single_f1:+.6f}"
    )

    print()
    print(
        "Report: "
        f"{args.report}"
    )

    print()
    print(
        "Q14-B LOGIT ENSEMBLE: PASS"
    )


if __name__ == "__main__":
    main()