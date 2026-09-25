import argparse
import math
import statistics
import sys
from pathlib import Path
from typing import Any

import torch
import transformers

from scripts.emotion.analyze_context_adaptive_fallback import (
    apply_full_fallback,
    calculate_gap_recovery,
    compute_uncertainty_signals,
)
from scripts.emotion.analyze_context_changed_only import (
    find_changed_indices,
    find_reference_contract,
    validate_protocol,
    validate_source_file,
)
from scripts.emotion.analyze_context_fixed_margin_fallback import (
    FIXED_MARGIN_THRESHOLD,
    select_margin_fallback_indices,
)
from scripts.emotion.build_context_classification_dataset import (
    load_normalized_records_for_ids,
)
from scripts.emotion.evaluate_context_ablation import (
    build_ablation_views,
    evaluate_pair,
    metric_delta,
    metric_summary,
)
from scripts.emotion.evaluate_layernorm_ensemble import (
    evaluate_logits,
    extract_validation_logits,
    load_report,
    save_logits,
)
from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    read_dataset,
    save_report,
    validate_positive_integer,
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


EXPECTED_SEEDS = (
    42,
    123,
    2026,
)

EXPECTED_TEST_SAMPLES = 5825

EXPECTED_TEST_SHA256 = (
    "ce37b80abf0f13e439e7cf51bb6598bae"
    "3b093d99680981b3063a22248a80f00"
)

KO_MODEL_KEY = "koelectra-v3"
KC_MODEL_KEY = "kcelectra-v2022"

DEFAULT_MAX_LENGTH = 96
DEFAULT_BATCH_SIZE = 64


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run the locked Q17 context policy "
            "against the sealed test split for "
            "seeds 42, 123, and 2026 in one "
            "single-shot evaluation."
        )
    )

    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path("."),
    )

    parser.add_argument(
        "--sealed-test-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed42-context-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed42-policy-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed123-context-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed123-policy-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed2026-context-report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--seed2026-policy-report",
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

    return parser.parse_args()


def resolve_existing_directory(
        project_root: Path,
        value: str,
        description: str,
) -> Path:
    path = Path(
        value
    )

    if not path.is_absolute():
        path = (
            project_root
            / path
        )

    path = (
        path
        .expanduser()
        .resolve()
    )

    if not path.exists():
        raise FileNotFoundError(
            f"{description} does not exist: "
            f"{path}"
        )

    if not path.is_dir():
        raise ValueError(
            f"{description} is not a "
            f"directory: {path}"
        )

    return path


def resolve_existing_file(
        path: Path,
        description: str,
) -> Path:
    resolved = (
        path
        .expanduser()
        .resolve()
    )

    if not resolved.exists():
        raise FileNotFoundError(
            f"{description} does not exist: "
            f"{resolved}"
        )

    if not resolved.is_file():
        raise ValueError(
            f"{description} is not a file: "
            f"{resolved}"
        )

    return resolved


def validate_sealed_test_contract(
        report: dict[str, Any],
) -> dict[str, Any]:
    try:
        protocol = report[
            "protocol"
        ]

        output_test = report[
            "output"
        ][
            "test"
        ]

        source = report[
            "source"
        ]

    except KeyError as error:
        raise ValueError(
            "Sealed test report is missing "
            "required contract fields."
        ) from error

    if (
            protocol.get(
                "test_split_used"
            )
            is not True
    ):
        raise ValueError(
            "Sealed test report must state "
            "test_split_used=True."
        )

    if (
            protocol.get(
                "test_output_created"
            )
            is not True
    ):
        raise ValueError(
            "Sealed test output was not "
            "recorded as created."
        )

    if (
            protocol.get(
                "training_output_created"
            )
            is not False
    ):
        raise ValueError(
            "Sealed test builder must not "
            "create training output."
        )

    if (
            protocol.get(
                "validation_output_created"
            )
            is not False
    ):
        raise ValueError(
            "Sealed test builder must not "
            "create validation output."
        )

    samples = int(
        output_test[
            "samples"
        ]
    )

    if samples != EXPECTED_TEST_SAMPLES:
        raise ValueError(
            "Unexpected sealed test sample "
            "count: "
            f"{samples}."
        )

    sha256 = str(
        output_test[
            "sha256"
        ]
    ).lower()

    if sha256 != EXPECTED_TEST_SHA256:
        raise ValueError(
            "Sealed test SHA256 changed: "
            f"{sha256}."
        )

    if (
            "normalized_training"
            not in source
            or "normalized_validation"
            not in source
    ):
        raise ValueError(
            "Sealed test report is missing "
            "normalized source contracts."
        )

    return output_test


def validate_locked_policy_contract(
        report: dict[str, Any],
        expected_seed: int,
        expected_context_sha256: str,
) -> None:
    try:
        protocol = report[
            "protocol"
        ]

        source = report[
            "source"
        ][
            "context_report"
        ]

    except KeyError as error:
        raise ValueError(
            "Locked policy report is missing "
            "required fields."
        ) from error

    seed = int(
        protocol[
            "seed"
        ]
    )

    if seed != expected_seed:
        raise ValueError(
            "Locked policy seed mismatch: "
            f"expected={expected_seed}, "
            f"actual={seed}."
        )

    if (
            protocol.get(
                "test_split_used"
            )
            is not False
    ):
        raise ValueError(
            "Locked policy must have been "
            "selected without the test split."
        )

    if (
            protocol.get(
                "threshold_tuning"
            )
            is not False
    ):
        raise ValueError(
            "Locked policy must state "
            "threshold_tuning=False."
        )

    if (
            protocol.get(
                "base_view"
            )
            != "first2"
    ):
        raise ValueError(
            "Locked policy base view must "
            "be FIRST2."
        )

    if (
            protocol.get(
                "fallback_view"
            )
            != "full"
    ):
        raise ValueError(
            "Locked policy fallback view "
            "must be FULL."
        )

    if (
            protocol.get(
                "signal"
            )
            != "margin"
    ):
        raise ValueError(
            "Locked policy signal must "
            "be margin."
        )

    threshold = float(
        protocol[
            "fixed_margin_threshold"
        ]
    )

    if not math.isclose(
            threshold,
            FIXED_MARGIN_THRESHOLD,
            rel_tol=0.0,
            abs_tol=1e-15,
    ):
        raise ValueError(
            "Locked margin threshold changed: "
            f"{threshold}."
        )

    context_sha256 = str(
        source[
            "sha256"
        ]
    ).lower()

    if (
            context_sha256
            != expected_context_sha256.lower()
    ):
        raise ValueError(
            "Locked policy context report "
            "SHA256 mismatch."
        )


def validate_training_report_contract(
        report: dict[str, Any],
        expected_seed: int,
        description: str,
) -> None:
    try:
        dataset = report[
            "dataset"
        ]

        hyperparameters = report[
            "hyperparameters"
        ]

    except KeyError as error:
        raise ValueError(
            f"{description} training report "
            "is missing required fields."
        ) from error

    if (
            dataset.get(
                "test_split_used"
            )
            is not False
    ):
        raise ValueError(
            f"{description} training report "
            "used the test split."
        )

    actual_seed = int(
        hyperparameters[
            "seed"
        ]
    )

    if actual_seed != expected_seed:
        raise ValueError(
            f"{description} training seed "
            "mismatch: "
            f"expected={expected_seed}, "
            f"actual={actual_seed}."
        )


def validate_normalized_source_consistency(
        context_report: dict[str, Any],
        sealed_test_report: dict[str, Any],
        seed: int,
) -> None:
    try:
        context_sources = (
            context_report[
                "dataset"
            ][
                "normalized_sources"
            ]
        )

        sealed_sources = (
            sealed_test_report[
                "source"
            ]
        )

    except KeyError as error:
        raise ValueError(
            "Normalized source contract "
            f"is missing for seed {seed}."
        ) from error

    pairs = (
        (
            "training",
            "normalized_training",
        ),
        (
            "validation",
            "normalized_validation",
        ),
    )

    for (
            context_key,
            sealed_key,
    ) in pairs:
        context_sha = str(
            context_sources[
                context_key
            ][
                "sha256"
            ]
        ).lower()

        sealed_sha = str(
            sealed_sources[
                sealed_key
            ][
                "sha256"
            ]
        ).lower()

        if context_sha != sealed_sha:
            raise ValueError(
                "Normalized source SHA256 "
                "differs between context and "
                "sealed-test reports for "
                f"seed {seed}: "
                f"{context_key}."
            )


def summary_stats(
        values: list[float],
) -> dict[str, float]:
    if not values:
        raise ValueError(
            "Cannot summarize empty values."
        )

    result = {
        "mean": float(
            statistics.mean(
                values
            )
        ),
        "min": float(
            min(
                values
            )
        ),
        "max": float(
            max(
                values
            )
        ),
    }

    if len(values) >= 2:
        result[
            "sample_sd"
        ] = float(
            statistics.stdev(
                values
            )
        )
    else:
        result[
            "sample_sd"
        ] = 0.0

    return result


def validate_same_labels(
        tensors: list[torch.Tensor],
        description: str,
) -> torch.Tensor:
    if not tensors:
        raise ValueError(
            "No label tensors supplied."
        )

    expected = (
        tensors[
            0
        ]
        .detach()
        .long()
        .cpu()
    )

    for tensor in tensors[
        1:
    ]:
        actual = (
            tensor
            .detach()
            .long()
            .cpu()
        )

        if not torch.equal(
                expected,
                actual,
        ):
            raise RuntimeError(
                f"{description} labels "
                "are not aligned."
            )

    return expected


def build_seed_inputs(
        args: argparse.Namespace,
) -> list[
    tuple[
        int,
        Path,
        Path,
    ]
]:
    return [
        (
            42,
            args.seed42_context_report,
            args.seed42_policy_report,
        ),
        (
            123,
            args.seed123_context_report,
            args.seed123_policy_report,
        ),
        (
            2026,
            args.seed2026_context_report,
            args.seed2026_policy_report,
        ),
    ]


def build_aggregate(
        seed_results: dict[
            str,
            dict[str, Any],
        ],
) -> dict[str, Any]:
    rows = [
        seed_results[
            f"seed{seed}"
        ]
        for seed
        in EXPECTED_SEEDS
    ]

    return {
        "seed_count": len(
            rows
        ),
        "seeds": list(
            EXPECTED_SEEDS
        ),
        "fallback_rate_eligible": (
            summary_stats(
                [
                    row[
                        "fallback"
                    ][
                        "rate_eligible"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "fallback_rate_overall": (
            summary_stats(
                [
                    row[
                        "fallback"
                    ][
                        "rate_overall"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "first2_fine_macro_f1": (
            summary_stats(
                [
                    row[
                        "baseline"
                    ][
                        "first2"
                    ][
                        "fine_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "fixed_fine_macro_f1": (
            summary_stats(
                [
                    row[
                        "fixed_policy"
                    ][
                        "metrics"
                    ][
                        "fine_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "full_fine_macro_f1": (
            summary_stats(
                [
                    row[
                        "baseline"
                    ][
                        "full"
                    ][
                        "fine_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "fine_full_retention": (
            summary_stats(
                [
                    row[
                        "fixed_policy"
                    ][
                        "full_retention"
                    ][
                        "fine_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "fine_gap_recovery": (
            summary_stats(
                [
                    row[
                        "fixed_policy"
                    ][
                        "gap_recovery"
                    ][
                        "fine_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "first2_coarse_macro_f1": (
            summary_stats(
                [
                    row[
                        "baseline"
                    ][
                        "first2"
                    ][
                        "coarse_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "fixed_coarse_macro_f1": (
            summary_stats(
                [
                    row[
                        "fixed_policy"
                    ][
                        "metrics"
                    ][
                        "coarse_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "full_coarse_macro_f1": (
            summary_stats(
                [
                    row[
                        "baseline"
                    ][
                        "full"
                    ][
                        "coarse_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "coarse_full_retention": (
            summary_stats(
                [
                    row[
                        "fixed_policy"
                    ][
                        "full_retention"
                    ][
                        "coarse_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
        "coarse_gap_recovery": (
            summary_stats(
                [
                    row[
                        "fixed_policy"
                    ][
                        "gap_recovery"
                    ][
                        "coarse_macro_f1"
                    ]
                    for row
                    in rows
                ]
            )
        ),
    }


def main() -> None:
    args = parse_args()

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.batch_size,
        "Evaluation batch size",
    )

    if (
            args.max_length
            != DEFAULT_MAX_LENGTH
    ):
        raise ValueError(
            "Locked Q17 test max_length "
            "must remain 96."
        )

    project_root = (
        args.project_root
        .expanduser()
        .resolve()
    )

    output_dir = (
        args.output_dir
        .expanduser()
        .resolve()
    )

    report_path = (
        args.report
        .expanduser()
        .resolve()
    )

    if output_dir.exists():
        raise FileExistsError(
            "Q17-C4 output directory "
            "already exists: "
            f"{output_dir}"
        )

    if report_path.exists():
        raise FileExistsError(
            "Q17-C4 report already exists: "
            f"{report_path}"
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is required for Q17-C4."
        )

    sealed_test_report_path = (
        resolve_existing_file(
            args.sealed_test_report,
            "Sealed test dataset report",
        )
    )

    sealed_test_report = load_report(
        sealed_test_report_path
    )

    test_entry = (
        validate_sealed_test_contract(
            sealed_test_report
        )
    )

    test_path = (
        validate_source_file(
            project_root=project_root,
            entry=test_entry,
            description=(
                "Sealed context test dataset"
            ),
        )
    )

    normalized_training_path = (
        validate_source_file(
            project_root=project_root,
            entry=(
                sealed_test_report[
                    "source"
                ][
                    "normalized_training"
                ]
            ),
            description=(
                "Normalized training source"
            ),
        )
    )

    normalized_validation_path = (
        validate_source_file(
            project_root=project_root,
            entry=(
                sealed_test_report[
                    "source"
                ][
                    "normalized_validation"
                ]
            ),
            description=(
                "Normalized validation source"
            ),
        )
    )

    actual_test_sha256 = (
        calculate_sha256(
            test_path
        )
    )

    if (
            actual_test_sha256.lower()
            != EXPECTED_TEST_SHA256
    ):
        raise RuntimeError(
            "Actual sealed test SHA256 "
            "does not match the locked "
            "Q10 artifact."
        )

    test_samples = read_dataset(
        test_path,
        "test",
    )

    if (
            len(
                test_samples
            )
            != EXPECTED_TEST_SAMPLES
    ):
        raise RuntimeError(
            "Loaded test sample count "
            "changed."
        )

    sample_ids = [
        sample.id
        for sample
        in test_samples
    ]

    if len(
            sample_ids
    ) != len(
            set(
                sample_ids
            )
    ):
        raise ValueError(
            "Duplicate test sample IDs "
            "detected."
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
        validation=test_samples,
        normalized_by_id=(
            normalized_by_id
        ),
    )

    full_text_mismatches = sum(
        1
        for (
            reference,
            full_sample,
        )
        in zip(
            test_samples,
            views[
                "full"
            ],
            strict=True,
        )
        if (
            reference.text
            != full_sample.text
        )
    )

    if full_text_mismatches != 0:
        raise RuntimeError(
            "Reconstructed FULL test view "
            "does not match the sealed "
            "context test dataset."
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

    eligible_count = len(
        eligible_indices
    )

    ineligible_count = (
        len(
            test_samples
        )
        - eligible_count
    )

    human_turn_count = (
        test_entry.get(
            "human_turn_count",
            {}
        )
    )

    if human_turn_count:
        expected_eligible = sum(
            int(
                count
            )
            for (
                turn_count,
                count,
            )
            in human_turn_count.items()
            if int(
                turn_count
            ) > 2
        )

        if (
                expected_eligible
                != eligible_count
        ):
            raise RuntimeError(
                "FIRST2/FULL eligibility "
                "does not match sealed-test "
                "human turn counts."
            )

    label_mapping = (
        EmotionLabelMapping()
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            samples=test_samples,
            label_mapping=(
                label_mapping
            ),
        )
    )

    device_index = (
        torch.cuda.current_device()
    )

    device = torch.device(
        "cuda",
        device_index,
    )

    seed_results: dict[
        str,
        dict[str, Any]
    ] = {}

    raw_artifacts: dict[
        str,
        dict[str, Any]
    ] = {}

    for (
            expected_seed,
            context_arg,
            policy_arg,
    ) in build_seed_inputs(
            args
    ):
        context_report_path = (
            resolve_existing_file(
                context_arg,
                (
                    f"Seed{expected_seed} "
                    "context report"
                ),
            )
        )

        policy_report_path = (
            resolve_existing_file(
                policy_arg,
                (
                    f"Seed{expected_seed} "
                    "locked policy report"
                ),
            )
        )

        context_report = load_report(
            context_report_path
        )

        actual_seed = validate_protocol(
            context_report
        )

        if actual_seed != expected_seed:
            raise ValueError(
                "Context report seed "
                "mismatch: "
                f"expected={expected_seed}, "
                f"actual={actual_seed}."
            )

        validate_normalized_source_consistency(
            context_report=(
                context_report
            ),
            sealed_test_report=(
                sealed_test_report
            ),
            seed=expected_seed,
        )

        context_sha256 = (
            calculate_sha256(
                context_report_path
            )
        )

        policy_report = load_report(
            policy_report_path
        )

        validate_locked_policy_contract(
            report=policy_report,
            expected_seed=expected_seed,
            expected_context_sha256=(
                context_sha256
            ),
        )

        reference = (
            find_reference_contract(
                context_report
            )
        )

        ko_model_dir = (
            resolve_existing_directory(
                project_root=project_root,
                value=reference[
                    "ko_model"
                ],
                description=(
                    f"Seed{expected_seed} "
                    "Ko model"
                ),
            )
        )

        kc_model_dir = (
            resolve_existing_directory(
                project_root=project_root,
                value=reference[
                    "kc_model"
                ],
                description=(
                    f"Seed{expected_seed} "
                    "Kc model"
                ),
            )
        )

        ko_report_path = (
            validate_source_file(
                project_root=project_root,
                entry=reference[
                    "ko_report"
                ],
                description=(
                    f"Seed{expected_seed} "
                    "Ko training report"
                ),
            )
        )

        kc_report_path = (
            validate_source_file(
                project_root=project_root,
                entry=reference[
                    "kc_report"
                ],
                description=(
                    f"Seed{expected_seed} "
                    "Kc training report"
                ),
            )
        )

        ko_training_report = (
            load_report(
                ko_report_path
            )
        )

        kc_training_report = (
            load_report(
                kc_report_path
            )
        )

        validate_training_report_contract(
            report=ko_training_report,
            expected_seed=expected_seed,
            description=(
                f"Seed{expected_seed} Ko"
            ),
        )

        validate_training_report_contract(
            report=kc_training_report,
            expected_seed=expected_seed,
            description=(
                f"Seed{expected_seed} Kc"
            ),
        )

        (
            first2_ko_logits,
            first2_ko_labels,
            first2_ko_extraction,
        ) = extract_validation_logits(
            model_dir=ko_model_dir,
            validation=views[
                "first2"
            ],
            label_mapping=(
                label_mapping
            ),
            max_length=args.max_length,
            batch_size=args.batch_size,
            device=device,
            description=(
                f"Seed{expected_seed} "
                "Ko FIRST2 test"
            ),
        )

        (
            first2_kc_logits,
            first2_kc_labels,
            first2_kc_extraction,
        ) = extract_validation_logits(
            model_dir=kc_model_dir,
            validation=views[
                "first2"
            ],
            label_mapping=(
                label_mapping
            ),
            max_length=args.max_length,
            batch_size=args.batch_size,
            device=device,
            description=(
                f"Seed{expected_seed} "
                "Kc FIRST2 test"
            ),
        )

        (
            full_ko_logits,
            full_ko_labels,
            full_ko_extraction,
        ) = extract_validation_logits(
            model_dir=ko_model_dir,
            validation=views[
                "full"
            ],
            label_mapping=(
                label_mapping
            ),
            max_length=args.max_length,
            batch_size=args.batch_size,
            device=device,
            description=(
                f"Seed{expected_seed} "
                "Ko FULL test"
            ),
        )

        (
            full_kc_logits,
            full_kc_labels,
            full_kc_extraction,
        ) = extract_validation_logits(
            model_dir=kc_model_dir,
            validation=views[
                "full"
            ],
            label_mapping=(
                label_mapping
            ),
            max_length=args.max_length,
            batch_size=args.batch_size,
            device=device,
            description=(
                f"Seed{expected_seed} "
                "Kc FULL test"
            ),
        )

        labels = validate_same_labels(
            tensors=[
                first2_ko_labels,
                first2_kc_labels,
                full_ko_labels,
                full_kc_labels,
            ],
            description=(
                f"Seed{expected_seed} test"
            ),
        )

        first2_pair = evaluate_pair(
            ko_logits=(
                first2_ko_logits
            ),
            kc_logits=(
                first2_kc_logits
            ),
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )

        full_pair = evaluate_pair(
            ko_logits=(
                full_ko_logits
            ),
            kc_logits=(
                full_kc_logits
            ),
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )

        first2_ensemble_logits = (
            first2_pair[
                "ensemble_logits"
            ]
            .detach()
            .cpu()
        )

        full_ensemble_logits = (
            full_pair[
                "ensemble_logits"
            ]
            .detach()
            .cpu()
        )

        signals = (
            compute_uncertainty_signals(
                first2_ensemble_logits
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

        first2_metrics = (
            metric_summary(
                first2_pair[
                    "ensemble"
                ]
            )
        )

        full_metrics = (
            metric_summary(
                full_pair[
                    "ensemble"
                ]
            )
        )

        fixed_metrics = (
            metric_summary(
                hybrid_evaluation
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
                    fixed_metrics[
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
                    fixed_metrics[
                        "coarse_macro_f1"
                    ]
                ),
            )
        )

        first2_predictions = (
            first2_pair[
                "ensemble"
            ][
                "predicted_ids"
            ]
        )

        full_predictions = (
            full_pair[
                "ensemble"
            ][
                "predicted_ids"
            ]
        )

        fixed_predictions = (
            hybrid_evaluation[
                "predicted_ids"
            ]
        )

        fallback_count = len(
            fallback_indices
        )

        seed_key = (
            f"seed{expected_seed}"
        )

        seed_results[
            seed_key
        ] = {
            "seed": expected_seed,
            "source": {
                "context_report": {
                    "file": str(
                        context_arg
                    ),
                    "sha256": (
                        context_sha256
                    ),
                },
                "locked_policy_report": {
                    "file": str(
                        policy_arg
                    ),
                    "sha256": (
                        calculate_sha256(
                            policy_report_path
                        )
                    ),
                },
                "ko_model": str(
                    reference[
                        "ko_model"
                    ]
                ),
                "ko_training_report": {
                    "file": str(
                        reference[
                            "ko_report"
                        ][
                            "file"
                        ]
                    ),
                    "sha256": (
                        calculate_sha256(
                            ko_report_path
                        )
                    ),
                },
                "kc_model": str(
                    reference[
                        "kc_model"
                    ]
                ),
                "kc_training_report": {
                    "file": str(
                        reference[
                            "kc_report"
                        ][
                            "file"
                        ]
                    ),
                    "sha256": (
                        calculate_sha256(
                            kc_report_path
                        )
                    ),
                },
            },
            "inference": {
                "first2": {
                    "ko": (
                        first2_ko_extraction
                    ),
                    "kc": (
                        first2_kc_extraction
                    ),
                },
                "full": {
                    "ko": (
                        full_ko_extraction
                    ),
                    "kc": (
                        full_kc_extraction
                    ),
                },
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
                    / len(
                        test_samples
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
                    fixed_metrics
                ),
                "delta_vs_first2": (
                    metric_delta(
                        candidate=(
                            fixed_metrics
                        ),
                        reference=(
                            first2_metrics
                        ),
                    )
                ),
                "delta_vs_full": (
                    metric_delta(
                        candidate=(
                            fixed_metrics
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
                        fixed_metrics[
                            "fine_macro_f1"
                        ]
                        / full_metrics[
                            "fine_macro_f1"
                        ]
                    ),
                    "coarse_macro_f1": (
                        fixed_metrics[
                            "coarse_macro_f1"
                        ]
                        / full_metrics[
                            "coarse_macro_f1"
                        ]
                    ),
                },
                "transitions_vs_first2": (
                    compare_prediction_transitions(
                        labels=(
                            labels.tolist()
                        ),
                        reference_predictions=(
                            first2_predictions
                        ),
                        candidate_predictions=(
                            fixed_predictions
                        ),
                        id2label=dict(
                            label_mapping.id2label
                        ),
                        fine_to_coarse=(
                            fine_to_coarse
                        ),
                    )
                ),
                "transitions_vs_full": (
                    compare_prediction_transitions(
                        labels=(
                            labels.tolist()
                        ),
                        reference_predictions=(
                            full_predictions
                        ),
                        candidate_predictions=(
                            fixed_predictions
                        ),
                        id2label=dict(
                            label_mapping.id2label
                        ),
                        fine_to_coarse=(
                            fine_to_coarse
                        ),
                    )
                ),
            },
        }

        raw_artifacts[
            seed_key
        ] = {
            "labels": labels,
            "first2_ko": (
                first2_ko_logits
            ),
            "first2_kc": (
                first2_kc_logits
            ),
            "full_ko": (
                full_ko_logits
            ),
            "full_kc": (
                full_kc_logits
            ),
        }

        torch.cuda.empty_cache()

    output_dir.mkdir(
        parents=True,
        exist_ok=False,
    )

    for seed in EXPECTED_SEEDS:
        seed_key = (
            f"seed{seed}"
        )

        tensors = (
            raw_artifacts[
                seed_key
            ]
        )

        artifact_reports = {}

        for (
                view_name,
                model_name,
                tensor_key,
                model_key,
        ) in (
            (
                "first2",
                "ko",
                "first2_ko",
                KO_MODEL_KEY,
            ),
            (
                "first2",
                "kc",
                "first2_kc",
                KC_MODEL_KEY,
            ),
            (
                "full",
                "ko",
                "full_ko",
                KO_MODEL_KEY,
            ),
            (
                "full",
                "kc",
                "full_kc",
                KC_MODEL_KEY,
            ),
        ):
            artifact_report = (
                save_logits(
                    path=(
                        output_dir
                        / (
                            f"seed{seed}-"
                            f"{view_name}-"
                            f"{model_name}-"
                            "test-logits.pt"
                        )
                    ),
                    logits=(
                        tensors[
                            tensor_key
                        ]
                    ),
                    labels=(
                        tensors[
                            "labels"
                        ]
                    ),
                    sample_ids=(
                        sample_ids
                    ),
                    model_key=model_key,
                )
            )

            artifact_reports[
                f"{view_name}_{model_name}"
            ] = (
                artifact_report
            )

        seed_results[
            seed_key
        ][
            "artifacts"
        ] = artifact_reports

    aggregate = build_aggregate(
        seed_results
    )

    final_report = {
        "experiment": (
            "T0-Q17-C4-locked-context-"
            "policy-test"
        ),
        "versions": {
            "python": (
                sys.version
            ),
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
                torch.cuda.get_device_name(
                    device_index
                )
            ),
        },
        "protocol": {
            "evaluation_split": "test",
            "single_shot": True,
            "training_performed": False,
            "model_selection_performed": False,
            "threshold_tuning": False,
            "test_result_used_for_tuning": False,
            "seeds": list(
                EXPECTED_SEEDS
            ),
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
            "fixed_margin_threshold": (
                FIXED_MARGIN_THRESHOLD
            ),
            "ensemble_type": (
                "weighted_raw_logits"
            ),
            "ko_weight": 0.5,
            "kc_weight": 0.5,
            "max_length": (
                args.max_length
            ),
            "batch_size": (
                args.batch_size
            ),
        },
        "dataset": {
            "test": {
                "file": str(
                    test_entry[
                        "file"
                    ]
                ),
                "samples": len(
                    test_samples
                ),
                "sha256": (
                    actual_test_sha256
                ),
            },
            "full_text_mismatches": (
                full_text_mismatches
            ),
            "fallback_eligible_samples": (
                eligible_count
            ),
            "no_extra_context_samples": (
                ineligible_count
            ),
        },
        "seeds": (
            seed_results
        ),
        "aggregate": (
            aggregate
        ),
    }

    save_report(
        report=final_report,
        output_path=report_path,
    )

    print()
    print(
        "Q17-C4 locked context policy "
        "test completed"
    )

    print()
    print(
        "Test samples: "
        f"{len(test_samples)}"
    )

    print(
        "Fallback eligible samples: "
        f"{eligible_count}"
    )

    print(
        "No-extra-context samples: "
        f"{ineligible_count}"
    )

    print(
        "Fixed margin threshold: "
        f"{FIXED_MARGIN_THRESHOLD:.6f}"
    )

    print()

    for seed in EXPECTED_SEEDS:
        row = seed_results[
            f"seed{seed}"
        ]

        print(
            f"Seed{seed}: "
            "fallback="
            f"{row['fallback']['rate_overall'] * 100:.2f}% "
            "FIRST2="
            f"{row['baseline']['first2']['fine_macro_f1']:.6f} "
            "FIXED="
            f"{row['fixed_policy']['metrics']['fine_macro_f1']:.6f} "
            "FULL="
            f"{row['baseline']['full']['fine_macro_f1']:.6f} "
            "retention="
            f"{row['fixed_policy']['full_retention']['fine_macro_f1'] * 100:.2f}% "
            "recovery="
            f"{row['fixed_policy']['gap_recovery']['fine_macro_f1'] * 100:.2f}%"
        )

    print()
    print(
        "===== 3-SEED TEST AGGREGATE ====="
    )

    print(
        "Overall fallback mean: "
        f"{aggregate['fallback_rate_overall']['mean'] * 100:.2f}%"
    )

    print(
        "FIRST2 Fine F1 mean: "
        f"{aggregate['first2_fine_macro_f1']['mean']:.6f}"
    )

    print(
        "FIXED Fine F1 mean: "
        f"{aggregate['fixed_fine_macro_f1']['mean']:.6f}"
    )

    print(
        "FULL Fine F1 mean: "
        f"{aggregate['full_fine_macro_f1']['mean']:.6f}"
    )

    print(
        "Fine FULL retention mean: "
        f"{aggregate['fine_full_retention']['mean'] * 100:.2f}%"
    )

    print(
        "Fine gap recovery mean: "
        f"{aggregate['fine_gap_recovery']['mean'] * 100:.2f}%"
    )

    print()
    print(
        "Report: "
        f"{args.report}"
    )

    print(
        "Artifacts: "
        f"{args.output_dir}"
    )

    print()
    print(
        "Q17-C4 LOCKED POLICY TEST: PASS"
    )


if __name__ == "__main__":
    main()