import torch
import pytest

from scripts.emotion.analyze_context_fixed_margin_fallback import (
    FIXED_MARGIN_THRESHOLD,
    select_margin_fallback_indices,
    summarize_selected_margins,
)


def test_fixed_margin_threshold_is_locked():
    assert (
        FIXED_MARGIN_THRESHOLD
        == pytest.approx(
            0.16
        )
    )


def test_select_margin_fallback_indices_uses_threshold():
    margins = torch.tensor(
        [
            0.05,
            0.16,
            0.17,
            0.10,
            0.30,
        ],
        dtype=torch.float32,
    )

    result = (
        select_margin_fallback_indices(
            margin_values=margins,
            eligible_indices=[
                0,
                1,
                2,
                3,
                4,
            ],
            threshold=0.16,
        )
    )

    assert result == [
        0,
        1,
        3,
    ]


def test_select_margin_fallback_indices_respects_eligibility():
    margins = torch.tensor(
        [
            0.01,
            0.02,
            0.03,
            0.50,
        ],
        dtype=torch.float32,
    )

    result = (
        select_margin_fallback_indices(
            margin_values=margins,
            eligible_indices=[
                1,
                3,
            ],
            threshold=0.16,
        )
    )

    assert result == [
        1,
    ]


def test_select_margin_fallback_indices_is_inclusive():
    margins = torch.tensor(
        [
            0.16,
        ],
        dtype=torch.float32,
    )

    result = (
        select_margin_fallback_indices(
            margin_values=margins,
            eligible_indices=[
                0,
            ],
            threshold=0.16,
        )
    )

    assert result == [
        0,
    ]


def test_select_margin_fallback_indices_can_return_empty():
    margins = torch.tensor(
        [
            0.20,
            0.30,
        ],
        dtype=torch.float32,
    )

    result = (
        select_margin_fallback_indices(
            margin_values=margins,
            eligible_indices=[
                0,
                1,
            ],
            threshold=0.16,
        )
    )

    assert result == []


def test_select_margin_fallback_indices_rejects_duplicate_indices():
    margins = torch.tensor(
        [
            0.1,
            0.2,
        ]
    )

    with pytest.raises(
        ValueError,
        match="duplicates",
    ):
        select_margin_fallback_indices(
            margin_values=margins,
            eligible_indices=[
                0,
                0,
            ],
            threshold=0.16,
        )


def test_select_margin_fallback_indices_rejects_bad_threshold():
    margins = torch.tensor(
        [
            0.1,
        ]
    )

    with pytest.raises(
        ValueError,
        match="must not be negative",
    ):
        select_margin_fallback_indices(
            margin_values=margins,
            eligible_indices=[
                0,
            ],
            threshold=-0.1,
        )


def test_summarize_selected_margins():
    margins = torch.tensor(
        [
            0.03,
            0.10,
            0.16,
            0.20,
            0.40,
        ],
        dtype=torch.float32,
    )

    result = (
        summarize_selected_margins(
            margin_values=margins,
            fallback_indices=[
                0,
                1,
                2,
            ],
            rejected_indices=[
                3,
                4,
            ],
        )
    )

    assert result[
        "selected_min"
    ] == pytest.approx(
        0.03
    )

    assert result[
        "selected_max"
    ] == pytest.approx(
        0.16
    )

    assert result[
        "rejected_min"
    ] == pytest.approx(
        0.20
    )

    assert result[
        "rejected_max"
    ] == pytest.approx(
        0.40
    )