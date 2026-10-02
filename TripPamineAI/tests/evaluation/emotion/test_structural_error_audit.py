import pytest
import torch

from trippamine_ai.evaluation.emotion.structural_error_audit import (
    HIGH_CONFIDENCE_CROSS_COARSE,
    TARGET_LABEL_PERSISTENT_ERROR,
    build_structural_audit_candidates,
)


def build_logits(
        predictions: list[int],
        strengths: list[float],
        num_labels: int = 4,
) -> torch.Tensor:
    if (
            len(
                predictions
            )
            != len(
                strengths
            )
    ):
        raise ValueError(
            "predictions/strengths "
            "length mismatch"
        )

    logits = torch.zeros(
        (
            len(
                predictions
            ),
            num_labels,
        ),
        dtype=torch.float32,
    )

    for (
            index,
            (
                prediction,
                strength,
            ),
    ) in enumerate(
        zip(
            predictions,
            strengths,
            strict=True,
        )
    ):
        logits[
            index,
            prediction,
        ] = strength

    return logits


def build_records():
    labels = [
        "E10",
        "E11",
        "E20",
        "E21",
        "E10",
    ]

    label_names = {
        "E10": "분노",
        "E11": "툴툴대는",
        "E20": "슬픔",
        "E21": "실망한",
    }

    coarse = {
        "E10": "분노",
        "E11": "분노",
        "E20": "슬픔",
        "E21": "슬픔",
    }

    records = []

    for (
            index,
            label,
    ) in enumerate(
        labels
    ):
        records.append(
            {
                "id": f"s{index}",
                "text": f"text {index}",
                "label": label,
                "label_name": (
                    label_names[
                        label
                    ]
                ),
                "coarse_label": (
                    coarse[
                        label
                    ]
                ),
                "situation_code": "S01",
                "situation": "테스트",
                "quality_status": "VALID",
                "quality_issue_codes": [],
                "source": {
                    "dataset": "test",
                    "version": "v1",
                    "split": "validation",
                    "profile_id": (
                        f"p{index}"
                    ),
                    "talk_id": (
                        f"t{index}"
                    ),
                },
            }
        )

    return records


def build_selection():
    labels = torch.tensor(
        [
            0,
            1,
            2,
            3,
            0,
        ],
        dtype=torch.long,
    )

    sample_ids = [
        "s0",
        "s1",
        "s2",
        "s3",
        "s4",
    ]

    id2label = {
        0: "E10",
        1: "E11",
        2: "E20",
        3: "E21",
    }

    label_names = {
        "E10": "분노",
        "E11": "툴툴대는",
        "E20": "슬픔",
        "E21": "실망한",
    }

    fine_to_coarse = {
        "E10": "분노",
        "E11": "분노",
        "E20": "슬픔",
        "E21": "슬픔",
    }

    seed42 = build_logits(
        predictions=[
            0,
            0,
            0,
            2,
            2,
        ],
        strengths=[
            3.0,
            4.0,
            8.0,
            3.0,
            6.0,
        ],
    )

    seed123 = build_logits(
        predictions=[
            0,
            0,
            0,
            0,
            2,
        ],
        strengths=[
            3.0,
            4.0,
            8.0,
            3.0,
            6.0,
        ],
    )

    seed2026 = build_logits(
        predictions=[
            0,
            0,
            0,
            0,
            2,
        ],
        strengths=[
            3.0,
            4.0,
            8.0,
            3.0,
            6.0,
        ],
    )

    return build_structural_audit_candidates(
        records=build_records(),
        labels=labels,
        sample_ids=sample_ids,
        ensemble_logits={
            "42": seed42,
            "123": seed123,
            "2026": seed2026,
        },
        id2label=id2label,
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
        target_labels=[
            "E10",
            "E11",
        ],
        cross_coarse_limit=2,
        target_limit_per_label=1,
    )


def test_selection_counts_persistent_errors():
    result = build_selection()

    assert (
        result.total_samples
        == 5
    )

    assert (
        result.persistent_errors
        == 4
    )

    assert (
        result.cross_coarse_persistent_errors
        == 2
    )


def test_selection_uses_cross_coarse_limit():
    result = build_selection()

    assert (
        result.cross_coarse_selected
        == 2
    )


def test_selection_deduplicates_overlapping_reasons():
    result = build_selection()

    assert (
        result.unique_selected
        == 3
    )

    by_id = {
        candidate.sample_id: candidate
        for candidate
        in result.candidates
    }

    assert set(
        by_id[
            "s4"
        ].candidate_reasons
    ) == {
        HIGH_CONFIDENCE_CROSS_COARSE,
        TARGET_LABEL_PERSISTENT_ERROR,
    }


def test_selection_keeps_target_label_candidate():
    result = build_selection()

    by_id = {
        candidate.sample_id: candidate
        for candidate
        in result.candidates
    }

    assert (
        TARGET_LABEL_PERSISTENT_ERROR
        in by_id[
            "s1"
        ].candidate_reasons
    )

    assert (
        by_id[
            "s1"
        ].coarse_status
        == "FINE_ONLY"
    )


def test_selection_keeps_highest_confidence_cross_coarse_first():
    result = build_selection()

    assert (
        result.candidates[
            0
        ].sample_id
        == "s2"
    )

    assert (
        result.candidates[
            0
        ].coarse_status
        == "CROSS_COARSE"
    )


def test_selection_preserves_source_context():
    result = build_selection()

    candidate = {
        item.sample_id: item
        for item
        in result.candidates
    }[
        "s2"
    ]

    assert (
        candidate.profile_id
        == "p2"
    )

    assert (
        candidate.talk_id
        == "t2"
    )

    assert (
        candidate.quality_status
        == "VALID"
    )

    assert (
        candidate.review_category
        is None
    )


def test_selection_rejects_record_label_mismatch():
    records = build_records()

    records[
        0
    ][
        "label"
    ] = "E11"

    labels = torch.tensor(
        [
            0,
        ],
        dtype=torch.long,
    )

    logits = torch.tensor(
        [
            [
                1.0,
                0.0,
                0.0,
                0.0,
            ],
        ],
        dtype=torch.float32,
    )

    with pytest.raises(
        ValueError,
        match="record label",
    ):
        build_structural_audit_candidates(
            records=[
                records[
                    0
                ]
            ],
            labels=labels,
            sample_ids=[
                "s0"
            ],
            ensemble_logits={
                "42": logits,
                "123": logits,
                "2026": logits,
            },
            id2label={
                0: "E10",
                1: "E11",
                2: "E20",
                3: "E21",
            },
            label_names={
                "E10": "분노",
                "E11": "툴툴대는",
                "E20": "슬픔",
                "E21": "실망한",
            },
            fine_to_coarse={
                "E10": "분노",
                "E11": "분노",
                "E20": "슬픔",
                "E21": "슬픔",
            },
            target_labels=[
                "E10"
            ],
            cross_coarse_limit=1,
            target_limit_per_label=1,
        )


def test_selection_rejects_wrong_seed_count():
    records = build_records()

    labels = torch.tensor(
        [
            0,
        ],
        dtype=torch.long,
    )

    logits = torch.tensor(
        [
            [
                1.0,
                0.0,
                0.0,
                0.0,
            ],
        ],
        dtype=torch.float32,
    )

    with pytest.raises(
        ValueError,
        match="exactly 3",
    ):
        build_structural_audit_candidates(
            records=[
                records[
                    0
                ]
            ],
            labels=labels,
            sample_ids=[
                "s0"
            ],
            ensemble_logits={
                "42": logits,
                "123": logits,
            },
            id2label={
                0: "E10",
                1: "E11",
                2: "E20",
                3: "E21",
            },
            label_names={
                "E10": "분노",
                "E11": "툴툴대는",
                "E20": "슬픔",
                "E21": "실망한",
            },
            fine_to_coarse={
                "E10": "분노",
                "E11": "분노",
                "E20": "슬픔",
                "E21": "슬픔",
            },
            target_labels=[
                "E10"
            ],
            cross_coarse_limit=1,
            target_limit_per_label=1,
        )


def test_selection_rejects_unknown_target_label():
    records = build_records()

    labels = torch.tensor(
        [
            0,
        ],
        dtype=torch.long,
    )

    logits = torch.tensor(
        [
            [
                1.0,
                0.0,
                0.0,
                0.0,
            ],
        ],
        dtype=torch.float32,
    )

    with pytest.raises(
        ValueError,
        match="Unknown target label",
    ):
        build_structural_audit_candidates(
            records=[
                records[
                    0
                ]
            ],
            labels=labels,
            sample_ids=[
                "s0"
            ],
            ensemble_logits={
                "42": logits,
                "123": logits,
                "2026": logits,
            },
            id2label={
                0: "E10",
                1: "E11",
                2: "E20",
                3: "E21",
            },
            label_names={
                "E10": "분노",
                "E11": "툴툴대는",
                "E20": "슬픔",
                "E21": "실망한",
            },
            fine_to_coarse={
                "E10": "분노",
                "E11": "분노",
                "E20": "슬픔",
                "E21": "슬픔",
            },
            target_labels=[
                "E99"
            ],
            cross_coarse_limit=1,
            target_limit_per_label=1,
        )