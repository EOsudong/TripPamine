import pytest
import torch

from trippamine_ai.evaluation.emotion.persistent_ensemble_error_analysis import (
    analyze_persistent_ensemble_errors,
    summarize_confidences,
)


def build_logits(
        predictions: list[int],
        strengths: list[float],
        num_labels: int = 4,
) -> torch.Tensor:
    if (
            len(
                predictions
            )
            != len(
                strengths
            )
    ):
        raise ValueError(
            "predictions/strengths "
            "length mismatch"
        )

    logits = torch.zeros(
        (
            len(
                predictions
            ),
            num_labels,
        ),
        dtype=torch.float32,
    )

    for (
            index,
            (
                prediction,
                strength,
            ),
    ) in enumerate(
        zip(
            predictions,
            strengths,
            strict=True,
        )
    ):
        logits[
            index,
            prediction,
        ] = strength

    return logits


def build_analysis():
    labels = torch.tensor(
        [
            0,
            1,
            2,
            3,
            0,
            1,
        ],
        dtype=torch.long,
    )

    sample_ids = [
        "s0",
        "s1",
        "s2",
        "s3",
        "s4",
        "s5",
    ]

    id2label = {
        0: "E10",
        1: "E11",
        2: "E20",
        3: "E21",
    }

    fine_to_coarse = {
        "E10": "A",
        "E11": "A",
        "E20": "B",
        "E21": "B",
    }

    seed42 = build_logits(
        predictions=[
            0,
            0,
            0,
            2,
            0,
            0,
        ],
        strengths=[
            3.0,
            3.0,
            7.0,
            4.0,
            3.0,
            3.0,
        ],
    )

    seed123 = build_logits(
        predictions=[
            0,
            0,
            0,
            0,
            1,
            0,
        ],
        strengths=[
            3.0,
            3.0,
            7.0,
            4.0,
            3.0,
            3.0,
        ],
    )

    seed2026 = build_logits(
        predictions=[
            0,
            0,
            0,
            2,
            1,
            1,
        ],
        strengths=[
            3.0,
            3.0,
            7.0,
            4.0,
            3.0,
            3.0,
        ],
    )

    return analyze_persistent_ensemble_errors(
        labels=labels,
        sample_ids=sample_ids,
        ensemble_logits={
            "42": seed42,
            "123": seed123,
            "2026": seed2026,
        },
        id2label=id2label,
        fine_to_coarse=(
            fine_to_coarse
        ),
        top_k=2,
    )


def test_persistent_analysis_separates_outcomes():
    result = build_analysis()

    assert (
        result.total_samples
        == 6
    )

    assert (
        result.stable_correct
        == 1
    )

    assert (
        result.persistent_errors
        == 3
    )

    assert (
        result.mixed_outcomes
        == 2
    )

    assert (
        result.stable_correct_rate
        == pytest.approx(
            1.0 / 6.0
        )
    )

    assert (
        result.persistent_error_rate
        == pytest.approx(
            3.0 / 6.0
        )
    )

    assert (
        result.mixed_outcome_rate
        == pytest.approx(
            2.0 / 6.0
        )
    )


def test_persistent_analysis_separates_prediction_consensus():
    result = build_analysis()

    assert (
        result.persistent_same_wrong_prediction
        == 2
    )

    assert (
        result.persistent_varying_wrong_prediction
        == 1
    )


def test_persistent_analysis_separates_coarse_error_types():
    result = build_analysis()

    assert (
        result.persistent_fine_only_errors
        == 1
    )

    assert (
        result.persistent_cross_coarse_errors
        == 1
    )

    assert (
        result.persistent_mixed_coarse_errors
        == 1
    )


def test_persistent_analysis_keeps_label_support():
    result = build_analysis()

    by_label = {
        summary.label: summary
        for summary
        in result.label_summary
    }

    assert (
        by_label[
            "E10"
        ].support
        == 2
    )

    assert (
        by_label[
            "E10"
        ].stable_correct
        == 1
    )

    assert (
        by_label[
            "E10"
        ].mixed_outcome
        == 1
    )

    assert (
        by_label[
            "E11"
        ].support
        == 2
    )

    assert (
        by_label[
            "E11"
        ].persistent_error
        == 1
    )


def test_highest_confidence_persistent_error_is_ranked_first():
    result = build_analysis()

    assert (
        result
        .highest_confidence_persistent_errors[
            0
        ]
        .sample_id
        == "s2"
    )

    assert (
        result
        .highest_confidence_persistent_errors[
            0
        ]
        .coarse_status
        == "CROSS_COARSE"
    )


def test_persistent_confusion_contains_consensus_errors():
    result = build_analysis()

    pairs = {
        (
            confusion.true_label,
            confusion.predicted_label,
        ): confusion.count
        for confusion
        in result.top_persistent_confusions
    }

    assert (
        pairs[
            (
                "E11",
                "E10",
            )
        ]
        == 1
    )

    assert (
        pairs[
            (
                "E20",
                "E10",
            )
        ]
        == 1
    )


def test_confidence_summary_reports_quantiles():
    result = summarize_confidences(
        [
            0.2,
            0.4,
            0.6,
            0.8,
        ]
    )

    assert result.count == 4

    assert result.mean == pytest.approx(
        0.5
    )

    assert result.p50 == pytest.approx(
        0.5
    )

    assert result.maximum == pytest.approx(
        0.8
    )


def test_confidence_summary_supports_empty_input():
    result = summarize_confidences(
        []
    )

    assert result.count == 0
    assert result.mean is None
    assert result.p95 is None


def test_analysis_rejects_wrong_seed_count():
    labels = torch.tensor(
        [
            0,
        ],
        dtype=torch.long,
    )

    logits = torch.tensor(
        [
            [
                1.0,
                0.0,
            ],
        ],
        dtype=torch.float32,
    )

    with pytest.raises(
        ValueError,
        match="exactly 3",
    ):
        analyze_persistent_ensemble_errors(
            labels=labels,
            sample_ids=[
                "s0"
            ],
            ensemble_logits={
                "42": logits,
                "123": logits,
            },
            id2label={
                0: "E10",
                1: "E11",
            },
            fine_to_coarse={
                "E10": "A",
                "E11": "A",
            },
        )


def test_analysis_rejects_logit_shape_mismatch():
    labels = torch.tensor(
        [
            0,
        ],
        dtype=torch.long,
    )

    valid = torch.tensor(
        [
            [
                1.0,
                0.0,
            ],
        ],
        dtype=torch.float32,
    )

    invalid = torch.tensor(
        [
            [
                1.0,
                0.0,
                0.0,
            ],
        ],
        dtype=torch.float32,
    )

    with pytest.raises(
        ValueError,
        match="shape mismatch",
    ):
        analyze_persistent_ensemble_errors(
            labels=labels,
            sample_ids=[
                "s0"
            ],
            ensemble_logits={
                "42": valid,
                "123": invalid,
                "2026": valid,
            },
            id2label={
                0: "E10",
                1: "E11",
            },
            fine_to_coarse={
                "E10": "A",
                "E11": "A",
            },
        )