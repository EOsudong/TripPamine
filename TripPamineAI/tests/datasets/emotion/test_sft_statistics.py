from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTBuilder,
)
from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)
from trippamine_ai.datasets.emotion.sft_statistics import (
    ConversationSFTStatisticsAnalyzer,
)


def create_samples():
    raw = {
        "profile": {
            "persona-id": "Pro_00001",
            "persona": {
                "persona-id": "A02_G01_C01",
                "human": ["A02", "G01"],
                "computer": ["C01"],
            },
            "emotion": {
                "emotion-id": "S06_D02_E31",
                "type": "E31",
                "situation": ["S06", "D02"],
            },
        },
        "talk": {
            "id": {
                "profile-id": "Pro_00001",
                "talk-id": "Pro_00001_00001",
            },
            "content": {
                "HS01": "요즘 회사 일이 너무 걱정돼.",
                "SS01": "많이 걱정되고 계시는군요.",
                "HS02": "실수할까 봐 계속 불안해.",
                "SS02": "실수에 대한 걱정이 크시군요.",
                "HS03": "그래도 잘하고 싶어.",
                "SS03": "잘하고 싶은 마음이 느껴져요.",
            },
        },
    }

    record = EmotionDialogueRecord.model_validate(
        raw
    )

    normalized = (
        EmotionDialogueNormalizer()
        .normalize(
            record,
            split="training",
        )
    )

    return ConversationSFTBuilder().build(
        normalized
    )


def test_total_samples():
    result = (
        ConversationSFTStatisticsAnalyzer()
        .analyze(
            create_samples()
        )
    )

    assert result.total_samples == 3


def test_target_turn_distribution():
    result = (
        ConversationSFTStatisticsAnalyzer()
        .analyze(
            create_samples()
        )
    )

    assert result.target_turns == {
        "1": 1,
        "2": 1,
        "3": 1,
    }


def test_no_duplicate_content_hash():
    result = (
        ConversationSFTStatisticsAnalyzer()
        .analyze(
            create_samples()
        )
    )

    assert (
        result.duplicate_content_hashes
        == 0
    )


def test_unique_source_record():
    result = (
        ConversationSFTStatisticsAnalyzer()
        .analyze(
            create_samples()
        )
    )

    assert result.unique_record_ids == 1


def test_no_empty_messages():
    result = (
        ConversationSFTStatisticsAnalyzer()
        .analyze(
            create_samples()
        )
    )

    assert result.empty_prompt_messages == 0
    assert (
        result.empty_completion_messages
        == 0
    )


def test_length_statistics():
    result = (
        ConversationSFTStatisticsAnalyzer()
        .analyze(
            create_samples()
        )
    )

    assert (
        result.prompt_characters.minimum
        > 0
    )

    assert (
        result.completion_characters.minimum
        > 0
    )

    assert (
        result.prompt_characters.maximum
        >= result.prompt_characters.minimum
    )