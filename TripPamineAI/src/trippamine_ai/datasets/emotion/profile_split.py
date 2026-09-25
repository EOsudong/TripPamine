from collections import defaultdict
from itertools import combinations

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
)


class ProfileGroup(BaseModel):
    profile_id: str
    label: str
    record_count: int


class LabelSplitStatistics(BaseModel):
    label: str

    total_records: int

    training_profiles: int
    validation_profiles: int
    test_profiles: int

    training_records: int
    validation_records: int
    test_records: int

    training_share: float
    validation_share: float
    test_share: float


class ProfileSplitResult(BaseModel):
    training_profile_ids: list[str]
    validation_profile_ids: list[str]
    test_profile_ids: list[str]

    label_statistics: list[
        LabelSplitStatistics
    ]


class ProfileStratifiedSplitter:
    ALGORITHM_VERSION = "1"

    VALIDATION_PROFILES_PER_LABEL = 3
    TEST_PROFILES_PER_LABEL = 3

    TARGET_VALIDATION_SHARE = 0.10
    TARGET_TEST_SHARE = 0.10

    CANDIDATE_LIMIT = 100

    def split(
            self,
            records: list[
                EmotionClassificationSample
            ],
    ) -> ProfileSplitResult:
        profiles_by_label = (
            self._build_profile_groups(records)
        )

        training_ids: set[str] = set()
        validation_ids: set[str] = set()
        test_ids: set[str] = set()

        statistics = []

        for label in sorted(
                EMOTION_CODEBOOK
        ):
            profiles = profiles_by_label[
                label
            ]

            self._validate_label_profiles(
                label,
                profiles,
            )

            (
                label_training,
                label_validation,
                label_test,
            ) = self._split_label(
                profiles
            )

            training_ids.update(
                profile.profile_id
                for profile in label_training
            )

            validation_ids.update(
                profile.profile_id
                for profile in label_validation
            )

            test_ids.update(
                profile.profile_id
                for profile in label_test
            )

            statistics.append(
                self._build_statistics(
                    label=label,
                    training=label_training,
                    validation=label_validation,
                    test=label_test,
                )
            )

        self._validate_global_isolation(
            training_ids,
            validation_ids,
            test_ids,
        )

        return ProfileSplitResult(
            training_profile_ids=sorted(
                training_ids
            ),
            validation_profile_ids=sorted(
                validation_ids
            ),
            test_profile_ids=sorted(
                test_ids
            ),
            label_statistics=statistics,
        )

    @staticmethod
    def _build_profile_groups(
            records: list[
                EmotionClassificationSample
            ],
    ) -> dict[str, list[ProfileGroup]]:
        records_by_profile: dict[
            str,
            list[EmotionClassificationSample],
        ] = defaultdict(list)

        for record in records:
            records_by_profile[
                record.source.profile_id
            ].append(record)

        profiles_by_label: dict[
            str,
            list[ProfileGroup],
        ] = defaultdict(list)

        for profile_id, profile_records in (
                records_by_profile.items()
        ):
            labels = {
                record.label
                for record in profile_records
            }

            if len(labels) != 1:
                raise ValueError(
                    "Profile contains multiple labels: "
                    f"{profile_id} -> "
                    f"{sorted(labels)}"
                )

            label = next(iter(labels))

            profiles_by_label[
                label
            ].append(
                ProfileGroup(
                    profile_id=profile_id,
                    label=label,
                    record_count=len(
                        profile_records
                    ),
                )
            )

        for profiles in (
                profiles_by_label.values()
        ):
            profiles.sort(
                key=lambda item: (
                    item.profile_id
                )
            )

        return profiles_by_label

    def _split_label(
            self,
            profiles: list[ProfileGroup],
    ) -> tuple[
        list[ProfileGroup],
        list[ProfileGroup],
        list[ProfileGroup],
    ]:
        total_records = sum(
            profile.record_count
            for profile in profiles
        )

        target_validation = (
                total_records
                * self.TARGET_VALIDATION_SHARE
        )

        target_test = (
                total_records
                * self.TARGET_TEST_SHARE
        )

        validation_candidates = (
            self._rank_combinations(
                profiles=profiles,
                select_count=(
                    self
                    .VALIDATION_PROFILES_PER_LABEL
                ),
                target=target_validation,
            )
        )

        test_candidates = (
            self._rank_combinations(
                profiles=profiles,
                select_count=(
                    self
                    .TEST_PROFILES_PER_LABEL
                ),
                target=target_test,
            )
        )

        best_result = None
        best_score = None

        for validation in (
                validation_candidates[
                    :self.CANDIDATE_LIMIT
                ]
        ):
            validation_ids = {
                profile.profile_id
                for profile in validation
            }

            test = next(
                (
                    candidate
                    for candidate
                    in test_candidates
                    if validation_ids.isdisjoint(
                    profile.profile_id
                    for profile
                    in candidate
                )
                ),
                None,
            )

            if test is None:
                continue

            test_ids = {
                profile.profile_id
                for profile in test
            }

            training = [
                profile
                for profile in profiles
                if (
                        profile.profile_id
                        not in validation_ids
                        and profile.profile_id
                        not in test_ids
                )
            ]

            validation_records = sum(
                profile.record_count
                for profile in validation
            )

            test_records = sum(
                profile.record_count
                for profile in test
            )

            score = (
                    abs(
                        validation_records
                        - target_validation
                    )
                    +
                    abs(
                        test_records
                        - target_test
                    )
            )

            if (
                    best_score is None
                    or score < best_score
            ):
                best_score = score

                best_result = (
                    training,
                    list(validation),
                    list(test),
                )

        if best_result is None:
            raise RuntimeError(
                "Unable to create profile split."
            )

        return best_result

    @staticmethod
    def _rank_combinations(
            profiles: list[ProfileGroup],
            select_count: int,
            target: float,
    ) -> list[tuple[ProfileGroup, ...]]:
        candidates = []

        for combo in combinations(
                profiles,
                select_count,
        ):
            record_count = sum(
                profile.record_count
                for profile in combo
            )

            difference = abs(
                record_count - target
            )

            profile_ids = tuple(
                profile.profile_id
                for profile in combo
            )

            candidates.append(
                (
                    difference,
                    profile_ids,
                    combo,
                )
            )

        candidates.sort(
            key=lambda item: (
                item[0],
                item[1],
            )
        )

        return [
            item[2]
            for item in candidates
        ]

    @staticmethod
    def _validate_label_profiles(
            label: str,
            profiles: list[ProfileGroup],
    ) -> None:
        required = (
                ProfileStratifiedSplitter
                .VALIDATION_PROFILES_PER_LABEL
                +
                ProfileStratifiedSplitter
                .TEST_PROFILES_PER_LABEL
                + 1
        )

        if len(profiles) < required:
            raise ValueError(
                f"Not enough profiles for "
                f"{label}: "
                f"{len(profiles)}"
            )

    @staticmethod
    def _validate_global_isolation(
            training_ids: set[str],
            validation_ids: set[str],
            test_ids: set[str],
    ) -> None:
        if training_ids & validation_ids:
            raise ValueError(
                "Training/validation "
                "profile overlap detected."
            )

        if training_ids & test_ids:
            raise ValueError(
                "Training/test profile "
                "overlap detected."
            )

        if validation_ids & test_ids:
            raise ValueError(
                "Validation/test profile "
                "overlap detected."
            )

    @staticmethod
    def _build_statistics(
            label: str,
            training: list[ProfileGroup],
            validation: list[ProfileGroup],
            test: list[ProfileGroup],
    ) -> LabelSplitStatistics:
        training_records = sum(
            profile.record_count
            for profile in training
        )

        validation_records = sum(
            profile.record_count
            for profile in validation
        )

        test_records = sum(
            profile.record_count
            for profile in test
        )

        total_records = (
                training_records
                + validation_records
                + test_records
        )

        return LabelSplitStatistics(
            label=label,
            total_records=total_records,
            training_profiles=len(
                training
            ),
            validation_profiles=len(
                validation
            ),
            test_profiles=len(
                test
            ),
            training_records=(
                training_records
            ),
            validation_records=(
                validation_records
            ),
            test_records=test_records,
            training_share=(
                    training_records
                    / total_records
            ),
            validation_share=(
                    validation_records
                    / total_records
            ),
            test_share=(
                    test_records
                    / total_records
            ),
        )
