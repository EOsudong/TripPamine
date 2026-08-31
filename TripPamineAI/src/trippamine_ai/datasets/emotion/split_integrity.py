from collections import Counter
from collections.abc import Iterable

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
)


class SplitSummary(BaseModel):
    total_records: int

    unique_record_ids: int
    unique_talk_ids: int
    unique_profile_ids: int

    duplicate_record_ids: int
    duplicate_talk_ids: int

    fine_emotion_counts: dict[str, int]
    coarse_emotion_counts: dict[str, int]


class LabelShareComparison(BaseModel):
    label: str
    label_name: str

    training_count: int
    training_share: float

    validation_count: int
    validation_share: float

    share_difference: float
    absolute_share_difference: float


class CoarseShareComparison(BaseModel):
    label: str

    training_count: int
    training_share: float

    validation_count: int
    validation_share: float

    share_difference: float
    absolute_share_difference: float


class OverlapSummary(BaseModel):
    record_id_count: int
    talk_id_count: int
    profile_id_count: int

    training_profile_overlap_rate: float
    validation_profile_overlap_rate: float

    record_id_samples: list[str]
    talk_id_samples: list[str]
    profile_id_samples: list[str]


class SplitIntegrityReport(BaseModel):
    training: SplitSummary
    validation: SplitSummary

    overlap: OverlapSummary

    fine_emotion_comparison: list[
        LabelShareComparison
    ]

    coarse_emotion_comparison: list[
        CoarseShareComparison
    ]

    top_fine_emotion_differences: list[
        LabelShareComparison
    ]


class _SplitSnapshot:

    def __init__(self) -> None:
        self.total_records = 0

        self.record_ids: Counter[str] = Counter()
        self.talk_ids: Counter[str] = Counter()
        self.profile_ids: set[str] = set()

        self.fine_emotions: Counter[str] = Counter()
        self.coarse_emotions: Counter[str] = Counter()

    def add(
        self,
        record: EmotionClassificationSample,
    ) -> None:
        self.total_records += 1

        self.record_ids[
            record.id
        ] += 1

        self.talk_ids[
            record.source.talk_id
        ] += 1

        self.profile_ids.add(
            record.source.profile_id
        )

        self.fine_emotions[
            record.label
        ] += 1

        self.coarse_emotions[
            record.coarse_label
        ] += 1

    def to_summary(self) -> SplitSummary:
        return SplitSummary(
            total_records=self.total_records,
            unique_record_ids=len(
                self.record_ids
            ),
            unique_talk_ids=len(
                self.talk_ids
            ),
            unique_profile_ids=len(
                self.profile_ids
            ),
            duplicate_record_ids=sum(
                count - 1
                for count in self.record_ids.values()
                if count > 1
            ),
            duplicate_talk_ids=sum(
                count - 1
                for count in self.talk_ids.values()
                if count > 1
            ),
            fine_emotion_counts=dict(
                sorted(
                    self.fine_emotions.items()
                )
            ),
            coarse_emotion_counts=dict(
                sorted(
                    self.coarse_emotions.items()
                )
            ),
        )


class SplitIntegrityAnalyzer:

    SAMPLE_LIMIT = 20

    def analyze(
        self,
        training_records: Iterable[
            EmotionClassificationSample
        ],
        validation_records: Iterable[
            EmotionClassificationSample
        ],
    ) -> SplitIntegrityReport:
        training = self._build_snapshot(
            training_records
        )

        validation = self._build_snapshot(
            validation_records
        )

        overlap = self._build_overlap(
            training,
            validation,
        )

        fine_comparison = (
            self._build_fine_comparison(
                training,
                validation,
            )
        )

        coarse_comparison = (
            self._build_coarse_comparison(
                training,
                validation,
            )
        )

        top_differences = sorted(
            fine_comparison,
            key=lambda item: (
                item.absolute_share_difference
            ),
            reverse=True,
        )[:10]

        return SplitIntegrityReport(
            training=training.to_summary(),
            validation=validation.to_summary(),
            overlap=overlap,
            fine_emotion_comparison=(
                fine_comparison
            ),
            coarse_emotion_comparison=(
                coarse_comparison
            ),
            top_fine_emotion_differences=(
                top_differences
            ),
        )

    @staticmethod
    def _build_snapshot(
        records: Iterable[
            EmotionClassificationSample
        ],
    ) -> _SplitSnapshot:
        snapshot = _SplitSnapshot()

        for record in records:
            snapshot.add(record)

        return snapshot

    def _build_overlap(
        self,
        training: _SplitSnapshot,
        validation: _SplitSnapshot,
    ) -> OverlapSummary:
        record_overlap = (
            set(training.record_ids)
            & set(validation.record_ids)
        )

        talk_overlap = (
            set(training.talk_ids)
            & set(validation.talk_ids)
        )

        profile_overlap = (
            training.profile_ids
            & validation.profile_ids
        )

        training_profile_rate = (
            len(profile_overlap)
            / len(training.profile_ids)
            if training.profile_ids
            else 0.0
        )

        validation_profile_rate = (
            len(profile_overlap)
            / len(validation.profile_ids)
            if validation.profile_ids
            else 0.0
        )

        return OverlapSummary(
            record_id_count=len(
                record_overlap
            ),
            talk_id_count=len(
                talk_overlap
            ),
            profile_id_count=len(
                profile_overlap
            ),
            training_profile_overlap_rate=(
                training_profile_rate
            ),
            validation_profile_overlap_rate=(
                validation_profile_rate
            ),
            record_id_samples=sorted(
                record_overlap
            )[:self.SAMPLE_LIMIT],
            talk_id_samples=sorted(
                talk_overlap
            )[:self.SAMPLE_LIMIT],
            profile_id_samples=sorted(
                profile_overlap
            )[:self.SAMPLE_LIMIT],
        )

    @staticmethod
    def _build_fine_comparison(
        training: _SplitSnapshot,
        validation: _SplitSnapshot,
    ) -> list[LabelShareComparison]:
        results = []

        for label in sorted(
            EMOTION_CODEBOOK
        ):
            training_count = (
                training.fine_emotions[label]
            )

            validation_count = (
                validation.fine_emotions[label]
            )

            training_share = (
                training_count
                / training.total_records
                if training.total_records
                else 0.0
            )

            validation_share = (
                validation_count
                / validation.total_records
                if validation.total_records
                else 0.0
            )

            difference = (
                validation_share
                - training_share
            )

            results.append(
                LabelShareComparison(
                    label=label,
                    label_name=(
                        EMOTION_CODEBOOK[label]
                    ),
                    training_count=(
                        training_count
                    ),
                    training_share=(
                        training_share
                    ),
                    validation_count=(
                        validation_count
                    ),
                    validation_share=(
                        validation_share
                    ),
                    share_difference=(
                        difference
                    ),
                    absolute_share_difference=(
                        abs(difference)
                    ),
                )
            )

        return results

    @staticmethod
    def _build_coarse_comparison(
        training: _SplitSnapshot,
        validation: _SplitSnapshot,
    ) -> list[CoarseShareComparison]:
        labels = sorted(
            set(training.coarse_emotions)
            | set(validation.coarse_emotions)
        )

        results = []

        for label in labels:
            training_count = (
                training.coarse_emotions[label]
            )

            validation_count = (
                validation.coarse_emotions[label]
            )

            training_share = (
                training_count
                / training.total_records
                if training.total_records
                else 0.0
            )

            validation_share = (
                validation_count
                / validation.total_records
                if validation.total_records
                else 0.0
            )

            difference = (
                validation_share
                - training_share
            )

            results.append(
                CoarseShareComparison(
                    label=label,
                    training_count=(
                        training_count
                    ),
                    training_share=(
                        training_share
                    ),
                    validation_count=(
                        validation_count
                    ),
                    validation_share=(
                        validation_share
                    ),
                    share_difference=(
                        difference
                    ),
                    absolute_share_difference=(
                        abs(difference)
                    ),
                )
            )

        return results