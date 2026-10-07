import pytest

from scripts.emotion.compare_transformer_validation_transitions import (
    build_paired_transition_audit,
    validate_and_align_validation_samples,
)
from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


def make_sample(
        sample_id: str,
        label: str,
        text: str,
        version: str,
) -> EmotionClassificationSample:
    return EmotionClassificationSample(
        id=sample_id,
        text=text,
        label=label,
        label_name=(
            EMOTION_CODEBOOK[
                label
            ]
        ),
        coarse_label=(
            get_coarse_emotion(
                label
            )
        ),
        situation_code="S01",
        situation="가족관계",
        quality_status="VALID",
        quality_issue_codes=[],
        source={
            "dataset": (
                "emotion-test"
            ),
            "version": (
                version
            ),
            "split": "validation",
            "profile_id": (
                f"profile-{sample_id}"
            ),
            "talk_id": (
                f"talk-{sample_id}"
            ),
        },
    )


def test_aligns_context_samples_by_sample_id():
    baseline = [
        make_sample(
            "sample-1",
            "E10",
            "baseline-1",
            "v2",
        ),
        make_sample(
            "sample-2",
            "E20",
            "baseline-2",
            "v2",
        ),
    ]

    context = [
        make_sample(
            "sample-2",
            "E20",
            "context-2",
            "v3-context",
        ),
        make_sample(
            "sample-1",
            "E10",
            "context-1",
            "v3-context",
        ),
    ]

    aligned = (
        validate_and_align_validation_samples(
            baseline_samples=(
                baseline
            ),
            context_samples=(
                context
            ),
        )
    )

    assert [
        sample.id
        for sample in aligned
    ] == [
        "sample-1",
        "sample-2",
    ]


def test_alignment_rejects_sample_id_mismatch():
    baseline = [
        make_sample(
            "sample-1",
            "E10",
            "baseline",
            "v2",
        ),
    ]

    context = [
        make_sample(
            "sample-2",
            "E10",
            "context",
            "v3-context",
        ),
    ]

    with pytest.raises(
        ValueError,
        match=(
            "sample ID sets "
            "do not match"
        ),
    ):
        validate_and_align_validation_samples(
            baseline_samples=(
                baseline
            ),
            context_samples=(
                context
            ),
        )


def test_alignment_rejects_label_mismatch():
    baseline = [
        make_sample(
            "sample-1",
            "E10",
            "baseline",
            "v2",
        ),
    ]

    context = [
        make_sample(
            "sample-1",
            "E11",
            "context",
            "v3-context",
        ),
    ]

    with pytest.raises(
        ValueError,
        match="label mismatch",
    ):
        validate_and_align_validation_samples(
            baseline_samples=(
                baseline
            ),
            context_samples=(
                context
            ),
        )


def test_paired_audit_counts_fine_and_coarse_transitions():
    baseline = [
        make_sample(
            "cc",
            "E10",
            "baseline cc",
            "v2",
        ),
        make_sample(
            "cw",
            "E10",
            "baseline cw",
            "v2",
        ),
        make_sample(
            "wc",
            "E20",
            "baseline wc",
            "v2",
        ),
        make_sample(
            "ww",
            "E20",
            "baseline ww",
            "v2",
        ),
    ]

    context = [
        make_sample(
            "cc",
            "E10",
            "context cc",
            "v3-context",
        ),
        make_sample(
            "cw",
            "E10",
            "context cw",
            "v3-context",
        ),
        make_sample(
            "wc",
            "E20",
            "context wc",
            "v3-context",
        ),
        make_sample(
            "ww",
            "E20",
            "context ww",
            "v3-context",
        ),
    ]

    result = (
        build_paired_transition_audit(
            baseline_samples=(
                baseline
            ),
            context_samples=(
                context
            ),
            baseline_predictions=[
                "E10",
                "E10",
                "E21",
                "E30",
            ],
            baseline_confidences=[
                0.9,
                0.8,
                0.7,
                0.6,
            ],
            context_predictions=[
                "E10",
                "E20",
                "E20",
                "E21",
            ],
            context_confidences=[
                0.95,
                0.75,
                0.85,
                0.65,
            ],
            label_mapping=(
                EmotionLabelMapping()
            ),
            audit_pairs=(
                (
                    "E10",
                    "E18",
                ),
            ),
            examples_per_bucket=2,
        )
    )

    assert (
        result[
            "overall"
        ][
            "fine"
        ]
        == {
            "correct_to_correct": 1,
            "correct_to_wrong": 1,
            "wrong_to_correct": 1,
            "wrong_to_wrong": 1,
            "baseline_correct": 2,
            "context_correct": 2,
            "net_gain": 0,
        }
    )

    assert (
        result[
            "overall"
        ][
            "wrong_to_wrong_detail"
        ][
            "coarse_wrong_to_correct"
        ]
        == 1
    )


def test_per_label_summary_tracks_recovery_and_regression():
    baseline = [
        make_sample(
            "recovered",
            "E10",
            "baseline recovered",
            "v2",
        ),
        make_sample(
            "regressed",
            "E10",
            "baseline regressed",
            "v2",
        ),
        make_sample(
            "stable",
            "E10",
            "baseline stable",
            "v2",
        ),
    ]

    context = [
        make_sample(
            sample.id,
            sample.label,
            (
                "context "
                f"{sample.id}"
            ),
            "v3-context",
        )
        for sample in baseline
    ]

    result = (
        build_paired_transition_audit(
            baseline_samples=(
                baseline
            ),
            context_samples=(
                context
            ),
            baseline_predictions=[
                "E11",
                "E10",
                "E10",
            ],
            baseline_confidences=[
                0.7,
                0.8,
                0.9,
            ],
            context_predictions=[
                "E10",
                "E11",
                "E10",
            ],
            context_confidences=[
                0.95,
                0.75,
                0.92,
            ],
            label_mapping=(
                EmotionLabelMapping()
            ),
            audit_pairs=(
                (
                    "E10",
                    "E18",
                ),
            ),
            examples_per_bucket=2,
        )
    )

    by_label = {
        item[
            "label"
        ]: item
        for item in (
            result[
                "per_label"
            ]
        )
    }

    assert (
        by_label[
            "E10"
        ][
            "support"
        ]
        == 3
    )

    assert (
        by_label[
            "E10"
        ][
            "recovered"
        ]
        == 1
    )

    assert (
        by_label[
            "E10"
        ][
            "regressed"
        ]
        == 1
    )

    assert (
        by_label[
            "E10"
        ][
            "net_gain"
        ]
        == 0
    )

    assert (
        by_label[
            "E20"
        ][
            "support"
        ]
        == 0
    )


def test_boundary_pair_tracks_direct_confusion_delta():
    baseline = [
        make_sample(
            "resolved",
            "E34",
            "baseline resolved",
            "v2",
        ),
        make_sample(
            "introduced",
            "E59",
            "baseline introduced",
            "v2",
        ),
        make_sample(
            "recovered",
            "E59",
            "baseline recovered",
            "v2",
        ),
    ]

    context = [
        make_sample(
            sample.id,
            sample.label,
            (
                "context "
                f"{sample.id}"
            ),
            "v3-context",
        )
        for sample in baseline
    ]

    result = (
        build_paired_transition_audit(
            baseline_samples=(
                baseline
            ),
            context_samples=(
                context
            ),
            baseline_predictions=[
                "E59",
                "E23",
                "E34",
            ],
            baseline_confidences=[
                0.8,
                0.7,
                0.9,
            ],
            context_predictions=[
                "E34",
                "E34",
                "E59",
            ],
            context_confidences=[
                0.95,
                0.85,
                0.92,
            ],
            label_mapping=(
                EmotionLabelMapping()
            ),
            audit_pairs=(
                (
                    "E34",
                    "E59",
                ),
            ),
            examples_per_bucket=5,
        )
    )

    counts = (
        result[
            "boundary_pairs"
        ][0][
            "counts"
        ]
    )

    assert (
        counts[
            "baseline_direct_confusion"
        ]
        == 2
    )

    assert (
        counts[
            "context_direct_confusion"
        ]
        == 1
    )

    assert (
        counts[
            "direct_confusion_resolved"
        ]
        == 2
    )

    assert (
        counts[
            "direct_confusion_introduced"
        ]
        == 1
    )

    assert (
        counts[
            "recovered"
        ]
        == 2
    )

    assert (
        counts[
            "regressed"
        ]
        == 0
    )

    assert (
        counts[
            "net_gain"
        ]
        == 2
    )


def test_examples_are_deterministic_and_limited():
    baseline = [
        make_sample(
            "sample-b",
            "E10",
            "baseline b",
            "v2",
        ),
        make_sample(
            "sample-a",
            "E10",
            "baseline a",
            "v2",
        ),
        make_sample(
            "sample-c",
            "E10",
            "baseline c",
            "v2",
        ),
    ]

    context = [
        make_sample(
            sample.id,
            sample.label,
            (
                "context "
                f"{sample.id}"
            ),
            "v3-context",
        )
        for sample in baseline
    ]

    result = (
        build_paired_transition_audit(
            baseline_samples=(
                baseline
            ),
            context_samples=(
                context
            ),
            baseline_predictions=[
                "E11",
                "E11",
                "E11",
            ],
            baseline_confidences=[
                0.5,
                0.5,
                0.5,
            ],
            context_predictions=[
                "E10",
                "E10",
                "E10",
            ],
            context_confidences=[
                0.8,
                0.8,
                0.9,
            ],
            label_mapping=(
                EmotionLabelMapping()
            ),
            audit_pairs=(
                (
                    "E10",
                    "E18",
                ),
            ),
            examples_per_bucket=2,
        )
    )

    sample_ids = [
        record[
            "sample_id"
        ]
        for record in (
            result[
                "examples"
            ][
                "wrong_to_correct"
            ]
        )
    ]

    assert sample_ids == [
        "sample-c",
        "sample-a",
    ]


def test_paired_audit_rejects_prediction_length_mismatch():
    baseline = [
        make_sample(
            "sample-1",
            "E10",
            "baseline",
            "v2",
        ),
    ]

    context = [
        make_sample(
            "sample-1",
            "E10",
            "context",
            "v3-context",
        ),
    ]

    with pytest.raises(
        ValueError,
        match=(
            "input lengths "
            "must match"
        ),
    ):
        build_paired_transition_audit(
            baseline_samples=(
                baseline
            ),
            context_samples=(
                context
            ),
            baseline_predictions=[],
            baseline_confidences=[],
            context_predictions=[],
            context_confidences=[],
            label_mapping=(
                EmotionLabelMapping()
            ),
            audit_pairs=(
                (
                    "E10",
                    "E18",
                ),
            ),
            examples_per_bucket=1,
        )