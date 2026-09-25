import pytest

from scripts.emotion.evaluate_locked_context_policy_test import (
    EXPECTED_TEST_SHA256,
    summary_stats,
    validate_locked_policy_contract,
    validate_sealed_test_contract,
)


def create_locked_policy_report():
    return {
        "source": {
            "context_report": {
                "file": "context.json",
                "sha256": "abc123",
            },
        },
        "protocol": {
            "seed": 42,
            "test_split_used": False,
            "threshold_tuning": False,
            "base_view": "first2",
            "fallback_view": "full",
            "signal": "margin",
            "fixed_margin_threshold": 0.16,
        },
    }


def create_sealed_test_report():
    return {
        "protocol": {
            "test_split_used": True,
            "test_output_created": True,
            "training_output_created": False,
            "validation_output_created": False,
        },
        "source": {
            "normalized_training": {
                "file": "training.jsonl",
                "sha256": "train",
            },
            "normalized_validation": {
                "file": "validation.jsonl",
                "sha256": "validation",
            },
        },
        "output": {
            "test": {
                "file": "test.jsonl",
                "samples": 5825,
                "sha256": EXPECTED_TEST_SHA256,
            },
        },
    }


def test_locked_policy_contract_accepts_frozen_policy():
    report = (
        create_locked_policy_report()
    )

    validate_locked_policy_contract(
        report=report,
        expected_seed=42,
        expected_context_sha256=(
            "abc123"
        ),
    )


def test_locked_policy_contract_rejects_threshold_change():
    report = (
        create_locked_policy_report()
    )

    report[
        "protocol"
    ][
        "fixed_margin_threshold"
    ] = 0.15

    with pytest.raises(
        ValueError,
        match="threshold changed",
    ):
        validate_locked_policy_contract(
            report=report,
            expected_seed=42,
            expected_context_sha256=(
                "abc123"
            ),
        )


def test_locked_policy_contract_rejects_test_selected_policy():
    report = (
        create_locked_policy_report()
    )

    report[
        "protocol"
    ][
        "test_split_used"
    ] = True

    with pytest.raises(
        ValueError,
        match="without the test split",
    ):
        validate_locked_policy_contract(
            report=report,
            expected_seed=42,
            expected_context_sha256=(
                "abc123"
            ),
        )


def test_locked_policy_contract_rejects_context_hash_change():
    report = (
        create_locked_policy_report()
    )

    with pytest.raises(
        ValueError,
        match="SHA256 mismatch",
    ):
        validate_locked_policy_contract(
            report=report,
            expected_seed=42,
            expected_context_sha256=(
                "different"
            ),
        )


def test_sealed_test_contract_accepts_frozen_artifact():
    report = (
        create_sealed_test_report()
    )

    result = (
        validate_sealed_test_contract(
            report
        )
    )

    assert (
        result[
            "samples"
        ]
        == 5825
    )

    assert (
        result[
            "sha256"
        ]
        == EXPECTED_TEST_SHA256
    )


def test_sealed_test_contract_rejects_hash_change():
    report = (
        create_sealed_test_report()
    )

    report[
        "output"
    ][
        "test"
    ][
        "sha256"
    ] = "bad"

    with pytest.raises(
        ValueError,
        match="SHA256 changed",
    ):
        validate_sealed_test_contract(
            report
        )


def test_summary_stats_uses_sample_standard_deviation():
    result = summary_stats(
        [
            1.0,
            2.0,
            3.0,
        ]
    )

    assert result[
        "mean"
    ] == pytest.approx(
        2.0
    )

    assert result[
        "sample_sd"
    ] == pytest.approx(
        1.0
    )

    assert result[
        "min"
    ] == pytest.approx(
        1.0
    )

    assert result[
        "max"
    ] == pytest.approx(
        3.0
    )