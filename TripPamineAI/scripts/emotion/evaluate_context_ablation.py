import argparse
import math
import sys
from pathlib import Path
from typing import Any

import torch
import transformers

from scripts.emotion.build_context_classification_dataset import (
    load_normalized_records_for_ids,
    validate_reference_match,
)
from scripts.emotion.evaluate_layernorm_ensemble import (
    evaluate_logits,
    extract_validation_logits,
    load_report,
    save_logits,
    validate_report_dataset,
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
    validate_positive_integer,
)
from trippamine_ai.evaluation.emotion.context_ablation import (
    CONTEXT_VIEW_ORDER,
    build_context_views,
    compare_prediction_transitions,
    summarize_view_texts,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_fine_to_coarse,
)


DEFAULT_BATCH_SIZE = 64
DEFAULT_MAX_LENGTH = 96
DEFAULT_METRIC_TOLERANCE = 1e-6

KO_MODEL_KEY = "koelectra-v3"
KC_MODEL_KEY = "kcelectra-v2022"

ENSEMBLE_WEIGHT = 0.5

METRIC_KEYS = (
    "fine_accuracy",
    "fine_macro_f1",
    "coarse_accuracy",
    "coarse_macro_f1",
)


def resolved_path(
        path: Path,
) -> Path:
    return path.expanduser().resolve()


def validate_path_matches(
        actual: Path,
        expected_value: str,
        description: str,
) -> None:
    expected = resolved_path(
        Path(
            expected_value
        )
    )

    resolved_actual = resolved_path(
        actual
    )

    if resolved_actual != expected:
        raise ValueError(
            f"{description} path mismatch: "
            f"expected={expected}, "
            f"actual={resolved_actual}."
        )


def validate_ensemble_contract(
        report: dict[str, Any],
        ko_model: Path,
        ko_report: Path,
        kc_model: Path,
        kc_report: Path,
        full_ko_logits: Path,
        full_kc_logits: Path,
) -> None:
    try:
        models = report[
            "models"
        ]

        ko = models[
            "koelectra_v3"
        ]

        kc = models[
            "kcelectra_v2022"
        ]

    except KeyError as error:
        raise ValueError(
            "Ensemble report is "
            "missing model contract data."
        ) from error

    checks = (
        (
            ko_model,
            ko[
                "model_dir"
            ],
            "Ko model",
        ),
        (
            ko_report,
            ko[
                "training_report"
            ],
            "Ko training report",
        ),
        (
            kc_model,
            kc[
                "model_dir"
            ],
            "Kc model",
        ),
        (
            kc_report,
            kc[
                "training_report"
            ],
            "Kc training report",
        ),
        (
            full_ko_logits,
            ko[
                "artifact"
            ][
                "file"
            ],
            "Full Ko logits",
        ),
        (
            full_kc_logits,
            kc[
                "artifact"
            ][
                "file"
            ],
            "Full Kc logits",
        ),
    )

    for (
            actual,
            expected,
            description,
    ) in checks:
        validate_path_matches(
            actual=actual,
            expected_value=expected,
            description=description,
        )


def validate_model_key(
        artifact: dict[str, Any],
        expected: str,
        description: str,
) -> None:
    actual = artifact.get(
        "model_key"
    )

    if actual != expected:
        raise ValueError(
            f"{description} model_key "
            "mismatch: "
            f"expected={expected}, "
            f"actual={actual}."
        )


def find_fixed_ensemble_metrics(
        report: dict[str, Any],
) -> dict[str, float]:
    try:
        grid = report[
            "grid"
        ]

    except KeyError as error:
        raise ValueError(
            "Ensemble report does not "
            "contain grid results."
        ) from error

    if not isinstance(
            grid,
            list,
    ):
        raise ValueError(
            "Ensemble grid must "
            "be a list."
        )

    matches = [
        result
        for result
        in grid
        if (
            math.isclose(
                float(
                    result[
                        "kc_weight"
                    ]
                ),
                ENSEMBLE_WEIGHT,
                rel_tol=0.0,
                abs_tol=1e-12,
            )
            and math.isclose(
                float(
                    result[
                        "ko_weight"
                    ]
                ),
                ENSEMBLE_WEIGHT,
                rel_tol=0.0,
                abs_tol=1e-12,
            )
        )
    ]

    if len(
            matches
    ) != 1:
        raise ValueError(
            "Expected exactly one fixed "
            "50:50 ensemble row, found "
            f"{len(matches)}."
        )

    result = matches[
        0
    ]

    return {
        metric_key: float(
            result[
                metric_key
            ]
        )
        for metric_key
        in METRIC_KEYS
    }


def metric_summary(
        metrics: dict[str, Any],
) -> dict[str, float]:
    return {
        metric_key: float(
            metrics[
                metric_key
            ]
        )
        for metric_key
        in METRIC_KEYS
    }


def validate_metrics(
        actual: dict[str, Any],
        expected: dict[str, Any],
        description: str,
        tolerance: float,
) -> None:
    for metric_key in METRIC_KEYS:
        actual_value = float(
            actual[
                metric_key
            ]
        )

        expected_value = float(
            expected[
                metric_key
            ]
        )

        if not math.isclose(
                actual_value,
                expected_value,
                rel_tol=0.0,
                abs_tol=tolerance,
        ):
            raise RuntimeError(
                f"{description} metric "
                "reproduction failed for "
                f"{metric_key}: "
                f"expected={expected_value}, "
                f"actual={actual_value}."
            )


def metric_delta(
        candidate: dict[str, Any],
        reference: dict[str, Any],
) -> dict[str, float]:
    return {
        metric_key: (
            float(
                candidate[
                    metric_key
                ]
            )
            - float(
                reference[
                    metric_key
                ]
            )
        )
        for metric_key
        in METRIC_KEYS
    }


def build_ablation_views(
        validation: list[Any],
        normalized_by_id: dict[
            str,
            Any,
        ],
) -> dict[
    str,
    list[Any],
]:
    views = {
        view_name: []
        for view_name
        in CONTEXT_VIEW_ORDER
    }

    for reference in validation:
        record = normalized_by_id.get(
            reference.id
        )

        if record is None:
            raise ValueError(
                "Normalized record missing "
                "for validation sample: "
                f"{reference.id}."
            )

        validate_reference_match(
            reference=reference,
            record=record,
        )

        sample_views = (
            build_context_views(
                reference=reference,
                record=record,
            )
        )

        for view_name in (
            CONTEXT_VIEW_ORDER
        ):
            views[
                view_name
            ].append(
                sample_views[
                    view_name
                ]
            )

    return views


def validate_view_labels(
        labels: torch.Tensor,
        expected: torch.Tensor,
        description: str,
) -> None:
    if not torch.equal(
            labels,
            expected,
    ):
        raise RuntimeError(
            f"{description} labels do "
            "not match full validation."
        )


def evaluate_pair(
        ko_logits: torch.Tensor,
        kc_logits: torch.Tensor,
        labels: torch.Tensor,
        label_mapping: EmotionLabelMapping,
        fine_to_coarse: dict[
            str,
            str,
        ],
) -> dict[str, Any]:
    ensemble_logits = combine_logits(
        first_logits=ko_logits,
        second_logits=kc_logits,
        first_weight=(
            ENSEMBLE_WEIGHT
        ),
        second_weight=(
            ENSEMBLE_WEIGHT
        ),
    )

    return {
        "ko": evaluate_logits(
            logits=ko_logits,
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        ),
        "kc": evaluate_logits(
            logits=kc_logits,
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        ),
        "ensemble": evaluate_logits(
            logits=ensemble_logits,
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        ),
        "ensemble_logits": (
            ensemble_logits
        ),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run Ko/Kc fixed 50:50 "
            "inference-only context ablation "
            "for FIRST1, FIRST2, and FULL."
        )
    )

    parser.add_argument(
        "--seed",
        required=True,
        type=int,
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--normalized-training",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--normalized-validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--ensemble-report",
        dest="ensemble_report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--ko-model",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--ko-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc-model",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--full-ko-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--full-kc-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output-dir",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--max-length",
        type=int,
        default=DEFAULT_MAX_LENGTH,
    )

    parser.add_argument(
        "--batch-size",
        type=int,
        default=DEFAULT_BATCH_SIZE,
    )

    parser.add_argument(
        "--metric-tolerance",
        type=float,
        default=(
            DEFAULT_METRIC_TOLERANCE
        ),
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.seed < 0:
        raise ValueError(
            "Seed must be non-negative."
        )

    if args.output_dir.exists():
        raise FileExistsError(
            "Context ablation output "
            "directory already exists: "
            f"{args.output_dir}"
        )

    if args.report.exists():
        raise FileExistsError(
            "Context ablation report "
            "already exists: "
            f"{args.report}"
        )

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.batch_size,
        "Evaluation batch size",
    )

    if (
            not math.isfinite(
                args.metric_tolerance
            )
            or args.metric_tolerance < 0.0
    ):
        raise ValueError(
            "Metric tolerance must be "
            "finite and non-negative."
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available."
        )

    ensemble_report = load_report(
        args.ensemble_report
    )

    ko_report = load_report(
        args.ko_report
    )

    kc_report = load_report(
        args.kc_report
    )

    validate_report_dataset(
        ensemble_report,
        args.validation,
    )

    validate_report_dataset(
        ko_report,
        args.validation,
    )

    validate_report_dataset(
        kc_report,
        args.validation,
    )

    validate_ensemble_contract(
        report=ensemble_report,
        ko_model=args.ko_model,
        ko_report=args.ko_report,
        kc_model=args.kc_model,
        kc_report=args.kc_report,
        full_ko_logits=(
            args.full_ko_logits
        ),
        full_kc_logits=(
            args.full_kc_logits
        ),
    )

    validation = read_dataset(
        args.validation,
        "validation",
    )

    sample_ids = [
        sample.id
        for sample
        in validation
    ]

    required_ids = set(
        sample_ids
    )

    if len(
            required_ids
    ) != len(
            sample_ids
    ):
        raise ValueError(
            "Validation sample IDs "
            "contain duplicates."
        )

    normalized_by_id = (
        load_normalized_records_for_ids(
            file_paths=[
                args.normalized_training,
                args.normalized_validation,
            ],
            required_ids=(
                required_ids
            ),
        )
    )

    views = build_ablation_views(
        validation=validation,
        normalized_by_id=(
            normalized_by_id
        ),
    )

    view_text_summary = (
        summarize_view_texts(
            views
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

    full_ko_artifact = (
        load_logit_artifact(
            args.full_ko_logits
        )
    )

    full_kc_artifact = (
        load_logit_artifact(
            args.full_kc_logits
        )
    )

    validate_alignment(
        [
            full_ko_artifact,
            full_kc_artifact,
        ]
    )

    validate_model_key(
        full_ko_artifact,
        KO_MODEL_KEY,
        "Full Ko",
    )

    validate_model_key(
        full_kc_artifact,
        KC_MODEL_KEY,
        "Full Kc",
    )

    if (
            full_ko_artifact[
                "sample_ids"
            ]
            != sample_ids
    ):
        raise ValueError(
            "Full Ko logit sample IDs "
            "do not match validation."
        )

    full_labels = (
        full_ko_artifact[
            "labels"
        ]
        .detach()
        .long()
        .cpu()
    )

    full_pair = evaluate_pair(
        ko_logits=(
            full_ko_artifact[
                "logits"
            ]
        ),
        kc_logits=(
            full_kc_artifact[
                "logits"
            ]
        ),
        labels=full_labels,
        label_mapping=(
            label_mapping
        ),
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    validate_metrics(
        actual=(
            full_pair[
                "ko"
            ]
        ),
        expected=(
            ensemble_report[
                "models"
            ][
                "koelectra_v3"
            ][
                "metrics"
            ]
        ),
        description=(
            "Full Ko"
        ),
        tolerance=(
            args.metric_tolerance
        ),
    )

    validate_metrics(
        actual=(
            full_pair[
                "kc"
            ]
        ),
        expected=(
            ensemble_report[
                "models"
            ][
                "kcelectra_v2022"
            ][
                "metrics"
            ]
        ),
        description=(
            "Full Kc"
        ),
        tolerance=(
            args.metric_tolerance
        ),
    )

    fixed_metrics = (
        find_fixed_ensemble_metrics(
            ensemble_report
        )
    )

    validate_metrics(
        actual=(
            full_pair[
                "ensemble"
            ]
        ),
        expected=(
            fixed_metrics
        ),
        description=(
            "Full fixed 50:50 ensemble"
        ),
        tolerance=(
            args.metric_tolerance
        ),
    )

    device_index = (
        torch.cuda.current_device()
    )

    device = torch.device(
        "cuda",
        device_index,
    )

    extracted = {}

    for view_name in (
        "first1",
        "first2",
    ):
        (
            ko_logits,
            ko_labels,
            ko_extraction,
        ) = extract_validation_logits(
            model_dir=args.ko_model,
            validation=(
                views[
                    view_name
                ]
            ),
            label_mapping=(
                label_mapping
            ),
            max_length=(
                args.max_length
            ),
            batch_size=(
                args.batch_size
            ),
            device=device,
            description=(
                f"KoELECTRA-{view_name}"
            ),
        )

        validate_view_labels(
            labels=ko_labels,
            expected=full_labels,
            description=(
                f"Ko {view_name}"
            ),
        )

        (
            kc_logits,
            kc_labels,
            kc_extraction,
        ) = extract_validation_logits(
            model_dir=args.kc_model,
            validation=(
                views[
                    view_name
                ]
            ),
            label_mapping=(
                label_mapping
            ),
            max_length=(
                args.max_length
            ),
            batch_size=(
                args.batch_size
            ),
            device=device,
            description=(
                f"KcELECTRA-{view_name}"
            ),
        )

        validate_view_labels(
            labels=kc_labels,
            expected=full_labels,
            description=(
                f"Kc {view_name}"
            ),
        )

        pair = evaluate_pair(
            ko_logits=ko_logits,
            kc_logits=kc_logits,
            labels=full_labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )

        extracted[
            view_name
        ] = {
            "ko_logits": ko_logits,
            "kc_logits": kc_logits,
            "ko_extraction": (
                ko_extraction
            ),
            "kc_extraction": (
                kc_extraction
            ),
            "pair": pair,
        }

    args.output_dir.mkdir(
        parents=True,
        exist_ok=False,
    )

    artifact_reports = {}

    for view_name in (
        "first1",
        "first2",
    ):
        data = extracted[
            view_name
        ]

        ko_artifact_report = save_logits(
            path=(
                args.output_dir
                / (
                    f"{view_name}-ko-"
                    "validation-logits.pt"
                )
            ),
            logits=(
                data[
                    "ko_logits"
                ]
            ),
            labels=full_labels,
            sample_ids=sample_ids,
            model_key=(
                KO_MODEL_KEY
            ),
        )

        kc_artifact_report = save_logits(
            path=(
                args.output_dir
                / (
                    f"{view_name}-kc-"
                    "validation-logits.pt"
                )
            ),
            logits=(
                data[
                    "kc_logits"
                ]
            ),
            labels=full_labels,
            sample_ids=sample_ids,
            model_key=(
                KC_MODEL_KEY
            ),
        )

        artifact_reports[
            view_name
        ] = {
            "ko": (
                ko_artifact_report
            ),
            "kc": (
                kc_artifact_report
            ),
        }

    view_metrics = {
        "full": {
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
            "ensemble": (
                metric_summary(
                    full_pair[
                        "ensemble"
                    ]
                )
            ),
        }
    }

    for view_name in (
        "first1",
        "first2",
    ):
        pair = extracted[
            view_name
        ][
            "pair"
        ]

        view_metrics[
            view_name
        ] = {
            "ko": metric_summary(
                pair[
                    "ko"
                ]
            ),
            "kc": metric_summary(
                pair[
                    "kc"
                ]
            ),
            "ensemble": (
                metric_summary(
                    pair[
                        "ensemble"
                    ]
                )
            ),
        }

    ensemble_deltas = {
        view_name: (
            metric_delta(
                candidate=(
                    view_metrics[
                        view_name
                    ][
                        "ensemble"
                    ]
                ),
                reference=(
                    view_metrics[
                        "full"
                    ][
                        "ensemble"
                    ]
                ),
            )
        )
        for view_name
        in (
            "first1",
            "first2",
        )
    }

    full_predictions = (
        full_pair[
            "ensemble"
        ][
            "predicted_ids"
        ]
    )

    transition_analysis = {
        "first1_vs_full": (
            compare_prediction_transitions(
                labels=(
                    full_labels.tolist()
                ),
                reference_predictions=(
                    full_predictions
                ),
                candidate_predictions=(
                    extracted[
                        "first1"
                    ][
                        "pair"
                    ][
                        "ensemble"
                    ][
                        "predicted_ids"
                    ]
                ),
                id2label=dict(
                    label_mapping.id2label
                ),
                fine_to_coarse=(
                    fine_to_coarse
                ),
            )
        ),
        "first2_vs_full": (
            compare_prediction_transitions(
                labels=(
                    full_labels.tolist()
                ),
                reference_predictions=(
                    full_predictions
                ),
                candidate_predictions=(
                    extracted[
                        "first2"
                    ][
                        "pair"
                    ][
                        "ensemble"
                    ][
                        "predicted_ids"
                    ]
                ),
                id2label=dict(
                    label_mapping.id2label
                ),
                fine_to_coarse=(
                    fine_to_coarse
                ),
            )
        ),
        "first2_vs_first1": (
            compare_prediction_transitions(
                labels=(
                    full_labels.tolist()
                ),
                reference_predictions=(
                    extracted[
                        "first1"
                    ][
                        "pair"
                    ][
                        "ensemble"
                    ][
                        "predicted_ids"
                    ]
                ),
                candidate_predictions=(
                    extracted[
                        "first2"
                    ][
                        "pair"
                    ][
                        "ensemble"
                    ][
                        "predicted_ids"
                    ]
                ),
                id2label=dict(
                    label_mapping.id2label
                ),
                fine_to_coarse=(
                    fine_to_coarse
                ),
            )
        ),
    }

    report = {
        "experiment": (
            "T0-Q17-C2-B-"
            f"seed{args.seed}-"
            "context-ablation"
        ),
        "versions": {
            "python": sys.version,
            "torch": (
                torch.__version__
            ),
            "transformers": (
                transformers.__version__
            ),
        },
        "hardware": {
            "device_index": (
                device_index
            ),
            "device_name": (
                torch.cuda
                .get_device_name(
                    device_index
                )
            ),
        },
        "dataset": {
            "validation": {
                "file": str(
                    args.validation
                ),
                "samples": len(
                    validation
                ),
                "sha256": (
                    calculate_sha256(
                        args.validation
                    )
                ),
            },
            "normalized_sources": {
                "training": {
                    "file": str(
                        args.normalized_training
                    ),
                    "sha256": (
                        calculate_sha256(
                            args.normalized_training
                        )
                    ),
                },
                "validation": {
                    "file": str(
                        args.normalized_validation
                    ),
                    "sha256": (
                        calculate_sha256(
                            args.normalized_validation
                        )
                    ),
                },
            },
            "test_split_used": False,
        },
        "protocol": {
            "seed": args.seed,
            "inference_only": True,
            "retraining": False,
            "views": {
                "first1": (
                    "first non-empty human turn"
                ),
                "first2": (
                    "first two non-empty human "
                    "turns joined by newline"
                ),
                "full": (
                    "all non-empty human turns "
                    "joined by newline"
                ),
            },
            "assistant_turns_included": False,
            "ensemble_type": (
                "weighted_raw_logits"
            ),
            "ko_weight": (
                ENSEMBLE_WEIGHT
            ),
            "kc_weight": (
                ENSEMBLE_WEIGHT
            ),
            "alpha_search": False,
            "max_length": (
                args.max_length
            ),
            "batch_size": (
                args.batch_size
            ),
            "selection_split": (
                "validation"
            ),
            "full_logits_reused": True,
            "full_inference_rerun": False,
            "test_split_used": False,
        },
        "seed_reference": {
            "seed": args.seed,
            "ensemble_report": {
                "file": str(
                    args.ensemble_report
                ),
                "sha256": (
                    calculate_sha256(
                        args.ensemble_report
                    )
                ),
            },
            "ko_model": str(
                args.ko_model
            ),
            "ko_report": {
                "file": str(
                    args.ko_report
                ),
                "sha256": (
                    calculate_sha256(
                        args.ko_report
                    )
                ),
            },
            "kc_model": str(
                args.kc_model
            ),
            "kc_report": {
                "file": str(
                    args.kc_report
                ),
                "sha256": (
                    calculate_sha256(
                        args.kc_report
                    )
                ),
            },
            "full_ko_logits": {
                "file": str(
                    args.full_ko_logits
                ),
                "sha256": (
                    calculate_sha256(
                        args.full_ko_logits
                    )
                ),
            },
            "full_kc_logits": {
                "file": str(
                    args.full_kc_logits
                ),
                "sha256": (
                    calculate_sha256(
                        args.full_kc_logits
                    )
                ),
            },
        },
        "view_text_summary": (
            view_text_summary
        ),
        "metrics": (
            view_metrics
        ),
        "ensemble_delta_vs_full": (
            ensemble_deltas
        ),
        "transitions": (
            transition_analysis
        ),
        "extraction": {
            view_name: {
                "ko": (
                    extracted[
                        view_name
                    ][
                        "ko_extraction"
                    ]
                ),
                "kc": (
                    extracted[
                        view_name
                    ][
                        "kc_extraction"
                    ]
                ),
            }
            for view_name
            in (
                "first1",
                "first2",
            )
        },
        "artifacts": (
            artifact_reports
        ),
    }

    save_report(
        report=report,
        output_path=(
            args.report
        ),
    )

    print()

    print(
        f"Seed{args.seed} context ablation "
        "completed"
    )

    print()

    print(
        "Validation samples: "
        f"{len(validation)}"
    )

    print(
        "Test split used: False"
    )

    print(
        "Retraining: False"
    )

    print()

    for view_name in (
        CONTEXT_VIEW_ORDER
    ):
        metrics = (
            view_metrics[
                view_name
            ][
                "ensemble"
            ]
        )

        print(
            f"{view_name.upper()} "
            "Fine Macro F1: "
            f"{metrics['fine_macro_f1']:.6f}"
        )

        print(
            f"{view_name.upper()} "
            "Coarse Macro F1: "
            f"{metrics['coarse_macro_f1']:.6f}"
        )

    print()

    for view_name in (
        "first1",
        "first2",
    ):
        delta = ensemble_deltas[
            view_name
        ]

        transition = (
            transition_analysis[
                f"{view_name}_vs_full"
            ][
                "fine"
            ]
        )

        print(
            f"{view_name.upper()} - FULL "
            "Fine F1: "
            f"{delta['fine_macro_f1']:+.6f}"
        )

        print(
            f"{view_name.upper()} "
            "fine rescued/regressed: "
            f"{transition['rescued_vs_reference']}"
            "/"
            f"{transition['regressed_vs_reference']}"
        )

    print()

    print(
        "Output directory: "
        f"{args.output_dir}"
    )

    print(
        "Report: "
        f"{args.report}"
    )

    print()

    print(
        f"Q17-C2-B SEED{args.seed} "
        "CONTEXT ABLATION: PASS"
    )


if __name__ == "__main__":
    main()