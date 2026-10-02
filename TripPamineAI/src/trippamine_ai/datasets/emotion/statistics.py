from collections import Counter
from collections.abc import Iterable

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
)


class LabelDistribution(BaseModel):
    label: str
    label_name: str
    count: int
    share: float


class ClassificationStatistics(BaseModel):
    total_records: int

    fine_emotion: list[LabelDistribution]

    coarse_emotion: dict[str, int]
    quality_status: dict[str, int]
    quality_issues: dict[str, int]

    missing_labels: list[str]

    minimum_label: str | None
    minimum_label_count: int | None

    maximum_label: str | None
    maximum_label_count: int | None

    imbalance_ratio: float | None


class ClassificationStatisticsAnalyzer:

    def analyze(
            self,
            records: Iterable[
                EmotionClassificationSample
            ],
    ) -> ClassificationStatistics:
        label_counts: Counter[str] = Counter()
        coarse_counts: Counter[str] = Counter()
        quality_counts: Counter[str] = Counter()
        issue_counts: Counter[str] = Counter()

        total_records = 0

        for record in records:
            total_records += 1

            label_counts[
                record.label
            ] += 1

            coarse_counts[
                record.coarse_label
            ] += 1

            quality_counts[
                record.quality_status
            ] += 1

            for issue_code in (
                    record.quality_issue_codes
            ):
                issue_counts[
                    issue_code
                ] += 1

        fine_emotion = self._build_fine_distribution(
            label_counts=label_counts,
            total_records=total_records,
        )

        missing_labels = [
            label
            for label in EMOTION_CODEBOOK
            if label_counts[label] == 0
        ]

        nonzero_counts = [
            count
            for count in label_counts.values()
            if count > 0
        ]

        minimum_label = None
        minimum_label_count = None

        maximum_label = None
        maximum_label_count = None

        imbalance_ratio = None

        if nonzero_counts:
            minimum_label = min(
                label_counts,
                key=label_counts.get,
            )

            maximum_label = max(
                label_counts,
                key=label_counts.get,
            )

            minimum_label_count = (
                label_counts[minimum_label]
            )

            maximum_label_count = (
                label_counts[maximum_label]
            )

            if minimum_label_count > 0:
                imbalance_ratio = (
                        maximum_label_count
                        / minimum_label_count
                )

        return ClassificationStatistics(
            total_records=total_records,
            fine_emotion=fine_emotion,
            coarse_emotion=dict(
                sorted(coarse_counts.items())
            ),
            quality_status=dict(
                sorted(quality_counts.items())
            ),
            quality_issues=dict(
                sorted(issue_counts.items())
            ),
            missing_labels=missing_labels,
            minimum_label=minimum_label,
            minimum_label_count=minimum_label_count,
            maximum_label=maximum_label,
            maximum_label_count=maximum_label_count,
            imbalance_ratio=imbalance_ratio,
        )

    @staticmethod
    def _build_fine_distribution(
            label_counts: Counter[str],
            total_records: int,
    ) -> list[LabelDistribution]:
        distribution = []

        for label in sorted(
                EMOTION_CODEBOOK.keys()
        ):
            count = label_counts[label]

            share = (
                count / total_records
                if total_records > 0
                else 0.0
            )

            distribution.append(
                LabelDistribution(
                    label=label,
                    label_name=(
                        EMOTION_CODEBOOK[label]
                    ),
                    count=count,
                    share=share,
                )
            )

        return distribution
