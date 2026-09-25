from collections import Counter, defaultdict
from collections.abc import Iterable
from statistics import median

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


class ProfileLabelCount(BaseModel):
    profile_id: str
    count: int


class LabelProfileSupport(BaseModel):
    label: str
    label_name: str

    total_records: int
    unique_profiles: int

    max_profile_id: str | None
    max_records_in_profile: int
    max_profile_share: float

    three_split_coverage_possible: bool

    top_profiles: list[ProfileLabelCount]


class ProfileSupportSummary(BaseModel):
    total_records: int
    unique_profiles: int

    minimum_records_per_profile: int
    median_records_per_profile: float
    maximum_records_per_profile: int

    labels_with_no_records: list[str]
    labels_with_fewer_than_three_profiles: list[str]


class ProfileLabelSupportReport(BaseModel):
    summary: ProfileSupportSummary
    labels: list[LabelProfileSupport]


class ProfileLabelSupportAnalyzer:

    def analyze(
        self,
        records: Iterable[
            NormalizedEmotionDialogue
        ],
        top_profile_limit: int = 10,
    ) -> ProfileLabelSupportReport:
        if top_profile_limit < 1:
            raise ValueError(
                "top_profile_limit must be at least 1."
            )

        total_records = 0

        profile_record_counts: Counter[str] = Counter()

        profile_label_counts: dict[
            str,
            Counter[str],
        ] = defaultdict(Counter)

        label_totals: Counter[str] = Counter()

        for record in records:
            total_records += 1

            profile_id = (
                record.source.profile_id
            )

            label = (
                record.emotion.emotion_code
            )

            profile_record_counts[
                profile_id
            ] += 1

            profile_label_counts[
                profile_id
            ][label] += 1

            label_totals[
                label
            ] += 1

        label_reports = []

        for label in sorted(
            EMOTION_CODEBOOK.keys()
        ):
            profile_counts = [
                ProfileLabelCount(
                    profile_id=profile_id,
                    count=counts[label],
                )
                for profile_id, counts
                in profile_label_counts.items()
                if counts[label] > 0
            ]

            profile_counts.sort(
                key=lambda item: (
                    -item.count,
                    item.profile_id,
                )
            )

            total = label_totals[label]

            if profile_counts:
                max_profile = (
                    profile_counts[0]
                )

                max_profile_id = (
                    max_profile.profile_id
                )

                max_records = (
                    max_profile.count
                )
            else:
                max_profile_id = None
                max_records = 0

            max_share = (
                max_records / total
                if total > 0
                else 0.0
            )

            label_reports.append(
                LabelProfileSupport(
                    label=label,
                    label_name=(
                        EMOTION_CODEBOOK[
                            label
                        ]
                    ),
                    total_records=total,
                    unique_profiles=len(
                        profile_counts
                    ),
                    max_profile_id=(
                        max_profile_id
                    ),
                    max_records_in_profile=(
                        max_records
                    ),
                    max_profile_share=(
                        max_share
                    ),
                    three_split_coverage_possible=(
                        len(profile_counts) >= 3
                    ),
                    top_profiles=(
                        profile_counts[
                            :top_profile_limit
                        ]
                    ),
                )
            )

        profile_sizes = sorted(
            profile_record_counts.values()
        )

        if profile_sizes:
            minimum_records = (
                profile_sizes[0]
            )

            median_records = float(
                median(profile_sizes)
            )

            maximum_records = (
                profile_sizes[-1]
            )
        else:
            minimum_records = 0
            median_records = 0.0
            maximum_records = 0

        return ProfileLabelSupportReport(
            summary=ProfileSupportSummary(
                total_records=total_records,
                unique_profiles=len(
                    profile_record_counts
                ),
                minimum_records_per_profile=(
                    minimum_records
                ),
                median_records_per_profile=(
                    median_records
                ),
                maximum_records_per_profile=(
                    maximum_records
                ),
                labels_with_no_records=[
                    item.label
                    for item in label_reports
                    if item.total_records == 0
                ],
                labels_with_fewer_than_three_profiles=[
                    item.label
                    for item in label_reports
                    if item.unique_profiles < 3
                ],
            ),
            labels=label_reports,
        )