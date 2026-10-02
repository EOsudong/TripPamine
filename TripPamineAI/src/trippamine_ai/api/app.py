from __future__ import annotations

import asyncio
import os

from collections.abc import (
    Callable,
)
from contextlib import (
    asynccontextmanager,
)
from pathlib import Path

from fastapi import FastAPI
from fastapi import HTTPException
from fastapi import Request

from starlette.concurrency import (
    run_in_threadpool,
)

from trippamine_ai.api.schemas import (
    EmotionMetricsResponse,
    EmotionPredictRequest,
    HealthResponse,
)
from trippamine_ai.runtime.emotion.locked_context_classifier import (
    EmotionRuntimeResult,
    LayerNormKoKcEnsemblePredictor,
    LockedContextEmotionClassifier,
)
from trippamine_ai.runtime.emotion.metrics import (
    EmotionRuntimeMetrics,
)


DEFAULT_KO_MODEL_DIR = Path(
    "artifacts/emotion/transformer/"
    "t0-q13-a-layernorm-es1-"
    "b32-lr4e5-e4-ml96-s42-v1/"
    "best-model"
)

DEFAULT_KC_MODEL_DIR = Path(
    "artifacts/emotion/transformer/"
    "t0-q14-a-kcelectra-v2022-"
    "layernorm-es1-b32-lr4e5-"
    "e4-ml96-s42-v1/"
    "best-model"
)

DEFAULT_DEVICE = "cuda"


ClassifierFactory = Callable[
    [],
    LockedContextEmotionClassifier,
]


def resolve_model_path(
        env_name: str,
        default_path: Path,
) -> Path:
    configured = os.getenv(
        env_name
    )

    if configured:
        return (
            Path(
                configured
            )
            .expanduser()
            .resolve()
        )

    return (
        default_path
        .expanduser()
        .resolve()
    )


def build_default_classifier(
) -> LockedContextEmotionClassifier:
    ko_model_dir = (
        resolve_model_path(
            env_name=(
                "TRIPPAMINE_AI_KO_MODEL_DIR"
            ),
            default_path=(
                DEFAULT_KO_MODEL_DIR
            ),
        )
    )

    kc_model_dir = (
        resolve_model_path(
            env_name=(
                "TRIPPAMINE_AI_KC_MODEL_DIR"
            ),
            default_path=(
                DEFAULT_KC_MODEL_DIR
            ),
        )
    )

    device = os.getenv(
        "TRIPPAMINE_AI_DEVICE",
        DEFAULT_DEVICE,
    )

    predictor = (
        LayerNormKoKcEnsemblePredictor(
            ko_model_dir=(
                ko_model_dir
            ),
            kc_model_dir=(
                kc_model_dir
            ),
            device=device,
        )
    )

    return (
        LockedContextEmotionClassifier(
            predictor
        )
    )


def create_app(
        classifier_factory: (
            ClassifierFactory
        ) = (
            build_default_classifier
        ),
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(
            app: FastAPI,
    ):
        classifier = (
            classifier_factory()
        )

        app.state.emotion_classifier = (
            classifier
        )

        app.state.emotion_metrics = (
            EmotionRuntimeMetrics()
        )

        app.state.emotion_inference_lock = (
            asyncio.Lock()
        )

        yield

        app.state.emotion_classifier = (
            None
        )

        app.state.emotion_metrics = (
            None
        )

        app.state.emotion_inference_lock = (
            None
        )

    application = FastAPI(
        title="TripPamine AI",
        version="0.1.0",
        lifespan=lifespan,
    )

    @application.get(
        "/health",
        response_model=(
            HealthResponse
        ),
    )
    async def health(
            request: Request,
    ) -> HealthResponse:
        classifier = (
            request.app.state
            .emotion_classifier
        )

        if classifier is None:
            raise HTTPException(
                status_code=503,
                detail=(
                    "Emotion classifier "
                    "is not ready."
                ),
            )

        return HealthResponse(
            status="UP",
            emotion_classifier="UP",
            policy_version=(
                classifier
                .policy_version
            ),
            model_version=(
                classifier
                .model_version
            ),
            margin_threshold=(
                classifier
                .margin_threshold
            ),
        )

    @application.get(
        "/api/v1/emotion/metrics",
        response_model=(
            EmotionMetricsResponse
        ),
    )
    async def emotion_metrics(
            request: Request,
    ) -> EmotionMetricsResponse:
        classifier = (
            request.app.state
            .emotion_classifier
        )

        metrics = (
            request.app.state
            .emotion_metrics
        )

        if (
                classifier is None
                or metrics is None
        ):
            raise HTTPException(
                status_code=503,
                detail=(
                    "Emotion runtime "
                    "is not ready."
                ),
            )

        snapshot = (
            metrics.snapshot()
        )

        return EmotionMetricsResponse(
            policy_version=(
                classifier
                .policy_version
            ),
            model_version=(
                classifier
                .model_version
            ),
            margin_threshold=(
                classifier
                .margin_threshold
            ),
            **snapshot.model_dump(),
        )

    @application.post(
        "/api/v1/emotion/predict",
        response_model=(
            EmotionRuntimeResult
        ),
    )
    async def predict_emotion(
            payload: (
                EmotionPredictRequest
            ),
            request: Request,
    ) -> EmotionRuntimeResult:
        classifier = (
            request.app.state
            .emotion_classifier
        )

        metrics = (
            request.app.state
            .emotion_metrics
        )

        inference_lock = (
            request.app.state
            .emotion_inference_lock
        )

        if (
                classifier is None
                or metrics is None
                or inference_lock is None
        ):
            raise HTTPException(
                status_code=503,
                detail=(
                    "Emotion classifier "
                    "is not ready."
                ),
            )

        metrics.record_request()

        try:
            async with (
                    inference_lock
            ):
                result = await (
                    run_in_threadpool(
                        classifier.predict,
                        payload.human_turns,
                    )
                )

            metrics.record_success(
                result
            )

        except ValueError as error:
            metrics.record_failure()

            raise HTTPException(
                status_code=422,
                detail=str(
                    error
                ),
            ) from error

        except RuntimeError as error:
            metrics.record_failure()

            raise HTTPException(
                status_code=503,
                detail=(
                    "Emotion inference "
                    f"failed: {error}"
                ),
            ) from error

        except Exception:
            metrics.record_failure()
            raise

        return result

    return application


app = create_app()