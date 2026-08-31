from trippamine_ai.datasets.emotion.classification import (
    ClassificationSource,
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.profile_distribution import (
    ProfileLabelDistributionAnalyzer,
)


def create_sample(
    sample_id: str,
    profile_id: str,
    label: str,
    label_name: str,
    coarse_label: str,
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
            dataset=(
                "aihub-emotional-dialogue"
            ),
            version="v1",
            split="training",
            profile_id=profile_id,
            talk_id=sample_id,
        ),
    )


def test_groups_records_by_profile():
    records = [
        create_sample(
            "record-1",
            "profile-1",
            "E10",
            "분노",
            "분노",
        ),
        create_sample(
            "record-2",
            "profile-1",
            "E11",
            "툴툴대는",
            "분노",
        ),
        create_sample(
            "record-3",
            "profile-2",
            "E31",
            "두려운",
            "불안",
        ),
    ]

    result = (
        ProfileLabelDistributionAnalyzer()
        .analyze(records)
    )

    assert result.total_records == 3
    assert result.total_profiles == 2


def test_profile_label_counts():
    records = [
        create_sample(
            "record-1",
            "profile-1",
            "E10",
            "분노",
            "분노",
        ),
        create_sample(
            "record-2",
            "profile-1",
            "E10",
            "분노",
            "분노",
        ),
        create_sample(
            "record-3",
            "profile-1",
            "E31",
            "두려운",
            "불안",
        ),
    ]

    result = (
        ProfileLabelDistributionAnalyzer()
        .analyze(records)
    )

    profile = result.profiles[0]

    assert profile.total_records == 3
    assert profile.unique_labels == 2

    assert (
        profile.label_counts["E10"]
        == 2
    )

    assert (
        profile.label_counts["E31"]
        == 1
    )


def test_label_profile_support():
    records = [
        create_sample(
            "record-1",
            "profile-1",
            "E10",
            "분노",
            "분노",
        ),
        create_sample(
            "record-2",
            "profile-1",
            "E10",
            "분노",
            "분노",
        ),
        create_sample(
            "record-3",
            "profile-2",
            "E10",
            "분노",
            "분노",
        ),
    ]

    result = (
        ProfileLabelDistributionAnalyzer()
        .analyze(records)
    )

    support = {
        item.label: item
        for item in result.label_support
    }["E10"]

    assert support.total_records == 3
    assert support.profile_count == 2

    assert (
        support.max_records_per_profile
        == 2
    )

    assert (
        support.max_profile_share
        == 2 / 3
    )


def test_single_label_profile_count():
    records = [
        create_sample(
            "record-1",
            "profile-1",
            "E10",
            "분노",
            "분노",
        ),
        create_sample(
            "record-2",
            "profile-2",
            "E10",
            "분노",
            "분노",
        ),
        create_sample(
            "record-3",
            "profile-2",
            "E31",
            "두려운",
            "불안",
        ),
    ]

    result = (
        ProfileLabelDistributionAnalyzer()
        .analyze(records)
    )

    assert (
        result.profiles_with_single_label
        == 1
    )


def test_duplicate_record_ids():
    records = [
        create_sample(
            "same-record",
            "profile-1",
            "E10",
            "분노",
            "분노",
        ),
        create_sample(
            "same-record",
            "profile-2",
            "E31",
            "두려운",
            "불안",
        ),
    ]

    result = (
        ProfileLabelDistributionAnalyzer()
        .analyze(records)
    )

    assert result.duplicate_record_ids == 1