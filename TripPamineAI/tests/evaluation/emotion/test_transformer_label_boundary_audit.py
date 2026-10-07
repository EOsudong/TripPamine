import pytest

from scripts.emotion.audit_transformer_label_boundaries import (
    build_label_boundary_audit,
    build_pair_audit,
    validate_audit_pairs,
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
            "version": "v2",
            "split": "validation",
            "profile_id": (
                f"profile-{sample_id}"
            ),
            "talk_id": (
                f"talk-{sample_id}"
            ),
        },
    )


def test_pair_audit_builds_four_expected_buckets():
    samples = [
        make_sample(
            "a-correct",
            "E34",
            "A correct",
        ),
        make_sample(
            "a-to-b",
            "E34",
            "A to B",
        ),
        make_sample(
            "a-other",
            "E34",
            "A other",
        ),
        make_sample(
            "b-correct",
            "E59",
            "B correct",
        ),
        make_sample(
            "b-to-a",
            "E59",
            "B to A",
        ),
        make_sample(
            "b-other",
            "E59",
            "B other",
        ),
    ]

    result = build_pair_audit(
        samples=samples,
        predicted_labels=[
            "E34",
            "E59",
            "E23",
            "E59",
            "E34",
            "E50",
        ],
        confidences=[
            0.9,
            0.8,
            0.7,
            0.95,
            0.85,
            0.6,
        ],
        label_a="E34",
        label_b="E59",
        examples_per_bucket=15,
    )

    assert result[
        "counts"
    ] == {
        "a_support": 3,
        "b_support": 3,
        "a_correct": 1,
        "a_to_b": 1,
        "a_other_errors": 1,
        "b_correct": 1,
        "b_to_a": 1,
        "b_other_errors": 1,
    }

    assert (
        result[
            "selected_counts"
        ][
            "a_correct"
        ]
        == 1
    )

    assert (
        result[
            "selected_counts"
        ][
            "a_to_b"
        ]
        == 1
    )

    assert (
        result[
            "selected_counts"
        ][
            "b_correct"
        ]
        == 1
    )

    assert (
        result[
            "selected_counts"
        ][
            "b_to_a"
        ]
        == 1
    )


def test_pair_audit_sorts_by_confidence_then_sample_id():
    samples = [
        make_sample(
            "sample-b",
            "E34",
            "Second by id",
        ),
        make_sample(
            "sample-a",
            "E34",
            "First by id",
        ),
        make_sample(
            "sample-c",
            "E34",
            "Highest confidence",
        ),
    ]

    result = build_pair_audit(
        samples=samples,
        predicted_labels=[
            "E59",
            "E59",
            "E59",
        ],
        confidences=[
            0.8,
            0.8,
            0.9,
        ],
        label_a="E34",
        label_b="E59",
        examples_per_bucket=3,
    )

    sample_ids = [
        record[
            "sample_id"
        ]
        for record
        in result[
            "examples"
        ][
            "a_to_b"
        ]
    ]

    assert sample_ids == [
        "sample-c",
        "sample-a",
        "sample-b",
    ]


def test_pair_audit_respects_examples_per_bucket():
    samples = [
        make_sample(
            f"sample-{index}",
            "E34",
            f"text-{index}",
        )
        for index
        in range(5)
    ]

    result = build_pair_audit(
        samples=samples,
        predicted_labels=[
            "E59"
            for _ in samples
        ],
        confidences=[
            0.5 + index * 0.01
            for index
            in range(5)
        ],
        label_a="E34",
        label_b="E59",
        examples_per_bucket=2,
    )

    assert (
        result[
            "counts"
        ][
            "a_to_b"
        ]
        == 5
    )

    assert (
        result[
            "selected_counts"
        ][
            "a_to_b"
        ]
        == 2
    )


def test_label_boundary_audit_rejects_length_mismatch():
    with pytest.raises(
        ValueError,
        match=(
            "Audit input lengths "
            "must match"
        ),
    ):
        build_label_boundary_audit(
            samples=[
                make_sample(
                    "sample-1",
                    "E34",
                    "text",
                ),
            ],
            predicted_labels=[],
            confidences=[],
            label_mapping=(
                EmotionLabelMapping()
            ),
            audit_pairs=(
                (
                    "E34",
                    "E59",
                ),
            ),
            examples_per_bucket=1,
        )


def test_validate_audit_pairs_rejects_duplicate_pair():
    with pytest.raises(
        ValueError,
        match=(
            "Duplicate audit pair"
        ),
    ):
        validate_audit_pairs(
            audit_pairs=(
                (
                    "E34",
                    "E59",
                ),
                (
                    "E59",
                    "E34",
                ),
            ),
            label_mapping=(
                EmotionLabelMapping()
            ),
        )