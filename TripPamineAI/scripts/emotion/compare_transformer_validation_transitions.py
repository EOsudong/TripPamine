import argparse
import math
import sys
import time
from pathlib import Path
from typing import Any

import torch
import transformers
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
)

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)

if __package__:
    from .analyze_transformer_classifier import (
        calculate_sha256,
        predict_validation,
        read_validation_dataset,
        save_report,
        validate_model_config,
        validate_positive_integer,
        validate_validation_samples,
    )
    from .audit_transformer_label_boundaries import (
        DEFAULT_AUDIT_PAIRS,
        validate_audit_pairs,
    )
else:
    from analyze_transformer_classifier import (
        calculate_sha256,
        predict_validation,
        read_validation_dataset,
        save_report,
        validate_model_config,
        validate_positive_integer,
        validate_validation_samples,
    )
    from audit_transformer_label_boundaries import (
        DEFAULT_AUDIT_PAIRS,
        validate_audit_pairs,
    )


DEFAULT_MAX_LENGTH = 96

DEFAULT_EVAL_BATCH_SIZE = 64

DEFAULT_EXAMPLES_PER_BUCKET = 15


def index_samples_by_id(
        samples: list[
            EmotionClassificationSample
        ],
        name: str,
) -> dict[
    str,
    EmotionClassificationSample,
]:
    indexed: dict[
        str,
        EmotionClassificationSample,
    ] = {}

    for sample in samples:
        if sample.id in indexed:
            raise ValueError(
                f"{name} contains "
                "duplicate sample ID: "
                f"{sample.id}"
            )

        indexed[
            sample.id
        ] = sample

    return indexed


def validate_and_align_validation_samples(
        baseline_samples: list[
            EmotionClassificationSample
        ],
        context_samples: list[
            EmotionClassificationSample
        ],
) -> list[
    EmotionClassificationSample
]:
    baseline_by_id = (
        index_samples_by_id(
            samples=baseline_samples,
            name="Baseline validation",
        )
    )

    context_by_id = (
        index_samples_by_id(
            samples=context_samples,
            name="Context validation",
        )
    )

    baseline_ids = set(
        baseline_by_id
    )

    context_ids = set(
        context_by_id
    )

    if baseline_ids != context_ids:
        missing_in_context = sorted(
            baseline_ids
            - context_ids
        )

        missing_in_baseline = sorted(
            context_ids
            - baseline_ids
        )

        raise ValueError(
            "Validation sample ID sets "
            "do not match. "
            "missing_in_context="
            f"{missing_in_context[:5]}, "
            "missing_in_baseline="
            f"{missing_in_baseline[:5]}"
        )

    aligned_context = []

    for baseline_sample in (
            baseline_samples
    ):
        context_sample = (
            context_by_id[
                baseline_sample.id
            ]
        )

        if (
                baseline_sample.label
                != context_sample.label
        ):
            raise ValueError(
                "Paired validation label "
                "mismatch for sample "
                f"{baseline_sample.id}: "
                f"'{baseline_sample.label}' "
                "vs "
                f"'{context_sample.label}'"
            )

        if (
                baseline_sample.coarse_label
                != context_sample.coarse_label
        ):
            raise ValueError(
                "Paired validation coarse "
                "label mismatch for sample "
                f"{baseline_sample.id}."
            )

        if (
                baseline_sample
                .source
                .profile_id
                != context_sample
                .source
                .profile_id
        ):
            raise ValueError(
                "Paired validation profile "
                "ID mismatch for sample "
                f"{baseline_sample.id}."
            )

        if (
                baseline_sample
                .source
                .talk_id
                != context_sample
                .source
                .talk_id
        ):
            raise ValueError(
                "Paired validation talk "
                "ID mismatch for sample "
                f"{baseline_sample.id}."
            )

        aligned_context.append(
            context_sample
        )

    return aligned_context


def validate_prediction_inputs(
        samples: list[
            EmotionClassificationSample
        ],
        baseline_predictions: list[str],
        baseline_confidences: list[float],
        context_predictions: list[str],
        context_confidences: list[float],
        label_mapping: EmotionLabelMapping,
) -> None:
    if not (
            len(samples)
            == len(baseline_predictions)
            == len(baseline_confidences)
            == len(context_predictions)
            == len(context_confidences)
    ):
        raise ValueError(
            "Paired prediction input "
            "lengths must match."
        )

    for (
            baseline_prediction,
            baseline_confidence,
            context_prediction,
            context_confidence,
    ) in zip(
            baseline_predictions,
            baseline_confidences,
            context_predictions,
            context_confidences,
            strict=True,
    ):
        label_mapping.encode(
            baseline_prediction
        )

        label_mapping.encode(
            context_prediction
        )

        for confidence in (
                baseline_confidence,
                context_confidence,
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


def transition_name(
        baseline_correct: bool,
        context_correct: bool,
) -> str:
    if (
            baseline_correct
            and context_correct
    ):
        return (
            "correct_to_correct"
        )

    if baseline_correct:
        return (
            "correct_to_wrong"
        )

    if context_correct:
        return (
            "wrong_to_correct"
        )

    return (
        "wrong_to_wrong"
    )


def build_transition_record(
        baseline_sample: (
            EmotionClassificationSample
        ),
        context_sample: (
            EmotionClassificationSample
        ),
        baseline_prediction: str,
        baseline_confidence: float,
        context_prediction: str,
        context_confidence: float,
) -> dict[
    str,
    Any,
]:
    true_label = (
        baseline_sample.label
    )

    true_coarse = (
        baseline_sample.coarse_label
    )

    baseline_coarse = (
        get_coarse_emotion(
            baseline_prediction
        )
    )

    context_coarse = (
        get_coarse_emotion(
            context_prediction
        )
    )

    baseline_fine_correct = (
        baseline_prediction
        == true_label
    )

    context_fine_correct = (
        context_prediction
        == true_label
    )

    baseline_coarse_correct = (
        baseline_coarse
        == true_coarse
    )

    context_coarse_correct = (
        context_coarse
        == true_coarse
    )

    return {
        "sample_id": (
            baseline_sample.id
        ),
        "true": {
            "label": (
                true_label
            ),
            "label_name": (
                EMOTION_CODEBOOK[
                    true_label
                ]
            ),
            "coarse_label": (
                true_coarse
            ),
        },
        "fine_transition": (
            transition_name(
                baseline_correct=(
                    baseline_fine_correct
                ),
                context_correct=(
                    context_fine_correct
                ),
            )
        ),
        "coarse_transition": (
            transition_name(
                baseline_correct=(
                    baseline_coarse_correct
                ),
                context_correct=(
                    context_coarse_correct
                ),
            )
        ),
        "baseline": {
            "prediction": (
                baseline_prediction
            ),
            "prediction_name": (
                EMOTION_CODEBOOK[
                    baseline_prediction
                ]
            ),
            "coarse_prediction": (
                baseline_coarse
            ),
            "confidence": (
                baseline_confidence
            ),
            "text": (
                baseline_sample.text
            ),
        },
        "context": {
            "prediction": (
                context_prediction
            ),
            "prediction_name": (
                EMOTION_CODEBOOK[
                    context_prediction
                ]
            ),
            "coarse_prediction": (
                context_coarse
            ),
            "confidence": (
                context_confidence
            ),
            "text": (
                context_sample.text
            ),
        },
        "metadata": {
            "situation_code": (
                baseline_sample
                .situation_code
            ),
            "situation": (
                baseline_sample
                .situation
            ),
            "quality_status": (
                baseline_sample
                .quality_status
            ),
            "quality_issue_codes": (
                baseline_sample
                .quality_issue_codes
            ),
        },
    }


def select_examples(
        records: list[
            dict[
                str,
                Any,
            ]
        ],
        limit: int,
        confidence_source: str,
) -> list[
    dict[
        str,
        Any,
    ]
]:
    validate_positive_integer(
        limit,
        "Examples per bucket",
    )

    if confidence_source not in {
        "baseline",
        "context",
    }:
        raise ValueError(
            "Confidence source must "
            "be 'baseline' or "
            "'context'."
        )

    return sorted(
        records,
        key=lambda record: (
            -record[
                confidence_source
            ][
                "confidence"
            ],
            record[
                "sample_id"
            ],
        ),
    )[:limit]


def build_overall_transition_summary(
        records: list[
            dict[
                str,
                Any,
            ]
        ],
) -> dict[
    str,
    Any,
]:
    transition_keys = (
        "correct_to_correct",
        "correct_to_wrong",
        "wrong_to_correct",
        "wrong_to_wrong",
    )

    fine = {
        key: 0
        for key in (
            transition_keys
        )
    }

    coarse = {
        key: 0
        for key in (
            transition_keys
        )
    }

    wrong_to_wrong_detail = {
        "prediction_changed": 0,
        "prediction_unchanged": 0,
        "coarse_wrong_to_correct": 0,
        "coarse_correct_to_wrong": 0,
        "coarse_wrong_to_wrong": 0,
        "coarse_correct_to_correct": 0,
    }

    for record in records:
        fine[
            record[
                "fine_transition"
            ]
        ] += 1

        coarse[
            record[
                "coarse_transition"
            ]
        ] += 1

        if (
                record[
                    "fine_transition"
                ]
                != "wrong_to_wrong"
        ):
            continue

        if (
                record[
                    "baseline"
                ][
                    "prediction"
                ]
                == record[
                    "context"
                ][
                    "prediction"
                ]
        ):
            wrong_to_wrong_detail[
                "prediction_unchanged"
            ] += 1

        else:
            wrong_to_wrong_detail[
                "prediction_changed"
            ] += 1

        coarse_transition = (
            record[
                "coarse_transition"
            ]
        )

        wrong_to_wrong_detail[
            "coarse_"
            + coarse_transition
        ] += 1

    baseline_fine_correct = (
        fine[
            "correct_to_correct"
        ]
        + fine[
            "correct_to_wrong"
        ]
    )

    context_fine_correct = (
        fine[
            "correct_to_correct"
        ]
        + fine[
            "wrong_to_correct"
        ]
    )

    baseline_coarse_correct = (
        coarse[
            "correct_to_correct"
        ]
        + coarse[
            "correct_to_wrong"
        ]
    )

    context_coarse_correct = (
        coarse[
            "correct_to_correct"
        ]
        + coarse[
            "wrong_to_correct"
        ]
    )

    return {
        "samples": len(
            records
        ),
        "fine": {
            **fine,
            "baseline_correct": (
                baseline_fine_correct
            ),
            "context_correct": (
                context_fine_correct
            ),
            "net_gain": (
                context_fine_correct
                - baseline_fine_correct
            ),
        },
        "coarse": {
            **coarse,
            "baseline_correct": (
                baseline_coarse_correct
            ),
            "context_correct": (
                context_coarse_correct
            ),
            "net_gain": (
                context_coarse_correct
                - baseline_coarse_correct
            ),
        },
        "wrong_to_wrong_detail": (
            wrong_to_wrong_detail
        ),
    }


def build_per_label_summary(
        records: list[
            dict[
                str,
                Any,
            ]
        ],
        label_mapping: EmotionLabelMapping,
) -> list[
    dict[
        str,
        Any,
    ]
]:
    result = []

    for label in (
            label_mapping.labels
    ):
        label_records = [
            record
            for record in records
            if (
                    record[
                        "true"
                    ][
                        "label"
                    ]
                    == label
            )
        ]

        support = len(
            label_records
        )

        baseline_correct = sum(
            1
            for record
            in label_records
            if (
                    record[
                        "baseline"
                    ][
                        "prediction"
                    ]
                    == label
            )
        )

        context_correct = sum(
            1
            for record
            in label_records
            if (
                    record[
                        "context"
                    ][
                        "prediction"
                    ]
                    == label
            )
        )

        recovered = sum(
            1
            for record
            in label_records
            if (
                    record[
                        "fine_transition"
                    ]
                    == "wrong_to_correct"
            )
        )

        regressed = sum(
            1
            for record
            in label_records
            if (
                    record[
                        "fine_transition"
                    ]
                    == "correct_to_wrong"
            )
        )

        result.append(
            {
                "label": (
                    label
                ),
                "label_name": (
                    EMOTION_CODEBOOK[
                        label
                    ]
                ),
                "coarse_label": (
                    get_coarse_emotion(
                        label
                    )
                ),
                "support": (
                    support
                ),
                "baseline_correct": (
                    baseline_correct
                ),
                "context_correct": (
                    context_correct
                ),
                "recovered": (
                    recovered
                ),
                "regressed": (
                    regressed
                ),
                "net_gain": (
                    recovered
                    - regressed
                ),
                "baseline_accuracy": (
                    baseline_correct
                    / support
                    if support
                    else None
                ),
                "context_accuracy": (
                    context_correct
                    / support
                    if support
                    else None
                ),
                "accuracy_delta": (
                    (
                        context_correct
                        - baseline_correct
                    )
                    / support
                    if support
                    else None
                ),
            }
        )

    return result


def is_direct_pair_confusion(
        true_label: str,
        predicted_label: str,
        label_a: str,
        label_b: str,
) -> bool:
    return (
        (
            true_label
            == label_a
            and predicted_label
            == label_b
        )
        or (
            true_label
            == label_b
            and predicted_label
            == label_a
        )
    )


def build_boundary_pair_summary(
        records: list[
            dict[
                str,
                Any,
            ]
        ],
        label_a: str,
        label_b: str,
        examples_per_bucket: int,
) -> dict[
    str,
    Any,
]:
    pair_records = [
        record
        for record in records
        if (
                record[
                    "true"
                ][
                    "label"
                ]
                in {
                    label_a,
                    label_b,
                }
        )
    ]

    recovered = [
        record
        for record in (
            pair_records
        )
        if (
                record[
                    "fine_transition"
                ]
                == "wrong_to_correct"
        )
    ]

    regressed = [
        record
        for record in (
            pair_records
        )
        if (
                record[
                    "fine_transition"
                ]
                == "correct_to_wrong"
        )
    ]

    baseline_direct = []
    context_direct = []
    direct_resolved = []
    direct_introduced = []

    for record in (
            pair_records
    ):
        true_label = (
            record[
                "true"
            ][
                "label"
            ]
        )

        baseline_is_direct = (
            is_direct_pair_confusion(
                true_label=(
                    true_label
                ),
                predicted_label=(
                    record[
                        "baseline"
                    ][
                        "prediction"
                    ]
                ),
                label_a=label_a,
                label_b=label_b,
            )
        )

        context_is_direct = (
            is_direct_pair_confusion(
                true_label=(
                    true_label
                ),
                predicted_label=(
                    record[
                        "context"
                    ][
                        "prediction"
                    ]
                ),
                label_a=label_a,
                label_b=label_b,
            )
        )

        if baseline_is_direct:
            baseline_direct.append(
                record
            )

        if context_is_direct:
            context_direct.append(
                record
            )

        if (
                baseline_is_direct
                and not context_is_direct
        ):
            direct_resolved.append(
                record
            )

        if (
                not baseline_is_direct
                and context_is_direct
        ):
            direct_introduced.append(
                record
            )

    return {
        "pair": {
            "label_a": (
                label_a
            ),
            "label_a_name": (
                EMOTION_CODEBOOK[
                    label_a
                ]
            ),
            "coarse_a": (
                get_coarse_emotion(
                    label_a
                )
            ),
            "label_b": (
                label_b
            ),
            "label_b_name": (
                EMOTION_CODEBOOK[
                    label_b
                ]
            ),
            "coarse_b": (
                get_coarse_emotion(
                    label_b
                )
            ),
        },
        "counts": {
            "a_support": sum(
                1
                for record
                in pair_records
                if (
                        record[
                            "true"
                        ][
                            "label"
                        ]
                        == label_a
                )
            ),
            "b_support": sum(
                1
                for record
                in pair_records
                if (
                        record[
                            "true"
                        ][
                            "label"
                        ]
                        == label_b
                )
            ),
            "support": len(
                pair_records
            ),
            "recovered": len(
                recovered
            ),
            "regressed": len(
                regressed
            ),
            "net_gain": (
                len(recovered)
                - len(regressed)
            ),
            "baseline_direct_confusion": (
                len(
                    baseline_direct
                )
            ),
            "context_direct_confusion": (
                len(
                    context_direct
                )
            ),
            "direct_confusion_delta": (
                len(
                    context_direct
                )
                - len(
                    baseline_direct
                )
            ),
            "direct_confusion_resolved": (
                len(
                    direct_resolved
                )
            ),
            "direct_confusion_introduced": (
                len(
                    direct_introduced
                )
            ),
        },
        "examples": {
            "recovered": (
                select_examples(
                    records=recovered,
                    limit=(
                        examples_per_bucket
                    ),
                    confidence_source=(
                        "context"
                    ),
                )
            ),
            "regressed": (
                select_examples(
                    records=regressed,
                    limit=(
                        examples_per_bucket
                    ),
                    confidence_source=(
                        "baseline"
                    ),
                )
            ),
            "direct_confusion_resolved": (
                select_examples(
                    records=(
                        direct_resolved
                    ),
                    limit=(
                        examples_per_bucket
                    ),
                    confidence_source=(
                        "context"
                    ),
                )
            ),
            "direct_confusion_introduced": (
                select_examples(
                    records=(
                        direct_introduced
                    ),
                    limit=(
                        examples_per_bucket
                    ),
                    confidence_source=(
                        "context"
                    ),
                )
            ),
        },
    }


def build_paired_transition_audit(
        baseline_samples: list[
            EmotionClassificationSample
        ],
        context_samples: list[
            EmotionClassificationSample
        ],
        baseline_predictions: list[str],
        baseline_confidences: list[float],
        context_predictions: list[str],
        context_confidences: list[float],
        label_mapping: EmotionLabelMapping,
        audit_pairs: tuple[
            tuple[str, str],
            ...,
        ] = DEFAULT_AUDIT_PAIRS,
        examples_per_bucket: int = (
            DEFAULT_EXAMPLES_PER_BUCKET
        ),
) -> dict[
    str,
    Any,
]:
    aligned_context = (
        validate_and_align_validation_samples(
            baseline_samples=(
                baseline_samples
            ),
            context_samples=(
                context_samples
            ),
        )
    )

    validate_prediction_inputs(
        samples=baseline_samples,
        baseline_predictions=(
            baseline_predictions
        ),
        baseline_confidences=(
            baseline_confidences
        ),
        context_predictions=(
            context_predictions
        ),
        context_confidences=(
            context_confidences
        ),
        label_mapping=(
            label_mapping
        ),
    )

    validate_positive_integer(
        examples_per_bucket,
        "Examples per bucket",
    )

    validate_audit_pairs(
        audit_pairs=(
            audit_pairs
        ),
        label_mapping=(
            label_mapping
        ),
    )

    records = [
        build_transition_record(
            baseline_sample=(
                baseline_sample
            ),
            context_sample=(
                context_sample
            ),
            baseline_prediction=(
                baseline_prediction
            ),
            baseline_confidence=(
                baseline_confidence
            ),
            context_prediction=(
                context_prediction
            ),
            context_confidence=(
                context_confidence
            ),
        )
        for (
                baseline_sample,
                context_sample,
                baseline_prediction,
                baseline_confidence,
                context_prediction,
                context_confidence,
        ) in zip(
            baseline_samples,
            aligned_context,
            baseline_predictions,
            baseline_confidences,
            context_predictions,
            context_confidences,
            strict=True,
        )
    ]

    recovered = [
        record
        for record in records
        if (
                record[
                    "fine_transition"
                ]
                == "wrong_to_correct"
        )
    ]

    regressed = [
        record
        for record in records
        if (
                record[
                    "fine_transition"
                ]
                == "correct_to_wrong"
        )
    ]

    wrong_changed = [
        record
        for record in records
        if (
                record[
                    "fine_transition"
                ]
                == "wrong_to_wrong"
                and record[
                    "baseline"
                ][
                    "prediction"
                ]
                != record[
                    "context"
                ][
                    "prediction"
                ]
        )
    ]

    coarse_recovered = [
        record
        for record in records
        if (
                record[
                    "coarse_transition"
                ]
                == "wrong_to_correct"
        )
    ]

    return {
        "overall": (
            build_overall_transition_summary(
                records
            )
        ),
        "per_label": (
            build_per_label_summary(
                records=records,
                label_mapping=(
                    label_mapping
                ),
            )
        ),
        "boundary_pairs": [
            build_boundary_pair_summary(
                records=records,
                label_a=label_a,
                label_b=label_b,
                examples_per_bucket=(
                    examples_per_bucket
                ),
            )
            for (
                    label_a,
                    label_b,
            ) in audit_pairs
        ],
        "examples": {
            "wrong_to_correct": (
                select_examples(
                    records=recovered,
                    limit=(
                        examples_per_bucket
                    ),
                    confidence_source=(
                        "context"
                    ),
                )
            ),
            "correct_to_wrong": (
                select_examples(
                    records=regressed,
                    limit=(
                        examples_per_bucket
                    ),
                    confidence_source=(
                        "baseline"
                    ),
                )
            ),
            "wrong_to_wrong_prediction_changed": (
                select_examples(
                    records=(
                        wrong_changed
                    ),
                    limit=(
                        examples_per_bucket
                    ),
                    confidence_source=(
                        "context"
                    ),
                )
            ),
            "coarse_wrong_to_correct": (
                select_examples(
                    records=(
                        coarse_recovered
                    ),
                    limit=(
                        examples_per_bucket
                    ),
                    confidence_source=(
                        "context"
                    ),
                )
            ),
        },
    }


def predict_model(
        model_dir: Path,
        samples: list[
            EmotionClassificationSample
        ],
        label_mapping: EmotionLabelMapping,
        max_length: int,
        eval_batch_size: int,
        device: torch.device,
        device_index: int,
) -> tuple[
    list[str],
    list[str],
    list[float],
    float,
]:
    if not model_dir.exists():
        raise FileNotFoundError(
            "Model directory not found: "
            f"{model_dir}"
        )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            model_dir,
            local_files_only=True,
        )
    )

    model = (
        AutoModelForSequenceClassification
        .from_pretrained(
            model_dir,
            local_files_only=True,
        )
    )

    validate_model_config(
        config=model.config,
        label_mapping=(
            label_mapping
        ),
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
            "Transition audit model "
            "is not on a CUDA "
            "device: "
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
        predictions,
        confidences,
    ) = predict_validation(
        model=model,
        tokenizer=tokenizer,
        samples=samples,
        label_mapping=(
            label_mapping
        ),
        max_length=(
            max_length
        ),
        eval_batch_size=(
            eval_batch_size
        ),
        device=device,
    )

    torch.cuda.synchronize(
        device_index
    )

    inference_seconds = (
        time.perf_counter()
        - started_at
    )

    model.to(
        "cpu"
    )

    del model
    del tokenizer

    torch.cuda.empty_cache()

    return (
        true_labels,
        predictions,
        confidences,
        inference_seconds,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Compare paired validation "
            "transitions between the "
            "TripPamine HS01 baseline "
            "and multi-turn KoELECTRA "
            "classifiers."
        )
    )

    parser.add_argument(
        "--baseline-validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--context-validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--baseline-model-dir",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--context-model-dir",
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
        default=(
            DEFAULT_EVAL_BATCH_SIZE
        ),
    )

    parser.add_argument(
        "--examples-per-bucket",
        type=int,
        default=(
            DEFAULT_EXAMPLES_PER_BUCKET
        ),
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output.exists():
        raise FileExistsError(
            "Transition audit report "
            "already exists: "
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
        args.examples_per_bucket,
        "Examples per bucket",
    )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. "
            "Validation transition "
            "audit cannot run."
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

    baseline_validation = (
        read_validation_dataset(
            args.baseline_validation
        )
    )

    context_validation = (
        read_validation_dataset(
            args.context_validation
        )
    )

    validate_validation_samples(
        samples=baseline_validation,
        label_mapping=(
            label_mapping
        ),
    )

    validate_validation_samples(
        samples=context_validation,
        label_mapping=(
            label_mapping
        ),
    )

    aligned_context = (
        validate_and_align_validation_samples(
            baseline_samples=(
                baseline_validation
            ),
            context_samples=(
                context_validation
            ),
        )
    )

    (
        baseline_true,
        baseline_predictions,
        baseline_confidences,
        baseline_seconds,
    ) = predict_model(
        model_dir=(
            args.baseline_model_dir
        ),
        samples=(
            baseline_validation
        ),
        label_mapping=(
            label_mapping
        ),
        max_length=(
            args.max_length
        ),
        eval_batch_size=(
            args.eval_batch_size
        ),
        device=device,
        device_index=(
            device_index
        ),
    )

    (
        context_true,
        context_predictions,
        context_confidences,
        context_seconds,
    ) = predict_model(
        model_dir=(
            args.context_model_dir
        ),
        samples=(
            aligned_context
        ),
        label_mapping=(
            label_mapping
        ),
        max_length=(
            args.max_length
        ),
        eval_batch_size=(
            args.eval_batch_size
        ),
        device=device,
        device_index=(
            device_index
        ),
    )

    expected_true = [
        sample.label
        for sample in (
            baseline_validation
        )
    ]

    if (
            baseline_true
            != expected_true
    ):
        raise RuntimeError(
            "Baseline prediction order "
            "does not match paired "
            "validation samples."
        )

    if (
            context_true
            != expected_true
    ):
        raise RuntimeError(
            "Context prediction order "
            "does not match paired "
            "validation samples."
        )

    audit = (
        build_paired_transition_audit(
            baseline_samples=(
                baseline_validation
            ),
            context_samples=(
                aligned_context
            ),
            baseline_predictions=(
                baseline_predictions
            ),
            baseline_confidences=(
                baseline_confidences
            ),
            context_predictions=(
                context_predictions
            ),
            context_confidences=(
                context_confidences
            ),
            label_mapping=(
                label_mapping
            ),
            audit_pairs=(
                DEFAULT_AUDIT_PAIRS
            ),
            examples_per_bucket=(
                args.examples_per_bucket
            ),
        )
    )

    report = {
        "experiment": (
            "T0-Q9-C-"
            "validation-transition-audit"
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
            "baseline": {
                "validation": {
                    "file": str(
                        args
                        .baseline_validation
                    ),
                    "samples": len(
                        baseline_validation
                    ),
                    "sha256": (
                        calculate_sha256(
                            args
                            .baseline_validation
                        )
                    ),
                },
                "model": {
                    "directory": str(
                        args
                        .baseline_model_dir
                    ),
                },
            },
            "context": {
                "validation": {
                    "file": str(
                        args
                        .context_validation
                    ),
                    "samples": len(
                        aligned_context
                    ),
                    "sha256": (
                        calculate_sha256(
                            args
                            .context_validation
                        )
                    ),
                },
                "model": {
                    "directory": str(
                        args
                        .context_model_dir
                    ),
                },
            },
            "test_split_used": False,
        },
        "protocol": {
            "paired_by": (
                "sample_id"
            ),
            "max_length": (
                args.max_length
            ),
            "eval_batch_size": (
                args.eval_batch_size
            ),
            "precision": "fp32",
            "padding": "dynamic",
            "examples_per_bucket": (
                args.examples_per_bucket
            ),
            "audit_pairs": [
                list(
                    pair
                )
                for pair
                in DEFAULT_AUDIT_PAIRS
            ],
        },
        "privacy": {
            "contains_raw_validation_text": (
                True
            ),
            "profile_id_included": False,
            "talk_id_included": False,
        },
        "audit": (
            audit
        ),
        "timing": {
            "baseline_inference_seconds": (
                baseline_seconds
            ),
            "context_inference_seconds": (
                context_seconds
            ),
            "total_inference_seconds": (
                baseline_seconds
                + context_seconds
            ),
        },
    }

    save_report(
        report=report,
        output_path=args.output,
    )

    overall = (
        audit[
            "overall"
        ]
    )

    print()
    print(
        "KoELECTRA paired validation "
        "transition audit completed"
    )

    print()
    print(
        "Validation samples: "
        f"{overall['samples']}"
    )

    print(
        "Test split used: False"
    )

    print()
    print(
        "Fine transitions"
    )

    for key in (
        "correct_to_correct",
        "correct_to_wrong",
        "wrong_to_correct",
        "wrong_to_wrong",
    ):
        print(
            f"  {key}: "
            f"{overall['fine'][key]}"
        )

    print(
        "  baseline correct: "
        f"{overall['fine']['baseline_correct']}"
    )

    print(
        "  context correct: "
        f"{overall['fine']['context_correct']}"
    )

    print(
        "  net gain: "
        f"{overall['fine']['net_gain']}"
    )

    print()
    print(
        "Coarse transitions"
    )

    for key in (
        "correct_to_correct",
        "correct_to_wrong",
        "wrong_to_correct",
        "wrong_to_wrong",
    ):
        print(
            f"  {key}: "
            f"{overall['coarse'][key]}"
        )

    print(
        "  net gain: "
        f"{overall['coarse']['net_gain']}"
    )

    print()
    print(
        "Boundary pairs"
    )

    for pair_audit in (
            audit[
                "boundary_pairs"
            ]
    ):
        pair = (
            pair_audit[
                "pair"
            ]
        )

        counts = (
            pair_audit[
                "counts"
            ]
        )

        print(
            f"  {pair['label_a']} "
            f"<-> "
            f"{pair['label_b']}: "
            f"recovered="
            f"{counts['recovered']}, "
            f"regressed="
            f"{counts['regressed']}, "
            f"net="
            f"{counts['net_gain']}, "
            f"direct "
            f"{counts['baseline_direct_confusion']} "
            f"-> "
            f"{counts['context_direct_confusion']}"
        )

    print()
    print(
        "Baseline inference seconds: "
        f"{baseline_seconds:.2f}"
    )

    print(
        "Context inference seconds: "
        f"{context_seconds:.2f}"
    )

    print(
        "Report: "
        f"{args.output}"
    )

    print()
    print(
        "PAIRED VALIDATION "
        "TRANSITION AUDIT: PASS"
    )


if __name__ == "__main__":
    main()