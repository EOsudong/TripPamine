import argparse
import csv
import json
import math
import sys
from collections import Counter
from pathlib import Path
from typing import Any

import torch

from scripts.emotion.evaluate_layernorm_ensemble import (
    evaluate_logits,
    load_report,
    validate_report_dataset,
)
from scripts.emotion.evaluate_layernorm_seed_ensemble import (
    combine_logits,
    load_logit_artifact,
    metric_summary,
    validate_alignment,
)
from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    save_report,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.evaluation.emotion.structural_error_audit import (
    REVIEW_CATEGORIES,
    StructuralAuditCandidate,
    build_structural_audit_candidates,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


DEFAULT_CROSS_COARSE_LIMIT = 100
DEFAULT_TARGET_LIMIT_PER_LABEL = 20

DEFAULT_TARGET_LABELS = [
    "E14",
    "E11",
    "E12",
    "E59",
    "E22",
]

METRIC_TOLERANCE = 1e-6

KO_MODEL_KEY = "koelectra-v3"
KC_MODEL_KEY = "kcelectra-v2022"

ENSEMBLE_WEIGHT = 0.5


def load_validation_records(
        path: Path,
) -> list[
    dict[str, Any]
]:
    if not path.exists():
        raise FileNotFoundError(
            "Validation file not found: "
            f"{path}"
        )

    records = []

    with path.open(
        "r",
        encoding="utf-8",
    ) as handle:
        for (
                line_number,
                line,
        ) in enumerate(
            handle,
            start=1,
        ):
            stripped = line.strip()

            if not stripped:
                continue

            try:
                record = json.loads(
                    stripped
                )

            except json.JSONDecodeError as error:
                raise ValueError(
                    "Invalid validation JSON "
                    "at line "
                    f"{line_number}."
                ) from error

            if not isinstance(
                    record,
                    dict,
            ):
                raise ValueError(
                    "Validation JSONL must "
                    "contain objects."
                )

            records.append(
                record
            )

    if not records:
        raise ValueError(
            "Validation dataset is empty."
        )

    return records


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


def validate_q17_report_protocol(
        report: dict[str, Any],
) -> None:
    try:
        protocol = report[
            "protocol"
        ]

        seeds = protocol[
            "seeds"
        ]

        backbone = protocol[
            "backbone_ensemble"
        ]

        calibrated = protocol[
            "confidence_calibrated"
        ]

    except KeyError as error:
        raise ValueError(
            "Q17-A report protocol is "
            "missing required fields."
        ) from error

    if seeds != [
        42,
        123,
        2026,
    ]:
        raise ValueError(
            "Q17-A report must contain "
            "seeds 42, 123, and 2026."
        )

    if not math.isclose(
            float(
                backbone[
                    "ko_weight"
                ]
            ),
            ENSEMBLE_WEIGHT,
            rel_tol=0.0,
            abs_tol=1e-12,
    ):
        raise ValueError(
            "Q17-A Ko ensemble weight "
            "must be 0.5."
        )

    if not math.isclose(
            float(
                backbone[
                    "kc_weight"
                ]
            ),
            ENSEMBLE_WEIGHT,
            rel_tol=0.0,
            abs_tol=1e-12,
    ):
        raise ValueError(
            "Q17-A Kc ensemble weight "
            "must be 0.5."
        )

    if calibrated is not False:
        raise ValueError(
            "Q17-A confidence must be "
            "recorded as uncalibrated."
        )

    if (
            protocol.get(
                "test_split_used"
            )
            is not False
    ):
        raise ValueError(
            "Q17-A protocol must state "
            "test_split_used=False."
        )


def validate_q17_seed_metrics(
        seed_name: str,
        actual: dict[
            str,
            Any,
        ],
        report: dict[
            str,
            Any,
        ],
) -> None:
    try:
        expected = report[
            "seed_metrics"
        ][
            seed_name
        ]

    except KeyError as error:
        raise ValueError(
            "Q17-A report is missing "
            f"seed metrics for {seed_name}."
        ) from error

    metric_keys = (
        "fine_accuracy",
        "fine_macro_f1",
        "coarse_macro_f1",
    )

    for metric_key in metric_keys:
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
                abs_tol=(
                    METRIC_TOLERANCE
                ),
        ):
            raise RuntimeError(
                "Q17-A metric reproduction "
                "failed for "
                f"seed={seed_name}, "
                f"metric={metric_key}: "
                f"expected={expected_value}, "
                f"actual={actual_value}."
            )


def combine_pair(
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


def write_jsonl(
        path: Path,
        candidates: list[
            StructuralAuditCandidate
        ],
) -> None:
    if path.exists():
        raise FileExistsError(
            "Audit JSONL already exists: "
            f"{path}"
        )

    with path.open(
        "w",
        encoding="utf-8",
        newline="\n",
    ) as handle:
        for (
                rank,
                candidate,
        ) in enumerate(
            candidates,
            start=1,
        ):
            payload = {
                "audit_rank": rank,
                **candidate.model_dump(
                    mode="json"
                ),
            }

            handle.write(
                json.dumps(
                    payload,
                    ensure_ascii=False,
                    separators=(
                        ",",
                        ":",
                    ),
                )
            )

            handle.write(
                "\n"
            )


def candidate_to_csv_row(
        rank: int,
        candidate: StructuralAuditCandidate,
) -> dict[str, Any]:
    row = {
        "audit_rank": rank,
        "candidate_reasons": "|".join(
            candidate.candidate_reasons
        ),
        "sample_id": (
            candidate.sample_id
        ),
        "text": candidate.text,
        "gold_label": (
            candidate.gold_label
        ),
        "gold_label_name": (
            candidate.gold_label_name
        ),
        "source_label_name": (
            candidate.source_label_name
        ),
        "gold_coarse": (
            candidate.gold_coarse
        ),
        "source_coarse_label": (
            candidate.source_coarse_label
        ),
        "situation_code": (
            candidate.situation_code
        ),
        "situation": (
            candidate.situation
        ),
        "quality_status": (
            candidate.quality_status
        ),
        "quality_issue_codes": "|".join(
            candidate.quality_issue_codes
        ),
        "source_dataset": (
            candidate.source_dataset
        ),
        "source_version": (
            candidate.source_version
        ),
        "source_split": (
            candidate.source_split
        ),
        "profile_id": (
            candidate.profile_id
        ),
        "talk_id": (
            candidate.talk_id
        ),
        "mean_confidence": (
            candidate.mean_confidence
        ),
        "minimum_confidence": (
            candidate.minimum_confidence
        ),
        "maximum_confidence": (
            candidate.maximum_confidence
        ),
        "same_wrong_prediction": (
            candidate.same_wrong_prediction
        ),
        "coarse_status": (
            candidate.coarse_status
        ),
        "review_category": "",
        "review_gold_label_correct": "",
        "review_notes": "",
    }

    for seed_name in [
        "42",
        "123",
        "2026",
    ]:
        prediction = (
            candidate.predictions[
                seed_name
            ]
        )

        row[
            f"seed{seed_name}_label"
        ] = prediction.label

        row[
            f"seed{seed_name}_label_name"
        ] = prediction.label_name

        row[
            f"seed{seed_name}_coarse"
        ] = prediction.coarse

        row[
            f"seed{seed_name}_confidence"
        ] = prediction.confidence

    return row


def write_csv(
        path: Path,
        candidates: list[
            StructuralAuditCandidate
        ],
) -> None:
    if path.exists():
        raise FileExistsError(
            "Audit CSV already exists: "
            f"{path}"
        )

    rows = [
        candidate_to_csv_row(
            rank=rank,
            candidate=candidate,
        )
        for (
            rank,
            candidate,
        )
        in enumerate(
            candidates,
            start=1,
        )
    ]

    if not rows:
        raise ValueError(
            "No audit candidates "
            "were selected."
        )

    with path.open(
        "w",
        encoding="utf-8-sig",
        newline="",
    ) as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=list(
                rows[
                    0
                ].keys()
            ),
        )

        writer.writeheader()

        writer.writerows(
            rows
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build a human-review audit "
            "dataset for persistent "
            "three-seed ensemble errors."
        )
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--q17-report",
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
        "--cross-coarse-limit",
        type=int,
        default=(
            DEFAULT_CROSS_COARSE_LIMIT
        ),
    )

    parser.add_argument(
        "--target-labels",
        nargs="+",
        default=list(
            DEFAULT_TARGET_LABELS
        ),
    )

    parser.add_argument(
        "--target-per-label",
        type=int,
        default=(
            DEFAULT_TARGET_LIMIT_PER_LABEL
        ),
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output_dir.exists():
        raise FileExistsError(
            "Audit output directory "
            "already exists: "
            f"{args.output_dir}"
        )

    if args.report.exists():
        raise FileExistsError(
            "Audit report already exists: "
            f"{args.report}"
        )

    q17_report = load_report(
        args.q17_report
    )

    validate_report_dataset(
        q17_report,
        args.validation,
    )

    validate_q17_report_protocol(
        q17_report
    )

    validation_records = (
        load_validation_records(
            args.validation
        )
    )

    label_mapping = (
        EmotionLabelMapping()
    )

    label_names = dict(
        EMOTION_CODEBOOK
    )

    fine_to_coarse = {
        label: (
            get_coarse_emotion(
                label
            )
        )
        for label
        in label_mapping.labels
    }

    seed42_ko = load_logit_artifact(
        args.seed42_ko_logits
    )

    seed42_kc = load_logit_artifact(
        args.seed42_kc_logits
    )

    seed123_ko = load_logit_artifact(
        args.seed123_ko_logits
    )

    seed123_kc = load_logit_artifact(
        args.seed123_kc_logits
    )

    seed2026_ko = load_logit_artifact(
        args.seed2026_ko_logits
    )

    seed2026_kc = load_logit_artifact(
        args.seed2026_kc_logits
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

    model_key_checks = [
        (
            seed42_ko,
            KO_MODEL_KEY,
            "seed42 Ko",
        ),
        (
            seed42_kc,
            KC_MODEL_KEY,
            "seed42 Kc",
        ),
        (
            seed123_ko,
            KO_MODEL_KEY,
            "seed123 Ko",
        ),
        (
            seed123_kc,
            KC_MODEL_KEY,
            "seed123 Kc",
        ),
        (
            seed2026_ko,
            KO_MODEL_KEY,
            "seed2026 Ko",
        ),
        (
            seed2026_kc,
            KC_MODEL_KEY,
            "seed2026 Kc",
        ),
    ]

    for (
            artifact,
            expected,
            description,
    ) in model_key_checks:
        validate_model_key(
            artifact=artifact,
            expected=expected,
            description=description,
        )

    labels = seed42_ko[
        "labels"
    ]

    sample_ids = seed42_ko[
        "sample_ids"
    ]

    if (
            len(
                validation_records
            )
            != labels.shape[
                0
            ]
    ):
        raise ValueError(
            "Validation/logit sample "
            "count mismatch."
        )

    validation_ids = [
        record[
            "id"
        ]
        for record
        in validation_records
    ]

    if (
            validation_ids
            != sample_ids
    ):
        raise ValueError(
            "Validation sample IDs "
            "do not match saved logits."
        )

    seed_logits = {
        "42": combine_pair(
            seed42_ko,
            seed42_kc,
        ),
        "123": combine_pair(
            seed123_ko,
            seed123_kc,
        ),
        "2026": combine_pair(
            seed2026_ko,
            seed2026_kc,
        ),
    }

    seed_metrics = {}

    for (
            seed_name,
            logits,
    ) in seed_logits.items():
        metrics = evaluate_logits(
            logits=logits,
            labels=labels,
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )

        validate_q17_seed_metrics(
            seed_name=seed_name,
            actual=metrics,
            report=q17_report,
        )

        seed_metrics[
            seed_name
        ] = metrics

    selection = (
        build_structural_audit_candidates(
            records=(
                validation_records
            ),
            labels=labels,
            sample_ids=(
                sample_ids
            ),
            ensemble_logits=(
                seed_logits
            ),
            id2label=dict(
                label_mapping.id2label
            ),
            label_names=(
                label_names
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
            target_labels=list(
                args.target_labels
            ),
            cross_coarse_limit=(
                args.cross_coarse_limit
            ),
            target_limit_per_label=(
                args.target_per_label
            ),
        )
    )

    args.output_dir.mkdir(
        parents=True,
        exist_ok=False,
    )

    jsonl_path = (
        args.output_dir
        / "audit-candidates.jsonl"
    )

    csv_path = (
        args.output_dir
        / "audit-candidates.csv"
    )

    write_jsonl(
        path=jsonl_path,
        candidates=(
            selection.candidates
        ),
    )

    write_csv(
        path=csv_path,
        candidates=(
            selection.candidates
        ),
    )

    reason_counts = Counter()

    coarse_status_counts = Counter()

    for candidate in selection.candidates:
        for reason in (
            candidate.candidate_reasons
        ):
            reason_counts[
                reason
            ] += 1

        coarse_status_counts[
            candidate.coarse_status
        ] += 1

    report = {
        "experiment": (
            "T0-Q17-B-structural-"
            "label-context-audit"
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
                "samples": len(
                    validation_records
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
                "ko_weight": 0.5,
                "kc_weight": 0.5,
                "ensemble_type": (
                    "weighted_raw_logits"
                ),
            },
            "confidence_source": (
                "softmax_of_fixed_"
                "ensemble_logits"
            ),
            "confidence_calibrated": False,
            "cross_coarse_selection": (
                "highest_mean_confidence"
            ),
            "cross_coarse_limit": (
                args.cross_coarse_limit
            ),
            "target_labels": list(
                args.target_labels
            ),
            "target_limit_per_label": (
                args.target_per_label
            ),
            "selection_split": (
                "validation"
            ),
            "test_split_used": False,
        },
        "source_q17_a": {
            "file": str(
                args.q17_report
            ),
            "sha256": (
                calculate_sha256(
                    args.q17_report
                )
            ),
        },
        "input_artifacts": {
            "seed42": {
                "ko": artifact_summary(
                    args.seed42_ko_logits,
                    seed42_ko,
                ),
                "kc": artifact_summary(
                    args.seed42_kc_logits,
                    seed42_kc,
                ),
            },
            "seed123": {
                "ko": artifact_summary(
                    args.seed123_ko_logits,
                    seed123_ko,
                ),
                "kc": artifact_summary(
                    args.seed123_kc_logits,
                    seed123_kc,
                ),
            },
            "seed2026": {
                "ko": artifact_summary(
                    args.seed2026_ko_logits,
                    seed2026_ko,
                ),
                "kc": artifact_summary(
                    args.seed2026_kc_logits,
                    seed2026_kc,
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
        "selection": {
            "total_samples": (
                selection.total_samples
            ),
            "persistent_errors": (
                selection.persistent_errors
            ),
            "cross_coarse_persistent_errors": (
                selection
                .cross_coarse_persistent_errors
            ),
            "cross_coarse_selected": (
                selection
                .cross_coarse_selected
            ),
            "target_selected_counts": (
                selection
                .target_selected_counts
            ),
            "unique_selected": (
                selection.unique_selected
            ),
            "reason_counts": dict(
                reason_counts
            ),
            "coarse_status_counts": dict(
                coarse_status_counts
            ),
        },
        "manual_review": {
            "review_categories": list(
                REVIEW_CATEGORIES
            ),
            "category_definitions": {
                "DIRECT_CUE_CONFLICT": (
                    "Text contains a strong "
                    "emotion cue that supports "
                    "a non-gold prediction."
                ),
                "GOLD_SEMANTIC_MISMATCH": (
                    "Gold emotion appears "
                    "semantically inconsistent "
                    "with the full text."
                ),
                "FINE_BOUNDARY_AMBIGUITY": (
                    "Gold and prediction are "
                    "plausible neighboring fine "
                    "labels, usually within the "
                    "same coarse class."
                ),
                "TRUE_MODEL_ERROR": (
                    "Gold/context are coherent "
                    "and the model prediction "
                    "lacks textual support."
                ),
                "UNCLEAR": (
                    "Insufficient evidence for "
                    "a reliable audit decision."
                ),
            },
            "automatic_semantic_labeling": (
                False
            ),
        },
        "outputs": {
            "jsonl": {
                "file": str(
                    jsonl_path
                ),
                "rows": (
                    selection.unique_selected
                ),
                "bytes": (
                    jsonl_path
                    .stat()
                    .st_size
                ),
                "sha256": (
                    calculate_sha256(
                        jsonl_path
                    )
                ),
            },
            "csv": {
                "file": str(
                    csv_path
                ),
                "rows": (
                    selection.unique_selected
                ),
                "bytes": (
                    csv_path
                    .stat()
                    .st_size
                ),
                "sha256": (
                    calculate_sha256(
                        csv_path
                    )
                ),
                "encoding": (
                    "utf-8-sig"
                ),
            },
        },
    }

    save_report(
        report=report,
        output_path=(
            args.report
        ),
    )

    print()

    print(
        "Structural label/context "
        "audit dataset completed"
    )

    print()

    print(
        "Validation samples: "
        f"{selection.total_samples}"
    )

    print(
        "Persistent errors: "
        f"{selection.persistent_errors}"
    )

    print(
        "Persistent cross-coarse errors: "
        f"{selection.cross_coarse_persistent_errors}"
    )

    print()

    print(
        "Cross-coarse selected: "
        f"{selection.cross_coarse_selected}"
    )

    for target_label in (
        args.target_labels
    ):
        print(
            f"{target_label} selected: "
            f"{selection.target_selected_counts[target_label]}"
        )

    print()

    print(
        "Unique audit candidates: "
        f"{selection.unique_selected}"
    )

    print()

    print(
        "JSONL: "
        f"{jsonl_path}"
    )

    print(
        "CSV: "
        f"{csv_path}"
    )

    print(
        "Report: "
        f"{args.report}"
    )

    print()

    print(
        "Q17-B STRUCTURAL AUDIT EXPORT: PASS"
    )


if __name__ == "__main__":
    main()