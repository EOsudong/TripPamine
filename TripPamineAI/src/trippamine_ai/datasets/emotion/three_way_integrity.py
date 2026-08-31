from collections import Counter

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
)


class SplitAuditSummary(BaseModel):
    split: str

    total_records: int

    unique_record_ids: int
    unique_profile_ids: int
    unique_talk_ids: int

    duplicate_record_ids: int

    source_split_mismatches: int

    missing_labels: list[str]

    label_counts: dict[str, int]
    profile_counts_by_label: dict[str, int]

    profile_target_mismatches: dict[str, int]


class PairwiseOverlap(BaseModel):
    left_split: str
    right_split: str

    record_id_count: int
    profile_id_count: int
    talk_id_count: int

    record_id_samples: list[str]
    profile_id_samples: list[str]
    talk_id_samples: list[str]


class ManifestCheck(BaseModel):
    source_records_match: bool

    training_records_match: bool
    validation_records_match: bool
    test_records_match: bool

    training_profiles_match: bool
    validation_profiles_match: bool
    test_profiles_match: bool

    all_match: bool


class ThreeWayIntegrityReport(BaseModel):
    total_records: int
    total_unique_record_ids: int
    total_unique_profile_ids: int

    training: SplitAuditSummary
    validation: SplitAuditSummary
    test: SplitAuditSummary

    pairwise_overlaps: list[PairwiseOverlap]

    all_records_isolated: bool
    all_profiles_isolated: bool
    all_labels_present: bool
    all_profile_targets_met: bool
    all_source_splits_correct: bool

    manifest: ManifestCheck

    integrity_pass: bool


class ThreeWaySplitIntegrityAnalyzer:

    SAMPLE_LIMIT = 20

    EXPECTED_PROFILE_COUNTS = {
        "training": 26,
        "validation": 3,
        "test": 3,
    }

    def analyze(
        self,
        training: list[
            EmotionClassificationSample
        ],
        validation: list[
            EmotionClassificationSample
        ],
        test: list[
            EmotionClassificationSample
        ],
        manifest: dict,
    ) -> ThreeWayIntegrityReport:
        training_summary = self._build_summary(
            training,
            "training",
        )

        validation_summary = self._build_summary(
            validation,
            "validation",
        )

        test_summary = self._build_summary(
            test,
            "test",
        )

        overlaps = [
            self._build_overlap(
                "training",
                training,
                "validation",
                validation,
            ),
            self._build_overlap(
                "training",
                training,
                "test",
                test,
            ),
            self._build_overlap(
                "validation",
                validation,
                "test",
                test,
            ),
        ]

        all_records = (
            training
            + validation
            + test
        )

        all_record_ids = {
            record.id
            for record in all_records
        }

        all_profile_ids = {
            record.source.profile_id
            for record in all_records
        }

        all_records_isolated = (
            all(
                overlap.record_id_count == 0
                for overlap in overlaps
            )
            and training_summary.duplicate_record_ids == 0
            and validation_summary.duplicate_record_ids == 0
            and test_summary.duplicate_record_ids == 0
            and len(all_record_ids)
            == len(all_records)
        )

        all_profiles_isolated = all(
            overlap.profile_id_count == 0
            for overlap in overlaps
        )

        summaries = [
            training_summary,
            validation_summary,
            test_summary,
        ]

        all_labels_present = all(
            not summary.missing_labels
            for summary in summaries
        )

        all_profile_targets_met = all(
            not summary.profile_target_mismatches
            for summary in summaries
        )

        all_source_splits_correct = all(
            summary.source_split_mismatches == 0
            for summary in summaries
        )

        manifest_check = (
            self._check_manifest(
                manifest=manifest,
                training=training_summary,
                validation=validation_summary,
                test=test_summary,
                total_records=len(all_records),
            )
        )

        integrity_pass = all(
            (
                all_records_isolated,
                all_profiles_isolated,
                all_labels_present,
                all_profile_targets_met,
                all_source_splits_correct,
                manifest_check.all_match,
            )
        )

        return ThreeWayIntegrityReport(
            total_records=len(all_records),
            total_unique_record_ids=len(
                all_record_ids
            ),
            total_unique_profile_ids=len(
                all_profile_ids
            ),
            training=training_summary,
            validation=validation_summary,
            test=test_summary,
            pairwise_overlaps=overlaps,
            all_records_isolated=(
                all_records_isolated
            ),
            all_profiles_isolated=(
                all_profiles_isolated
            ),
            all_labels_present=(
                all_labels_present
            ),
            all_profile_targets_met=(
                all_profile_targets_met
            ),
            all_source_splits_correct=(
                all_source_splits_correct
            ),
            manifest=manifest_check,
            integrity_pass=integrity_pass,
        )

    def _build_summary(
        self,
        records: list[
            EmotionClassificationSample
        ],
        split_name: str,
    ) -> SplitAuditSummary:
        record_ids: Counter[str] = Counter()
        talk_ids: set[str] = set()
        profile_ids: set[str] = set()

        label_counts: Counter[str] = Counter()

        profiles_by_label: dict[
            str,
            set[str],
        ] = {
            label: set()
            for label in EMOTION_CODEBOOK
        }

        source_split_mismatches = 0

        for record in records:
            record_ids[
                record.id
            ] += 1

            talk_ids.add(
                record.source.talk_id
            )

            profile_id = (
                record.source.profile_id
            )

            profile_ids.add(
                profile_id
            )

            label_counts[
                record.label
            ] += 1

            if record.label in profiles_by_label:
                profiles_by_label[
                    record.label
                ].add(profile_id)

            if (
                record.source.split
                != split_name
            ):
                source_split_mismatches += 1

        duplicate_record_ids = sum(
            count - 1
            for count in record_ids.values()
            if count > 1
        )

        missing_labels = [
            label
            for label in EMOTION_CODEBOOK
            if label_counts[label] == 0
        ]

        profile_counts_by_label = {
            label: len(
                profiles_by_label[label]
            )
            for label in sorted(
                EMOTION_CODEBOOK
            )
        }

        expected_profile_count = (
            self.EXPECTED_PROFILE_COUNTS[
                split_name
            ]
        )

        profile_target_mismatches = {
            label: count
            for label, count
            in profile_counts_by_label.items()
            if count != expected_profile_count
        }

        return SplitAuditSummary(
            split=split_name,
            total_records=len(records),
            unique_record_ids=len(
                record_ids
            ),
            unique_profile_ids=len(
                profile_ids
            ),
            unique_talk_ids=len(
                talk_ids
            ),
            duplicate_record_ids=(
                duplicate_record_ids
            ),
            source_split_mismatches=(
                source_split_mismatches
            ),
            missing_labels=missing_labels,
            label_counts=dict(
                sorted(
                    label_counts.items()
                )
            ),
            profile_counts_by_label=(
                profile_counts_by_label
            ),
            profile_target_mismatches=(
                profile_target_mismatches
            ),
        )

    def _build_overlap(
        self,
        left_name: str,
        left: list[
            EmotionClassificationSample
        ],
        right_name: str,
        right: list[
            EmotionClassificationSample
        ],
    ) -> PairwiseOverlap:
        left_record_ids = {
            record.id
            for record in left
        }

        right_record_ids = {
            record.id
            for record in right
        }

        left_profile_ids = {
            record.source.profile_id
            for record in left
        }

        right_profile_ids = {
            record.source.profile_id
            for record in right
        }

        left_talk_ids = {
            record.source.talk_id
            for record in left
        }

        right_talk_ids = {
            record.source.talk_id
            for record in right
        }

        record_overlap = (
            left_record_ids
            & right_record_ids
        )

        profile_overlap = (
            left_profile_ids
            & right_profile_ids
        )

        talk_overlap = (
            left_talk_ids
            & right_talk_ids
        )

        return PairwiseOverlap(
            left_split=left_name,
            right_split=right_name,
            record_id_count=len(
                record_overlap
            ),
            profile_id_count=len(
                profile_overlap
            ),
            talk_id_count=len(
                talk_overlap
            ),
            record_id_samples=sorted(
                record_overlap
            )[:self.SAMPLE_LIMIT],
            profile_id_samples=sorted(
                profile_overlap
            )[:self.SAMPLE_LIMIT],
            talk_id_samples=sorted(
                talk_overlap
            )[:self.SAMPLE_LIMIT],
        )

    @staticmethod
    def _check_manifest(
        manifest: dict,
        training: SplitAuditSummary,
        validation: SplitAuditSummary,
        test: SplitAuditSummary,
        total_records: int,
    ) -> ManifestCheck:
        checks = {
            "source_records_match": (
                manifest.get(
                    "source_records"
                )
                == total_records
            ),
            "training_records_match": (
                manifest.get(
                    "training_records"
                )
                == training.total_records
            ),
            "validation_records_match": (
                manifest.get(
                    "validation_records"
                )
                == validation.total_records
            ),
            "test_records_match": (
                manifest.get(
                    "test_records"
                )
                == test.total_records
            ),
            "training_profiles_match": (
                manifest.get(
                    "training_profiles"
                )
                == training.unique_profile_ids
            ),
            "validation_profiles_match": (
                manifest.get(
                    "validation_profiles"
                )
                == validation.unique_profile_ids
            ),
            "test_profiles_match": (
                manifest.get(
                    "test_profiles"
                )
                == test.unique_profile_ids
            ),
        }

        return ManifestCheck(
            **checks,
            all_match=all(
                checks.values()
            ),
        )