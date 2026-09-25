from __future__ import annotations

import threading

from datetime import (
    datetime,
    timezone,
)

from pydantic import BaseModel

from trippamine_ai.runtime.emotion.locked_context_classifier import (
    ContextMode,
    EmotionRuntimeResult,
)


class EmotionRuntimeMetricsSnapshot(
    BaseModel,
):
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


class EmotionRuntimeMetrics:
    def __init__(
            self,
    ) -> None:
        self._lock = (
            threading.Lock()
        )

        self._started_at = (
            datetime.now(
                timezone.utc
            )
        )

        self._total_requests = 0
        self._successful_requests = 0
        self._failed_requests = 0

        self._first2_requests = 0
        self._fallback_requests = 0

        self._eligible_requests = 0

        self._latency_total_ms = 0.0
        self._latency_max_ms = 0.0

        self._first2_latency_total_ms = 0.0
        self._first2_latency_count = 0

        self._fallback_latency_total_ms = 0.0
        self._fallback_latency_count = 0

        self._eligible_margin_count = 0
        self._eligible_margin_total = 0.0

        self._eligible_margin_min = None
        self._eligible_margin_max = None

    def record_request(
            self,
    ) -> None:
        with self._lock:
            self._total_requests += 1

    def record_failure(
            self,
    ) -> None:
        with self._lock:
            self._failed_requests += 1

    def record_success(
            self,
            result: EmotionRuntimeResult,
    ) -> None:
        with self._lock:
            self._successful_requests += 1

            latency_ms = float(
                result.latency_ms
            )

            self._latency_total_ms += (
                latency_ms
            )

            self._latency_max_ms = max(
                self._latency_max_ms,
                latency_ms,
            )

            if (
                    result.context_mode
                    == ContextMode.FULL_FALLBACK
            ):
                self._fallback_requests += 1

                self._fallback_latency_count += 1

                self._fallback_latency_total_ms += (
                    latency_ms
                )

            else:
                self._first2_requests += 1

                self._first2_latency_count += 1

                self._first2_latency_total_ms += (
                    latency_ms
                )

            if (
                    result.human_turn_count
                    > 2
            ):
                self._eligible_requests += 1

                margin = float(
                    result.first2_margin
                )

                self._eligible_margin_count += 1

                self._eligible_margin_total += (
                    margin
                )

                if (
                        self._eligible_margin_min
                        is None
                        or margin
                        < self._eligible_margin_min
                ):
                    self._eligible_margin_min = (
                        margin
                    )

                if (
                        self._eligible_margin_max
                        is None
                        or margin
                        > self._eligible_margin_max
                ):
                    self._eligible_margin_max = (
                        margin
                    )

    def snapshot(
            self,
    ) -> EmotionRuntimeMetricsSnapshot:
        with self._lock:
            successful = (
                self._successful_requests
            )

            eligible = (
                self._eligible_requests
            )

            fallback = (
                self._fallback_requests
            )

            average_latency = (
                self._latency_total_ms
                / successful
                if successful
                else 0.0
            )

            first2_average = (
                self._first2_latency_total_ms
                / self._first2_latency_count
                if self._first2_latency_count
                else 0.0
            )

            fallback_average = (
                self._fallback_latency_total_ms
                / self._fallback_latency_count
                if self._fallback_latency_count
                else 0.0
            )

            eligible_margin_mean = (
                self._eligible_margin_total
                / self._eligible_margin_count
                if self._eligible_margin_count
                else None
            )

            return (
                EmotionRuntimeMetricsSnapshot(
                    started_at=(
                        self._started_at
                    ),
                    total_requests=(
                        self._total_requests
                    ),
                    successful_requests=(
                        successful
                    ),
                    failed_requests=(
                        self._failed_requests
                    ),
                    first2_requests=(
                        self._first2_requests
                    ),
                    full_fallback_requests=(
                        fallback
                    ),
                    fallback_eligible_requests=(
                        eligible
                    ),
                    fallback_rate_overall=(
                        fallback
                        / successful
                        if successful
                        else 0.0
                    ),
                    fallback_rate_eligible=(
                        fallback
                        / eligible
                        if eligible
                        else 0.0
                    ),
                    average_latency_ms=(
                        average_latency
                    ),
                    max_latency_ms=(
                        self._latency_max_ms
                    ),
                    first2_average_latency_ms=(
                        first2_average
                    ),
                    full_fallback_average_latency_ms=(
                        fallback_average
                    ),
                    eligible_margin_mean=(
                        eligible_margin_mean
                    ),
                    eligible_margin_min=(
                        self._eligible_margin_min
                    ),
                    eligible_margin_max=(
                        self._eligible_margin_max
                    ),
                )
            )