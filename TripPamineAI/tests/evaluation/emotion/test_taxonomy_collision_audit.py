import pytest

from trippamine_ai.evaluation.emotion.taxonomy_collision_audit import (
    BASE_NAME_COLLISION,
    NO_COLLISION,
    analyze_taxonomy_collisions,
    base_label_name,
    build_collision_families,
    detect_collision_type,
)


def build_candidate(
        sample_id: str,
        gold_label: str,
        predicted_label: str,
        label_names: dict[str, str],
        fine_to_coarse: dict[str, str],
        confidence: float,
        same_wrong_prediction: bool = True,
) -> dict:
    prediction_labels = [
        predicted_label,
        predicted_label,
        predicted_label,
    ]

    if not same_wrong_prediction:
        prediction_labels[
            2
        ] = gold_label

    predictions = {}

    for (
            seed_name,
            label,
    ) in zip(
        [
            "42",
            "123",
            "2026",
        ],
        prediction_labels,
        strict=True,
    ):
        predictions[
            seed_name
        ] = {
            "label": label,
            "label_name": (
                label_names[
                    label
                ]
            ),
            "coarse": (
                fine_to_coarse[
                    label
                ]
            ),
            "confidence": (
                confidence
            ),
        }

    gold_coarse = (
        fine_to_coarse[
            gold_label
        ]
    )

    predicted_coarse = (
        fine_to_coarse[
            predicted_label
        ]
    )

    coarse_status = (
        "FINE_ONLY"
        if (
            gold_coarse
            == predicted_coarse
        )
        else "CROSS_COARSE"
    )

    return {
        "sample_id": sample_id,
        "gold_label": gold_label,
        "gold_label_name": (
            label_names[
                gold_label
            ]
        ),
        "source_label_name": (
            label_names[
                gold_label
            ]
        ),
        "gold_coarse": (
            gold_coarse
        ),
        "predictions": (
            predictions
        ),
        "mean_confidence": (
            confidence
        ),
        "minimum_confidence": (
            confidence
        ),
        "maximum_confidence": (
            confidence
        ),
        "same_wrong_prediction": (
            same_wrong_prediction
        ),
        "coarse_status": (
            coarse_status
        ),
        "candidate_reasons": [
            "TEST"
        ],
    }


def test_base_label_name_removes_parenthetical_qualifier():
    assert (
        base_label_name(
            "혼란스러운(당황한)"
        )
        == "혼란스러운"
    )

    assert (
        base_label_name(
            "고립된"
        )
        == "고립된"
    )


def test_build_collision_families_detects_cross_coarse_pairs():
    label_names = {
        "E34": "혼란스러운",
        "E43": "고립된",
        "E51": "고립된(당황한)",
        "E59": "혼란스러운(당황한)",
        "E60": "기쁨",
    }

    fine_to_coarse = {
        "E34": "불안",
        "E43": "상처",
        "E51": "당황",
        "E59": "당황",
        "E60": "기쁨",
    }

    families = (
        build_collision_families(
            label_names=label_names,
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    by_name = {
        family.base_name: family
        for family
        in families
    }

    assert set(
        by_name
    ) == {
        "고립된",
        "혼란스러운",
    }

    assert (
        by_name[
            "혼란스러운"
        ].cross_coarse
        is True
    )

    assert (
        by_name[
            "고립된"
        ].cross_coarse
        is True
    )


def test_detect_collision_type_detects_base_name_collision():
    label_names = {
        "E34": "혼란스러운",
        "E59": "혼란스러운(당황한)",
        "E60": "기쁨",
    }

    assert (
        detect_collision_type(
            first_label="E34",
            second_label="E59",
            label_names=label_names,
        )
        == BASE_NAME_COLLISION
    )

    assert (
        detect_collision_type(
            first_label="E34",
            second_label="E60",
            label_names=label_names,
        )
        == NO_COLLISION
    )


def test_analysis_aggregates_consensus_pairs():
    label_names = {
        "E34": "혼란스러운",
        "E59": "혼란스러운(당황한)",
        "E60": "기쁨",
    }

    fine_to_coarse = {
        "E34": "불안",
        "E59": "당황",
        "E60": "기쁨",
    }

    candidates = [
        build_candidate(
            sample_id="a",
            gold_label="E59",
            predicted_label="E34",
            label_names=label_names,
            fine_to_coarse=(
                fine_to_coarse
            ),
            confidence=0.8,
        ),
        build_candidate(
            sample_id="b",
            gold_label="E59",
            predicted_label="E34",
            label_names=label_names,
            fine_to_coarse=(
                fine_to_coarse
            ),
            confidence=0.6,
        ),
        build_candidate(
            sample_id="c",
            gold_label="E60",
            predicted_label="E34",
            label_names=label_names,
            fine_to_coarse=(
                fine_to_coarse
            ),
            confidence=0.7,
        ),
    ]

    result = analyze_taxonomy_collisions(
        candidates=candidates,
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    assert (
        result.total_candidates
        == 3
    )

    assert (
        result.consensus_error_candidates
        == 3
    )

    assert (
        result.distinct_consensus_pairs
        == 2
    )

    assert (
        result.observed_taxonomy_collision_pairs
        == 1
    )

    assert (
        result.observed_taxonomy_collision_occurrences
        == 2
    )

    pair = next(
        item
        for item
        in result.observed_confusion_pairs
        if (
            item.gold_label
            == "E59"
            and item.predicted_label
            == "E34"
        )
    )

    assert pair.count == 2

    assert pair.mean_confidence == (
        pytest.approx(
            0.7
        )
    )

    assert (
        pair.taxonomy_collision_type
        == BASE_NAME_COLLISION
    )

    assert pair.same_coarse is False


def test_analysis_excludes_varying_predictions_from_pairs():
    label_names = {
        "E34": "혼란스러운",
        "E59": "혼란스러운(당황한)",
    }

    fine_to_coarse = {
        "E34": "불안",
        "E59": "당황",
    }

    candidate = build_candidate(
        sample_id="a",
        gold_label="E59",
        predicted_label="E34",
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
        confidence=0.8,
        same_wrong_prediction=False,
    )

    result = analyze_taxonomy_collisions(
        candidates=[
            candidate
        ],
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    assert (
        result.consensus_error_candidates
        == 0
    )

    assert (
        result.varying_prediction_candidates
        == 1
    )

    assert (
        result.distinct_consensus_pairs
        == 0
    )


def test_analysis_records_source_label_name_mismatch():
    label_names = {
        "E34": "혼란스러운",
        "E59": "혼란스러운(당황한)",
    }

    fine_to_coarse = {
        "E34": "불안",
        "E59": "당황",
    }

    candidate = build_candidate(
        sample_id="a",
        gold_label="E59",
        predicted_label="E34",
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
        confidence=0.8,
    )

    candidate[
        "source_label_name"
    ] = "다른 이름"

    result = analyze_taxonomy_collisions(
        candidates=[
            candidate
        ],
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    assert (
        len(
            result
            .source_label_name_mismatches
        )
        == 1
    )


def test_analysis_rejects_inconsistent_consensus_flag():
    label_names = {
        "E34": "혼란스러운",
        "E59": "혼란스러운(당황한)",
    }

    fine_to_coarse = {
        "E34": "불안",
        "E59": "당황",
    }

    candidate = build_candidate(
        sample_id="a",
        gold_label="E59",
        predicted_label="E34",
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
        confidence=0.8,
    )

    candidate[
        "predictions"
    ][
        "2026"
    ][
        "label"
    ] = "E59"

    with pytest.raises(
        ValueError,
        match="seed predictions differ",
    ):
        analyze_taxonomy_collisions(
            candidates=[
                candidate
            ],
            label_names=label_names,
            fine_to_coarse=(
                fine_to_coarse
            ),
        )