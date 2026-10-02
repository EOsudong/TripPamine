import pytest

from trippamine_ai.datasets.emotion.tokenizer_statistics import (
    TokenizerStatisticsAnalyzer,
)


def test_analyze_basic_statistics():
    result = (
        TokenizerStatisticsAnalyzer()
        .analyze(
            [
                10,
                20,
                30,
                40,
            ]
        )
    )

    assert result.total_samples == 4
    assert result.minimum == 10
    assert result.maximum == 40
    assert result.mean == 25.0

    assert result.p50 == pytest.approx(
        25.0
    )

    assert result.p90 == pytest.approx(
        37.0
    )

    assert result.p95 == pytest.approx(
        38.5
    )

    assert result.p99 == pytest.approx(
        39.7
    )


def test_analyze_thresholds():
    result = (
        TokenizerStatisticsAnalyzer()
        .analyze(
            lengths=[
                10,
                20,
                30,
                40,
            ],
            thresholds=[
                20,
                30,
            ],
        )
    )

    thresholds = {
        item.threshold: item
        for item in result.thresholds
    }

    assert (
        thresholds[20]
        .exceeded_count
        == 2
    )

    assert (
        thresholds[20]
        .exceeded_rate
        == pytest.approx(
            0.5
        )
    )

    assert (
        thresholds[30]
        .exceeded_count
        == 1
    )

    assert (
        thresholds[30]
        .exceeded_rate
        == pytest.approx(
            0.25
        )
    )


def test_default_thresholds():
    result = (
        TokenizerStatisticsAnalyzer()
        .analyze(
            [
                10,
                40,
                70,
                100,
                140,
                300,
                520,
            ]
        )
    )

    assert [
        item.threshold
        for item
        in result.thresholds
    ] == [
        32,
        64,
        96,
        128,
        256,
        512,
    ]


def test_empty_lengths_are_rejected():
    with pytest.raises(
        ValueError,
        match=(
            "Token lengths must "
            "not be empty"
        ),
    ):
        (
            TokenizerStatisticsAnalyzer()
            .analyze([])
        )


def test_invalid_lengths_are_rejected():
    with pytest.raises(
        ValueError,
        match=(
            "Token lengths must be "
            "positive integers"
        ),
    ):
        (
            TokenizerStatisticsAnalyzer()
            .analyze(
                [
                    10,
                    0,
                    30,
                ]
            )
        )


def test_invalid_thresholds_are_rejected():
    with pytest.raises(
        ValueError,
        match=(
            "Thresholds must be "
            "positive integers"
        ),
    ):
        (
            TokenizerStatisticsAnalyzer()
            .analyze(
                lengths=[
                    10,
                    20,
                ],
                thresholds=[
                    0,
                ],
            )
        )


def test_duplicate_thresholds_are_rejected():
    with pytest.raises(
        ValueError,
        match=(
            "Thresholds must "
            "not contain duplicates"
        ),
    ):
        (
            TokenizerStatisticsAnalyzer()
            .analyze(
                lengths=[
                    10,
                    20,
                ],
                thresholds=[
                    64,
                    64,
                ],
            )
        )