from typing import Any

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


CONTEXT_VIEW_ORDER = (
    "first1",
    "first2",
    "full",
)


def extract_human_turns(
        record: NormalizedEmotionDialogue,
) -> list[str]:
    human_turns = [
        turn.human.strip()
        for turn in record.turns
        if turn.human.strip()
    ]

    if not human_turns:
        raise ValueError(
            "Context ablation record "
            "contains no human turns."
        )

    return human_turns


def build_context_views(
        reference: EmotionClassificationSample,
        record: NormalizedEmotionDialogue,
) -> dict[
    str,
    EmotionClassificationSample,
]:
    if reference.id != record.record_id:
        raise ValueError(
            "Reference/normalized record "
            "ID mismatch: "
            f"reference={reference.id}, "
            f"normalized={record.record_id}."
        )

    human_turns = extract_human_turns(
        record
    )

    view_texts = {
        "first1": "\n".join(
            human_turns[
                :1
            ]
        ),
        "first2": "\n".join(
            human_turns[
                :2
            ]
        ),
        "full": "\n".join(
            human_turns
        ),
    }

    if (
            view_texts[
                "full"
            ]
            != reference.text
    ):
        raise ValueError(
            "Reconstructed full context "
            "does not match reference text "
            f"for {reference.id}."
        )

    return {
        view_name: (
            reference.model_copy(
                update={
                    "text": view_texts[
                        view_name
                    ]
                }
            )
        )
        for view_name
        in CONTEXT_VIEW_ORDER
    }


def summarize_view_texts(
        views: dict[
            str,
            list[
                EmotionClassificationSample
            ],
        ],
) -> dict[
    str,
    dict[
        str,
        Any,
    ],
]:
    missing = (
        set(
            CONTEXT_VIEW_ORDER
        )
        - set(
            views
        )
    )

    if missing:
        raise ValueError(
            "Missing context views: "
            f"{sorted(missing)}."
        )

    sample_counts = {
        len(
            views[
                view_name
            ]
        )
        for view_name
        in CONTEXT_VIEW_ORDER
    }

    if len(
            sample_counts
    ) != 1:
        raise ValueError(
            "Context view sample counts "
            "do not match."
        )

    if not sample_counts:
        raise ValueError(
            "Context views are empty."
        )

    total_samples = next(
        iter(
            sample_counts
        )
    )

    if total_samples == 0:
        raise ValueError(
            "Context views contain "
            "no samples."
        )

    full_samples = views[
        "full"
    ]

    summary = {}

    for view_name in CONTEXT_VIEW_ORDER:
        samples = views[
            view_name
        ]

        changed_from_full = sum(
            1
            for (
                sample,
                full_sample,
            )
            in zip(
                samples,
                full_samples,
                strict=True,
            )
            if (
                sample.text
                != full_sample.text
            )
        )

        character_counts = [
            len(
                sample.text
            )
            for sample
            in samples
        ]

        summary[
            view_name
        ] = {
            "samples": (
                total_samples
            ),
            "changed_from_full": (
                changed_from_full
            ),
            "unchanged_from_full": (
                total_samples
                - changed_from_full
            ),
            "mean_characters": (
                sum(
                    character_counts
                )
                / total_samples
            ),
            "minimum_characters": min(
                character_counts
            ),
            "maximum_characters": max(
                character_counts
            ),
        }

    return summary


def compare_prediction_transitions(
        labels: list[int],
        reference_predictions: list[int],
        candidate_predictions: list[int],
        id2label: dict[
            int,
            str,
        ],
        fine_to_coarse: dict[
            str,
            str,
        ],
) -> dict[str, Any]:
    if not (
            len(
                labels
            )
            == len(
                reference_predictions
            )
            == len(
                candidate_predictions
            )
    ):
        raise ValueError(
            "Prediction transition input "
            "lengths must match."
        )

    if not labels:
        raise ValueError(
            "Prediction transition inputs "
            "must not be empty."
        )

    valid_ids = set(
        id2label
    )

    for value in (
        labels
        + reference_predictions
        + candidate_predictions
    ):
        if value not in valid_ids:
            raise ValueError(
                "Unknown label ID in "
                "prediction transition: "
                f"{value}."
            )

    missing_coarse = (
        set(
            id2label.values()
        )
        - set(
            fine_to_coarse
        )
    )

    if missing_coarse:
        raise ValueError(
            "Missing coarse mapping for "
            "labels: "
            f"{sorted(missing_coarse)}."
        )

    fine_counts = {
        "both_correct": 0,
        "reference_only_correct": 0,
        "candidate_only_correct": 0,
        "both_wrong_same_prediction": 0,
        "both_wrong_changed_prediction": 0,
        "prediction_disagreements": 0,
    }

    coarse_counts = {
        "both_correct": 0,
        "reference_only_correct": 0,
        "candidate_only_correct": 0,
        "both_wrong": 0,
        "prediction_disagreements": 0,
    }

    for (
            true_id,
            reference_id,
            candidate_id,
    ) in zip(
        labels,
        reference_predictions,
        candidate_predictions,
        strict=True,
    ):
        reference_correct = (
            reference_id
            == true_id
        )

        candidate_correct = (
            candidate_id
            == true_id
        )

        if reference_id != candidate_id:
            fine_counts[
                "prediction_disagreements"
            ] += 1

        if (
                reference_correct
                and candidate_correct
        ):
            fine_counts[
                "both_correct"
            ] += 1

        elif reference_correct:
            fine_counts[
                "reference_only_correct"
            ] += 1

        elif candidate_correct:
            fine_counts[
                "candidate_only_correct"
            ] += 1

        elif reference_id == candidate_id:
            fine_counts[
                "both_wrong_same_prediction"
            ] += 1

        else:
            fine_counts[
                "both_wrong_changed_prediction"
            ] += 1

        true_coarse = (
            fine_to_coarse[
                id2label[
                    true_id
                ]
            ]
        )

        reference_coarse = (
            fine_to_coarse[
                id2label[
                    reference_id
                ]
            ]
        )

        candidate_coarse = (
            fine_to_coarse[
                id2label[
                    candidate_id
                ]
            ]
        )

        reference_coarse_correct = (
            reference_coarse
            == true_coarse
        )

        candidate_coarse_correct = (
            candidate_coarse
            == true_coarse
        )

        if (
                reference_coarse
                != candidate_coarse
        ):
            coarse_counts[
                "prediction_disagreements"
            ] += 1

        if (
                reference_coarse_correct
                and candidate_coarse_correct
        ):
            coarse_counts[
                "both_correct"
            ] += 1

        elif reference_coarse_correct:
            coarse_counts[
                "reference_only_correct"
            ] += 1

        elif candidate_coarse_correct:
            coarse_counts[
                "candidate_only_correct"
            ] += 1

        else:
            coarse_counts[
                "both_wrong"
            ] += 1

    total = len(
        labels
    )

    fine_rescued = fine_counts[
        "candidate_only_correct"
    ]

    fine_regressed = fine_counts[
        "reference_only_correct"
    ]

    coarse_rescued = coarse_counts[
        "candidate_only_correct"
    ]

    coarse_regressed = coarse_counts[
        "reference_only_correct"
    ]

    return {
        "total_samples": total,
        "fine": {
            **fine_counts,
            "rescued_vs_reference": (
                fine_rescued
            ),
            "regressed_vs_reference": (
                fine_regressed
            ),
            "net_correct_change": (
                fine_rescued
                - fine_regressed
            ),
            "disagreement_rate": (
                fine_counts[
                    "prediction_disagreements"
                ]
                / total
            ),
        },
        "coarse": {
            **coarse_counts,
            "rescued_vs_reference": (
                coarse_rescued
            ),
            "regressed_vs_reference": (
                coarse_regressed
            ),
            "net_correct_change": (
                coarse_rescued
                - coarse_regressed
            ),
            "disagreement_rate": (
                coarse_counts[
                    "prediction_disagreements"
                ]
                / total
            ),
        },
    }