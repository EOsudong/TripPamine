import pytest

from scripts.emotion.evaluate_context_ablation import (
    find_fixed_ensemble_metrics,
)
from trippamine_ai.datasets.emotion.context_classification import (
    EmotionContextClassificationBuilder,
)
from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)
from trippamine_ai.evaluation.emotion.context_ablation import (
    build_context_views,
    compare_prediction_transitions,
    summarize_view_texts,
)


def create_normalized_record(
        second_human: str = "두 번째 사용자 발화야.",
        third_human: str = "세 번째 사용자 발화야.",
):
    raw = {
        "profile": {
            "persona-id": "Pro_05349",
            "persona": {
                "persona-id": "A02_G02_C01",
                "human": [
                    "A02",
                    "G02",
                ],
                "computer": [
                    "C01"
                ],
            },
            "emotion": {
                "emotion-id": "S06_D02_E18",
                "type": "E18",
                "situation": [
                    "S06",
                    "D02",
                ],
            },
        },
        "talk": {
            "id": {
                "profile-id": "Pro_05349",
                "talk-id": "Pro_05349_00053",
            },
            "content": {
                "HS01": "첫 번째 사용자 발화야.",
                "SS01": "첫 번째 상담자 응답이야.",
                "HS02": second_human,
                "SS02": "두 번째 상담자 응답이야.",
                "HS03": third_human,
                "SS03": "세 번째 상담자 응답이야.",
            },
        },
    }

    record = (
        EmotionDialogueRecord
        .model_validate(
            raw
        )
    )

    return (
        EmotionDialogueNormalizer()
        .normalize(
            record,
            split="validation",
        )
    )


def create_full_reference(
        normalized,
):
    return (
        EmotionContextClassificationBuilder()
        .build(
            normalized,
            split="validation",
        )
    )


def test_build_context_views_uses_expected_human_turns():
    normalized = (
        create_normalized_record()
    )

    reference = (
        create_full_reference(
            normalized
        )
    )

    views = build_context_views(
        reference=reference,
        record=normalized,
    )

    assert views[
        "first1"
    ].text == (
        "첫 번째 사용자 발화야."
    )

    assert views[
        "first2"
    ].text == (
        "첫 번째 사용자 발화야.\n"
        "두 번째 사용자 발화야."
    )

    assert views[
        "full"
    ].text == (
        "첫 번째 사용자 발화야.\n"
        "두 번째 사용자 발화야.\n"
        "세 번째 사용자 발화야."
    )

    assert (
        "상담자 응답"
        not in views[
            "full"
        ].text
    )


def test_build_context_views_uses_non_empty_turns_only():
    normalized = (
        create_normalized_record(
            second_human="",
            third_human="",
        )
    )

    reference = (
        create_full_reference(
            normalized
        )
    )

    views = build_context_views(
        reference=reference,
        record=normalized,
    )

    assert (
        views[
            "first1"
        ].text
        == views[
            "first2"
        ].text
        == views[
            "full"
        ].text
        == "첫 번째 사용자 발화야."
    )


def test_build_context_views_rejects_full_reference_mismatch():
    normalized = (
        create_normalized_record()
    )

    reference = (
        create_full_reference(
            normalized
        )
        .model_copy(
            update={
                "text": "다른 본문"
            }
        )
    )

    with pytest.raises(
        ValueError,
        match="does not match reference text",
    ):
        build_context_views(
            reference=reference,
            record=normalized,
        )


def test_summarize_view_texts_counts_changes():
    first = create_normalized_record()

    second = (
        create_normalized_record(
            second_human="",
            third_human="",
        )
    )

    first_reference = (
        create_full_reference(
            first
        )
    )

    second_reference = (
        create_full_reference(
            second
        )
    )

    first_views = (
        build_context_views(
            reference=first_reference,
            record=first,
        )
    )

    second_views = (
        build_context_views(
            reference=second_reference,
            record=second,
        )
    )

    views = {
        view_name: [
            first_views[
                view_name
            ],
            second_views[
                view_name
            ],
        ]
        for view_name
        in (
            "first1",
            "first2",
            "full",
        )
    }

    result = summarize_view_texts(
        views
    )

    assert (
        result[
            "first1"
        ][
            "changed_from_full"
        ]
        == 1
    )

    assert (
        result[
            "first2"
        ][
            "changed_from_full"
        ]
        == 1
    )

    assert (
        result[
            "full"
        ][
            "changed_from_full"
        ]
        == 0
    )


def test_compare_prediction_transitions_counts_fine_and_coarse():
    result = (
        compare_prediction_transitions(
            labels=[
                0,
                1,
                2,
                3,
            ],
            reference_predictions=[
                0,
                0,
                2,
                0,
            ],
            candidate_predictions=[
                0,
                1,
                0,
                0,
            ],
            id2label={
                0: "E10",
                1: "E11",
                2: "E20",
                3: "E21",
            },
            fine_to_coarse={
                "E10": "A",
                "E11": "A",
                "E20": "B",
                "E21": "B",
            },
        )
    )

    assert (
        result[
            "fine"
        ][
            "both_correct"
        ]
        == 1
    )

    assert (
        result[
            "fine"
        ][
            "rescued_vs_reference"
        ]
        == 1
    )

    assert (
        result[
            "fine"
        ][
            "regressed_vs_reference"
        ]
        == 1
    )

    assert (
        result[
            "fine"
        ][
            "both_wrong_same_prediction"
        ]
        == 1
    )

    assert (
        result[
            "fine"
        ][
            "prediction_disagreements"
        ]
        == 2
    )

    assert (
        result[
            "coarse"
        ][
            "both_correct"
        ]
        == 2
    )

    assert (
        result[
            "coarse"
        ][
            "reference_only_correct"
        ]
        == 1
    )

    assert (
        result[
            "coarse"
        ][
            "both_wrong"
        ]
        == 1
    )

    assert (
        result[
            "coarse"
        ][
            "prediction_disagreements"
        ]
        == 1
    )


def test_compare_prediction_transitions_rejects_length_mismatch():
    with pytest.raises(
        ValueError,
        match="lengths must match",
    ):
        compare_prediction_transitions(
            labels=[
                0,
            ],
            reference_predictions=[
                0,
            ],
            candidate_predictions=[
                0,
                1,
            ],
            id2label={
                0: "E10",
                1: "E11",
            },
            fine_to_coarse={
                "E10": "A",
                "E11": "A",
            },
        )


def test_find_fixed_ensemble_metrics_selects_50_50_row():
    report = {
        "grid": [
            {
                "kc_weight": 0.45,
                "ko_weight": 0.55,
                "fine_accuracy": 0.4,
                "fine_macro_f1": 0.4,
                "coarse_accuracy": 0.6,
                "coarse_macro_f1": 0.6,
            },
            {
                "kc_weight": 0.5,
                "ko_weight": 0.5,
                "fine_accuracy": 0.45,
                "fine_macro_f1": 0.46,
                "coarse_accuracy": 0.7,
                "coarse_macro_f1": 0.72,
            },
        ]
    }

    result = (
        find_fixed_ensemble_metrics(
            report
        )
    )

    assert (
        result[
            "fine_macro_f1"
        ]
        == pytest.approx(
            0.46
        )
    )

    assert (
        result[
            "coarse_macro_f1"
        ]
        == pytest.approx(
            0.72
        )
    )