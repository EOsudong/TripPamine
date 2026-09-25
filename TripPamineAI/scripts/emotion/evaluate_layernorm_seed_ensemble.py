import argparse
import math
from pathlib import Path
from typing import Any

import torch

from scripts.emotion.evaluate_layernorm_ensemble import (
    calculate_complementarity,
    evaluate_logits,
)
from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    read_dataset,
    save_report,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_fine_to_coarse,
)


EXPECTED_FINE_LABEL_COUNT = 60
EXPECTED_KC_SEED_COUNT = 3


def load_logit_artifact(
        path: Path,
) -> dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(
            "Logit artifact not found: "
            f"{path}"
        )

    artifact = torch.load(
        path,
        map_location="cpu",
    )

    if not isinstance(
            artifact,
            dict,
    ):
        raise ValueError(
            "Logit artifact must "
            "contain a dictionary."
        )

    required_keys = {
        "model_key",
        "logits",
        "labels",
        "sample_ids",
    }

    missing_keys = (
        required_keys
        - set(
            artifact.keys()
        )
    )

    if missing_keys:
        raise ValueError(
            "Logit artifact is missing "
            "required keys: "
            f"{sorted(missing_keys)}"
        )

    logits = artifact[
        "logits"
    ]

    labels = artifact[
        "labels"
    ]

    sample_ids = artifact[
        "sample_ids"
    ]

    if not torch.is_tensor(
            logits
    ):
        raise ValueError(
            "Artifact logits must "
            "be a tensor."
        )

    if not torch.is_tensor(
            labels
    ):
        raise ValueError(
            "Artifact labels must "
            "be a tensor."
        )

    if logits.ndim != 2:
        raise ValueError(
            "Artifact logits must "
            "be rank 2."
        )

    if (
            logits.shape[
                1
            ]
            != EXPECTED_FINE_LABEL_COUNT
    ):
        raise ValueError(
            "Expected "
            f"{EXPECTED_FINE_LABEL_COUNT} "
            "fine logits, found "
            f"{logits.shape[1]}."
        )

    if labels.ndim != 1:
        raise ValueError(
            "Artifact labels must "
            "be rank 1."
        )

    if (
            logits.shape[
                0
            ]
            != labels.shape[
                0
            ]
    ):
        raise ValueError(
            "Artifact logits/labels "
            "sample count mismatch."
        )

    if (
            len(
                sample_ids
            )
            != labels.shape[
                0
            ]
    ):
        raise ValueError(
            "Artifact sample IDs "
            "do not match labels."
        )

    if not torch.isfinite(
            logits
    ).all():
        raise ValueError(
            "Artifact contains "
            "non-finite logits."
        )

    artifact[
        "logits"
    ] = (
        logits
        .detach()
        .float()
        .cpu()
    )

    artifact[
        "labels"
    ] = (
        labels
        .detach()
        .long()
        .cpu()
    )

    artifact[
        "sample_ids"
    ] = list(
        sample_ids
    )

    return artifact


def validate_alignment(
        artifacts: list[
            dict[str, Any]
        ],
) -> None:
    if not artifacts:
        raise ValueError(
            "At least one artifact "
            "is required."
        )

    reference = artifacts[
        0
    ]

    reference_labels = (
        reference[
            "labels"
        ]
    )

    reference_sample_ids = (
        reference[
            "sample_ids"
        ]
    )

    reference_shape = tuple(
        reference[
            "logits"
        ].shape
    )

    for artifact in artifacts[
            1:
    ]:
        if (
                tuple(
                    artifact[
                        "logits"
                    ].shape
                )
                != reference_shape
        ):
            raise ValueError(
                "Logit artifact shapes "
                "are not aligned."
            )

        if not torch.equal(
                artifact[
                    "labels"
                ],
                reference_labels,
        ):
            raise ValueError(
                "Logit artifact labels "
                "are not aligned."
            )

        if (
                artifact[
                    "sample_ids"
                ]
                != reference_sample_ids
        ):
            raise ValueError(
                "Logit artifact sample "
                "IDs are not aligned."
            )


def average_logits(
        artifacts: list[
            dict[str, Any]
        ],
) -> torch.Tensor:
    if not artifacts:
        raise ValueError(
            "At least one artifact "
            "is required."
        )

    validate_alignment(
        artifacts
    )

    stacked_logits = torch.stack(
        [
            artifact[
                "logits"
            ]
            for artifact
            in artifacts
        ],
        dim=0,
    )

    averaged_logits = torch.mean(
        stacked_logits,
        dim=0,
    )

    if not torch.isfinite(
            averaged_logits
    ).all():
        raise RuntimeError(
            "Averaged logits contain "
            "non-finite values."
        )

    return averaged_logits


def combine_logits(
        first_logits: torch.Tensor,
        second_logits: torch.Tensor,
        first_weight: float,
        second_weight: float,
) -> torch.Tensor:
    if (
            first_logits.shape
            != second_logits.shape
    ):
        raise ValueError(
            "Logit shapes differ."
        )

    if (
            not math.isfinite(
                first_weight
            )
            or not math.isfinite(
                second_weight
            )
    ):
        raise ValueError(
            "Ensemble weights must "
            "be finite."
        )

    if (
            first_weight < 0.0
            or second_weight < 0.0
    ):
        raise ValueError(
            "Ensemble weights must "
            "be non-negative."
        )

    if not math.isclose(
            first_weight
            + second_weight,
            1.0,
            rel_tol=0.0,
            abs_tol=1e-12,
    ):
        raise ValueError(
            "Ensemble weights must "
            "sum to 1.0."
        )

    combined = (
        first_logits
        * first_weight
        + second_logits
        * second_weight
    )

    if not torch.isfinite(
            combined
    ).all():
        raise RuntimeError(
            "Combined logits contain "
            "non-finite values."
        )

    return combined


def save_logit_artifact(
        path: Path,
        model_key: str,
        logits: torch.Tensor,
        labels: torch.Tensor,
        sample_ids: list[str],
) -> dict[str, Any]:
    if path.exists():
        raise FileExistsError(
            "Output logit artifact "
            "already exists: "
            f"{path}"
        )

    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    torch.save(
        {
            "model_key": (
                model_key
            ),
            "logits": (
                logits
                .detach()
                .float()
                .cpu()
                .contiguous()
            ),
            "labels": (
                labels
                .detach()
                .long()
                .cpu()
                .contiguous()
            ),
            "sample_ids": list(
                sample_ids
            ),
        },
        path,
    )

    return {
        "file": str(
            path
        ),
        "bytes": (
            path
            .stat()
            .st_size
        ),
        "sha256": (
            calculate_sha256(
                path
            )
        ),
        "logits_shape": list(
            logits.shape
        ),
    }


def metric_summary(
        result: dict[
            str,
            Any,
        ],
) -> dict[str, Any]:
    return {
        key: value
        for key, value
        in result.items()
        if key
        != "predicted_ids"
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Evaluate the TripPamine "
            "KcELECTRA three-seed "
            "logit ensemble and fixed "
            "Ko/Kc backbone ensemble."
        )
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--ko-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc42-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc123-logits",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc2026-logits",
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

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output_dir.exists():
        raise FileExistsError(
            "Output directory already "
            "exists: "
            f"{args.output_dir}"
        )

    if args.report.exists():
        raise FileExistsError(
            "Report already exists: "
            f"{args.report}"
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

    ko_artifact = (
        load_logit_artifact(
            args.ko_logits
        )
    )

    kc42_artifact = (
        load_logit_artifact(
            args.kc42_logits
        )
    )

    kc123_artifact = (
        load_logit_artifact(
            args.kc123_logits
        )
    )

    kc2026_artifact = (
        load_logit_artifact(
            args.kc2026_logits
        )
    )

    all_artifacts = [
        ko_artifact,
        kc42_artifact,
        kc123_artifact,
        kc2026_artifact,
    ]

    validate_alignment(
        all_artifacts
    )

    expected_samples = len(
        validation
    )

    actual_samples = int(
        ko_artifact[
            "labels"
        ].shape[
            0
        ]
    )

    if (
            actual_samples
            != expected_samples
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
            != ko_artifact[
                "sample_ids"
            ]
    ):
        raise ValueError(
            "Validation sample IDs "
            "do not match saved logits."
        )

    labels = ko_artifact[
        "labels"
    ]

    kc_artifacts = [
        kc42_artifact,
        kc123_artifact,
        kc2026_artifact,
    ]

    if (
            len(
                kc_artifacts
            )
            != EXPECTED_KC_SEED_COUNT
    ):
        raise RuntimeError(
            "Expected exactly "
            f"{EXPECTED_KC_SEED_COUNT} "
            "Kc seed artifacts."
        )

    kc_average_logits = (
        average_logits(
            kc_artifacts
        )
    )

    final_logits = (
        combine_logits(
            first_logits=(
                ko_artifact[
                    "logits"
                ]
            ),
            second_logits=(
                kc_average_logits
            ),
            first_weight=0.5,
            second_weight=0.5,
        )
    )

    ko_metrics = evaluate_logits(
        logits=(
            ko_artifact[
                "logits"
            ]
        ),
        labels=labels,
        label_mapping=(
            label_mapping
        ),
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    kc42_metrics = evaluate_logits(
        logits=(
            kc42_artifact[
                "logits"
            ]
        ),
        labels=labels,
        label_mapping=(
            label_mapping
        ),
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    kc123_metrics = evaluate_logits(
        logits=(
            kc123_artifact[
                "logits"
            ]
        ),
        labels=labels,
        label_mapping=(
            label_mapping
        ),
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    kc2026_metrics = (
        evaluate_logits(
            logits=(
                kc2026_artifact[
                    "logits"
                ]
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

    kc_average_metrics = (
        evaluate_logits(
            logits=(
                kc_average_logits
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

    final_metrics = (
        evaluate_logits(
            logits=final_logits,
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    complementarity = (
        calculate_complementarity(
            ko_predictions=(
                ko_metrics[
                    "predicted_ids"
                ]
            ),
            kc_predictions=(
                kc_average_metrics[
                    "predicted_ids"
                ]
            ),
            labels=labels,
        )
    )

    args.output_dir.mkdir(
        parents=True,
        exist_ok=False,
    )

    kc_average_artifact = (
        save_logit_artifact(
            path=(
                args.output_dir
                / (
                    "kc-three-seed-"
                    "average-logits.pt"
                )
            ),
            model_key=(
                "kcelectra-v2022-"
                "seed-average"
            ),
            logits=(
                kc_average_logits
            ),
            labels=labels,
            sample_ids=(
                ko_artifact[
                    "sample_ids"
                ]
            ),
        )
    )

    final_artifact = (
        save_logit_artifact(
            path=(
                args.output_dir
                / (
                    "ko42-kc-three-seed-"
                    "50-50-logits.pt"
                )
            ),
            model_key=(
                "ko42-kc3seed-50-50"
            ),
            logits=(
                final_logits
            ),
            labels=labels,
            sample_ids=(
                ko_artifact[
                    "sample_ids"
                ]
            ),
        )
    )

    kc_individual_f1 = [
        float(
            kc42_metrics[
                "fine_macro_f1"
            ]
        ),
        float(
            kc123_metrics[
                "fine_macro_f1"
            ]
        ),
        float(
            kc2026_metrics[
                "fine_macro_f1"
            ]
        ),
    ]

    kc_mean_single_f1 = (
        sum(
            kc_individual_f1
        )
        / len(
            kc_individual_f1
        )
    )

    report = {
        "experiment": (
            "T0-Q14-E-"
            "seed-averaged-logit-"
            "ensemble"
        ),
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
            "kc_seed_count": 3,
            "kc_seeds": [
                42,
                123,
                2026,
            ],
            "kc_seed_weights": [
                1.0 / 3.0,
                1.0 / 3.0,
                1.0 / 3.0,
            ],
            "backbone_ensemble": {
                "ko_weight": 0.5,
                "kc_seed_average_weight": 0.5,
            },
            "weight_tuning": False,
            "selection_split": (
                "validation"
            ),
            "primary_metric": (
                "fine_macro_f1"
            ),
            "test_split_used": False,
        },
        "input_artifacts": {
            "ko42": {
                "file": str(
                    args.ko_logits
                ),
                "sha256": (
                    calculate_sha256(
                        args.ko_logits
                    )
                ),
            },
            "kc42": {
                "file": str(
                    args.kc42_logits
                ),
                "sha256": (
                    calculate_sha256(
                        args.kc42_logits
                    )
                ),
            },
            "kc123": {
                "file": str(
                    args.kc123_logits
                ),
                "sha256": (
                    calculate_sha256(
                        args.kc123_logits
                    )
                ),
            },
            "kc2026": {
                "file": str(
                    args.kc2026_logits
                ),
                "sha256": (
                    calculate_sha256(
                        args.kc2026_logits
                    )
                ),
            },
        },
        "single_models": {
            "ko42": (
                metric_summary(
                    ko_metrics
                )
            ),
            "kc42": (
                metric_summary(
                    kc42_metrics
                )
            ),
            "kc123": (
                metric_summary(
                    kc123_metrics
                )
            ),
            "kc2026": (
                metric_summary(
                    kc2026_metrics
                )
            ),
        },
        "kc_three_seed": {
            "mean_single_seed_f1": (
                kc_mean_single_f1
            ),
            "ensemble_metrics": (
                metric_summary(
                    kc_average_metrics
                )
            ),
            "gain_vs_mean_single_seed": (
                float(
                    kc_average_metrics[
                        "fine_macro_f1"
                    ]
                )
                - kc_mean_single_f1
            ),
            "artifact": (
                kc_average_artifact
            ),
        },
        "ko_kc_seed_ensemble": {
            "ko_weight": 0.5,
            "kc_weight": 0.5,
            "metrics": (
                metric_summary(
                    final_metrics
                )
            ),
            "gain_vs_kc_seed_ensemble": (
                float(
                    final_metrics[
                        "fine_macro_f1"
                    ]
                )
                - float(
                    kc_average_metrics[
                        "fine_macro_f1"
                    ]
                )
            ),
            "gain_vs_ko42": (
                float(
                    final_metrics[
                        "fine_macro_f1"
                    ]
                )
                - float(
                    ko_metrics[
                        "fine_macro_f1"
                    ]
                )
            ),
            "artifact": (
                final_artifact
            ),
        },
        "complementarity": (
            complementarity
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
        "Kc three-seed / Ko-Kc "
        "ensemble completed"
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
    print(
        "Kc42 Fine Macro F1: "
        f"{kc42_metrics['fine_macro_f1']:.6f}"
    )

    print(
        "Kc123 Fine Macro F1: "
        f"{kc123_metrics['fine_macro_f1']:.6f}"
    )

    print(
        "Kc2026 Fine Macro F1: "
        f"{kc2026_metrics['fine_macro_f1']:.6f}"
    )

    print()
    print(
        "Kc single-seed mean F1: "
        f"{kc_mean_single_f1:.6f}"
    )

    print(
        "Kc 3-seed logit ensemble F1: "
        f"{kc_average_metrics['fine_macro_f1']:.6f}"
    )

    print(
        "Kc 3-seed Coarse Macro F1: "
        f"{kc_average_metrics['coarse_macro_f1']:.6f}"
    )

    print()
    print(
        "Ko42 + Kc3 50:50 Fine F1: "
        f"{final_metrics['fine_macro_f1']:.6f}"
    )

    print(
        "Ko42 + Kc3 50:50 Accuracy: "
        f"{final_metrics['fine_accuracy']:.6f}"
    )

    print(
        "Ko42 + Kc3 50:50 Coarse F1: "
        f"{final_metrics['coarse_macro_f1']:.6f}"
    )

    print()
    print(
        "Kc3 gain vs seed mean: "
        f"{float(kc_average_metrics['fine_macro_f1']) - kc_mean_single_f1:+.6f}"
    )

    print(
        "Final gain vs Kc3: "
        f"{float(final_metrics['fine_macro_f1']) - float(kc_average_metrics['fine_macro_f1']):+.6f}"
    )

    print(
        "Final gain vs Ko42: "
        f"{float(final_metrics['fine_macro_f1']) - float(ko_metrics['fine_macro_f1']):+.6f}"
    )

    print()
    print(
        "Ko/Kc3 disagreement rate: "
        f"{complementarity['disagreement_rate']:.2%}"
    )

    print(
        "Ko-only correct: "
        f"{complementarity['ko_only_correct']}"
    )

    print(
        "Kc3-only correct: "
        f"{complementarity['kc_only_correct']}"
    )

    print()
    print(
        "Report: "
        f"{args.report}"
    )

    print()
    print(
        "Q14-E SEED ENSEMBLE: PASS"
    )


if __name__ == "__main__":
    main()