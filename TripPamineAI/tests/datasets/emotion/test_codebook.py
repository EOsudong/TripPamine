import pytest

from trippamine_ai.datasets.emotion.codebook import (
    AGE_CODEBOOK,
    COMPUTER_CODEBOOK,
    DISEASE_CODEBOOK,
    EMOTION_CODEBOOK,
    GENDER_CODEBOOK,
    SITUATION_CODEBOOK,
    get_coarse_emotion,
)


def test_age_codebook():
    assert len(AGE_CODEBOOK) == 4
    assert AGE_CODEBOOK["A01"] == "청소년"
    assert AGE_CODEBOOK["A04"] == "노년"


def test_gender_codebook():
    assert len(GENDER_CODEBOOK) == 2
    assert GENDER_CODEBOOK["G01"] == "남성"
    assert GENDER_CODEBOOK["G02"] == "여성"


def test_computer_codebook():
    assert COMPUTER_CODEBOOK == {
        "C01": "응답",
    }


def test_situation_codebook():
    assert len(SITUATION_CODEBOOK) == 13
    assert SITUATION_CODEBOOK["S01"] == "가족관계"
    assert SITUATION_CODEBOOK["S12"] == "대인관계(노년)"
    assert SITUATION_CODEBOOK["S13"] == "재정"


def test_disease_codebook():
    assert len(DISEASE_CODEBOOK) == 2
    assert DISEASE_CODEBOOK["D01"] == "만성질환 유"
    assert DISEASE_CODEBOOK["D02"] == "만성질환 무"


def test_emotion_codebook_contains_all_60_codes():
    expected_codes = {
        f"E{number}"
        for number in range(10, 70)
    }

    assert len(EMOTION_CODEBOOK) == 60
    assert set(EMOTION_CODEBOOK) == expected_codes


@pytest.mark.parametrize(
    ("emotion_code", "expected"),
    [
        ("E10", "분노"),
        ("E19", "분노"),
        ("E20", "슬픔"),
        ("E31", "불안"),
        ("E45", "상처"),
        ("E55", "당황"),
        ("E68", "기쁨"),
    ],
)
def test_get_coarse_emotion(emotion_code, expected):
    assert get_coarse_emotion(emotion_code) == expected


def test_get_coarse_emotion_rejects_unknown_code():
    with pytest.raises(
        ValueError,
        match="Unknown emotion code",
    ):
        get_coarse_emotion("E70")