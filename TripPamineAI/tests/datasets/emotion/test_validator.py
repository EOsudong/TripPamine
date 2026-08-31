from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.validator import (
    EmotionDatasetValidator,
    ValidationStatus,
)


def create_valid_record() -> EmotionDialogueRecord:
    raw = {
        "profile": {
            "persona-id": "Pro_03802",
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
                "profile-id": "Pro_03802",
                "talk-id": "Pro_03802_00028",
            },
            "content": {
                "HS01": "발표 실수를 해서 정말 미안해.",
                "SS01": "많이 미안한 마음이 드시겠어요.",
                "HS02": "내가 능력이 부족한 것 같아.",
                "SS02": "그렇게 느끼고 계시는군요.",
                "HS03": "앞으로는 더 준비해야겠어.",
                "SS03": "좋은 결과가 있기를 바라요.",
            },
        },
    }

    return EmotionDialogueRecord.model_validate(raw)


def test_valid_record():
    validator = EmotionDatasetValidator()

    result = validator.validate(create_valid_record())

    assert result.status == ValidationStatus.VALID
    assert result.issues == []


def test_empty_complete_third_turn_is_valid():
    record = create_valid_record()

    record.talk.content.HS03 = ""
    record.talk.content.SS03 = ""

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.VALID


def test_incomplete_third_turn_returns_warning():
    record = create_valid_record()

    record.talk.content.SS03 = ""

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.WARNING

    assert any(
        issue.code == "INCOMPLETE_THIRD_TURN"
        for issue in result.issues
    )


def test_unknown_emotion_code_is_rejected():
    record = create_valid_record()

    record.profile.emotion.type = "E70"
    record.profile.emotion.emotion_id = "S06_D02_E70"

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.REJECTED

    assert any(
        issue.code == "UNKNOWN_EMOTION_CODE"
        for issue in result.issues
    )


def test_emotion_id_mismatch_is_rejected():
    record = create_valid_record()

    record.profile.emotion.emotion_id = "S06_D02_E32"

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.REJECTED

    assert any(
        issue.code == "EMOTION_ID_MISMATCH"
        for issue in result.issues
    )


def test_persona_id_mismatch_is_rejected():
    record = create_valid_record()

    record.profile.persona.persona_id = "A01_G01_C01"

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.REJECTED

    assert any(
        issue.code == "PERSONA_ID_MISMATCH"
        for issue in result.issues
    )


def test_profile_id_mismatch_is_rejected():
    record = create_valid_record()

    record.talk.id.profile_id = "Pro_99999"

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.REJECTED

    assert any(
        issue.code == "PROFILE_ID_MISMATCH"
        for issue in result.issues
    )


def test_empty_required_turn_is_rejected():
    record = create_valid_record()

    record.talk.content.HS01 = ""

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.REJECTED

    assert any(
        issue.code == "EMPTY_REQUIRED_TURN"
        for issue in result.issues
    )


def test_short_utterance_returns_warning():
    record = create_valid_record()

    record.talk.content.HS01 = "미안해."

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.WARNING

    assert any(
        issue.code == "WORD_COUNT_OUT_OF_RANGE"
        for issue in result.issues
    )


def test_english_and_digit_return_warnings():
    record = create_valid_record()

    record.talk.content.HS01 = "오늘 project 3개 때문에 힘들어."

    result = EmotionDatasetValidator().validate(record)

    assert result.status == ValidationStatus.WARNING

    issue_codes = {
        issue.code
        for issue in result.issues
    }

    assert "CONTAINS_ENGLISH" in issue_codes
    assert "CONTAINS_DIGIT" in issue_codes