import pytest

from scripts.emotion.analyze_context_changed_only import (
    calculate_per_label_metrics,
    compare_per_label_metrics,
    find_changed_indices,
)


def test_find_changed_indices_selects_only_different_texts():
    result = find_changed_indices(
        candidate_texts=[
            "first",
            "same",
            "short",
            "same again",
        ],
        reference_texts=[
            "first\nsecond",
            "same",
            "short\nmore",
            "same again",
        ],
    )

    assert result == [
        0,
        2,
    ]


def test_find_changed_indices_rejects_length_mismatch():
    with pytest.raises(
        ValueError,
        match="lengths must match",
    ):
        find_changed_indices(
            candidate_texts=[
                "one",
            ],
            reference_texts=[
                "one",
                "two",
            ],
        )


def test_find_changed_indices_rejects_empty_input():
    with pytest.raises(
        ValueError,
        match="must not be empty",
    ):
        find_changed_indices(
            candidate_texts=[],
            reference_texts=[],
        )


def test_calculate_per_label_metrics():
    result = calculate_per_label_metrics(
        labels=[
            0,
            0,
            1,
            1,
        ],
        predictions=[
            0,
            1,
            1,
            1,
        ],
        id2label={
            0: "E10",
            1: "E11",
        },
    )

    assert (
        result[
            "E10"
        ][
            "support"
        ]
        == 2
    )

    assert (
        result[
            "E10"
        ][
            "precision"
        ]
        == pytest.approx(
            1.0
        )
    )

    assert (
        result[
            "E10"
        ][
            "recall"
        ]
        == pytest.approx(
            0.5
        )
    )

    assert (
        result[
            "E10"
        ][
            "f1"
        ]
        == pytest.approx(
            2.0 / 3.0
        )
    )

    assert (
        result[
            "E11"
        ][
            "support"
        ]
        == 2
    )

    assert (
        result[
            "E11"
        ][
            "precision"
        ]
        == pytest.approx(
            2.0 / 3.0
        )
    )

    assert (
        result[
            "E11"
        ][
            "recall"
        ]
        == pytest.approx(
            1.0
        )
    )

    assert (
        result[
            "E11"
        ][
            "f1"
        ]
        == pytest.approx(
            0.8
        )
    )


def test_compare_per_label_metrics_calculates_delta():
    result = compare_per_label_metrics(
        labels=[
            0,
            0,
            1,
            1,
        ],
        reference_predictions=[
            0,
            0,
            1,
            1,
        ],
        candidate_predictions=[
            0,
            1,
            1,
            1,
        ],
        id2label={
            0: "E10",
            1: "E11",
        },
    )

    assert (
        result[
            "E10"
        ][
            "full"
        ][
            "f1"
        ]
        == pytest.approx(
            1.0
        )
    )

    assert (
        result[
            "E10"
        ][
            "first2"
        ][
            "f1"
        ]
        == pytest.approx(
            2.0 / 3.0
        )
    )

    assert (
        result[
            "E10"
        ][
            "first2_minus_full"
        ][
            "f1"
        ]
        == pytest.approx(
            -1.0 / 3.0
        )
    )