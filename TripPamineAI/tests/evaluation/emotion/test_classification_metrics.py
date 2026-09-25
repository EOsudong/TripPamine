from trippamine_ai.evaluation.emotion.classification_metrics import (
    EmotionClassificationEvaluator,
)


def test_perfect_predictions():
    true_labels = [
        "E10",
        "E10",
        "E20",
        "E20",
        "E60",
        "E60",
    ]

    predictions = list(
        true_labels
    )

    mapping = {
        "E10": "분노",
        "E20": "슬픔",
        "E60": "기쁨",
    }

    result = (
        EmotionClassificationEvaluator()
        .evaluate(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                predictions
            ),
            fine_to_coarse=mapping,
        )
    )

    assert (
        result.fine.aggregate.accuracy
        == 1.0
    )

    assert (
        result.fine.aggregate.macro_f1
        == 1.0
    )

    assert (
        result.coarse.aggregate.macro_f1
        == 1.0
    )


def test_coarse_prediction_can_be_correct():
    true_labels = [
        "E10",
        "E11",
    ]

    predictions = [
        "E11",
        "E10",
    ]

    mapping = {
        "E10": "분노",
        "E11": "분노",
    }

    result = (
        EmotionClassificationEvaluator()
        .evaluate(
            true_labels=(
                true_labels
            ),
            predicted_labels=(
                predictions
            ),
            fine_to_coarse=mapping,
        )
    )

    assert (
        result.fine.aggregate.accuracy
        == 0.0
    )

    assert (
        result.coarse.aggregate.accuracy
        == 1.0
    )