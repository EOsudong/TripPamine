import pytest

from scripts.emotion.analyze_transformer_classifier import (
    build_all_label_metrics,
    build_confidence_summary,
    build_fine_to_coarse,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


def test_fine_to_coarse_contains_all_labels():
    mapping = EmotionLabelMapping()

    result = build_fine_to_coarse(
        mapping
    )

    assert len(
        result
    ) == 60

    assert result[
        "E10"
    ] == "분노"

    assert result[
        "E69"
    ] == "기쁨"


def test_all_label_metrics_keep_zero_support_labels():
    mapping = EmotionLabelMapping()

    metrics = build_all_label_metrics(
        true_labels=[
            "E10",
            "E10",
        ],
        predicted_labels=[
            "E10",
            "E11",
        ],
        label_mapping=mapping,
    )

    assert len(
        metrics
    ) == 60

    by_label = {
        metric["label"]: metric
        for metric in metrics
    }

    assert (
        by_label[
            "E10"
        ][
            "support"
        ]
        == 2
    )

    assert (
        by_label[
            "E20"
        ][
            "support"
        ]
        == 0
    )


def test_confidence_summary_separates_correct_and_wrong():
    result = build_confidence_summary(
        true_labels=[
            "E10",
            "E20",
        ],
        predicted_labels=[
            "E10",
            "E10",
        ],
        confidences=[
            0.8,
            0.6,
        ],
    )

    assert (
        result[
            "mean_top1_confidence"
        ]
        == pytest.approx(
            0.7
        )
    )

    assert (
        result[
            "correct_mean_top1_confidence"
        ]
        == pytest.approx(
            0.8
        )
    )

    assert (
        result[
            "incorrect_mean_top1_confidence"
        ]
        == pytest.approx(
            0.6
        )
    )


def test_confidence_summary_rejects_length_mismatch():
    with pytest.raises(
        ValueError,
        match=(
            "input lengths must match"
        ),
    ):
        build_confidence_summary(
            true_labels=[
                "E10"
            ],
            predicted_labels=[
                "E10"
            ],
            confidences=[],
        )


def test_confidence_summary_rejects_invalid_probability():
    with pytest.raises(
        ValueError,
        match=(
            "between 0 and 1"
        ),
    ):
        build_confidence_summary(
            true_labels=[
                "E10"
            ],
            predicted_labels=[
                "E10"
            ],
            confidences=[
                1.1
            ],
        )