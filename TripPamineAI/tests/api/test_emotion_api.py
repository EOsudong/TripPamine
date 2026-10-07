import pytest

from fastapi.testclient import (
    TestClient,
)

from trippamine_ai.api.app import (
    create_app,
)
from trippamine_ai.runtime.emotion.locked_context_classifier import (
    EnsemblePrediction,
    LockedContextEmotionClassifier,
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
        return (
            "fake-ko-kc-v1"
        )

    def predict(
            self,
            text,
    ):
        self.calls.append(
            text
        )

        return (
            self.predictions[
                text
            ]
        )


def make_prediction(
        label="E30",
        label_name="불안",
        coarse_label="불안",
        confidence=0.7,
        margin=0.3,
):
    return EnsemblePrediction(
        fine_label=label,
        fine_label_name=(
            label_name
        ),
        coarse_label=(
            coarse_label
        ),
        confidence=(
            confidence
        ),
        margin=(
            margin
        ),
    )


def test_health_reports_locked_policy():
    predictor = FakePredictor(
        {}
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    app = create_app(
        classifier_factory=(
            lambda: classifier
        )
    )

    with TestClient(
        app
    ) as client:
        response = client.get(
            "/health"
        )

    assert (
        response.status_code
        == 200
    )

    body = response.json()

    assert (
        body[
            "status"
        ]
        == "UP"
    )

    assert (
        body[
            "emotion_classifier"
        ]
        == "UP"
    )

    assert (
        body[
            "margin_threshold"
        ]
        == pytest.approx(
            0.16
        )
    )

    assert (
        body[
            "policy_version"
        ]
        == (
            "q17-locked-"
            "margin-0.16-v1"
        )
    )

    assert (
        body[
            "model_version"
        ]
        == "fake-ko-kc-v1"
    )


def test_predict_high_margin_uses_first2():
    first2 = (
        "요즘 마음이 불안해.\n"
        "결과가 걱정돼."
    )

    predictor = FakePredictor(
        {
            first2: (
                make_prediction(
                    margin=0.40
                )
            ),
        }
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    app = create_app(
        classifier_factory=(
            lambda: classifier
        )
    )

    with TestClient(
        app
    ) as client:
        response = client.post(
            "/api/v1/emotion/predict",
            json={
                "human_turns": [
                    "요즘 마음이 불안해.",
                    "결과가 걱정돼.",
                    "계속 신경 쓰여.",
                ],
            },
        )

    assert (
        response.status_code
        == 200
    )

    body = response.json()

    assert (
        body[
            "context_mode"
        ]
        == "FIRST2"
    )

    assert (
        body[
            "fallback_triggered"
        ]
        is False
    )

    assert (
        body[
            "human_turn_count"
        ]
        == 3
    )

    assert (
        body[
            "turns_used"
        ]
        == 2
    )

    assert predictor.calls == [
        first2,
    ]


def test_predict_low_margin_uses_full_fallback():
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
                    label="E30",
                    label_name="불안",
                    coarse_label="불안",
                    confidence=0.35,
                    margin=0.10,
                )
            ),
            full: (
                make_prediction(
                    label="E64",
                    label_name="만족스러운",
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

    app = create_app(
        classifier_factory=(
            lambda: classifier
        )
    )

    with TestClient(
        app
    ) as client:
        response = client.post(
            "/api/v1/emotion/predict",
            json={
                "human_turns": [
                    "첫 번째",
                    "두 번째",
                    "세 번째",
                ],
            },
        )

    assert (
        response.status_code
        == 200
    )

    body = response.json()

    assert (
        body[
            "context_mode"
        ]
        == "FULL_FALLBACK"
    )

    assert (
        body[
            "fallback_triggered"
        ]
        is True
    )

    assert (
        body[
            "fine_label"
        ]
        == "E64"
    )

    assert (
        body[
            "first2_margin"
        ]
        == pytest.approx(
            0.10
        )
    )

    assert (
        body[
            "turns_used"
        ]
        == 3
    )

    assert predictor.calls == [
        first2,
        full,
    ]


def test_predict_rejects_empty_turn_list():
    predictor = FakePredictor(
        {}
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    app = create_app(
        classifier_factory=(
            lambda: classifier
        )
    )

    with TestClient(
        app
    ) as client:
        response = client.post(
            "/api/v1/emotion/predict",
            json={
                "human_turns": [],
            },
        )

    assert (
        response.status_code
        == 422
    )


def test_predict_rejects_blank_turn():
    predictor = FakePredictor(
        {}
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    app = create_app(
        classifier_factory=(
            lambda: classifier
        )
    )

    with TestClient(
        app
    ) as client:
        response = client.post(
            "/api/v1/emotion/predict",
            json={
                "human_turns": [
                    "   ",
                ],
            },
        )

    assert (
        response.status_code
        == 422
    )


def test_classifier_factory_runs_once_per_lifespan():
    calls = []

    predictor = FakePredictor(
        {}
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    def factory():
        calls.append(
            "load"
        )

        return classifier

    app = create_app(
        classifier_factory=(
            factory
        )
    )

    with TestClient(
        app
    ) as client:
        first = client.get(
            "/health"
        )

        second = client.get(
            "/health"
        )

        assert (
            first.status_code
            == 200
        )

        assert (
            second.status_code
            == 200
        )

    assert calls == [
        "load",
    ]