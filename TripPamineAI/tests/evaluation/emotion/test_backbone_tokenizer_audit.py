import pytest

from scripts.emotion.audit_backbone_tokenizers import (
    build_limit_summary,
    build_pairwise_comparison,
    build_shortest_summary,
    summarize_lengths,
)


def test_summarize_lengths():
    result = summarize_lengths(
        [
            10,
            20,
            30,
            40,
            50,
        ]
    )

    assert result[
        "samples"
    ] == 5

    assert result[
        "minimum"
    ] == 10

    assert result[
        "mean"
    ] == pytest.approx(
        30.0
    )

    assert result[
        "p50"
    ] == pytest.approx(
        30.0
    )

    assert result[
        "maximum"
    ] == 50


def test_build_limit_summary():
    result = build_limit_summary(
        lengths=[
            90,
            96,
            97,
            100,
        ],
        limit=96,
    )

    assert result[
        "samples_over_limit"
    ] == 2

    assert result[
        "share_over_limit"
    ] == pytest.approx(
        0.5
    )

    assert result[
        "total_excess_tokens"
    ] == 5

    assert result[
        "mean_excess_tokens"
    ] == pytest.approx(
        2.5
    )

    assert result[
        "maximum_excess_tokens"
    ] == 4


def test_pairwise_comparison():
    result = (
        build_pairwise_comparison(
            lengths_a=[
                10,
                20,
                30,
                40,
            ],
            lengths_b=[
                11,
                19,
                30,
                45,
            ],
            key_a="a",
            key_b="b",
        )
    )

    assert result[
        "a_shorter"
    ] == 2

    assert result[
        "b_shorter"
    ] == 1

    assert result[
        "equal"
    ] == 1

    assert result[
        "mean_a_minus_b_tokens"
    ] == pytest.approx(
        -1.25
    )


def test_shortest_summary():
    result = (
        build_shortest_summary(
            {
                "ko": [
                    10,
                    20,
                    30,
                    40,
                ],
                "kc23": [
                    11,
                    19,
                    30,
                    40,
                ],
                "kc22": [
                    12,
                    18,
                    29,
                    40,
                ],
            }
        )
    )

    assert result[
        "unique_shortest"
    ] == {
        "ko": 1,
        "kc23": 0,
        "kc22": 2,
    }

    assert result[
        "tied_shortest"
    ] == 1

    assert result[
        "all_equal"
    ] == 1


def test_pairwise_rejects_length_mismatch():
    with pytest.raises(
        ValueError,
        match=(
            "lengths must match"
        ),
    ):
        build_pairwise_comparison(
            lengths_a=[
                10,
            ],
            lengths_b=[
                10,
                20,
            ],
            key_a="a",
            key_b="b",
        )