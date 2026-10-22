from collections.abc import Callable
from typing import Any

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.evaluation.emotion.classification_metrics import (
    EmotionClassificationEvaluator,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


def select_balanced_samples(
        samples: list[
            EmotionClassificationSample
        ],
        label_mapping: EmotionLabelMapping,
        samples_per_label: int,
) -> list[
    EmotionClassificationSample
]:
    if (
            isinstance(
                samples_per_label,
                bool,
            )
            or not isinstance(
                samples_per_label,
                int,
            )
            or samples_per_label <= 0
    ):
        raise ValueError(
            "Samples per label must be "
            "a positive integer."
        )

    buckets = {
        label: []
        for label in (
            label_mapping.labels
        )
    }

    for sample in samples:
        if (
                sample.label
                not in buckets
        ):
            raise ValueError(
                "Unknown fine emotion "
                f"label: {sample.label}"
            )

        bucket = buckets[
            sample.label
        ]

        if (
                len(bucket)
                < samples_per_label
        ):
            bucket.append(
                sample
            )

    shortages = {
        label: len(records)
        for label, records
        in buckets.items()
        if (
                len(records)
                < samples_per_label
        )
    }

    if shortages:
        raise ValueError(
            "Insufficient balanced "
            "samples for labels: "
            f"{shortages}"
        )

    selected = []

    for label in (
            label_mapping.labels
    ):
        selected.extend(
            buckets[label]
        )

    return selected


def build_fine_to_coarse(
        samples: list[
            EmotionClassificationSample
        ],
        label_mapping: EmotionLabelMapping,
) -> dict[str, str]:
    mapping: dict[
        str,
        str,
    ] = {}

    for sample in samples:
        if (
                sample.label
                not in label_mapping.label2id
        ):
            raise ValueError(
                "Unknown fine emotion "
                f"label: {sample.label}"
            )

        existing = mapping.get(
            sample.label
        )

        if (
                existing is not None
                and existing
                != sample.coarse_label
        ):
            raise ValueError(
                "Inconsistent coarse "
                "emotion mapping for "
                f"{sample.label}: "
                f"'{existing}' vs "
                f"'{sample.coarse_label}'"
            )

        mapping[
            sample.label
        ] = sample.coarse_label

    missing_labels = [
        label
        for label in (
            label_mapping.labels
        )
        if label not in mapping
    ]

    if missing_labels:
        raise ValueError(
            "Missing fine emotion "
            "labels for coarse mapping: "
            f"{missing_labels}"
        )

    return mapping


def build_compute_metrics(
        label_mapping: EmotionLabelMapping,
        fine_to_coarse: dict[
            str,
            str,
        ],
) -> Callable[
    [Any],
    dict[str, float],
]:
    evaluator = (
        EmotionClassificationEvaluator()
    )

    def compute_metrics(
            eval_prediction: Any,
    ) -> dict[str, float]:
        predictions = (
            eval_prediction.predictions
        )

        if isinstance(
                predictions,
                tuple,
        ):
            predictions = (
                predictions[0]
            )

        predicted_ids = (
            predictions
            .argmax(
                axis=-1
            )
            .tolist()
        )

        true_ids = (
            eval_prediction
            .label_ids
            .tolist()
        )

        predicted_labels = [
            label_mapping.decode(
                int(label_id)
            )
            for label_id
            in predicted_ids
        ]

        true_labels = [
            label_mapping.decode(
                int(label_id)
            )
            for label_id
            in true_ids
        ]

        evaluation = (
            evaluator.evaluate(
                true_labels=(
                    true_labels
                ),
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
        }

    return compute_metrics
