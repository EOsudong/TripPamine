from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)
from trippamine_ai.datasets.emotion.profile_support import (
    ProfileLabelSupportAnalyzer,
)


def create_record(
    profile_id: str,
    talk_id: str,
    emotion_code: str,
):
    raw = {
        "profile": {
            "persona-id": profile_id,
            "persona": {
                "persona-id": "A02_G01_C01",
                "human": ["A02", "G01"],
                "computer": ["C01"],
            },
            "emotion": {
                "emotion-id": (
                    f"S06_D02_{emotion_code}"
                ),
                "type": emotion_code,
                "situation": [
                    "S06",
                    "D02",
                ],
            },
        },
        "talk": {
            "id": {
                "profile-id": profile_id,
                "talk-id": talk_id,
            },
            "content": {
                "HS01": "요즘 회사 일이 많이 걱정돼.",
                "SS01": "걱정이 많이 되시겠어요.",
                "HS02": "계속 실수할까 봐 불안해.",
                "SS02": "실수에 대한 걱정이 크시군요.",
                "HS03": "",
                "SS03": "",
            },
        },
    }

    record = (
        EmotionDialogueRecord.model_validate(
            raw
        )
    )

    return (
        EmotionDialogueNormalizer()
        .normalize(
            record,
            split="training",
        )
    )


def test_counts_total_records():
    records = [
        create_record(
            "Pro_00001",
            "Talk_1",
            "E31",
        ),
        create_record(
            "Pro_00002",
            "Talk_2",
            "E31",
        ),
    ]

    result = (
        ProfileLabelSupportAnalyzer()
        .analyze(records)
    )

    assert (
        result.summary.total_records
        == 2
    )

    assert (
        result.summary.unique_profiles
        == 2
    )


def test_counts_unique_profiles_per_label():
    records = [
        create_record(
            "Pro_00001",
            "Talk_1",
            "E31",
        ),
        create_record(
            "Pro_00001",
            "Talk_2",
            "E31",
        ),
        create_record(
            "Pro_00002",
            "Talk_3",
            "E31",
        ),
    ]

    result = (
        ProfileLabelSupportAnalyzer()
        .analyze(records)
    )

    labels = {
        item.label: item
        for item in result.labels
    }

    assert (
        labels["E31"].total_records
        == 3
    )

    assert (
        labels["E31"].unique_profiles
        == 2
    )


def test_detects_dominant_profile():
    records = [
        create_record(
            "Pro_00001",
            "Talk_1",
            "E31",
        ),
        create_record(
            "Pro_00001",
            "Talk_2",
            "E31",
        ),
        create_record(
            "Pro_00002",
            "Talk_3",
            "E31",
        ),
    ]

    result = (
        ProfileLabelSupportAnalyzer()
        .analyze(records)
    )

    labels = {
        item.label: item
        for item in result.labels
    }

    emotion = labels["E31"]

    assert (
        emotion.max_profile_id
        == "Pro_00001"
    )

    assert (
        emotion.max_records_in_profile
        == 2
    )

    assert (
        emotion.max_profile_share
        == 2 / 3
    )


def test_three_split_coverage_requires_three_profiles():
    records = [
        create_record(
            "Pro_00001",
            "Talk_1",
            "E31",
        ),
        create_record(
            "Pro_00002",
            "Talk_2",
            "E31",
        ),
    ]

    result = (
        ProfileLabelSupportAnalyzer()
        .analyze(records)
    )

    labels = {
        item.label: item
        for item in result.labels
    }

    assert (
        labels[
            "E31"
        ].three_split_coverage_possible
        is False
    )


def test_three_profiles_allow_individual_coverage():
    records = [
        create_record(
            "Pro_00001",
            "Talk_1",
            "E31",
        ),
        create_record(
            "Pro_00002",
            "Talk_2",
            "E31",
        ),
        create_record(
            "Pro_00003",
            "Talk_3",
            "E31",
        ),
    ]

    result = (
        ProfileLabelSupportAnalyzer()
        .analyze(records)
    )

    labels = {
        item.label: item
        for item in result.labels
    }

    assert (
        labels[
            "E31"
        ].three_split_coverage_possible
        is True
    )