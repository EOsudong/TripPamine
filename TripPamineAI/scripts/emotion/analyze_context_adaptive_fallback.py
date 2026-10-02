import argparse
import math
from pathlib import Path
from typing import Any

import torch

from scripts.emotion.analyze_context_changed_only import (
    find_changed_indices,
    find_reference_contract,
    validate_artifact_sample_ids,
    validate_protocol,
    validate_source_file,
)
from scripts.emotion.build_context_classification_dataset import (
    load_normalized_records_for_ids,
)
from scripts.emotion.evaluate_context_ablation import (
    build_ablation_views,
    metric_delta,
    metric_summary,
    validate_model_key,
)
from scripts.emotion.evaluate_layernorm_ensemble import (
    evaluate_logits,
    load_report,
)
from scripts.emotion.evaluate_layernorm_seed_ensemble import (
    combine_logits,
    load_logit_artifact,
    validate_alignment,
)
from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    read_dataset,
    save_report,
)
from trippamine_ai.evaluation.emotion.context_ablation import (
    compare_prediction_transitions,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_fine_to_coarse,
)


KO_MODEL_KEY = "koelectra-v3"
KC_MODEL_KEY = "kcelectra-v2022"

ENSEMBLE_WEIGHT = 0.5

FALLBACK_BUDGETS = (
    0,
    10,
    20,
    25,
    30,
    35,
    40,
    50,
    75,
    100,
)

SIGNAL_DIRECTIONS = {
    "msp": "lower",
    "margin": "lower",
    "entropy": "higher",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Simulate uncertainty-gated FULL-context "
            "fallback from FIRST2 ensemble logits."
        )
    )

    parser.add_argument(
        "--context-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path("."),
    )

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    return parser.parse_args()


def compute_uncertainty_signals(
        logits: torch.Tensor,
) -> dict[str, torch.Tensor]:
    if logits.ndim != 2:
        raise ValueError(
            "Logits must be a 2D tensor."
        )

    if logits.shape[0] == 0:
        raise ValueError(
            "Logits must contain samples."
        )

    if logits.shape[1] < 2:
        raise ValueError(
            "Logits must contain at least "
            "two classes."
        )

    values = (
        logits
        .detach()
        .float()
        .cpu()
    )

    probabilities = torch.softmax(
        values,
        dim=1,
    )

    top2 = torch.topk(
        probabilities,
        k=2,
        dim=1,
    ).values

    msp = top2[
        :,
        0,
    ]

    margin = (
        top2[
            :,
            0,
        ]
        - top2[
            :,
            1,
        ]
    )

    log_probabilities = torch.log(
        probabilities.clamp_min(
            torch.finfo(
                probabilities.dtype
            ).tiny
        )
    )

    entropy = -(
        probabilities
        * log_probabilities
    ).sum(
        dim=1
    )

    normalized_entropy = (
        entropy
        / math.log(
            probabilities.shape[
                1
            ]
        )
    )

    return {
        "msp": msp,
        "margin": margin,
        "entropy": (
            normalized_entropy
        ),
    }


def calculate_fallback_count(
        eligible_count: int,
        budget_percent: int,
) -> int:
    if eligible_count <= 0:
        raise ValueError(
            "Eligible sample count must "
            "be positive."
        )

    if not (
            0
            <= budget_percent
            <= 100
    ):
        raise ValueError(
            "Fallback budget must be "
            "between 0 and 100."
        )

    return (
        eligible_count
        * budget_percent
        // 100
    )


def select_fallback_indices(
        signal_values: torch.Tensor,
        eligible_indices: list[int],
        fallback_count: int,
        direction: str,
) -> list[int]:
    if signal_values.ndim != 1:
        raise ValueError(
            "Signal values must be 1D."
        )

    if direction not in (
        "lower",
        "higher",
    ):
        raise ValueError(
            "Direction must be lower "
            "or higher."
        )

    if fallback_count < 0:
        raise ValueError(
            "Fallback count must not "
            "be negative."
        )

    if fallback_count > len(
            eligible_indices
    ):
        raise ValueError(
            "Fallback count exceeds "
            "eligible samples."
        )

    total_samples = int(
        signal_values.shape[
            0
        ]
    )

    for index in eligible_indices:
        if not (
                0
                <= index
                < total_samples
        ):
            raise ValueError(
                "Eligible index is "
                "out of range."
            )

    if len(
            eligible_indices
    ) != len(
            set(
                eligible_indices
            )
    ):
        raise ValueError(
            "Eligible indices contain "
            "duplicates."
        )

    def rank_key(
            index: int,
    ) -> tuple[float, int]:
        value = float(
            signal_values[
                index
            ].item()
        )

        if direction == "higher":
            value = -value

        return (
            value,
            index,
        )

    ranked = sorted(
        eligible_indices,
        key=rank_key,
    )

    return ranked[
        :fallback_count
    ]


def apply_full_fallback(
        first2_logits: torch.Tensor,
        full_logits: torch.Tensor,
        fallback_indices: list[int],
) -> torch.Tensor:
    if (
            first2_logits.shape
            != full_logits.shape
    ):
        raise ValueError(
            "FIRST2/FULL logits shapes "
            "must match."
        )

    if first2_logits.ndim != 2:
        raise ValueError(
            "Fallback logits must be 2D."
        )

    hybrid = (
        first2_logits
        .detach()
        .clone()
    )

    if not fallback_indices:
        return hybrid

    index_tensor = torch.tensor(
        fallback_indices,
        dtype=torch.long,
        device=hybrid.device,
    )

    hybrid.index_copy_(
        0,
        index_tensor,
        full_logits.index_select(
            0,
            index_tensor,
        ),
    )

    return hybrid


def calculate_gap_recovery(
        first2_value: float,
        full_value: float,
        hybrid_value: float,
) -> float | None:
    gap = (
        full_value
        - first2_value
    )

    if math.isclose(
            gap,
            0.0,
            rel_tol=0.0,
            abs_tol=1e-15,
    ):
        return None

    return (
        hybrid_value
        - first2_value
    ) / gap


def calculate_boundary_score(
        signal_values: torch.Tensor,
        fallback_indices: list[int],
        direction: str,
) -> float | None:
    if not fallback_indices:
        return None

    selected = [
        float(
            signal_values[
                index
            ].item()
        )
        for index
        in fallback_indices
    ]

    if direction == "lower":
        return max(
            selected
        )

    return min(
        selected
    )


def validate_labels(
        artifacts: list[
            dict[str, Any]
        ],
        expected: torch.Tensor,
) -> None:
    for artifact in artifacts:
        labels = (
            artifact[
                "labels"
            ]
            .detach()
            .long()
            .cpu()
        )

        if not torch.equal(
                labels,
                expected,
        ):
            raise ValueError(
                "Logit artifact labels "
                "do not match."
            )


def main() -> None:
    args = parse_args()

    project_root = (
        args.project_root
        .expanduser()
        .resolve()
    )

    context_report_path = (
        args.context_report
        .expanduser()
        .resolve()
    )

    report_path = (
        args.report
        .expanduser()
        .resolve()
    )

    if report_path.exists():
        raise FileExistsError(
            "Adaptive fallback report "
            "already exists: "
            f"{report_path}"
        )

    context_report = load_report(
        context_report_path
    )

    seed = validate_protocol(
        context_report
    )

    try:
        dataset = context_report[
            "dataset"
        ]

        validation_contract = dataset[
            "validation"
        ]

        normalized_sources = dataset[
            "normalized_sources"
        ]

        first2_artifacts = (
            context_report[
                "artifacts"
            ][
                "first2"
            ]
        )

    except KeyError as error:
        raise ValueError(
            "Context report is missing "
            "adaptive fallback source data."
        ) from error

    seed_reference = (
        find_reference_contract(
            context_report
        )
    )

    validation_path = (
        validate_source_file(
            project_root=project_root,
            entry=validation_contract,
            description=(
                "Validation dataset"
            ),
        )
    )

    normalized_training_path = (
        validate_source_file(
            project_root=project_root,
            entry=(
                normalized_sources[
                    "training"
                ]
            ),
            description=(
                "Normalized training"
            ),
        )
    )

    normalized_validation_path = (
        validate_source_file(
            project_root=project_root,
            entry=(
                normalized_sources[
                    "validation"
                ]
            ),
            description=(
                "Normalized validation"
            ),
        )
    )

    first2_ko_path = (
        validate_source_file(
            project_root=project_root,
            entry=(
                first2_artifacts[
                    "ko"
                ]
            ),
            description=(
                "FIRST2 Ko logits"
            ),
        )
    )

    first2_kc_path = (
        validate_source_file(
            project_root=project_root,
            entry=(
                first2_artifacts[
                    "kc"
                ]
            ),
            description=(
                "FIRST2 Kc logits"
            ),
        )
    )

    full_ko_path = validate_source_file(
        project_root=project_root,
        entry=(
            seed_reference[
                "full_ko_logits"
            ]
        ),
        description="FULL Ko logits",
    )

    full_kc_path = validate_source_file(
        project_root=project_root,
        entry=(
            seed_reference[
                "full_kc_logits"
            ]
        ),
        description="FULL Kc logits",
    )

    validation = read_dataset(
        validation_path,
        "validation",
    )

    sample_ids = [
        sample.id
        for sample
        in validation
    ]

    if len(
            sample_ids
    ) != len(
            set(
                sample_ids
            )
    ):
        raise ValueError(
            "Validation sample IDs "
            "contain duplicates."
        )

    normalized_by_id = (
        load_normalized_records_for_ids(
            file_paths=[
                normalized_training_path,
                normalized_validation_path,
            ],
            required_ids=set(
                sample_ids
            ),
        )
    )

    views = build_ablation_views(
        validation=validation,
        normalized_by_id=(
            normalized_by_id
        ),
    )

    eligible_indices = (
        find_changed_indices(
            candidate_texts=[
                sample.text
                for sample
                in views[
                    "first2"
                ]
            ],
            reference_texts=[
                sample.text
                for sample
                in views[
                    "full"
                ]
            ],
        )
    )

    expected_changed = int(
        context_report[
            "view_text_summary"
        ][
            "first2"
        ][
            "changed_from_full"
        ]
    )

    if (
            len(
                eligible_indices
            )
            != expected_changed
    ):
        raise RuntimeError(
            "Eligible fallback count "
            "does not reproduce source "
            "report: "
            f"expected={expected_changed}, "
            f"actual={len(eligible_indices)}."
        )

    first2_ko = load_logit_artifact(
        first2_ko_path
    )

    first2_kc = load_logit_artifact(
        first2_kc_path
    )

    full_ko = load_logit_artifact(
        full_ko_path
    )

    full_kc = load_logit_artifact(
        full_kc_path
    )

    artifacts = [
        first2_ko,
        first2_kc,
        full_ko,
        full_kc,
    ]

    validate_alignment(
        artifacts
    )

    validate_model_key(
        first2_ko,
        KO_MODEL_KEY,
        "FIRST2 Ko",
    )

    validate_model_key(
        first2_kc,
        KC_MODEL_KEY,
        "FIRST2 Kc",
    )

    validate_model_key(
        full_ko,
        KO_MODEL_KEY,
        "FULL Ko",
    )

    validate_model_key(
        full_kc,
        KC_MODEL_KEY,
        "FULL Kc",
    )

    for (
        artifact,
        description,
    ) in (
        (
            first2_ko,
            "FIRST2 Ko",
        ),
        (
            first2_kc,
            "FIRST2 Kc",
        ),
        (
            full_ko,
            "FULL Ko",
        ),
        (
            full_kc,
            "FULL Kc",
        ),
    ):
        validate_artifact_sample_ids(
            artifact=artifact,
            expected_sample_ids=(
                sample_ids
            ),
            description=description,
        )

    labels = (
        full_ko[
            "labels"
        ]
        .detach()
        .long()
        .cpu()
    )

    validate_labels(
        artifacts=artifacts,
        expected=labels,
    )

    first2_ensemble_logits = (
        combine_logits(
            first_logits=(
                first2_ko[
                    "logits"
                ]
            ),
            second_logits=(
                first2_kc[
                    "logits"
                ]
            ),
            first_weight=(
                ENSEMBLE_WEIGHT
            ),
            second_weight=(
                ENSEMBLE_WEIGHT
            ),
        )
        .detach()
        .cpu()
    )

    full_ensemble_logits = (
        combine_logits(
            first_logits=(
                full_ko[
                    "logits"
                ]
            ),
            second_logits=(
                full_kc[
                    "logits"
                ]
            ),
            first_weight=(
                ENSEMBLE_WEIGHT
            ),
            second_weight=(
                ENSEMBLE_WEIGHT
            ),
        )
        .detach()
        .cpu()
    )

    label_mapping = (
        EmotionLabelMapping()
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            samples=validation,
            label_mapping=(
                label_mapping
            ),
        )
    )

    first2_evaluation = (
        evaluate_logits(
            logits=(
                first2_ensemble_logits
            ),
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    full_evaluation = (
        evaluate_logits(
            logits=(
                full_ensemble_logits
            ),
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    first2_metrics = (
        metric_summary(
            first2_evaluation
        )
    )

    full_metrics = (
        metric_summary(
            full_evaluation
        )
    )

    signals = (
        compute_uncertainty_signals(
            first2_ensemble_logits
        )
    )

    first2_predictions = (
        first2_evaluation[
            "predicted_ids"
        ]
    )

    full_predictions = (
        full_evaluation[
            "predicted_ids"
        ]
    )

    grid = []

    for signal_name in (
        "msp",
        "margin",
        "entropy",
    ):
        signal_values = signals[
            signal_name
        ]

        direction = (
            SIGNAL_DIRECTIONS[
                signal_name
            ]
        )

        for budget_percent in (
            FALLBACK_BUDGETS
        ):
            fallback_count = (
                calculate_fallback_count(
                    eligible_count=len(
                        eligible_indices
                    ),
                    budget_percent=(
                        budget_percent
                    ),
                )
            )

            fallback_indices = (
                select_fallback_indices(
                    signal_values=(
                        signal_values
                    ),
                    eligible_indices=(
                        eligible_indices
                    ),
                    fallback_count=(
                        fallback_count
                    ),
                    direction=direction,
                )
            )

            hybrid_logits = (
                apply_full_fallback(
                    first2_logits=(
                        first2_ensemble_logits
                    ),
                    full_logits=(
                        full_ensemble_logits
                    ),
                    fallback_indices=(
                        fallback_indices
                    ),
                )
            )

            hybrid_evaluation = (
                evaluate_logits(
                    logits=hybrid_logits,
                    labels=labels,
                    label_mapping=(
                        label_mapping
                    ),
                    fine_to_coarse=(
                        fine_to_coarse
                    ),
                )
            )

            hybrid_metrics = (
                metric_summary(
                    hybrid_evaluation
                )
            )

            hybrid_predictions = (
                hybrid_evaluation[
                    "predicted_ids"
                ]
            )

            delta_vs_first2 = (
                metric_delta(
                    candidate=hybrid_metrics,
                    reference=first2_metrics,
                )
            )

            delta_vs_full = (
                metric_delta(
                    candidate=hybrid_metrics,
                    reference=full_metrics,
                )
            )

            transition_vs_first2 = (
                compare_prediction_transitions(
                    labels=labels.tolist(),
                    reference_predictions=(
                        first2_predictions
                    ),
                    candidate_predictions=(
                        hybrid_predictions
                    ),
                    id2label=dict(
                        label_mapping.id2label
                    ),
                    fine_to_coarse=(
                        fine_to_coarse
                    ),
                )
            )

            transition_vs_full = (
                compare_prediction_transitions(
                    labels=labels.tolist(),
                    reference_predictions=(
                        full_predictions
                    ),
                    candidate_predictions=(
                        hybrid_predictions
                    ),
                    id2label=dict(
                        label_mapping.id2label
                    ),
                    fine_to_coarse=(
                        fine_to_coarse
                    ),
                )
            )

            gap_recovery_fine = (
                calculate_gap_recovery(
                    first2_value=(
                        first2_metrics[
                            "fine_macro_f1"
                        ]
                    ),
                    full_value=(
                        full_metrics[
                            "fine_macro_f1"
                        ]
                    ),
                    hybrid_value=(
                        hybrid_metrics[
                            "fine_macro_f1"
                        ]
                    ),
                )
            )

            gap_recovery_coarse = (
                calculate_gap_recovery(
                    first2_value=(
                        first2_metrics[
                            "coarse_macro_f1"
                        ]
                    ),
                    full_value=(
                        full_metrics[
                            "coarse_macro_f1"
                        ]
                    ),
                    hybrid_value=(
                        hybrid_metrics[
                            "coarse_macro_f1"
                        ]
                    ),
                )
            )

            grid.append(
                {
                    "signal": signal_name,
                    "direction": direction,
                    "eligible_budget_percent": (
                        budget_percent
                    ),
                    "fallback_count": (
                        fallback_count
                    ),
                    "fallback_rate_eligible": (
                        fallback_count
                        / len(
                            eligible_indices
                        )
                    ),
                    "fallback_rate_overall": (
                        fallback_count
                        / len(
                            validation
                        )
                    ),
                    "boundary_score": (
                        calculate_boundary_score(
                            signal_values=(
                                signal_values
                            ),
                            fallback_indices=(
                                fallback_indices
                            ),
                            direction=direction,
                        )
                    ),
                    "metrics": (
                        hybrid_metrics
                    ),
                    "delta_vs_first2": (
                        delta_vs_first2
                    ),
                    "delta_vs_full": (
                        delta_vs_full
                    ),
                    "gap_recovery": {
                        "fine_macro_f1": (
                            gap_recovery_fine
                        ),
                        "coarse_macro_f1": (
                            gap_recovery_coarse
                        ),
                    },
                    "full_retention": {
                        "fine_macro_f1": (
                            hybrid_metrics[
                                "fine_macro_f1"
                            ]
                            / full_metrics[
                                "fine_macro_f1"
                            ]
                        ),
                        "coarse_macro_f1": (
                            hybrid_metrics[
                                "coarse_macro_f1"
                            ]
                            / full_metrics[
                                "coarse_macro_f1"
                            ]
                        ),
                    },
                    "transitions_vs_first2": (
                        transition_vs_first2
                    ),
                    "transitions_vs_full": (
                        transition_vs_full
                    ),
                }
            )

    unchanged_indices = [
        index
        for index
        in range(
            len(
                validation
            )
        )
        if index not in set(
            eligible_indices
        )
    ]

    unchanged_prediction_disagreements = (
        sum(
            1
            for index
            in unchanged_indices
            if (
                first2_predictions[
                    index
                ]
                != full_predictions[
                    index
                ]
            )
        )
    )

    report = {
        "experiment": (
            "T0-Q17-C3-B-"
            f"seed{seed}-"
            "adaptive-context-fallback"
        ),
        "source": {
            "context_report": {
                "file": str(
                    args.context_report
                ),
                "sha256": (
                    calculate_sha256(
                        context_report_path
                    )
                ),
            },
        },
        "protocol": {
            "seed": seed,
            "analysis_only": True,
            "retraining": False,
            "new_inference": False,
            "test_split_used": False,
            "base_view": "first2",
            "fallback_view": "full",
            "eligible_criterion": (
                "first2.text != full.text"
            ),
            "ensemble_type": (
                "weighted_raw_logits"
            ),
            "ko_weight": 0.5,
            "kc_weight": 0.5,
            "selection_mode": (
                "exact uncertainty rank "
                "within eligible samples"
            ),
            "signals": {
                "msp": (
                    "lower is more uncertain"
                ),
                "margin": (
                    "lower is more uncertain"
                ),
                "entropy": (
                    "higher normalized entropy "
                    "is more uncertain"
                ),
            },
            "fallback_budgets_percent": list(
                FALLBACK_BUDGETS
            ),
        },
        "dataset": {
            "validation_samples": len(
                validation
            ),
            "eligible_samples": len(
                eligible_indices
            ),
            "ineligible_samples": (
                len(
                    validation
                )
                - len(
                    eligible_indices
                )
            ),
            "eligible_fraction": (
                len(
                    eligible_indices
                )
                / len(
                    validation
                )
            ),
            "unchanged_prediction_disagreements": (
                unchanged_prediction_disagreements
            ),
        },
        "baseline": {
            "first2": first2_metrics,
            "full": full_metrics,
            "first2_minus_full": (
                metric_delta(
                    candidate=first2_metrics,
                    reference=full_metrics,
                )
            ),
        },
        "grid": grid,
    }

    save_report(
        report=report,
        output_path=report_path,
    )

    print()
    print(
        f"Seed{seed} adaptive fallback "
        "analysis completed"
    )

    print()
    print(
        "Validation samples: "
        f"{len(validation)}"
    )

    print(
        "Fallback eligible samples: "
        f"{len(eligible_indices)}"
    )

    print(
        "No-extra-context samples: "
        f"{len(validation) - len(eligible_indices)}"
    )

    print(
        "Unchanged-context prediction "
        "disagreements: "
        f"{unchanged_prediction_disagreements}"
    )

    print()
    print(
        "FIRST2 Fine Macro F1: "
        f"{first2_metrics['fine_macro_f1']:.6f}"
    )

    print(
        "FULL Fine Macro F1: "
        f"{full_metrics['fine_macro_f1']:.6f}"
    )

    print()
    print(
        "===== TARGET FALLBACK WINDOW ====="
    )

    for signal_name in (
        "msp",
        "margin",
        "entropy",
    ):
        print()
        print(
            signal_name.upper()
        )

        for row in grid:
            if (
                    row[
                        "signal"
                    ]
                    == signal_name
                    and row[
                        "eligible_budget_percent"
                    ]
                    in (
                        25,
                        30,
                        35,
                    )
            ):
                print(
                    "eligible="
                    f"{row['eligible_budget_percent']:>2}% "
                    "overall="
                    f"{row['fallback_rate_overall'] * 100:>6.2f}% "
                    "Fine="
                    f"{row['metrics']['fine_macro_f1']:.6f} "
                    "retention="
                    f"{row['full_retention']['fine_macro_f1'] * 100:>6.2f}% "
                    "recovery="
                    f"{row['gap_recovery']['fine_macro_f1'] * 100:>6.2f}%"
                )

    print()
    print(
        "Report: "
        f"{args.report}"
    )

    print()
    print(
        f"Q17-C3-B SEED{seed} "
        "ADAPTIVE FALLBACK: PASS"
    )


if __name__ == "__main__":
    main()