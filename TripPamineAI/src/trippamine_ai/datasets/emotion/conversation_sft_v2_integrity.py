from collections import Counter, defaultdict
from typing import Any

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTSample,
)


class SFTSplitIntegritySummary(BaseModel):
    split: str

    total_samples: int

    unique_sample_ids: int
    unique_record_ids: int
    unique_profile_ids: int
    unique_talk_ids: int
    unique_content_hashes: int

    duplicate_sample_ids: int
    duplicate_content_hashes: int

    source_split_mismatches: int

    empty_prompt_messages: int
    empty_completion_messages: int

    invalid_sample_ids: int
    invalid_target_sequences: int

    target_turns: dict[str, int]


class SFTPairwiseOverlap(BaseModel):
    left_split: str
    right_split: str

    sample_id_count: int
    record_id_count: int
    profile_id_count: int
    talk_id_count: int
    content_hash_count: int

    sample_id_samples: list[str]
    record_id_samples: list[str]
    profile_id_samples: list[str]
    talk_id_samples: list[str]
    content_hash_samples: list[str]


class SFTManifestCheck(BaseModel):
    source_records_match: bool

    training_records_match: bool
    validation_records_match: bool
    test_records_match: bool

    training_profiles_match: bool
    validation_profiles_match: bool
    test_profiles_match: bool

    all_match: bool


class SFTBuildReportCheck(BaseModel):
    source_dialogues_match: bool
    generated_samples_match: bool
    duplicate_content_hashes_match: bool

    training_dialogues_match: bool
    training_samples_match: bool
    training_profiles_match: bool

    validation_dialogues_match: bool
    validation_samples_match: bool
    validation_profiles_match: bool

    test_dialogues_match: bool
    test_samples_match: bool
    test_profiles_match: bool

    all_match: bool


class ConversationSFTV2IntegrityReport(BaseModel):
    total_samples: int
    total_unique_record_ids: int
    total_unique_profile_ids: int
    total_unique_content_hashes: int

    training: SFTSplitIntegritySummary
    validation: SFTSplitIntegritySummary
    test: SFTSplitIntegritySummary

    pairwise_overlaps: list[
        SFTPairwiseOverlap
    ]

    all_sample_ids_isolated: bool
    all_record_ids_isolated: bool
    all_profiles_isolated: bool
    all_content_hashes_unique: bool

    all_source_splits_correct: bool
    all_target_sequences_valid: bool
    all_messages_non_empty: bool

    manifest: SFTManifestCheck
    build_report: SFTBuildReportCheck

    integrity_pass: bool


class ConversationSFTV2IntegrityAnalyzer:

    SAMPLE_LIMIT = 20

    SPLIT_NAMES = (
        "training",
        "validation",
        "test",
    )

    def analyze(
        self,
        training: list[
            ConversationSFTSample
        ],
        validation: list[
            ConversationSFTSample
        ],
        test: list[
            ConversationSFTSample
        ],
        manifest: dict[str, Any],
        build_report: dict[str, Any],
    ) -> ConversationSFTV2IntegrityReport:
        training_summary = (
            self._build_summary(
                training,
                "training",
            )
        )

        validation_summary = (
            self._build_summary(
                validation,
                "validation",
            )
        )

        test_summary = (
            self._build_summary(
                test,
                "test",
            )
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

        all_samples = (
            training
            + validation
            + test
        )

        all_sample_ids = [
            sample.id
            for sample in all_samples
        ]

        all_record_ids = {
            sample.source.record_id
            for sample in all_samples
        }

        all_profile_ids = {
            sample.source.profile_id
            for sample in all_samples
        }

        all_content_hashes = [
            sample.content_hash
            for sample in all_samples
        ]

        summaries = [
            training_summary,
            validation_summary,
            test_summary,
        ]

        all_sample_ids_isolated = (
            len(all_sample_ids)
            == len(set(all_sample_ids))
            and all(
                overlap.sample_id_count == 0
                for overlap in overlaps
            )
        )

        all_record_ids_isolated = all(
            overlap.record_id_count == 0
            for overlap in overlaps
        )

        all_profiles_isolated = all(
            overlap.profile_id_count == 0
            for overlap in overlaps
        )

        all_content_hashes_unique = (
            len(all_content_hashes)
            == len(
                set(all_content_hashes)
            )
            and all(
                overlap.content_hash_count == 0
                for overlap in overlaps
            )
        )

        all_source_splits_correct = all(
            summary.source_split_mismatches
            == 0
            for summary in summaries
        )

        all_target_sequences_valid = all(
            summary.invalid_target_sequences
            == 0
            and summary.invalid_sample_ids
            == 0
            for summary in summaries
        )

        all_messages_non_empty = all(
            summary.empty_prompt_messages
            == 0
            and summary.empty_completion_messages
            == 0
            for summary in summaries
        )

        manifest_check = (
            self._check_manifest(
                manifest=manifest,
                training_samples=training,
                validation_samples=validation,
                test_samples=test,
                training_summary=(
                    training_summary
                ),
                validation_summary=(
                    validation_summary
                ),
                test_summary=test_summary,
                total_records=len(
                    all_record_ids
                ),
            )
        )

        build_report_check = (
            self._check_build_report(
                build_report=build_report,
                training=training_summary,
                validation=(
                    validation_summary
                ),
                test=test_summary,
                total_samples=len(
                    all_samples
                ),
                total_records=len(
                    all_record_ids
                ),
                duplicate_content_hashes=(
                    len(all_content_hashes)
                    - len(
                        set(
                            all_content_hashes
                        )
                    )
                ),
            )
        )

        integrity_pass = all(
            (
                all_sample_ids_isolated,
                all_record_ids_isolated,
                all_profiles_isolated,
                all_content_hashes_unique,
                all_source_splits_correct,
                all_target_sequences_valid,
                all_messages_non_empty,
                manifest_check.all_match,
                build_report_check.all_match,
            )
        )

        return (
            ConversationSFTV2IntegrityReport(
                total_samples=len(
                    all_samples
                ),
                total_unique_record_ids=len(
                    all_record_ids
                ),
                total_unique_profile_ids=len(
                    all_profile_ids
                ),
                total_unique_content_hashes=len(
                    set(
                        all_content_hashes
                    )
                ),
                training=training_summary,
                validation=(
                    validation_summary
                ),
                test=test_summary,
                pairwise_overlaps=overlaps,
                all_sample_ids_isolated=(
                    all_sample_ids_isolated
                ),
                all_record_ids_isolated=(
                    all_record_ids_isolated
                ),
                all_profiles_isolated=(
                    all_profiles_isolated
                ),
                all_content_hashes_unique=(
                    all_content_hashes_unique
                ),
                all_source_splits_correct=(
                    all_source_splits_correct
                ),
                all_target_sequences_valid=(
                    all_target_sequences_valid
                ),
                all_messages_non_empty=(
                    all_messages_non_empty
                ),
                manifest=manifest_check,
                build_report=(
                    build_report_check
                ),
                integrity_pass=(
                    integrity_pass
                ),
            )
        )

    def _build_summary(
        self,
        samples: list[
            ConversationSFTSample
        ],
        split_name: str,
    ) -> SFTSplitIntegritySummary:
        sample_ids: Counter[str] = Counter()
        content_hashes: Counter[str] = (
            Counter()
        )

        record_ids: set[str] = set()
        profile_ids: set[str] = set()
        talk_ids: set[str] = set()

        target_turns: Counter[int] = (
            Counter()
        )

        turns_by_record: dict[
            str,
            list[int],
        ] = defaultdict(list)

        source_split_mismatches = 0

        empty_prompt_messages = 0
        empty_completion_messages = 0

        invalid_sample_ids = 0

        for sample in samples:
            sample_ids[
                sample.id
            ] += 1

            content_hashes[
                sample.content_hash
            ] += 1

            record_id = (
                sample.source.record_id
            )

            record_ids.add(record_id)

            profile_ids.add(
                sample.source.profile_id
            )

            talk_ids.add(
                sample.source.talk_id
            )

            target_turns[
                sample.target_turn
            ] += 1

            turns_by_record[
                record_id
            ].append(
                sample.target_turn
            )

            if (
                sample.source.split
                != split_name
            ):
                source_split_mismatches += 1

            expected_sample_id = (
                f"{record_id}:"
                f"turn-{sample.target_turn}"
            )

            if (
                sample.id
                != expected_sample_id
            ):
                invalid_sample_ids += 1

            for message in sample.prompt:
                if not (
                    message.content.strip()
                ):
                    empty_prompt_messages += 1

            for message in (
                sample.completion
            ):
                if not (
                    message.content.strip()
                ):
                    empty_completion_messages += 1

        invalid_target_sequences = 0

        for turns in (
            turns_by_record.values()
        ):
            ordered = sorted(turns)

            if ordered not in (
                [1, 2],
                [1, 2, 3],
            ):
                invalid_target_sequences += 1

        duplicate_sample_ids = sum(
            count - 1
            for count
            in sample_ids.values()
            if count > 1
        )

        duplicate_content_hashes = sum(
            count - 1
            for count
            in content_hashes.values()
            if count > 1
        )

        return SFTSplitIntegritySummary(
            split=split_name,
            total_samples=len(samples),
            unique_sample_ids=len(
                sample_ids
            ),
            unique_record_ids=len(
                record_ids
            ),
            unique_profile_ids=len(
                profile_ids
            ),
            unique_talk_ids=len(
                talk_ids
            ),
            unique_content_hashes=len(
                content_hashes
            ),
            duplicate_sample_ids=(
                duplicate_sample_ids
            ),
            duplicate_content_hashes=(
                duplicate_content_hashes
            ),
            source_split_mismatches=(
                source_split_mismatches
            ),
            empty_prompt_messages=(
                empty_prompt_messages
            ),
            empty_completion_messages=(
                empty_completion_messages
            ),
            invalid_sample_ids=(
                invalid_sample_ids
            ),
            invalid_target_sequences=(
                invalid_target_sequences
            ),
            target_turns={
                str(turn): count
                for turn, count
                in sorted(
                    target_turns.items()
                )
            },
        )

    def _build_overlap(
        self,
        left_name: str,
        left: list[
            ConversationSFTSample
        ],
        right_name: str,
        right: list[
            ConversationSFTSample
        ],
    ) -> SFTPairwiseOverlap:
        def values(
            samples: list[
                ConversationSFTSample
            ],
        ) -> tuple[
            set[str],
            set[str],
            set[str],
            set[str],
            set[str],
        ]:
            return (
                {
                    sample.id
                    for sample in samples
                },
                {
                    sample.source.record_id
                    for sample in samples
                },
                {
                    sample.source.profile_id
                    for sample in samples
                },
                {
                    sample.source.talk_id
                    for sample in samples
                },
                {
                    sample.content_hash
                    for sample in samples
                },
            )

        (
            left_sample_ids,
            left_record_ids,
            left_profile_ids,
            left_talk_ids,
            left_content_hashes,
        ) = values(left)

        (
            right_sample_ids,
            right_record_ids,
            right_profile_ids,
            right_talk_ids,
            right_content_hashes,
        ) = values(right)

        sample_overlap = (
            left_sample_ids
            & right_sample_ids
        )

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

        content_overlap = (
            left_content_hashes
            & right_content_hashes
        )

        return SFTPairwiseOverlap(
            left_split=left_name,
            right_split=right_name,
            sample_id_count=len(
                sample_overlap
            ),
            record_id_count=len(
                record_overlap
            ),
            profile_id_count=len(
                profile_overlap
            ),
            talk_id_count=len(
                talk_overlap
            ),
            content_hash_count=len(
                content_overlap
            ),
            sample_id_samples=sorted(
                sample_overlap
            )[:self.SAMPLE_LIMIT],
            record_id_samples=sorted(
                record_overlap
            )[:self.SAMPLE_LIMIT],
            profile_id_samples=sorted(
                profile_overlap
            )[:self.SAMPLE_LIMIT],
            talk_id_samples=sorted(
                talk_overlap
            )[:self.SAMPLE_LIMIT],
            content_hash_samples=sorted(
                content_overlap
            )[:self.SAMPLE_LIMIT],
        )

    @staticmethod
    def _profile_ids(
        samples: list[
            ConversationSFTSample
        ],
    ) -> set[str]:
        return {
            sample.source.profile_id
            for sample in samples
        }

    def _check_manifest(
        self,
        manifest: dict[str, Any],
        training_samples: list[
            ConversationSFTSample
        ],
        validation_samples: list[
            ConversationSFTSample
        ],
        test_samples: list[
            ConversationSFTSample
        ],
        training_summary: (
            SFTSplitIntegritySummary
        ),
        validation_summary: (
            SFTSplitIntegritySummary
        ),
        test_summary: (
            SFTSplitIntegritySummary
        ),
        total_records: int,
    ) -> SFTManifestCheck:
        split = manifest.get(
            "split",
            {},
        )

        expected_training_profiles = set(
            split.get(
                "training_profile_ids",
                [],
            )
        )

        expected_validation_profiles = set(
            split.get(
                "validation_profile_ids",
                [],
            )
        )

        expected_test_profiles = set(
            split.get(
                "test_profile_ids",
                [],
            )
        )

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
                == training_summary
                .unique_record_ids
            ),
            "validation_records_match": (
                manifest.get(
                    "validation_records"
                )
                == validation_summary
                .unique_record_ids
            ),
            "test_records_match": (
                manifest.get(
                    "test_records"
                )
                == test_summary
                .unique_record_ids
            ),
            "training_profiles_match": (
                self._profile_ids(
                    training_samples
                )
                == expected_training_profiles
            ),
            "validation_profiles_match": (
                self._profile_ids(
                    validation_samples
                )
                == expected_validation_profiles
            ),
            "test_profiles_match": (
                self._profile_ids(
                    test_samples
                )
                == expected_test_profiles
            ),
        }

        return SFTManifestCheck(
            **checks,
            all_match=all(
                checks.values()
            ),
        )

    @staticmethod
    def _check_build_report(
        build_report: dict[str, Any],
        training: SFTSplitIntegritySummary,
        validation: SFTSplitIntegritySummary,
        test: SFTSplitIntegritySummary,
        total_samples: int,
        total_records: int,
        duplicate_content_hashes: int,
    ) -> SFTBuildReportCheck:
        summary = build_report.get(
            "summary",
            {},
        )

        splits = build_report.get(
            "splits",
            {},
        )

        checks = {
            "source_dialogues_match": (
                summary.get(
                    "source_dialogues"
                )
                == total_records
            ),
            "generated_samples_match": (
                summary.get(
                    "generated_samples"
                )
                == total_samples
            ),
            "duplicate_content_hashes_match": (
                summary.get(
                    "duplicate_content_hashes"
                )
                == duplicate_content_hashes
            ),
        }

        for (
            split_name,
            actual,
        ) in (
            (
                "training",
                training,
            ),
            (
                "validation",
                validation,
            ),
            (
                "test",
                test,
            ),
        ):
            split_report = splits.get(
                split_name,
                {},
            )

            checks[
                f"{split_name}_dialogues_match"
            ] = (
                split_report.get(
                    "source_dialogues"
                )
                == actual.unique_record_ids
            )

            checks[
                f"{split_name}_samples_match"
            ] = (
                split_report.get(
                    "generated_samples"
                )
                == actual.total_samples
            )

            checks[
                f"{split_name}_profiles_match"
            ] = (
                split_report.get(
                    "profiles"
                )
                == actual.unique_profile_ids
            )

        return SFTBuildReportCheck(
            **checks,
            all_match=all(
                checks.values()
            ),
        )