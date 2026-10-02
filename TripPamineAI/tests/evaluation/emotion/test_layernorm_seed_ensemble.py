import pytest
import torch

from scripts.emotion.evaluate_layernorm_seed_ensemble import (
    average_logits,
    combine_logits,
    validate_alignment,
)


def build_artifact(
        logits: list[list[float]],
        labels: list[int],
        sample_ids: list[str],
) -> dict:
    return {
        "model_key": "test",
        "logits": torch.tensor(
            logits,
            dtype=torch.float32,
        ),
        "labels": torch.tensor(
            labels,
            dtype=torch.long,
        ),
        "sample_ids": sample_ids,
    }


def test_average_logits():
    first = build_artifact(
        logits=[
            [1.0, 3.0],
            [5.0, 7.0],
        ],
        labels=[
            0,
            1,
        ],
        sample_ids=[
            "a",
            "b",
        ],
    )

    second = build_artifact(
        logits=[
            [3.0, 5.0],
            [7.0, 9.0],
        ],
        labels=[
            0,
            1,
        ],
        sample_ids=[
            "a",
            "b",
        ],
    )

    result = average_logits(
        [
            first,
            second,
        ]
    )

    expected = torch.tensor(
        [
            [2.0, 4.0],
            [6.0, 8.0],
        ],
        dtype=torch.float32,
    )

    assert torch.equal(
        result,
        expected,
    )


def test_validate_alignment_rejects_label_mismatch():
    first = build_artifact(
        logits=[
            [1.0, 2.0],
        ],
        labels=[
            0,
        ],
        sample_ids=[
            "a",
        ],
    )

    second = build_artifact(
        logits=[
            [1.0, 2.0],
        ],
        labels=[
            1,
        ],
        sample_ids=[
            "a",
        ],
    )

    with pytest.raises(
        ValueError,
        match="labels",
    ):
        validate_alignment(
            [
                first,
                second,
            ]
        )


def test_validate_alignment_rejects_sample_id_mismatch():
    first = build_artifact(
        logits=[
            [1.0, 2.0],
        ],
        labels=[
            0,
        ],
        sample_ids=[
            "a",
        ],
    )

    second = build_artifact(
        logits=[
            [1.0, 2.0],
        ],
        labels=[
            0,
        ],
        sample_ids=[
            "b",
        ],
    )

    with pytest.raises(
        ValueError,
        match="sample",
    ):
        validate_alignment(
            [
                first,
                second,
            ]
        )


def test_combine_logits_uses_fixed_weights():
    first = torch.tensor(
        [
            [2.0, 4.0],
        ],
        dtype=torch.float32,
    )

    second = torch.tensor(
        [
            [6.0, 8.0],
        ],
        dtype=torch.float32,
    )

    result = combine_logits(
        first_logits=first,
        second_logits=second,
        first_weight=0.5,
        second_weight=0.5,
    )

    expected = torch.tensor(
        [
            [4.0, 6.0],
        ],
        dtype=torch.float32,
    )

    assert torch.equal(
        result,
        expected,
    )


def test_combine_logits_rejects_invalid_weight_sum():
    logits = torch.tensor(
        [
            [1.0, 2.0],
        ],
        dtype=torch.float32,
    )

    with pytest.raises(
        ValueError,
        match="sum",
    ):
        combine_logits(
            first_logits=logits,
            second_logits=logits,
            first_weight=0.7,
            second_weight=0.7,
        )