from datetime import datetime
from typing import Annotated
from typing import Literal

from pydantic import BaseModel
from pydantic import Field
from pydantic import StringConstraints


HumanTurn = Annotated[
    str,
    StringConstraints(
        strip_whitespace=True,
        min_length=1,
        max_length=4000,
    ),
]


class EmotionPredictRequest(
    BaseModel,
):
    human_turns: list[
        HumanTurn
    ] = Field(
        min_length=1,
        max_length=50,
    )


class HealthResponse(
    BaseModel,
):
    status: Literal[
        "UP"
    ]

    emotion_classifier: Literal[
        "UP"
    ]

    policy_version: str
    model_version: str

    margin_threshold: float


class EmotionMetricsResponse(
    BaseModel,
):
    policy_version: str
    model_version: str
    margin_threshold: float

    started_at: datetime

    total_requests: int
    successful_requests: int
    failed_requests: int

    first2_requests: int
    full_fallback_requests: int

    fallback_eligible_requests: int

    fallback_rate_overall: float
    fallback_rate_eligible: float

    average_latency_ms: float
    max_latency_ms: float

    first2_average_latency_ms: float
    full_fallback_average_latency_ms: float

    eligible_margin_mean: float | None
    eligible_margin_min: float | None
    eligible_margin_max: float | None