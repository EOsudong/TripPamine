import pytest

from trippamine_ai.evaluation.emotion.classification_error_analysis import (
    EmotionClassificationErrorAnalyzer,
)


def test_same_and_cross_coarse_errors():
    true_labels = [
        "E10",
        "E11",
        "E20",
        "E20",
    ]

    predicted_labels = [
        "E11",
        "E20",
        "E20",
        "E10",
    ]

    mapping = {
        "E10": "분노",
        "E11": "분노",
        "E20": "슬픔",
    }

    result = (
        EmotionClassificationErrorAnalyzer()
        .analyze(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                predicted_labels
            ),
            fine_to_coarse=mapping,
        )
    )

    assert (
        result.total_samples
        == 4
    )

    assert (
        result.correct_predictions
        == 1
    )

    assert (
        result.incorrect_predictions
        == 3
    )

    assert (
        result.error_boundary
        .same_coarse_errors
        == 1
    )

    assert (
        result.error_boundary
        .cross_coarse_errors
        == 2
    )

    assert (
        result.error_boundary
        .same_coarse_error_rate
        == pytest.approx(
            1 / 3
        )
    )

    assert (
        result.error_boundary
        .cross_coarse_error_rate
        == pytest.approx(
            2 / 3
        )
    )


def test_perfect_predictions_have_no_errors():
    true_labels = [
        "E10",
        "E20",
        "E60",
    ]

    mapping = {
        "E10": "분노",
        "E20": "슬픔",
        "E60": "기쁨",
    }

    result = (
        EmotionClassificationErrorAnalyzer()
        .analyze(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                list(
                    true_labels
                )
            ),
            fine_to_coarse=mapping,
        )
    )

    assert (
        result.incorrect_predictions
        == 0
    )

    assert (
        result.error_boundary
        .same_coarse_errors
        == 0
    )

    assert (
        result.error_boundary
        .cross_coarse_errors
        == 0
    )

    assert (
        result.error_boundary
        .same_coarse_error_rate
        == 0.0
    )

    assert (
        result.error_boundary
        .cross_coarse_error_rate
        == 0.0
    )


def test_label_ranking():
    true_labels = [
        "E10",
        "E10",
        "E20",
        "E20",
    ]

    predicted_labels = [
        "E10",
        "E10",
        "E10",
        "E20",
    ]

    mapping = {
        "E10": "분노",
        "E20": "슬픔",
    }

    result = (
        EmotionClassificationErrorAnalyzer()
        .analyze(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                predicted_labels
            ),
            fine_to_coarse=mapping,
            top_k=1,
        )
    )

    assert (
        result.label_ranking
        .best_labels[0]
        .label
        == "E10"
    )

    assert (
        result.label_ranking
        .worst_labels[0]
        .label
        == "E20"
    )


def test_confusion_matrix_shape():
    true_labels = [
        "E10",
        "E10",
        "E20",
        "E20",
    ]

    predicted_labels = [
        "E10",
        "E20",
        "E20",
        "E10",
    ]

    mapping = {
        "E10": "분노",
        "E20": "슬픔",
    }

    result = (
        EmotionClassificationErrorAnalyzer()
        .analyze(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                predicted_labels
            ),
            fine_to_coarse=mapping,
        )
    )

    matrix = (
        result
        .fine_confusion_matrix
    )

    assert len(
        matrix.labels
    ) == 2

    assert len(
        matrix.values
    ) == 2

    assert sum(
        sum(row)
        for row
        in matrix.values
    ) == 4


def test_invalid_top_k_is_rejected():
    with pytest.raises(
        ValueError,
        match="top_k",
    ):
        (
            EmotionClassificationErrorAnalyzer()
            .analyze(
                true_labels=[
                    "E10",
                ],
                predicted_labels=[
                    "E10",
                ],
                fine_to_coarse={
                    "E10": "분노",
                },
                top_k=0,
            )
        )