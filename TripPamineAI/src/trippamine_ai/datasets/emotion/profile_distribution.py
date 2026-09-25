from collections import Counter, defaultdict
from collections.abc import Iterable
from statistics import median

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
)


class ProfileLabelDistribution(BaseModel):
    profile_id: str

    total_records: int
    unique_labels: int

    label_counts: dict[str, int]
    coarse_label_counts: dict[str, int]


class LabelProfileSupport(BaseModel):
    label: str
    label_name: str

    total_records: int
    profile_count: int

    min_records_per_profile: int
    median_records_per_profile: float
    max_records_per_profile: int

    max_profile_share: float


class ProfileDistributionSummary(BaseModel):
    total_records: int
    total_profiles: int

    duplicate_record_ids: int

    min_records_per_profile: int
    median_records_per_profile: float
    max_records_per_profile: int

    min_labels_per_profile: int
    median_labels_per_profile: float
    max_labels_per_profile: int

    profiles_with_single_label: int

    label_support: list[LabelProfileSupport]

    profiles: list[ProfileLabelDistribution]


class ProfileLabelDistributionAnalyzer:

    def analyze(
        self,
        records: Iterable[
            EmotionClassificationSample
        ],
    ) -> ProfileDistributionSummary:
        records_by_profile: dict[
            str,
            list[EmotionClassificationSample],
        ] = defaultdict(list)

        record_ids: Counter[str] = Counter()

        total_records = 0

        for record in records:
            total_records += 1

            record_ids[
                record.id
            ] += 1

            records_by_profile[
                record.source.profile_id
            ].append(record)

        profiles = self._build_profiles(
            records_by_profile
        )

        label_support = self._build_label_support(
            profiles
        )

        record_counts = [
            profile.total_records
            for profile in profiles
        ]

        label_counts = [
            profile.unique_labels
            for profile in profiles
        ]

        duplicate_record_ids = sum(
            count - 1
            for count in record_ids.values()
            if count > 1
        )

        return ProfileDistributionSummary(
            total_records=total_records,
            total_profiles=len(profiles),
            duplicate_record_ids=(
                duplicate_record_ids
            ),
            min_records_per_profile=(
                min(record_counts)
                if record_counts
                else 0
            ),
            median_records_per_profile=(
                float(median(record_counts))
                if record_counts
                else 0.0
            ),
            max_records_per_profile=(
                max(record_counts)
                if record_counts
                else 0
            ),
            min_labels_per_profile=(
                min(label_counts)
                if label_counts
                else 0
            ),
            median_labels_per_profile=(
                float(median(label_counts))
                if label_counts
                else 0.0
            ),
            max_labels_per_profile=(
                max(label_counts)
                if label_counts
                else 0
            ),
            profiles_with_single_label=sum(
                1
                for profile in profiles
                if profile.unique_labels == 1
            ),
            label_support=label_support,
            profiles=profiles,
        )

    @staticmethod
    def _build_profiles(
        records_by_profile: dict[
            str,
            list[EmotionClassificationSample],
        ],
    ) -> list[ProfileLabelDistribution]:
        profiles = []

        for profile_id in sorted(
            records_by_profile
        ):
            records = records_by_profile[
                profile_id
            ]

            label_counts: Counter[str] = (
                Counter()
            )

            coarse_counts: Counter[str] = (
                Counter()
            )

            for record in records:
                label_counts[
                    record.label
                ] += 1

                coarse_counts[
                    record.coarse_label
                ] += 1

            profiles.append(
                ProfileLabelDistribution(
                    profile_id=profile_id,
                    total_records=len(records),
                    unique_labels=len(
                        label_counts
                    ),
                    label_counts=dict(
                        sorted(
                            label_counts.items()
                        )
                    ),
                    coarse_label_counts=dict(
                        sorted(
                            coarse_counts.items()
                        )
                    ),
                )
            )

        return profiles

    @staticmethod
    def _build_label_support(
        profiles: list[
            ProfileLabelDistribution
        ],
    ) -> list[LabelProfileSupport]:
        results = []

        for label in sorted(
            EMOTION_CODEBOOK
        ):
            counts = [
                profile.label_counts[label]
                for profile in profiles
                if label
                in profile.label_counts
            ]

            total_records = sum(counts)

            if counts:
                max_records = max(counts)

                max_profile_share = (
                    max_records
                    / total_records
                    if total_records > 0
                    else 0.0
                )

                min_records = min(counts)
                median_records = float(
                    median(counts)
                )
            else:
                max_records = 0
                max_profile_share = 0.0
                min_records = 0
                median_records = 0.0

            results.append(
                LabelProfileSupport(
                    label=label,
                    label_name=(
                        EMOTION_CODEBOOK[
                            label
                        ]
                    ),
                    total_records=(
                        total_records
                    ),
                    profile_count=len(
                        counts
                    ),
                    min_records_per_profile=(
                        min_records
                    ),
                    median_records_per_profile=(
                        median_records
                    ),
                    max_records_per_profile=(
                        max_records
                    ),
                    max_profile_share=(
                        max_profile_share
                    ),
                )
            )

        return results