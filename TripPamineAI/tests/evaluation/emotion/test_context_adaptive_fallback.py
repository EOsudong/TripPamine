import torch
import pytest

from scripts.emotion.analyze_context_adaptive_fallback import (
    apply_full_fallback,
    calculate_fallback_count,
    calculate_gap_recovery,
    compute_uncertainty_signals,
    select_fallback_indices,
)


def test_compute_uncertainty_signals_orders_confidence():
    logits = torch.tensor(
        [
            [
                6.0,
                0.0,
                0.0,
            ],
            [
                0.0,
                0.0,
                0.0,
            ],
        ],
        dtype=torch.float32,
    )

    result = (
        compute_uncertainty_signals(
            logits
        )
    )

    assert (
        result[
            "msp"
        ][
            0
        ]
        > result[
            "msp"
        ][
            1
        ]
    )

    assert (
        result[
            "margin"
        ][
            0
        ]
        > result[
            "margin"
        ][
            1
        ]
    )

    assert (
        result[
            "entropy"
        ][
            0
        ]
        < result[
            "entropy"
        ][
            1
        ]
    )


def test_calculate_fallback_count_uses_floor_budget():
    assert (
        calculate_fallback_count(
            eligible_count=4707,
            budget_percent=25,
        )
        == 1176
    )

    assert (
        calculate_fallback_count(
            eligible_count=4707,
            budget_percent=30,
        )
        == 1412
    )

    assert (
        calculate_fallback_count(
            eligible_count=4707,
            budget_percent=100,
        )
        == 4707
    )


def test_select_fallback_indices_lower_signal_first():
    values = torch.tensor(
        [
            0.9,
            0.1,
            0.3,
            0.2,
        ]
    )

    result = select_fallback_indices(
        signal_values=values,
        eligible_indices=[
            0,
            1,
            2,
            3,
        ],
        fallback_count=2,
        direction="lower",
    )

    assert result == [
        1,
        3,
    ]


def test_select_fallback_indices_higher_signal_first():
    values = torch.tensor(
        [
            0.2,
            0.7,
            0.5,
            0.9,
        ]
    )

    result = select_fallback_indices(
        signal_values=values,
        eligible_indices=[
            0,
            1,
            2,
            3,
        ],
        fallback_count=2,
        direction="higher",
    )

    assert result == [
        3,
        1,
    ]


def test_select_fallback_indices_breaks_ties_by_index():
    values = torch.tensor(
        [
            0.5,
            0.2,
            0.2,
            0.2,
        ]
    )

    result = select_fallback_indices(
        signal_values=values,
        eligible_indices=[
            0,
            1,
            2,
            3,
        ],
        fallback_count=2,
        direction="lower",
    )

    assert result == [
        1,
        2,
    ]


def test_apply_full_fallback_replaces_only_selected_rows():
    first2 = torch.tensor(
        [
            [
                1.0,
                2.0,
            ],
            [
                3.0,
                4.0,
            ],
            [
                5.0,
                6.0,
            ],
        ]
    )

    full = torch.tensor(
        [
            [
                10.0,
                20.0,
            ],
            [
                30.0,
                40.0,
            ],
            [
                50.0,
                60.0,
            ],
        ]
    )

    result = apply_full_fallback(
        first2_logits=first2,
        full_logits=full,
        fallback_indices=[
            1,
        ],
    )

    assert torch.equal(
        result[
            0
        ],
        first2[
            0
        ],
    )

    assert torch.equal(
        result[
            1
        ],
        full[
            1
        ],
    )

    assert torch.equal(
        result[
            2
        ],
        first2[
            2
        ],
    )


def test_calculate_gap_recovery():
    result = calculate_gap_recovery(
        first2_value=0.40,
        full_value=0.50,
        hybrid_value=0.47,
    )

    assert result == pytest.approx(
        0.7
    )


def test_calculate_gap_recovery_returns_none_for_zero_gap():
    result = calculate_gap_recovery(
        first2_value=0.5,
        full_value=0.5,
        hybrid_value=0.5,
    )

    assert result is None