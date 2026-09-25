import argparse
import hashlib
import math
from pathlib import Path
from typing import Any

import torch

from scripts.emotion.evaluate_context_ablation import (
    build_ablation_views,
    evaluate_pair,
    metric_delta,
    metric_summary,
    validate_model_key,
)
from scripts.emotion.evaluate_layernorm_ensemble import (
    load_report,
)
from scripts.emotion.evaluate_layernorm_seed_ensemble import (
    load_logit_artifact,
    validate_alignment,
)
from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    read_dataset,
    save_report,
)
from scripts.emotion.build_context_classification_dataset import (
    load_normalized_records_for_ids,
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

EXPECTED_KO_WEIGHT = 0.5
EXPECTED_KC_WEIGHT = 0.5


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Analyze FIRST2 vs FULL only on validation "
            "samples where FIRST2 text actually differs "
            "from FULL context."
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


def resolved_path(
        project_root: Path,
        value: str,
) -> Path:
    path = Path(
        value
    )

    if not path.is_absolute():
        path = (
            project_root
            / path
        )

    return (
        path
        .expanduser()
        .resolve()
    )


def validate_source_file(
        project_root: Path,
        entry: dict[str, Any],
        description: str,
) -> Path:
    try:
        file_value = entry[
            "file"
        ]

        expected_sha256 = entry[
            "sha256"
        ]

    except KeyError as error:
        raise ValueError(
            f"{description} source contract "
            "is incomplete."
        ) from error

    path = resolved_path(
        project_root=project_root,
        value=file_value,
    )

    if not path.is_file():
        raise FileNotFoundError(
            f"{description} does not exist: "
            f"{path}"
        )

    actual_sha256 = calculate_sha256(
        path
    )

    if actual_sha256 != expected_sha256:
        raise ValueError(
            f"{description} SHA256 mismatch: "
            f"expected={expected_sha256}, "
            f"actual={actual_sha256}."
        )

    return path


def validate_protocol(
        context_report: dict[str, Any],
) -> int:
    try:
        protocol = context_report[
            "protocol"
        ]

        seed = int(
            protocol[
                "seed"
            ]
        )

    except (
        KeyError,
        TypeError,
        ValueError,
    ) as error:
        raise ValueError(
            "Context report protocol "
            "is incomplete."
        ) from error

    if seed < 0:
        raise ValueError(
            "Context report seed must "
            "be non-negative."
        )

    if protocol.get(
            "inference_only"
    ) is not True:
        raise ValueError(
            "Context report must be "
            "inference-only."
        )

    if protocol.get(
            "retraining"
    ) is not False:
        raise ValueError(
            "Context report must not "
            "contain retraining."
        )

    if protocol.get(
            "assistant_turns_included"
    ) is not False:
        raise ValueError(
            "Assistant turns must not "
            "be included."
        )

    if protocol.get(
            "ensemble_type"
    ) != "weighted_raw_logits":
        raise ValueError(
            "Unexpected ensemble type."
        )

    ko_weight = float(
        protocol.get(
            "ko_weight",
            math.nan,
        )
    )

    kc_weight = float(
        protocol.get(
            "kc_weight",
            math.nan,
        )
    )

    if not math.isclose(
            ko_weight,
            EXPECTED_KO_WEIGHT,
            rel_tol=0.0,
            abs_tol=1e-12,
    ):
        raise ValueError(
            "Context report Ko weight "
            "must be 0.5."
        )

    if not math.isclose(
            kc_weight,
            EXPECTED_KC_WEIGHT,
            rel_tol=0.0,
            abs_tol=1e-12,
    ):
        raise ValueError(
            "Context report Kc weight "
            "must be 0.5."
        )

    if protocol.get(
            "alpha_search"
    ) is not False:
        raise ValueError(
            "Context report must not "
            "use alpha search."
        )

    if protocol.get(
            "test_split_used"
    ) is not False:
        raise ValueError(
            "Test split must not be used."
        )

    return seed


def find_changed_indices(
        candidate_texts: list[str],
        reference_texts: list[str],
) -> list[int]:
    if len(
            candidate_texts
    ) != len(
            reference_texts
    ):
        raise ValueError(
            "Candidate/reference text "
            "lengths must match."
        )

    if not candidate_texts:
        raise ValueError(
            "Candidate/reference texts "
            "must not be empty."
        )

    return [
        index
        for (
            index,
            (
                candidate,
                reference,
            ),
        )
        in enumerate(
            zip(
                candidate_texts,
                reference_texts,
                strict=True,
            )
        )
        if candidate != reference
    ]


def subset_tensor(
        tensor: torch.Tensor,
        indices: list[int],
) -> torch.Tensor:
    if not indices:
        raise ValueError(
            "Changed-only subset "
            "must not be empty."
        )

    index_tensor = torch.tensor(
        indices,
        dtype=torch.long,
        device=tensor.device,
    )

    return tensor.index_select(
        0,
        index_tensor,
    )


def subset_list(
        values: list[Any],
        indices: list[int],
) -> list[Any]:
    return [
        values[
            index
        ]
        for index
        in indices
    ]


def calculate_per_label_metrics(
        labels: list[int],
        predictions: list[int],
        id2label: dict[int, str],
) -> dict[str, dict[str, Any]]:
    if len(
            labels
    ) != len(
            predictions
    ):
        raise ValueError(
            "Per-label metric input "
            "lengths must match."
        )

    if not labels:
        raise ValueError(
            "Per-label metric inputs "
            "must not be empty."
        )

    valid_ids = set(
        id2label
    )

    for value in (
        labels
        + predictions
    ):
        if value not in valid_ids:
            raise ValueError(
                "Unknown label ID in "
                "per-label metrics: "
                f"{value}."
            )

    result = {}

    for label_id in sorted(
        id2label
    ):
        label_name = id2label[
            label_id
        ]

        true_positive = sum(
            1
            for (
                true_id,
                predicted_id,
            )
            in zip(
                labels,
                predictions,
                strict=True,
            )
            if (
                true_id == label_id
                and predicted_id == label_id
            )
        )

        false_positive = sum(
            1
            for (
                true_id,
                predicted_id,
            )
            in zip(
                labels,
                predictions,
                strict=True,
            )
            if (
                true_id != label_id
                and predicted_id == label_id
            )
        )

        false_negative = sum(
            1
            for (
                true_id,
                predicted_id,
            )
            in zip(
                labels,
                predictions,
                strict=True,
            )
            if (
                true_id == label_id
                and predicted_id != label_id
            )
        )

        support = (
            true_positive
            + false_negative
        )

        precision_denominator = (
            true_positive
            + false_positive
        )

        precision = (
            true_positive
            / precision_denominator
            if precision_denominator
            else 0.0
        )

        recall = (
            true_positive
            / support
            if support
            else 0.0
        )

        f1_denominator = (
            precision
            + recall
        )

        f1 = (
            2.0
            * precision
            * recall
            / f1_denominator
            if f1_denominator
            else 0.0
        )

        result[
            label_name
        ] = {
            "label_id": label_id,
            "support": support,
            "true_positive": (
                true_positive
            ),
            "false_positive": (
                false_positive
            ),
            "false_negative": (
                false_negative
            ),
            "precision": precision,
            "recall": recall,
            "f1": f1,
        }

    return result


def compare_per_label_metrics(
        labels: list[int],
        reference_predictions: list[int],
        candidate_predictions: list[int],
        id2label: dict[int, str],
) -> dict[str, dict[str, Any]]:
    reference = (
        calculate_per_label_metrics(
            labels=labels,
            predictions=(
                reference_predictions
            ),
            id2label=id2label,
        )
    )

    candidate = (
        calculate_per_label_metrics(
            labels=labels,
            predictions=(
                candidate_predictions
            ),
            id2label=id2label,
        )
    )

    result = {}

    for label_name in reference:
        reference_metrics = (
            reference[
                label_name
            ]
        )

        candidate_metrics = (
            candidate[
                label_name
            ]
        )

        result[
            label_name
        ] = {
            "label_id": (
                reference_metrics[
                    "label_id"
                ]
            ),
            "support": (
                reference_metrics[
                    "support"
                ]
            ),
            "full": (
                reference_metrics
            ),
            "first2": (
                candidate_metrics
            ),
            "first2_minus_full": {
                "precision": (
                    candidate_metrics[
                        "precision"
                    ]
                    - reference_metrics[
                        "precision"
                    ]
                ),
                "recall": (
                    candidate_metrics[
                        "recall"
                    ]
                    - reference_metrics[
                        "recall"
                    ]
                ),
                "f1": (
                    candidate_metrics[
                        "f1"
                    ]
                    - reference_metrics[
                        "f1"
                    ]
                ),
            },
        }

    return result


def calculate_id_sha256(
        sample_ids: list[str],
) -> str:
    payload = "\n".join(
        sample_ids
    ).encode(
        "utf-8"
    )

    return hashlib.sha256(
        payload
    ).hexdigest()


def find_reference_contract(
        context_report: dict[str, Any],
) -> dict[str, Any]:
    reference = context_report.get(
        "seed_reference"
    )

    if reference is not None:
        return reference

    reference = context_report.get(
        "seed42_reference"
    )

    if reference is not None:
        return reference

    raise ValueError(
        "Context report does not contain "
        "a seed reference contract."
    )


def validate_artifact_sample_ids(
        artifact: dict[str, Any],
        expected_sample_ids: list[str],
        description: str,
) -> None:
    if (
            artifact[
                "sample_ids"
            ]
            != expected_sample_ids
    ):
        raise ValueError(
            f"{description} sample IDs "
            "do not match validation."
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
            "Changed-only report already "
            "exists: "
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
            "changed-only source data."
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

    changed_indices = (
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
                changed_indices
            )
            != expected_changed
    ):
        raise RuntimeError(
            "Changed-only subset count "
            "does not reproduce source "
            "report: "
            f"expected={expected_changed}, "
            f"actual={len(changed_indices)}."
        )

    changed_sample_ids = (
        subset_list(
            values=sample_ids,
            indices=changed_indices,
        )
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

    validate_alignment(
        [
            first2_ko,
            first2_kc,
            full_ko,
            full_kc,
        ]
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

    full_labels = (
        full_ko[
            "labels"
        ]
        .detach()
        .long()
        .cpu()
    )

    changed_labels = (
        subset_tensor(
            tensor=full_labels,
            indices=changed_indices,
        )
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

    full_pair = evaluate_pair(
        ko_logits=subset_tensor(
            tensor=full_ko[
                "logits"
            ],
            indices=changed_indices,
        ),
        kc_logits=subset_tensor(
            tensor=full_kc[
                "logits"
            ],
            indices=changed_indices,
        ),
        labels=changed_labels,
        label_mapping=label_mapping,
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    first2_pair = evaluate_pair(
        ko_logits=subset_tensor(
            tensor=first2_ko[
                "logits"
            ],
            indices=changed_indices,
        ),
        kc_logits=subset_tensor(
            tensor=first2_kc[
                "logits"
            ],
            indices=changed_indices,
        ),
        labels=changed_labels,
        label_mapping=label_mapping,
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    full_metrics = {
        "ko": metric_summary(
            full_pair[
                "ko"
            ]
        ),
        "kc": metric_summary(
            full_pair[
                "kc"
            ]
        ),
        "ensemble": metric_summary(
            full_pair[
                "ensemble"
            ]
        ),
    }

    first2_metrics = {
        "ko": metric_summary(
            first2_pair[
                "ko"
            ]
        ),
        "kc": metric_summary(
            first2_pair[
                "kc"
            ]
        ),
        "ensemble": metric_summary(
            first2_pair[
                "ensemble"
            ]
        ),
    }

    delta = metric_delta(
        candidate=(
            first2_metrics[
                "ensemble"
            ]
        ),
        reference=(
            full_metrics[
                "ensemble"
            ]
        ),
    )

    labels_list = (
        changed_labels
        .tolist()
    )

    full_predictions = (
        full_pair[
            "ensemble"
        ][
            "predicted_ids"
        ]
    )

    first2_predictions = (
        first2_pair[
            "ensemble"
        ][
            "predicted_ids"
        ]
    )

    transitions = (
        compare_prediction_transitions(
            labels=labels_list,
            reference_predictions=(
                full_predictions
            ),
            candidate_predictions=(
                first2_predictions
            ),
            id2label=dict(
                label_mapping.id2label
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    per_label = (
        compare_per_label_metrics(
            labels=labels_list,
            reference_predictions=(
                full_predictions
            ),
            candidate_predictions=(
                first2_predictions
            ),
            id2label=dict(
                label_mapping.id2label
            ),
        )
    )

    report = {
        "experiment": (
            "T0-Q17-C3-A-"
            f"seed{seed}-"
            "changed-only-context-ablation"
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
            "candidate_view": (
                "first2"
            ),
            "reference_view": (
                "full"
            ),
            "subset_criterion": (
                "first2.text != full.text"
            ),
            "ensemble_type": (
                "weighted_raw_logits"
            ),
            "ko_weight": 0.5,
            "kc_weight": 0.5,
        },
        "dataset": {
            "validation_samples": len(
                validation
            ),
            "changed_only_samples": len(
                changed_indices
            ),
            "excluded_unchanged_samples": (
                len(
                    validation
                )
                - len(
                    changed_indices
                )
            ),
            "changed_sample_ids_sha256": (
                calculate_id_sha256(
                    changed_sample_ids
                )
            ),
        },
        "metrics": {
            "full": full_metrics,
            "first2": first2_metrics,
        },
        "first2_minus_full": delta,
        "transitions": transitions,
        "per_label": per_label,
    }

    save_report(
        report=report,
        output_path=report_path,
    )

    print()
    print(
        f"Seed{seed} changed-only "
        "context analysis completed"
    )
    print()
    print(
        "Validation samples: "
        f"{len(validation)}"
    )
    print(
        "Changed-only samples: "
        f"{len(changed_indices)}"
    )
    print(
        "Excluded unchanged samples: "
        f"{len(validation) - len(changed_indices)}"
    )
    print()
    print(
        "FIRST2 Fine Macro F1: "
        f"{first2_metrics['ensemble']['fine_macro_f1']:.6f}"
    )
    print(
        "FULL Fine Macro F1: "
        f"{full_metrics['ensemble']['fine_macro_f1']:.6f}"
    )
    print(
        "FIRST2 - FULL Fine F1: "
        f"{delta['fine_macro_f1']:+.6f}"
    )
    print()
    print(
        "FIRST2 Coarse Macro F1: "
        f"{first2_metrics['ensemble']['coarse_macro_f1']:.6f}"
    )
    print(
        "FULL Coarse Macro F1: "
        f"{full_metrics['ensemble']['coarse_macro_f1']:.6f}"
    )
    print(
        "FIRST2 - FULL Coarse F1: "
        f"{delta['coarse_macro_f1']:+.6f}"
    )
    print()
    print(
        "Fine rescued/regressed: "
        f"{transitions['fine']['rescued_vs_reference']}"
        "/"
        f"{transitions['fine']['regressed_vs_reference']}"
    )
    print(
        "Fine disagreement rate: "
        f"{transitions['fine']['disagreement_rate']:.6f}"
    )
    print()
    print(
        "Report: "
        f"{args.report}"
    )
    print()
    print(
        f"Q17-C3-A SEED{seed} "
        "CHANGED-ONLY: PASS"
    )


if __name__ == "__main__":
    main()