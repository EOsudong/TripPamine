import argparse
import math
from pathlib import Path
from typing import Any

import torch

from scripts.emotion.analyze_context_adaptive_fallback import (
    ENSEMBLE_WEIGHT,
    apply_full_fallback,
    calculate_gap_recovery,
    compute_uncertainty_signals,
    validate_labels,
)
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

FIXED_MARGIN_THRESHOLD = 0.16

METRIC_KEYS = (
    "fine_accuracy",
    "fine_macro_f1",
    "coarse_accuracy",
    "coarse_macro_f1",
)

METRIC_TOLERANCE = 1e-9


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Validate the locked fixed-margin "
            "FIRST2 -> FULL fallback policy."
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


def select_margin_fallback_indices(
        margin_values: torch.Tensor,
        eligible_indices: list[int],
        threshold: float,
) -> list[int]:
    if margin_values.ndim != 1:
        raise ValueError(
            "Margin values must be 1D."
        )

    if not math.isfinite(
            threshold
    ):
        raise ValueError(
            "Margin threshold must "
            "be finite."
        )

    if threshold < 0.0:
        raise ValueError(
            "Margin threshold must "
            "not be negative."
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

    sample_count = int(
        margin_values.shape[
            0
        ]
    )

    result = []

    for index in eligible_indices:
        if not (
                0
                <= index
                < sample_count
        ):
            raise ValueError(
                "Eligible index is "
                "out of range."
            )

        margin = float(
            margin_values[
                index
            ].item()
        )

        if margin <= threshold:
            result.append(
                index
            )

    return result


def validate_baseline_metric_contract(
        context_report: dict[str, Any],
        view_name: str,
        actual_metrics: dict[str, float],
) -> None:
    try:
        expected_metrics = (
            context_report[
                "metrics"
            ][
                view_name
            ][
                "ensemble"
            ]
        )
    except KeyError as error:
        raise ValueError(
            "Context report is missing "
            f"{view_name} ensemble metrics."
        ) from error

    for metric_key in METRIC_KEYS:
        expected = float(
            expected_metrics[
                metric_key
            ]
        )

        actual = float(
            actual_metrics[
                metric_key
            ]
        )

        if not math.isclose(
                actual,
                expected,
                rel_tol=0.0,
                abs_tol=(
                    METRIC_TOLERANCE
                ),
        ):
            raise RuntimeError(
                f"{view_name} metric "
                "contract mismatch for "
                f"{metric_key}: "
                f"expected={expected}, "
                f"actual={actual}."
            )


def summarize_selected_margins(
        margin_values: torch.Tensor,
        fallback_indices: list[int],
        rejected_indices: list[int],
) -> dict[str, float | None]:
    selected_values = [
        float(
            margin_values[
                index
            ].item()
        )
        for index
        in fallback_indices
    ]

    rejected_values = [
        float(
            margin_values[
                index
            ].item()
        )
        for index
        in rejected_indices
    ]

    return {
        "selected_min": (
            min(
                selected_values
            )
            if selected_values
            else None
        ),
        "selected_max": (
            max(
                selected_values
            )
            if selected_values
            else None
        ),
        "rejected_min": (
            min(
                rejected_values
            )
            if rejected_values
            else None
        ),
        "rejected_max": (
            max(
                rejected_values
            )
            if rejected_values
            else None
        ),
    }


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
            "Fixed-margin report "
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
            "fixed-margin source data."
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

    full_ko_path = (
        validate_source_file(
            project_root=project_root,
            entry=(
                seed_reference[
                    "full_ko_logits"
                ]
            ),
            description=(
                "FULL Ko logits"
            ),
        )
    )

    full_kc_path = (
        validate_source_file(
            project_root=project_root,
            entry=(
                seed_reference[
                    "full_kc_logits"
                ]
            ),
            description=(
                "FULL Kc logits"
            ),
        )
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
            "Eligible sample count "
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

    first2_logits = (
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

    full_logits = (
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
            logits=first2_logits,
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
            logits=full_logits,
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

    validate_baseline_metric_contract(
        context_report=context_report,
        view_name="first2",
        actual_metrics=first2_metrics,
    )

    validate_baseline_metric_contract(
        context_report=context_report,
        view_name="full",
        actual_metrics=full_metrics,
    )

    signals = (
        compute_uncertainty_signals(
            first2_logits
        )
    )

    margin_values = signals[
        "margin"
    ]

    fallback_indices = (
        select_margin_fallback_indices(
            margin_values=(
                margin_values
            ),
            eligible_indices=(
                eligible_indices
            ),
            threshold=(
                FIXED_MARGIN_THRESHOLD
            ),
        )
    )

    fallback_index_set = set(
        fallback_indices
    )

    rejected_eligible_indices = [
        index
        for index
        in eligible_indices
        if index not in (
            fallback_index_set
        )
    ]

    hybrid_logits = (
        apply_full_fallback(
            first2_logits=(
                first2_logits
            ),
            full_logits=(
                full_logits
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

    hybrid_predictions = (
        hybrid_evaluation[
            "predicted_ids"
        ]
    )

    eligible_index_set = set(
        eligible_indices
    )

    ineligible_indices = [
        index
        for index
        in range(
            len(
                validation
            )
        )
        if index not in (
            eligible_index_set
        )
    ]

    unchanged_prediction_disagreements = (
        sum(
            1
            for index
            in ineligible_indices
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

    if (
            unchanged_prediction_disagreements
            != 0
    ):
        raise RuntimeError(
            "FIRST2/FULL predictions differ "
            "for samples with identical "
            "context text."
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

    fine_gap_recovery = (
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

    coarse_gap_recovery = (
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

    fallback_count = len(
        fallback_indices
    )

    eligible_count = len(
        eligible_indices
    )

    validation_count = len(
        validation
    )

    report = {
        "experiment": (
            "T0-Q17-C3-C-"
            f"seed{seed}-"
            "fixed-margin-fallback"
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
            "signal": "margin",
            "signal_definition": (
                "top1_probability - "
                "top2_probability"
            ),
            "fallback_rule": (
                "margin <= 0.16"
            ),
            "fixed_margin_threshold": (
                FIXED_MARGIN_THRESHOLD
            ),
            "ensemble_type": (
                "weighted_raw_logits"
            ),
            "ko_weight": 0.5,
            "kc_weight": 0.5,
            "threshold_tuning": False,
        },
        "dataset": {
            "validation_samples": (
                validation_count
            ),
            "eligible_samples": (
                eligible_count
            ),
            "ineligible_samples": (
                validation_count
                - eligible_count
            ),
            "eligible_fraction": (
                eligible_count
                / validation_count
            ),
            "unchanged_prediction_disagreements": (
                unchanged_prediction_disagreements
            ),
        },
        "fallback": {
            "count": (
                fallback_count
            ),
            "rate_eligible": (
                fallback_count
                / eligible_count
            ),
            "rate_overall": (
                fallback_count
                / validation_count
            ),
            "margin_summary": (
                summarize_selected_margins(
                    margin_values=(
                        margin_values
                    ),
                    fallback_indices=(
                        fallback_indices
                    ),
                    rejected_indices=(
                        rejected_eligible_indices
                    ),
                )
            ),
        },
        "baseline": {
            "first2": (
                first2_metrics
            ),
            "full": (
                full_metrics
            ),
            "first2_minus_full": (
                metric_delta(
                    candidate=(
                        first2_metrics
                    ),
                    reference=(
                        full_metrics
                    ),
                )
            ),
        },
        "fixed_policy": {
            "metrics": (
                hybrid_metrics
            ),
            "delta_vs_first2": (
                metric_delta(
                    candidate=(
                        hybrid_metrics
                    ),
                    reference=(
                        first2_metrics
                    ),
                )
            ),
            "delta_vs_full": (
                metric_delta(
                    candidate=(
                        hybrid_metrics
                    ),
                    reference=(
                        full_metrics
                    ),
                )
            ),
            "gap_recovery": {
                "fine_macro_f1": (
                    fine_gap_recovery
                ),
                "coarse_macro_f1": (
                    coarse_gap_recovery
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
        },
    }

    save_report(
        report=report,
        output_path=report_path,
    )

    print()
    print(
        f"Seed{seed} fixed-margin "
        "fallback analysis completed"
    )

    print()
    print(
        "Validation samples: "
        f"{validation_count}"
    )

    print(
        "Fallback eligible samples: "
        f"{eligible_count}"
    )

    print(
        "No-extra-context samples: "
        f"{validation_count - eligible_count}"
    )

    print(
        "Unchanged-context prediction "
        "disagreements: "
        f"{unchanged_prediction_disagreements}"
    )

    print()
    print(
        "Fixed margin threshold: "
        f"{FIXED_MARGIN_THRESHOLD:.6f}"
    )

    print(
        "Fallback count: "
        f"{fallback_count}"
    )

    print(
        "Fallback rate eligible: "
        f"{fallback_count / eligible_count * 100:.2f}%"
    )

    print(
        "Fallback rate overall: "
        f"{fallback_count / validation_count * 100:.2f}%"
    )

    print()
    print(
        "FIRST2 Fine Macro F1: "
        f"{first2_metrics['fine_macro_f1']:.6f}"
    )

    print(
        "FIXED Fine Macro F1: "
        f"{hybrid_metrics['fine_macro_f1']:.6f}"
    )

    print(
        "FULL Fine Macro F1: "
        f"{full_metrics['fine_macro_f1']:.6f}"
    )

    print()
    print(
        "Fine FULL retention: "
        f"{hybrid_metrics['fine_macro_f1'] / full_metrics['fine_macro_f1'] * 100:.2f}%"
    )

    print(
        "Fine gap recovery: "
        f"{fine_gap_recovery * 100:.2f}%"
    )

    print()
    print(
        "Coarse FULL retention: "
        f"{hybrid_metrics['coarse_macro_f1'] / full_metrics['coarse_macro_f1'] * 100:.2f}%"
    )

    print(
        "Coarse gap recovery: "
        f"{coarse_gap_recovery * 100:.2f}%"
    )

    print()
    print(
        "Report: "
        f"{args.report}"
    )

    print()
    print(
        f"Q17-C3-C SEED{seed} "
        "FIXED MARGIN: PASS"
    )


if __name__ == "__main__":
    main()