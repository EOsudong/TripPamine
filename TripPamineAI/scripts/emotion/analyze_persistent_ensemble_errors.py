import argparse
import sys
from pathlib import Path
from typing import Any

import torch

from scripts.emotion.evaluate_layernorm_ensemble import (
    evaluate_logits,
)
from scripts.emotion.evaluate_layernorm_seed_ensemble import (
    combine_logits,
    load_logit_artifact,
    metric_summary,
    validate_alignment,
)
from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    read_dataset,
    save_report,
)
from trippamine_ai.evaluation.emotion.persistent_ensemble_error_analysis import (
    analyze_persistent_ensemble_errors,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_fine_to_coarse,
)


DEFAULT_TOP_K = 20

KO_MODEL_KEY = "koelectra-v3"
KC_MODEL_KEY = "kcelectra-v2022"

ENSEMBLE_WEIGHT = 0.5


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


def build_fixed_pair_logits(
        ko_artifact: dict[
            str,
            Any,
        ],
        kc_artifact: dict[
            str,
            Any,
        ],
) -> torch.Tensor:
    validate_alignment(
        [
            ko_artifact,
            kc_artifact,
        ]
    )

    return combine_logits(
        first_logits=(
            ko_artifact[
                "logits"
            ]
        ),
        second_logits=(
            kc_artifact[
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


def artifact_summary(
        path: Path,
        artifact: dict[
            str,
            Any,
        ],
) -> dict[str, Any]:
    return {
        "file": str(
            path
        ),
        "sha256": (
            calculate_sha256(
                path
            )
        ),
        "model_key": (
            artifact[
                "model_key"
            ]
        ),
        "logits_shape": list(
            artifact[
                "logits"
            ].shape
        ),
        "labels_shape": list(
            artifact[
                "labels"
            ].shape
        ),
        "sample_count": len(
            artifact[
                "sample_ids"
            ]
        ),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Audit persistent errors "
            "across three fixed 50:50 "
            "KoELECTRA/KcELECTRA "
            "validation ensembles."
        )
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed42-ko-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed42-kc-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed123-ko-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed123-kc-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed2026-ko-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed2026-kc-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--top-k",
        type=int,
        default=DEFAULT_TOP_K,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.report.exists():
        raise FileExistsError(
            "Report already exists: "
            f"{args.report}"
        )

    if args.top_k <= 0:
        raise ValueError(
            "top_k must be "
            "greater than zero."
        )

    validation = read_dataset(
        args.validation,
        "validation",
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

    seed42_ko = (
        load_logit_artifact(
            args.seed42_ko_logits
        )
    )

    seed42_kc = (
        load_logit_artifact(
            args.seed42_kc_logits
        )
    )

    seed123_ko = (
        load_logit_artifact(
            args.seed123_ko_logits
        )
    )

    seed123_kc = (
        load_logit_artifact(
            args.seed123_kc_logits
        )
    )

    seed2026_ko = (
        load_logit_artifact(
            args.seed2026_ko_logits
        )
    )

    seed2026_kc = (
        load_logit_artifact(
            args.seed2026_kc_logits
        )
    )

    artifacts = [
        seed42_ko,
        seed42_kc,
        seed123_ko,
        seed123_kc,
        seed2026_ko,
        seed2026_kc,
    ]

    validate_alignment(
        artifacts
    )

    validate_model_key(
        seed42_ko,
        KO_MODEL_KEY,
        "seed42 Ko",
    )

    validate_model_key(
        seed42_kc,
        KC_MODEL_KEY,
        "seed42 Kc",
    )

    validate_model_key(
        seed123_ko,
        KO_MODEL_KEY,
        "seed123 Ko",
    )

    validate_model_key(
        seed123_kc,
        KC_MODEL_KEY,
        "seed123 Kc",
    )

    validate_model_key(
        seed2026_ko,
        KO_MODEL_KEY,
        "seed2026 Ko",
    )

    validate_model_key(
        seed2026_kc,
        KC_MODEL_KEY,
        "seed2026 Kc",
    )

    expected_samples = len(
        validation
    )

    labels = seed42_ko[
        "labels"
    ]

    actual_samples = int(
        labels.shape[
            0
        ]
    )

    if (
            expected_samples
            != actual_samples
    ):
        raise ValueError(
            "Validation/logit sample "
            "count mismatch: "
            f"validation={expected_samples}, "
            f"logits={actual_samples}."
        )

    validation_ids = [
        sample.id
        for sample
        in validation
    ]

    if (
            validation_ids
            != seed42_ko[
                "sample_ids"
            ]
    ):
        raise ValueError(
            "Validation sample IDs "
            "do not match saved logits."
        )

    seed42_logits = (
        build_fixed_pair_logits(
            seed42_ko,
            seed42_kc,
        )
    )

    seed123_logits = (
        build_fixed_pair_logits(
            seed123_ko,
            seed123_kc,
        )
    )

    seed2026_logits = (
        build_fixed_pair_logits(
            seed2026_ko,
            seed2026_kc,
        )
    )

    seed_logits = {
        "42": seed42_logits,
        "123": seed123_logits,
        "2026": seed2026_logits,
    }

    seed_metrics = {
        seed_name: (
            evaluate_logits(
                logits=logits,
                labels=labels,
                label_mapping=(
                    label_mapping
                ),
                fine_to_coarse=(
                    fine_to_coarse
                ),
            )
        )
        for seed_name, logits
        in seed_logits.items()
    }

    analysis = (
        analyze_persistent_ensemble_errors(
            labels=labels,
            sample_ids=(
                seed42_ko[
                    "sample_ids"
                ]
            ),
            ensemble_logits=(
                seed_logits
            ),
            id2label=dict(
                label_mapping.id2label
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
            top_k=(
                args.top_k
            ),
        )
    )

    report = {
        "experiment": (
            "T0-Q17-A-three-seed-"
            "persistent-ensemble-"
            "error-audit"
        ),
        "versions": {
            "python": (
                sys.version
            ),
            "torch": (
                torch.__version__
            ),
        },
        "dataset": {
            "validation": {
                "file": str(
                    args.validation
                ),
                "samples": (
                    expected_samples
                ),
                "sha256": (
                    calculate_sha256(
                        args.validation
                    )
                ),
            },
            "test_split_used": False,
        },
        "protocol": {
            "seed_count": 3,
            "seeds": [
                42,
                123,
                2026,
            ],
            "backbone_ensemble": {
                "ko_weight": (
                    ENSEMBLE_WEIGHT
                ),
                "kc_weight": (
                    ENSEMBLE_WEIGHT
                ),
                "ensemble_type": (
                    "weighted_raw_logits"
                ),
            },
            "confidence_source": (
                "softmax_of_fixed_"
                "ensemble_logits"
            ),
            "confidence_calibrated": False,
            "selection_split": (
                "validation"
            ),
            "test_split_used": False,
            "top_k": (
                args.top_k
            ),
        },
        "input_artifacts": {
            "seed42": {
                "ko": (
                    artifact_summary(
                        args.seed42_ko_logits,
                        seed42_ko,
                    )
                ),
                "kc": (
                    artifact_summary(
                        args.seed42_kc_logits,
                        seed42_kc,
                    )
                ),
            },
            "seed123": {
                "ko": (
                    artifact_summary(
                        args.seed123_ko_logits,
                        seed123_ko,
                    )
                ),
                "kc": (
                    artifact_summary(
                        args.seed123_kc_logits,
                        seed123_kc,
                    )
                ),
            },
            "seed2026": {
                "ko": (
                    artifact_summary(
                        args.seed2026_ko_logits,
                        seed2026_ko,
                    )
                ),
                "kc": (
                    artifact_summary(
                        args.seed2026_kc_logits,
                        seed2026_kc,
                    )
                ),
            },
        },
        "seed_metrics": {
            seed_name: (
                metric_summary(
                    metrics
                )
            )
            for seed_name, metrics
            in seed_metrics.items()
        },
        "analysis": (
            analysis.model_dump(
                mode="json"
            )
        ),
    }

    save_report(
        report=report,
        output_path=(
            args.report
        ),
    )

    persistent_confidence = (
        analysis.confidence[
            "persistent_error"
        ]
    )

    print()

    print(
        "Three-seed persistent "
        "ensemble error audit completed"
    )

    print()

    print(
        "Validation samples: "
        f"{expected_samples}"
    )

    print(
        "Test split used: False"
    )

    print()

    for seed_name in [
        "42",
        "123",
        "2026",
    ]:
        metrics = seed_metrics[
            seed_name
        ]

        print(
            f"seed{seed_name} fixed 50:50 "
            "Fine Macro F1: "
            f"{metrics['fine_macro_f1']:.6f}"
        )

    print()

    print(
        "Stable correct: "
        f"{analysis.stable_correct} "
        f"({analysis.stable_correct_rate:.2%})"
    )

    print(
        "Persistent errors: "
        f"{analysis.persistent_errors} "
        f"({analysis.persistent_error_rate:.2%})"
    )

    print(
        "Mixed outcomes: "
        f"{analysis.mixed_outcomes} "
        f"({analysis.mixed_outcome_rate:.2%})"
    )

    print()

    print(
        "Persistent same wrong label: "
        f"{analysis.persistent_same_wrong_prediction}"
    )

    print(
        "Persistent varying wrong label: "
        f"{analysis.persistent_varying_wrong_prediction}"
    )

    print()

    print(
        "Persistent fine-only errors: "
        f"{analysis.persistent_fine_only_errors}"
    )

    print(
        "Persistent cross-coarse errors: "
        f"{analysis.persistent_cross_coarse_errors}"
    )

    print(
        "Persistent mixed-coarse errors: "
        f"{analysis.persistent_mixed_coarse_errors}"
    )

    print()

    print(
        "Persistent error mean confidence: "
        f"{persistent_confidence.mean}"
    )

    print(
        "Persistent error p95 confidence: "
        f"{persistent_confidence.p95}"
    )

    print()

    print(
        "Report: "
        f"{args.report}"
    )

    print()

    print(
        "Q17-A PERSISTENT ERROR AUDIT: PASS"
    )


if __name__ == "__main__":
    main()