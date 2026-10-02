import pytest
import torch

from scripts.emotion.probe_frozen_backbone_features import (
    FrozenFeatureProbe,
    load_feature_payload,
    summarize_seed_results,
    validate_pair_alignment,
)


def build_payload(
        backbone_key: str,
):
    return {
        "backbone_key": (
            backbone_key
        ),
        "model_name": "model",
        "revision": "revision",
        "split": "training",
        "max_length": 96,
        "features": torch.randn(
            4,
            768,
        ),
        "labels": torch.tensor(
            [
                0,
                1,
                2,
                3,
            ],
            dtype=torch.long,
        ),
        "sample_ids": [
            "a",
            "b",
            "c",
            "d",
        ],
    }


def test_probe_output_shape():
    model = FrozenFeatureProbe()

    features = torch.randn(
        8,
        768,
    )

    logits = model(
        features
    )

    assert logits.shape == (
        8,
        60,
    )


def test_probe_contains_layernorm():
    model = FrozenFeatureProbe()

    assert isinstance(
        model.layer_norm,
        torch.nn.LayerNorm,
    )

    assert (
        model.layer_norm
        .normalized_shape
        == (
            768,
        )
    )

    assert model.dropout.p == (
        pytest.approx(
            0.1
        )
    )


def test_load_feature_payload(
        tmp_path,
):
    path = (
            tmp_path
            / "features.pt"
    )

    torch.save(
        build_payload(
            "ko"
        ),
        path,
    )

    result = (
        load_feature_payload(
            path,
            "training",
        )
    )

    assert result[
        "features"
    ].shape == (
        4,
        768,
    )

    assert result[
        "labels"
    ].shape == (
        4,
    )


def test_validate_pair_alignment():
    first = build_payload(
        "ko"
    )

    second = build_payload(
        "kc"
    )

    validate_pair_alignment(
        first=first,
        second=second,
        split_name="training",
    )


def test_validate_pair_alignment_rejects_ids():
    first = build_payload(
        "ko"
    )

    second = build_payload(
        "kc"
    )

    second[
        "sample_ids"
    ][
        0
    ] = "different"

    with pytest.raises(
        ValueError,
        match=(
            "sample ID alignment "
            "mismatch"
        ),
    ):
        validate_pair_alignment(
            first=first,
            second=second,
            split_name="training",
        )


def test_summarize_seed_results():
    result = (
        summarize_seed_results(
            [
                {
                    "seed": 42,
                    "best_fine_macro_f1": 0.3,
                    "best_fine_accuracy": 0.4,
                },
                {
                    "seed": 123,
                    "best_fine_macro_f1": 0.4,
                    "best_fine_accuracy": 0.5,
                },
                {
                    "seed": 2026,
                    "best_fine_macro_f1": 0.5,
                    "best_fine_accuracy": 0.6,
                },
            ]
        )
    )

    assert result[
        "fine_macro_f1_mean"
    ] == pytest.approx(
        0.4
    )

    assert result[
        "fine_accuracy_mean"
    ] == pytest.approx(
        0.5
    )

    assert result[
        "fine_macro_f1_sample_std"
    ] == pytest.approx(
        0.1
    )