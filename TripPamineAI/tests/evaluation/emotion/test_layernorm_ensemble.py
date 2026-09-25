import pytest
import torch

from scripts.emotion.evaluate_layernorm_ensemble import (
    build_alpha_values,
    calculate_complementarity,
)


def test_build_alpha_values():
    result = build_alpha_values(
        0.25
    )

    assert result == [
        0.0,
        0.25,
        0.5,
        0.75,
        1.0,
    ]


def test_build_alpha_values_rejects_invalid_step():
    with pytest.raises(
        ValueError,
    ):
        build_alpha_values(
            0.3
        )


def test_calculate_complementarity():
    labels = torch.tensor(
        [
            0,
            1,
            2,
            3,
        ],
        dtype=torch.long,
    )

    result = (
        calculate_complementarity(
            ko_predictions=[
                0,
                1,
                9,
                9,
            ],
            kc_predictions=[
                0,
                8,
                2,
                9,
            ],
            labels=labels,
        )
    )

    assert result[
        "both_correct"
    ] == 1

    assert result[
        "ko_only_correct"
    ] == 1

    assert result[
        "kc_only_correct"
    ] == 1

    assert result[
        "both_wrong"
    ] == 1

    assert result[
        "prediction_disagreements"
    ] == 2

    assert result[
        "disagreement_rate"
    ] == pytest.approx(
        0.5
    )