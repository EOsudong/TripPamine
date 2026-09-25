from trippamine_ai.datasets.emotion.classification import (
    ClassificationSource,
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.split_integrity import (
    SplitIntegrityAnalyzer,
)


def create_sample(
    sample_id: str,
    talk_id: str,
    profile_id: str,
    label: str,
    label_name: str,
    coarse_label: str,
    split: str,
) -> EmotionClassificationSample:
    return EmotionClassificationSample(
        id=sample_id,
        text="테스트 감정 문장입니다.",
        label=label,
        label_name=label_name,
        coarse_label=coarse_label,
        situation_code="S06",
        situation="진로,취업,직장",
        quality_status="VALID",
        quality_issue_codes=[],
        source=ClassificationSource(
            dataset="aihub-emotional-dialogue",
            version="v1",
            split=split,
            profile_id=profile_id,
            talk_id=talk_id,
        ),
    )


def test_no_overlap():
    training = [
        create_sample(
            "record-1",
            "talk-1",
            "profile-1",
            "E10",
            "분노",
            "분노",
            "training",
        )
    ]

    validation = [
        create_sample(
            "record-2",
            "talk-2",
            "profile-2",
            "E10",
            "분노",
            "분노",
            "validation",
        )
    ]

    result = SplitIntegrityAnalyzer().analyze(
        training,
        validation,
    )

    assert result.overlap.record_id_count == 0
    assert result.overlap.talk_id_count == 0
    assert result.overlap.profile_id_count == 0


def test_record_overlap():
    training = [
        create_sample(
            "same-record",
            "talk-1",
            "profile-1",
            "E10",
            "분노",
            "분노",
            "training",
        )
    ]

    validation = [
        create_sample(
            "same-record",
            "talk-2",
            "profile-2",
            "E10",
            "분노",
            "분노",
            "validation",
        )
    ]

    result = SplitIntegrityAnalyzer().analyze(
        training,
        validation,
    )

    assert result.overlap.record_id_count == 1


def test_talk_id_overlap():
    training = [
        create_sample(
            "record-1",
            "same-talk",
            "profile-1",
            "E10",
            "분노",
            "분노",
            "training",
        )
    ]

    validation = [
        create_sample(
            "record-2",
            "same-talk",
            "profile-2",
            "E10",
            "분노",
            "분노",
            "validation",
        )
    ]

    result = SplitIntegrityAnalyzer().analyze(
        training,
        validation,
    )

    assert result.overlap.talk_id_count == 1


def test_profile_overlap():
    training = [
        create_sample(
            "record-1",
            "talk-1",
            "same-profile",
            "E10",
            "분노",
            "분노",
            "training",
        )
    ]

    validation = [
        create_sample(
            "record-2",
            "talk-2",
            "same-profile",
            "E11",
            "툴툴대는",
            "분노",
            "validation",
        )
    ]

    result = SplitIntegrityAnalyzer().analyze(
        training,
        validation,
    )

    assert result.overlap.profile_id_count == 1

    assert (
        result.overlap
        .validation_profile_overlap_rate
        == 1.0
    )


def test_duplicate_record_id_inside_split():
    training = [
        create_sample(
            "record-1",
            "talk-1",
            "profile-1",
            "E10",
            "분노",
            "분노",
            "training",
        ),
        create_sample(
            "record-1",
            "talk-2",
            "profile-2",
            "E10",
            "분노",
            "분노",
            "training",
        ),
    ]

    result = SplitIntegrityAnalyzer().analyze(
        training,
        [],
    )

    assert (
        result.training.duplicate_record_ids
        == 1
    )


def test_distribution_difference():
    training = [
        create_sample(
            "record-1",
            "talk-1",
            "profile-1",
            "E10",
            "분노",
            "분노",
            "training",
        ),
        create_sample(
            "record-2",
            "talk-2",
            "profile-2",
            "E10",
            "분노",
            "분노",
            "training",
        ),
    ]

    validation = [
        create_sample(
            "record-3",
            "talk-3",
            "profile-3",
            "E31",
            "두려운",
            "불안",
            "validation",
        )
    ]

    result = SplitIntegrityAnalyzer().analyze(
        training,
        validation,
    )

    comparison = {
        item.label: item
        for item in (
            result.fine_emotion_comparison
        )
    }

    assert (
        comparison["E10"]
        .training_share
        == 1.0
    )

    assert (
        comparison["E10"]
        .validation_share
        == 0.0
    )

    assert (
        comparison["E10"]
        .absolute_share_difference
        == 1.0
    )