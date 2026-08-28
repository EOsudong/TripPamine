from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationBuilder,
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
                "HS01": "일은 왜 해도 해도 끝이 없을까? 화가 난다.",
                "SS01": "많이 힘드시겠어요. 주위에 의논할 상대가 있나요?",
                "HS02": "그냥 내가 해결하는 게 나아.",
                "SS02": "혼자 감당하고 계시는군요.",
                "HS03": "",
                "SS03": "",
            },
        },
    }

    record = EmotionDialogueRecord.model_validate(raw)

    return EmotionDialogueNormalizer().normalize(
        record,
        split="training",
    )


def test_build_classification_sample():
    normalized = create_normalized_record()

    sample = EmotionClassificationBuilder().build(
        normalized
    )

    assert sample.id == normalized.record_id

    assert (
        sample.text
        == "일은 왜 해도 해도 끝이 없을까? 화가 난다."
    )

    assert sample.label == "E18"
    assert sample.label_name == "노여워하는"
    assert sample.coarse_label == "분노"


def test_uses_only_first_human_turn():
    normalized = create_normalized_record()

    sample = EmotionClassificationBuilder().build(
        normalized
    )

    assert sample.text == normalized.turns[0].human
    assert normalized.turns[1].human not in sample.text


def test_preserves_situation():
    sample = EmotionClassificationBuilder().build(
        create_normalized_record()
    )

    assert sample.situation_code == "S06"
    assert sample.situation == "진로,취업,직장"


def test_preserves_source_metadata():
    sample = EmotionClassificationBuilder().build(
        create_normalized_record()
    )

    assert (
        sample.source.dataset
        == "aihub-emotional-dialogue"
    )

    assert sample.source.version == "v1"
    assert sample.source.split == "training"

    assert sample.source.profile_id == "Pro_05349"
    assert (
        sample.source.talk_id
        == "Pro_05349_00053"
    )


def test_preserves_quality_information():
    sample = EmotionClassificationBuilder().build(
        create_normalized_record()
    )

    assert sample.quality_status in {
        "VALID",
        "WARNING",
    }

    assert isinstance(
        sample.quality_issue_codes,
        list,
    )