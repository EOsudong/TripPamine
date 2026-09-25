from collections import Counter

from pydantic import BaseModel
from sklearn.metrics import (
    confusion_matrix,
)

from trippamine_ai.evaluation.emotion.classification_metrics import (
    EmotionClassificationEvaluator,
    EmotionClassificationMetrics,
    LabelMetrics,
)


class ConfusionMatrixData(BaseModel):
    labels: list[str]
    values: list[
        list[int]
    ]


class FineConfusion(BaseModel):
    true_label: str
    predicted_label: str

    true_coarse: str
    predicted_coarse: str

    same_coarse: bool

    count: int


class CoarseConfusion(BaseModel):
    true_coarse: str
    predicted_coarse: str

    count: int


class ErrorBoundarySummary(BaseModel):
    total_errors: int

    same_coarse_errors: int
    cross_coarse_errors: int

    same_coarse_error_rate: float
    cross_coarse_error_rate: float


class LabelRanking(BaseModel):
    best_labels: list[
        LabelMetrics
    ]

    worst_labels: list[
        LabelMetrics
    ]


class EmotionClassificationErrorAnalysis(
    BaseModel
):
    total_samples: int

    correct_predictions: int
    incorrect_predictions: int

    evaluation: (
        EmotionClassificationMetrics
    )

    error_boundary: (
        ErrorBoundarySummary
    )

    label_ranking: LabelRanking

    fine_confusion_matrix: (
        ConfusionMatrixData
    )

    coarse_confusion_matrix: (
        ConfusionMatrixData
    )

    top_fine_confusions: list[
        FineConfusion
    ]

    cross_coarse_confusions: list[
        CoarseConfusion
    ]


class EmotionClassificationErrorAnalyzer:

    def analyze(
        self,
        true_labels: list[str],
        predicted_labels: list[str],
        fine_to_coarse: dict[
            str,
            str,
        ],
        top_k: int = 10,
    ) -> EmotionClassificationErrorAnalysis:
        if top_k <= 0:
            raise ValueError(
                "top_k must be "
                "greater than zero."
            )

        evaluation = (
            EmotionClassificationEvaluator()
            .evaluate(
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

        true_coarse = [
            fine_to_coarse[label]
            for label in true_labels
        ]

        predicted_coarse = [
            fine_to_coarse[label]
            for label
            in predicted_labels
        ]

        correct_predictions = sum(
            1
            for true_label, predicted_label
            in zip(
                true_labels,
                predicted_labels,
            )
            if true_label
            == predicted_label
        )

        incorrect_predictions = (
            len(true_labels)
            - correct_predictions
        )

        fine_confusion_counts: Counter[
            tuple[
                str,
                str,
            ]
        ] = Counter()

        coarse_confusion_counts: Counter[
            tuple[
                str,
                str,
            ]
        ] = Counter()

        same_coarse_errors = 0
        cross_coarse_errors = 0

        for (
            true_label,
            predicted_label,
            true_coarse_label,
            predicted_coarse_label,
        ) in zip(
            true_labels,
            predicted_labels,
            true_coarse,
            predicted_coarse,
        ):
            if (
                true_label
                == predicted_label
            ):
                continue

            fine_confusion_counts[
                (
                    true_label,
                    predicted_label,
                )
            ] += 1

            if (
                true_coarse_label
                == predicted_coarse_label
            ):
                same_coarse_errors += 1

            else:
                cross_coarse_errors += 1

                coarse_confusion_counts[
                    (
                        true_coarse_label,
                        predicted_coarse_label,
                    )
                ] += 1

        if incorrect_predictions:
            same_coarse_error_rate = (
                same_coarse_errors
                / incorrect_predictions
            )

            cross_coarse_error_rate = (
                cross_coarse_errors
                / incorrect_predictions
            )

        else:
            same_coarse_error_rate = 0.0
            cross_coarse_error_rate = 0.0

        per_label = (
            evaluation.fine.per_label
        )

        best_labels = sorted(
            per_label,
            key=lambda metric: (
                -metric.f1,
                -metric.recall,
                -metric.precision,
                -metric.support,
                metric.label,
            ),
        )[:top_k]

        worst_labels = sorted(
            per_label,
            key=lambda metric: (
                metric.f1,
                metric.recall,
                metric.precision,
                -metric.support,
                metric.label,
            ),
        )[:top_k]

        top_fine_confusions = [
            FineConfusion(
                true_label=(
                    true_label
                ),
                predicted_label=(
                    predicted_label
                ),
                true_coarse=(
                    fine_to_coarse[
                        true_label
                    ]
                ),
                predicted_coarse=(
                    fine_to_coarse[
                        predicted_label
                    ]
                ),
                same_coarse=(
                    fine_to_coarse[
                        true_label
                    ]
                    == fine_to_coarse[
                        predicted_label
                    ]
                ),
                count=count,
            )
            for (
                (
                    true_label,
                    predicted_label,
                ),
                count,
            )
            in sorted(
                fine_confusion_counts.items(),
                key=lambda item: (
                    -item[1],
                    item[0][0],
                    item[0][1],
                ),
            )[:top_k]
        ]

        cross_coarse_confusions = [
            CoarseConfusion(
                true_coarse=(
                    true_coarse_label
                ),
                predicted_coarse=(
                    predicted_coarse_label
                ),
                count=count,
            )
            for (
                (
                    true_coarse_label,
                    predicted_coarse_label,
                ),
                count,
            )
            in sorted(
                coarse_confusion_counts.items(),
                key=lambda item: (
                    -item[1],
                    item[0][0],
                    item[0][1],
                ),
            )
        ]

        fine_labels = (
            evaluation.fine.labels
        )

        coarse_labels = (
            evaluation.coarse.labels
        )

        fine_matrix = confusion_matrix(
            true_labels,
            predicted_labels,
            labels=fine_labels,
        ).tolist()

        coarse_matrix = confusion_matrix(
            true_coarse,
            predicted_coarse,
            labels=coarse_labels,
        ).tolist()

        return (
            EmotionClassificationErrorAnalysis(
                total_samples=len(
                    true_labels
                ),
                correct_predictions=(
                    correct_predictions
                ),
                incorrect_predictions=(
                    incorrect_predictions
                ),
                evaluation=evaluation,
                error_boundary=(
                    ErrorBoundarySummary(
                        total_errors=(
                            incorrect_predictions
                        ),
                        same_coarse_errors=(
                            same_coarse_errors
                        ),
                        cross_coarse_errors=(
                            cross_coarse_errors
                        ),
                        same_coarse_error_rate=(
                            same_coarse_error_rate
                        ),
                        cross_coarse_error_rate=(
                            cross_coarse_error_rate
                        ),
                    )
                ),
                label_ranking=(
                    LabelRanking(
                        best_labels=(
                            best_labels
                        ),
                        worst_labels=(
                            worst_labels
                        ),
                    )
                ),
                fine_confusion_matrix=(
                    ConfusionMatrixData(
                        labels=fine_labels,
                        values=fine_matrix,
                    )
                ),
                coarse_confusion_matrix=(
                    ConfusionMatrixData(
                        labels=coarse_labels,
                        values=coarse_matrix,
                    )
                ),
                top_fine_confusions=(
                    top_fine_confusions
                ),
                cross_coarse_confusions=(
                    cross_coarse_confusions
                ),
            )
        )