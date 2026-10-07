from trippamine_ai.datasets.emotion.classification import (
    ClassificationSource,
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.statistics import (
    ClassificationStatisticsAnalyzer,
)


def create_sample(
    sample_id: str,
    label: str,
    label_name: str,
    coarse_label: str,
    quality_status: str = "VALID",
) -> EmotionClassificationSample:
    return EmotionClassificationSample(
        id=sample_id,
        text="감정을 표현하는 테스트 문장입니다.",
        label=label,
        label_name=label_name,
        coarse_label=coarse_label,
        situation_code="S06",
        situation="진로,취업,직장",
        quality_status=quality_status,
        quality_issue_codes=[],
        source=ClassificationSource(
            dataset="aihub-emotional-dialogue",
            version="v1",
            split="training",
            profile_id="Pro_00001",
            talk_id=sample_id,
        ),
    )


def test_analyze_total_records():
    records = [
        create_sample(
            "1",
            "E18",
            "노여워하는",
            "분노",
        ),
        create_sample(
            "2",
            "E18",
            "노여워하는",
            "분노",
        ),
        create_sample(
            "3",
            "E31",
            "두려운",
            "불안",
        ),
    ]

    result = (
        ClassificationStatisticsAnalyzer()
        .analyze(records)
    )

    assert result.total_records == 3


def test_analyze_label_distribution():
    records = [
        create_sample(
            "1",
            "E18",
            "노여워하는",
            "분노",
        ),
        create_sample(
            "2",
            "E18",
            "노여워하는",
            "분노",
        ),
        create_sample(
            "3",
            "E31",
            "두려운",
            "불안",
        ),
    ]

    result = (
        ClassificationStatisticsAnalyzer()
        .analyze(records)
    )

    distributions = {
        item.label: item
        for item in result.fine_emotion
    }

    assert distributions["E18"].count == 2
    assert distributions["E31"].count == 1

    assert (
        distributions["E18"].share
        == 2 / 3
    )


def test_analyze_coarse_emotion():
    records = [
        create_sample(
            "1",
            "E18",
            "노여워하는",
            "분노",
        ),
        create_sample(
            "2",
            "E31",
            "두려운",
            "불안",
        ),
    ]

    result = (
        ClassificationStatisticsAnalyzer()
        .analyze(records)
    )

    assert result.coarse_emotion["분노"] == 1
    assert result.coarse_emotion["불안"] == 1


def test_missing_labels_are_detected():
    records = [
        create_sample(
            "1",
            "E18",
            "노여워하는",
            "분노",
        )
    ]

    result = (
        ClassificationStatisticsAnalyzer()
        .analyze(records)
    )

    assert "E18" not in result.missing_labels
    assert "E31" in result.missing_labels


def test_imbalance_ratio():
    records = [
        create_sample(
            "1",
            "E18",
            "노여워하는",
            "분노",
        ),
        create_sample(
            "2",
            "E18",
            "노여워하는",
            "분노",
        ),
        create_sample(
            "3",
            "E31",
            "두려운",
            "불안",
        ),
    ]

    result = (
        ClassificationStatisticsAnalyzer()
        .analyze(records)
    )

    assert result.maximum_label_count == 2
    assert result.minimum_label_count == 1
    assert result.imbalance_ratio == 2.0