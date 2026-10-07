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

    @property
    def model_version(
            self,
    ):
        return "fake-model"

    def predict(
            self,
            text,
    ):
        return (
            self.predictions[
                text
            ]
        )


def make_prediction(
        *,
        margin,
        label="E30",
):
    return EnsemblePrediction(
        fine_label=label,
        fine_label_name="불안",
        coarse_label="불안",
        confidence=0.7,
        margin=margin,
    )


def test_metrics_endpoint_starts_empty():
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
            "/api/v1/emotion/metrics"
        )

    assert (
        response.status_code
        == 200
    )

    body = response.json()

    assert (
        body[
            "total_requests"
        ]
        == 0
    )

    assert (
        body[
            "margin_threshold"
        ]
        == pytest.approx(
            0.16
        )
    )


def test_metrics_updates_after_prediction():
    first2 = (
        "첫 번째\n"
        "두 번째"
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
        predict_response = (
            client.post(
                "/api/v1/emotion/predict",
                json={
                    "human_turns": [
                        "첫 번째",
                        "두 번째",
                        "세 번째",
                    ],
                },
            )
        )

        metrics_response = (
            client.get(
                "/api/v1/emotion/metrics"
            )
        )

    assert (
        predict_response.status_code
        == 200
    )

    assert (
        metrics_response.status_code
        == 200
    )

    body = (
        metrics_response.json()
    )

    assert (
        body[
            "total_requests"
        ]
        == 1
    )

    assert (
        body[
            "successful_requests"
        ]
        == 1
    )

    assert (
        body[
            "failed_requests"
        ]
        == 0
    )

    assert (
        body[
            "first2_requests"
        ]
        == 1
    )

    assert (
        body[
            "full_fallback_requests"
        ]
        == 0
    )

    assert (
        body[
            "fallback_eligible_requests"
        ]
        == 1
    )

    assert (
        body[
            "fallback_rate_eligible"
        ]
        == pytest.approx(
            0.0
        )
    )

    assert (
        body[
            "eligible_margin_mean"
        ]
        == pytest.approx(
            0.40
        )
    )