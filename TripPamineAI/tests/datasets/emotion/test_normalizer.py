from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)
from trippamine_ai.datasets.emotion.validator import (
    ValidationStatus,
)


def create_record() -> EmotionDialogueRecord:
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


def test_normalize_record():
    normalizer = EmotionDialogueNormalizer()

    normalized = normalizer.normalize(
        create_record(),
        split="training",
    )

    assert (
        normalized.source.dataset
        == "aihub-emotional-dialogue"
    )

    assert normalized.source.version == "v1"
    assert normalized.source.split == "training"

    assert normalized.source.profile_id == "Pro_03802"
    assert (
        normalized.source.talk_id
        == "Pro_03802_00028"
    )


def test_normalize_persona():
    normalized = EmotionDialogueNormalizer().normalize(
        create_record(),
        split="training",
    )

    assert normalized.persona.age_code == "A02"
    assert normalized.persona.age == "청년"

    assert normalized.persona.gender_code == "G01"
    assert normalized.persona.gender == "남성"

    assert normalized.persona.computer_code == "C01"


def test_normalize_emotion():
    normalized = EmotionDialogueNormalizer().normalize(
        create_record(),
        split="training",
    )

    assert normalized.emotion.emotion_code == "E31"
    assert normalized.emotion.emotion == "두려운"

    assert normalized.emotion.coarse_emotion == "불안"

    assert normalized.emotion.situation_code == "S06"
    assert (
        normalized.emotion.situation
        == "진로,취업,직장"
    )

    assert normalized.emotion.disease_code == "D02"
    assert normalized.emotion.disease == "만성질환 무"


def test_normalize_three_turns():
    normalized = EmotionDialogueNormalizer().normalize(
        create_record(),
        split="training",
    )

    assert len(normalized.turns) == 3

    assert normalized.turns[0].turn == 1
    assert normalized.turns[1].turn == 2
    assert normalized.turns[2].turn == 3


def test_empty_third_turn_is_removed():
    record = create_record()

    record.talk.content.HS03 = ""
    record.talk.content.SS03 = ""

    normalized = EmotionDialogueNormalizer().normalize(
        record,
        split="training",
    )

    assert len(normalized.turns) == 2


def test_incomplete_third_turn_is_preserved():
    record = create_record()

    record.talk.content.SS03 = ""

    normalized = EmotionDialogueNormalizer().normalize(
        record,
        split="training",
    )

    assert len(normalized.turns) == 3
    assert normalized.turns[2].human
    assert normalized.turns[2].assistant == ""

    assert (
        normalized.quality.status
        == ValidationStatus.WARNING
    )


def test_record_id_is_deterministic():
    normalizer = EmotionDialogueNormalizer()

    record = create_record()

    first = normalizer.normalize(
        record,
        split="training",
    )

    second = normalizer.normalize(
        record,
        split="validation",
    )

    assert first.record_id == second.record_id


def test_record_id_changes_when_content_changes():
    normalizer = EmotionDialogueNormalizer()

    first_record = create_record()
    second_record = create_record()

    second_record.talk.content.HS01 = (
        "완전히 다른 내용의 사용자 발화야."
    )

    first = normalizer.normalize(
        first_record,
        split="training",
    )

    second = normalizer.normalize(
        second_record,
        split="training",
    )

    assert first.record_id != second.record_id


def test_quality_result_is_preserved():
    normalized = EmotionDialogueNormalizer().normalize(
        create_record(),
        split="training",
    )

    assert (
        normalized.quality.status
        == ValidationStatus.VALID
    )

    assert normalized.quality.issues == []


def test_record_id_is_sha256():
    normalized = EmotionDialogueNormalizer().normalize(
        create_record(),
        split="training",
    )

    assert len(normalized.record_id) == 64

    int(normalized.record_id, 16)