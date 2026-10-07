from collections import Counter
from collections.abc import Iterable
from statistics import mean

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTSample,
)


class LengthStatistics(BaseModel):
    minimum: int
    maximum: int
    mean: float

    p50: int
    p95: int
    p99: int


class RepeatedCompletion(BaseModel):
    text: str
    count: int


class ConversationSFTStatistics(BaseModel):
    total_samples: int

    unique_record_ids: int
    unique_profile_ids: int
    unique_talk_ids: int
    unique_content_hashes: int

    duplicate_content_hashes: int

    target_turns: dict[str, int]

    quality_status: dict[str, int]
    quality_issues: dict[str, int]

    prompt_message_counts: dict[str, int]

    prompt_characters: LengthStatistics
    completion_characters: LengthStatistics

    empty_prompt_messages: int
    empty_completion_messages: int

    repeated_completion_texts: int

    top_repeated_completions: list[
        RepeatedCompletion
    ]


class ConversationSFTStatisticsAnalyzer:

    def analyze(
        self,
        samples: Iterable[
            ConversationSFTSample
        ],
    ) -> ConversationSFTStatistics:
        total_samples = 0

        record_ids: set[str] = set()
        profile_ids: set[str] = set()
        talk_ids: set[str] = set()
        content_hashes: set[str] = set()

        target_turn_counts: Counter[int] = Counter()
        quality_counts: Counter[str] = Counter()
        quality_issue_counts: Counter[str] = Counter()
        prompt_message_counts: Counter[int] = Counter()

        completion_counts: Counter[str] = Counter()

        prompt_lengths: list[int] = []
        completion_lengths: list[int] = []

        empty_prompt_messages = 0
        empty_completion_messages = 0

        for sample in samples:
            total_samples += 1

            record_ids.add(
                sample.source.record_id
            )

            profile_ids.add(
                sample.source.profile_id
            )

            talk_ids.add(
                sample.source.talk_id
            )

            content_hashes.add(
                sample.content_hash
            )

            target_turn_counts[
                sample.target_turn
            ] += 1

            quality_counts[
                sample.source_quality_status
            ] += 1

            for issue_code in (
                sample.source_quality_issue_codes
            ):
                quality_issue_counts[
                    issue_code
                ] += 1

            prompt_message_counts[
                len(sample.prompt)
            ] += 1

            prompt_char_count = 0

            for message in sample.prompt:
                stripped = message.content.strip()

                if not stripped:
                    empty_prompt_messages += 1

                prompt_char_count += len(
                    stripped
                )

            completion_char_count = 0

            for message in sample.completion:
                stripped = message.content.strip()

                if not stripped:
                    empty_completion_messages += 1

                completion_char_count += len(
                    stripped
                )

            prompt_lengths.append(
                prompt_char_count
            )

            completion_lengths.append(
                completion_char_count
            )

            completion_text = "\n".join(
                message.content.strip()
                for message
                in sample.completion
            )

            completion_counts[
                completion_text
            ] += 1

        duplicate_content_hashes = (
            total_samples
            - len(content_hashes)
        )

        repeated = [
            RepeatedCompletion(
                text=text,
                count=count,
            )
            for text, count
            in completion_counts.most_common()
            if count > 1
        ]

        return ConversationSFTStatistics(
            total_samples=total_samples,
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
            duplicate_content_hashes=(
                duplicate_content_hashes
            ),
            target_turns={
                str(key): value
                for key, value
                in sorted(
                    target_turn_counts.items()
                )
            },
            quality_status=dict(
                sorted(
                    quality_counts.items()
                )
            ),
            quality_issues=dict(
                sorted(
                    quality_issue_counts.items()
                )
            ),
            prompt_message_counts={
                str(key): value
                for key, value
                in sorted(
                    prompt_message_counts.items()
                )
            },
            prompt_characters=(
                self._length_statistics(
                    prompt_lengths
                )
            ),
            completion_characters=(
                self._length_statistics(
                    completion_lengths
                )
            ),
            empty_prompt_messages=(
                empty_prompt_messages
            ),
            empty_completion_messages=(
                empty_completion_messages
            ),
            repeated_completion_texts=len(
                repeated
            ),
            top_repeated_completions=(
                repeated[:20]
            ),
        )

    @staticmethod
    def _length_statistics(
        values: list[int],
    ) -> LengthStatistics:
        if not values:
            return LengthStatistics(
                minimum=0,
                maximum=0,
                mean=0.0,
                p50=0,
                p95=0,
                p99=0,
            )

        ordered = sorted(values)

        return LengthStatistics(
            minimum=ordered[0],
            maximum=ordered[-1],
            mean=mean(ordered),
            p50=ConversationSFTStatisticsAnalyzer
            ._percentile(
                ordered,
                0.50,
            ),
            p95=ConversationSFTStatisticsAnalyzer
            ._percentile(
                ordered,
                0.95,
            ),
            p99=ConversationSFTStatisticsAnalyzer
            ._percentile(
                ordered,
                0.99,
            ),
        )

    @staticmethod
    def _percentile(
        ordered: list[int],
        percentile: float,
    ) -> int:
        if not ordered:
            return 0

        index = round(
            (len(ordered) - 1)
            * percentile
        )

        return ordered[index]