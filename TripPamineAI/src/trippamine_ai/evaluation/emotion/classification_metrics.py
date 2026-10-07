from pydantic import BaseModel

from sklearn.metrics import (
    accuracy_score,
    precision_recall_fscore_support,
)


class AggregateMetrics(BaseModel):
    accuracy: float

    macro_precision: float
    macro_recall: float
    macro_f1: float

    weighted_f1: float


class LabelMetrics(BaseModel):
    label: str

    precision: float
    recall: float
    f1: float
    support: int


class ClassificationMetrics(BaseModel):
    total_samples: int
    labels: list[str]

    aggregate: AggregateMetrics

    per_label: list[
        LabelMetrics
    ]


class EmotionClassificationMetrics(
    BaseModel
):
    fine: ClassificationMetrics
    coarse: ClassificationMetrics


class EmotionClassificationEvaluator:

    def evaluate(
        self,
        true_labels: list[str],
        predicted_labels: list[str],
        fine_to_coarse: dict[
            str,
            str,
        ],
    ) -> EmotionClassificationMetrics:
        if not true_labels:
            raise ValueError(
                "Evaluation labels "
                "must not be empty."
            )

        if len(
            true_labels
        ) != len(
            predicted_labels
        ):
            raise ValueError(
                "True/predicted label "
                "count mismatch."
            )

        fine_labels = sorted(
            set(true_labels)
            | set(predicted_labels)
        )

        unknown_labels = {
            label
            for label in fine_labels
            if label
            not in fine_to_coarse
        }

        if unknown_labels:
            raise ValueError(
                "Missing coarse mapping "
                "for labels: "
                f"{sorted(unknown_labels)}"
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

        return EmotionClassificationMetrics(
            fine=self._calculate(
                true_labels,
                predicted_labels,
            ),
            coarse=self._calculate(
                true_coarse,
                predicted_coarse,
            ),
        )

    @staticmethod
    def _calculate(
        true_labels: list[str],
        predicted_labels: list[str],
    ) -> ClassificationMetrics:
        labels = sorted(
            set(true_labels)
            | set(predicted_labels)
        )

        (
            precision,
            recall,
            f1,
            support,
        ) = (
            precision_recall_fscore_support(
                true_labels,
                predicted_labels,
                labels=labels,
                average=None,
                zero_division=0,
            )
        )

        (
            macro_precision,
            macro_recall,
            macro_f1,
            _,
        ) = (
            precision_recall_fscore_support(
                true_labels,
                predicted_labels,
                labels=labels,
                average="macro",
                zero_division=0,
            )
        )

        (
            _,
            _,
            weighted_f1,
            _,
        ) = (
            precision_recall_fscore_support(
                true_labels,
                predicted_labels,
                labels=labels,
                average="weighted",
                zero_division=0,
            )
        )

        return ClassificationMetrics(
            total_samples=len(
                true_labels
            ),
            labels=labels,
            aggregate=AggregateMetrics(
                accuracy=float(
                    accuracy_score(
                        true_labels,
                        predicted_labels,
                    )
                ),
                macro_precision=float(
                    macro_precision
                ),
                macro_recall=float(
                    macro_recall
                ),
                macro_f1=float(
                    macro_f1
                ),
                weighted_f1=float(
                    weighted_f1
                ),
            ),
            per_label=[
                LabelMetrics(
                    label=label,
                    precision=float(
                        precision[index]
                    ),
                    recall=float(
                        recall[index]
                    ),
                    f1=float(
                        f1[index]
                    ),
                    support=int(
                        support[index]
                    ),
                )
                for index, label
                in enumerate(labels)
            ],
        )