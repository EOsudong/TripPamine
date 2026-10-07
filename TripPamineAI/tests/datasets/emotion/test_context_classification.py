import pytest

from trippamine_ai.datasets.emotion.context_classification import (
    EmotionContextClassificationBuilder,
)
from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)


def create_normalized_record():
    raw = {
        "profile": {
            "persona-id": "Pro_05349",
            "persona": {
                "persona-id": "A02_G02_C01",
                "human": ["A02", "G02"],
                "computer": ["C01"],
            },
            "emotion": {
                "emotion-id": "S06_D02_E18",
                "type": "E18",
                "situation": ["S06", "D02"],
            },
        },
        "talk": {
            "id": {
                "profile-id": "Pro_05349",
                "talk-id": "Pro_05349_00053",
            },
            "content": {
                "HS01": "첫 번째 사용자 발화야.",
                "SS01": "첫 번째 상담자 응답이야.",
                "HS02": "두 번째 사용자 발화야.",
                "SS02": "두 번째 상담자 응답이야.",
                "HS03": "세 번째 사용자 발화야.",
                "SS03": "세 번째 상담자 응답이야.",
            },
        },
    }

    record = EmotionDialogueRecord.model_validate(
        raw
    )

    return EmotionDialogueNormalizer().normalize(
        record,
        split="training",
    )


def test_uses_all_human_turns_in_order():
    normalized = create_normalized_record()

    sample = (
        EmotionContextClassificationBuilder()
        .build(
            normalized,
            split="training",
        )
    )

    assert sample.text == (
        "첫 번째 사용자 발화야.\n"
        "두 번째 사용자 발화야.\n"
        "세 번째 사용자 발화야."
    )


def test_excludes_assistant_turns():
    normalized = create_normalized_record()

    sample = (
        EmotionContextClassificationBuilder()
        .build(
            normalized,
            split="training",
        )
    )

    assert "상담자 응답" not in sample.text


def test_assigns_requested_split_without_mutating_record():
    normalized = create_normalized_record()

    sample = (
        EmotionContextClassificationBuilder()
        .build(
            normalized,
            split="validation",
        )
    )

    assert sample.source.split == "validation"
    assert normalized.source.split == "training"


def test_preserves_classification_metadata():
    normalized = create_normalized_record()

    sample = (
        EmotionContextClassificationBuilder()
        .build(
            normalized,
            split="training",
        )
    )

    assert sample.id == normalized.record_id
    assert sample.label == "E18"
    assert sample.label_name == "노여워하는"
    assert sample.coarse_label == "분노"
    assert sample.situation_code == "S06"
    assert sample.source.profile_id == "Pro_05349"
    assert (
        sample.source.talk_id
        == "Pro_05349_00053"
    )


def test_rejects_empty_turn_separator():
    with pytest.raises(
        ValueError,
        match="turn_separator",
    ):
        EmotionContextClassificationBuilder(
            turn_separator="",
        )


def test_rejects_unknown_split():
    with pytest.raises(
        ValueError,
        match="Unsupported dataset split",
    ):
        (
            EmotionContextClassificationBuilder()
            .build(
                create_normalized_record(),
                split="unknown",
            )
        )
