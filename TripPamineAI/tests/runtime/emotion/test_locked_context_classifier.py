import pytest
import torch

from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.runtime.emotion.locked_context_classifier import (
    ContextMode,
    EnsemblePrediction,
    LOCKED_MARGIN_THRESHOLD,
    LockedContextEmotionClassifier,
    build_ensemble_prediction,
    build_first2_text,
    build_full_text,
    normalize_human_turns,
)


class FakePredictor:
    def __init__(
            self,
            predictions,
    ):
        self.predictions = (
            predictions
        )

        self.calls = []

    @property
    def model_version(
            self,
    ):
        return "fake-model-v1"

    def predict(
            self,
            text,
    ):
        self.calls.append(
            text
        )

        try:
            return (
                self.predictions[
                    text
                ]
            )

        except KeyError as exc:
            raise AssertionError(
                "Unexpected predictor "
                f"input: {text}"
            ) from exc


def make_prediction(
        label="E10",
        confidence=0.70,
        margin=0.30,
):
    return EnsemblePrediction(
        fine_label=label,
        fine_label_name="분노",
        coarse_label="분노",
        confidence=confidence,
        margin=margin,
    )


def test_locked_threshold_is_016():
    assert (
        LOCKED_MARGIN_THRESHOLD
        == pytest.approx(
            0.16
        )
    )


def test_normalize_turns_removes_empty_values():
    assert (
        normalize_human_turns(
            [
                " 첫 번째 ",
                "",
                "   ",
                "두 번째",
            ]
        )
        == [
            "첫 번째",
            "두 번째",
        ]
    )


def test_normalize_turns_rejects_all_empty():
    with pytest.raises(
        ValueError,
        match="At least one",
    ):
        normalize_human_turns(
            [
                "",
                " ",
            ]
        )


def test_build_context_text_contract():
    turns = [
        "첫 번째",
        "두 번째",
        "세 번째",
    ]

    assert (
        build_first2_text(
            turns
        )
        == (
            "첫 번째\n"
            "두 번째"
        )
    )

    assert (
        build_full_text(
            turns
        )
        == (
            "첫 번째\n"
            "두 번째\n"
            "세 번째"
        )
    )


def test_one_turn_never_falls_back():
    predictor = FakePredictor(
        {
            "첫 번째": (
                make_prediction(
                    margin=0.01
                )
            ),
        }
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    result = classifier.predict(
        [
            "첫 번째",
        ]
    )

    assert (
        result.context_mode
        == ContextMode.FIRST2
    )

    assert (
        result.fallback_triggered
        is False
    )

    assert predictor.calls == [
        "첫 번째",
    ]


def test_two_turns_never_fall_back():
    text = (
        "첫 번째\n"
        "두 번째"
    )

    predictor = FakePredictor(
        {
            text: (
                make_prediction(
                    margin=0.01
                )
            ),
        }
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    result = classifier.predict(
        [
            "첫 번째",
            "두 번째",
        ]
    )

    assert (
        result.context_mode
        == ContextMode.FIRST2
    )

    assert predictor.calls == [
        text,
    ]


def test_high_margin_with_extra_context_uses_first2():
    first2 = (
        "첫 번째\n"
        "두 번째"
    )

    predictor = FakePredictor(
        {
            first2: (
                make_prediction(
                    margin=0.30
                )
            ),
        }
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    result = classifier.predict(
        [
            "첫 번째",
            "두 번째",
            "세 번째",
        ]
    )

    assert (
        result.fallback_triggered
        is False
    )

    assert (
        result.context_mode
        == ContextMode.FIRST2
    )

    assert (
        result.turns_used
        == 2
    )

    assert predictor.calls == [
        first2,
    ]


def test_margin_equal_to_threshold_falls_back():
    first2 = (
        "첫 번째\n"
        "두 번째"
    )

    full = (
        "첫 번째\n"
        "두 번째\n"
        "세 번째"
    )

    predictor = FakePredictor(
        {
            first2: (
                make_prediction(
                    label="E10",
                    confidence=0.40,
                    margin=0.16,
                )
            ),
            full: (
                make_prediction(
                    label="E60",
                    confidence=0.80,
                    margin=0.50,
                )
            ),
        }
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    result = classifier.predict(
        [
            "첫 번째",
            "두 번째",
            "세 번째",
        ]
    )

    assert (
        result.fallback_triggered
        is True
    )

    assert (
        result.context_mode
        == (
            ContextMode
            .FULL_FALLBACK
        )
    )

    assert (
        result.fine_label
        == "E60"
    )

    assert (
        result.first2_margin
        == pytest.approx(
            0.16
        )
    )

    assert predictor.calls == [
        first2,
        full,
    ]


def test_low_margin_fallback_uses_full_result():
    first2 = (
        "A\nB"
    )

    full = (
        "A\nB\nC"
    )

    predictor = FakePredictor(
        {
            first2: (
                make_prediction(
                    label="E10",
                    confidence=0.35,
                    margin=0.05,
                )
            ),
            full: (
                EnsemblePrediction(
                    fine_label="E64",
                    fine_label_name=(
                        "만족스러운"
                    ),
                    coarse_label="기쁨",
                    confidence=0.82,
                    margin=0.60,
                )
            ),
        }
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    result = classifier.predict(
        [
            "A",
            "B",
            "C",
        ]
    )

    assert (
        result.fine_label
        == "E64"
    )

    assert (
        result.confidence
        == pytest.approx(
            0.82
        )
    )

    assert (
        result.first2_confidence
        == pytest.approx(
            0.35
        )
    )

    assert (
        result.human_turn_count
        == 3
    )

    assert (
        result.turns_used
        == 3
    )


def test_threshold_cannot_be_changed():
    predictor = FakePredictor(
        {}
    )

    with pytest.raises(
        ValueError,
        match=(
            "must remain 0.16"
        ),
    ):
        LockedContextEmotionClassifier(
            predictor,
            margin_threshold=0.15,
        )


def test_build_ensemble_prediction_calculates_margin():
    mapping = (
        EmotionLabelMapping()
    )

    logits = torch.zeros(
        (
            1,
            60,
        ),
        dtype=torch.float32,
    )

    logits[
        0,
        0,
    ] = 5.0

    logits[
        0,
        1,
    ] = 3.0

    result = (
        build_ensemble_prediction(
            logits=logits,
            label_mapping=mapping,
        )
    )

    assert (
        result.fine_label
        == mapping.decode(
            0
        )
    )

    assert (
        0.0
        <= result.confidence
        <= 1.0
    )

    assert (
        result.margin
        > 0.0
    )