import pytest

from trippamine_ai.runtime.emotion.locked_context_classifier import (
    ContextMode,
    EmotionRuntimeResult,
)
from trippamine_ai.runtime.emotion.metrics import (
    EmotionRuntimeMetrics,
)


def make_result(
        *,
        mode=ContextMode.FIRST2,
        fallback=False,
        turns=3,
        margin=0.4,
        latency_ms=100.0,
):
    return EmotionRuntimeResult(
        fine_label="E30",
        fine_label_name="불안",
        coarse_label="불안",
        confidence=0.7,
        first2_confidence=0.7,
        first2_margin=margin,
        context_mode=mode,
        fallback_triggered=fallback,
        human_turn_count=turns,
        turns_used=(
            turns
            if fallback
            else min(
                2,
                turns,
            )
        ),
        policy_version=(
            "q17-locked-margin-0.16-v1"
        ),
        model_version=(
            "test-model"
        ),
        latency_ms=latency_ms,
    )


def test_metrics_initial_state():
    metrics = (
        EmotionRuntimeMetrics()
    )

    snapshot = (
        metrics.snapshot()
    )

    assert (
        snapshot.total_requests
        == 0
    )

    assert (
        snapshot.successful_requests
        == 0
    )

    assert (
        snapshot.failed_requests
        == 0
    )

    assert (
        snapshot.fallback_rate_overall
        == 0.0
    )

    assert (
        snapshot.fallback_rate_eligible
        == 0.0
    )


def test_metrics_tracks_first2_and_fallback():
    metrics = (
        EmotionRuntimeMetrics()
    )

    metrics.record_request()

    metrics.record_success(
        make_result(
            mode=(
                ContextMode.FIRST2
            ),
            fallback=False,
            turns=3,
            margin=0.40,
            latency_ms=100.0,
        )
    )

    metrics.record_request()

    metrics.record_success(
        make_result(
            mode=(
                ContextMode
                .FULL_FALLBACK
            ),
            fallback=True,
            turns=4,
            margin=0.10,
            latency_ms=200.0,
        )
    )

    snapshot = (
        metrics.snapshot()
    )

    assert (
        snapshot.total_requests
        == 2
    )

    assert (
        snapshot.successful_requests
        == 2
    )

    assert (
        snapshot.first2_requests
        == 1
    )

    assert (
        snapshot.full_fallback_requests
        == 1
    )

    assert (
        snapshot.fallback_eligible_requests
        == 2
    )

    assert (
        snapshot.fallback_rate_overall
        == pytest.approx(
            0.5
        )
    )

    assert (
        snapshot.fallback_rate_eligible
        == pytest.approx(
            0.5
        )
    )

    assert (
        snapshot.average_latency_ms
        == pytest.approx(
            150.0
        )
    )

    assert (
        snapshot.first2_average_latency_ms
        == pytest.approx(
            100.0
        )
    )

    assert (
        snapshot.full_fallback_average_latency_ms
        == pytest.approx(
            200.0
        )
    )

    assert (
        snapshot.eligible_margin_mean
        == pytest.approx(
            0.25
        )
    )

    assert (
        snapshot.eligible_margin_min
        == pytest.approx(
            0.10
        )
    )

    assert (
        snapshot.eligible_margin_max
        == pytest.approx(
            0.40
        )
    )


def test_metrics_tracks_failure():
    metrics = (
        EmotionRuntimeMetrics()
    )

    metrics.record_request()
    metrics.record_failure()

    snapshot = (
        metrics.snapshot()
    )

    assert (
        snapshot.total_requests
        == 1
    )

    assert (
        snapshot.failed_requests
        == 1
    )

    assert (
        snapshot.successful_requests
        == 0
    )