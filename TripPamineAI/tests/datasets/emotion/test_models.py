import pytest
from pydantic import ValidationError

from trippamine_ai.datasets.emotion.models import EmotionDialogueRecord


def create_valid_record() -> dict:
    return {
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
                "HS01": (
                    "이번 프로젝트에서 내가 발표 실수를 해서 "
                    "우리 팀이 감점을 받아서 너무 미안해."
                ),
                "SS01": "실수하시다니 정말 죄송한 마음이 크겠어요.",
                "HS02": "내 능력이 부족한 거 같은데 그만 다녀야 하려나 봐.",
                "SS02": "능력을 올리려면 어떤 방법이 있을까요?",
                "HS03": (
                    "퇴근 후 여가에 회사 일을 더 열심히 해서 "
                    "피해가 가지 않도록 해야겠어."
                ),
                "SS03": "꼭 좋은 결과 있길 바라요.",
            },
        },
    }


def test_parse_valid_record():
    record = EmotionDialogueRecord.model_validate(
        create_valid_record()
    )

    assert record.profile.persona_id == "Pro_03802"
    assert record.profile.persona.persona_id == "A02_G01_C01"
    assert record.profile.persona.human == ["A02", "G01"]

    assert record.profile.emotion.emotion_id == "S06_D02_E31"
    assert record.profile.emotion.type == "E31"
    assert record.profile.emotion.situation == ["S06", "D02"]

    assert record.talk.id.profile_id == "Pro_03802"
    assert record.talk.id.talk_id == "Pro_03802_00028"

    assert record.talk.content.HS01
    assert record.talk.content.SS03 == "꼭 좋은 결과 있길 바라요."


def test_allows_empty_third_turn():
    raw = create_valid_record()

    raw["talk"]["content"]["HS03"] = ""
    raw["talk"]["content"]["SS03"] = ""

    record = EmotionDialogueRecord.model_validate(raw)

    assert record.talk.content.HS03 == ""
    assert record.talk.content.SS03 == ""


def test_rejects_missing_required_field():
    raw = create_valid_record()

    del raw["talk"]["content"]["HS01"]

    with pytest.raises(ValidationError):
        EmotionDialogueRecord.model_validate(raw)


def test_rejects_unknown_field():
    raw = create_valid_record()

    raw["profile"]["unknown-field"] = "invalid"

    with pytest.raises(ValidationError):
        EmotionDialogueRecord.model_validate(raw)


def test_dump_preserves_original_aliases():
    record = EmotionDialogueRecord.model_validate(
        create_valid_record()
    )

    dumped = record.model_dump(by_alias=True)

    assert dumped["profile"]["persona-id"] == "Pro_03802"
    assert (
        dumped["profile"]["persona"]["persona-id"]
        == "A02_G01_C01"
    )
    assert (
        dumped["profile"]["emotion"]["emotion-id"]
        == "S06_D02_E31"
    )
    assert dumped["talk"]["id"]["profile-id"] == "Pro_03802"
    assert dumped["talk"]["id"]["talk-id"] == "Pro_03802_00028"


def test_rejects_wrong_data_type():
    raw = create_valid_record()

    raw["profile"]["persona"]["human"] = "A02_G01"

    with pytest.raises(ValidationError):
        EmotionDialogueRecord.model_validate(raw)