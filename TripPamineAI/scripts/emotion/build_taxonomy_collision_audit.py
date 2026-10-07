import argparse
import csv
import json
from pathlib import Path
from typing import Any

from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    save_report,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.evaluation.emotion.taxonomy_collision_audit import (
    NO_COLLISION,
    TaxonomyCollisionAudit,
    analyze_taxonomy_collisions,
)


def load_audit_candidates(
        path: Path,
) -> list[
    dict[str, Any]
]:
    if not path.exists():
        raise FileNotFoundError(
            "Audit candidates not found: "
            f"{path}"
        )

    if path.stat().st_size == 0:
        raise ValueError(
            "Audit candidates file "
            "is empty."
        )

    candidates = []

    seen_ids = set()

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
                candidate = json.loads(
                    stripped
                )

            except json.JSONDecodeError as error:
                raise ValueError(
                    "Invalid audit JSONL at "
                    f"line {line_number}."
                ) from error

            if not isinstance(
                    candidate,
                    dict,
            ):
                raise ValueError(
                    "Audit JSONL must "
                    "contain objects."
                )

            sample_id = candidate.get(
                "sample_id"
            )

            if not isinstance(
                    sample_id,
                    str,
            ):
                raise ValueError(
                    "Audit candidate sample_id "
                    "is missing or invalid."
                )

            if sample_id in seen_ids:
                raise ValueError(
                    "Duplicate audit sample ID: "
                    f"{sample_id}."
                )

            seen_ids.add(
                sample_id
            )

            candidates.append(
                candidate
            )

    if not candidates:
        raise ValueError(
            "Audit candidates contain "
            "no records."
        )

    return candidates


def write_pair_csv(
        path: Path,
        audit: TaxonomyCollisionAudit,
) -> None:
    if path.exists():
        raise FileExistsError(
            "Pair CSV already exists: "
            f"{path}"
        )

    rows = []

    for (
            rank,
            pair,
    ) in enumerate(
        audit.observed_confusion_pairs,
        start=1,
    ):
        rows.append(
            {
                "rank": rank,
                "gold_label": (
                    pair.gold_label
                ),
                "gold_label_name": (
                    pair.gold_label_name
                ),
                "gold_coarse": (
                    pair.gold_coarse
                ),
                "predicted_label": (
                    pair.predicted_label
                ),
                "predicted_label_name": (
                    pair.predicted_label_name
                ),
                "predicted_coarse": (
                    pair.predicted_coarse
                ),
                "count": pair.count,
                "same_coarse": (
                    pair.same_coarse
                ),
                "mean_confidence": (
                    pair.mean_confidence
                ),
                "minimum_confidence": (
                    pair.minimum_confidence
                ),
                "maximum_confidence": (
                    pair.maximum_confidence
                ),
                "taxonomy_collision_type": (
                    pair.taxonomy_collision_type
                ),
                "coarse_status_counts": (
                    json.dumps(
                        pair.coarse_status_counts,
                        ensure_ascii=False,
                        separators=(
                            ",",
                            ":",
                        ),
                    )
                ),
                "candidate_reason_counts": (
                    json.dumps(
                        pair
                        .candidate_reason_counts,
                        ensure_ascii=False,
                        separators=(
                            ",",
                            ":",
                        ),
                    )
                ),
            }
        )

    if not rows:
        raise ValueError(
            "No observed confusion pairs "
            "were produced."
        )

    path.parent.mkdir(
        parents=True,
        exist_ok=True,
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
            "Audit objective taxonomy "
            "collisions and observed "
            "persistent confusion pairs."
        )
    )

    parser.add_argument(
        "--audit-candidates",
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

    candidates = load_audit_candidates(
        args.audit_candidates
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
        in label_names
    }

    audit = analyze_taxonomy_collisions(
        candidates=candidates,
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    args.output_dir.mkdir(
        parents=True,
        exist_ok=False,
    )

    pair_csv = (
        args.output_dir
        / "observed-confusion-pairs.csv"
    )

    write_pair_csv(
        path=pair_csv,
        audit=audit,
    )

    structural_pairs = [
        pair
        for pair
        in audit.observed_confusion_pairs
        if (
            pair.taxonomy_collision_type
            != NO_COLLISION
        )
    ]

    report = {
        "experiment": (
            "T0-Q17-C1-taxonomy-"
            "collision-audit"
        ),
        "protocol": {
            "source": (
                "q17-b-audit-candidates"
            ),
            "automatic_semantic_similarity": (
                False
            ),
            "collision_rules": [
                "exact_normalized_name",
                "same_base_name_after_"
                "trailing_parenthetical_"
                "qualifier_removal",
            ],
            "test_split_used": False,
        },
        "input": {
            "audit_candidates": {
                "file": str(
                    args.audit_candidates
                ),
                "rows": len(
                    candidates
                ),
                "sha256": (
                    calculate_sha256(
                        args.audit_candidates
                    )
                ),
            },
        },
        "summary": {
            "total_candidates": (
                audit.total_candidates
            ),
            "consensus_error_candidates": (
                audit
                .consensus_error_candidates
            ),
            "varying_prediction_candidates": (
                audit
                .varying_prediction_candidates
            ),
            "distinct_consensus_pairs": (
                audit
                .distinct_consensus_pairs
            ),
            "taxonomy_collision_family_count": (
                audit
                .taxonomy_collision_family_count
            ),
            "cross_coarse_taxonomy_family_count": (
                audit
                .cross_coarse_taxonomy_family_count
            ),
            "observed_taxonomy_collision_pairs": (
                audit
                .observed_taxonomy_collision_pairs
            ),
            "observed_taxonomy_collision_occurrences": (
                audit
                .observed_taxonomy_collision_occurrences
            ),
            "source_label_name_mismatch_count": (
                len(
                    audit
                    .source_label_name_mismatches
                )
            ),
        },
        "taxonomy_collision_families": [
            family.model_dump(
                mode="json"
            )
            for family
            in audit.taxonomy_collision_families
        ],
        "observed_taxonomy_collision_pairs": [
            pair.model_dump(
                mode="json"
            )
            for pair
            in structural_pairs
        ],
        "source_label_name_mismatches": [
            mismatch.model_dump(
                mode="json"
            )
            for mismatch
            in audit.source_label_name_mismatches
        ],
        "top_observed_confusion_pairs": [
            pair.model_dump(
                mode="json"
            )
            for pair
            in audit.observed_confusion_pairs[
                :30
            ]
        ],
        "outputs": {
            "pair_csv": {
                "file": str(
                    pair_csv
                ),
                "rows": (
                    audit
                    .distinct_consensus_pairs
                ),
                "bytes": (
                    pair_csv
                    .stat()
                    .st_size
                ),
                "sha256": (
                    calculate_sha256(
                        pair_csv
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
        "Taxonomy collision audit "
        "completed"
    )

    print()

    print(
        "Audit candidates: "
        f"{audit.total_candidates}"
    )

    print(
        "Consensus errors: "
        f"{audit.consensus_error_candidates}"
    )

    print(
        "Varying predictions: "
        f"{audit.varying_prediction_candidates}"
    )

    print()

    print(
        "Taxonomy collision families: "
        f"{audit.taxonomy_collision_family_count}"
    )

    print(
        "Cross-coarse collision families: "
        f"{audit.cross_coarse_taxonomy_family_count}"
    )

    print()

    print(
        "Observed collision pairs: "
        f"{audit.observed_taxonomy_collision_pairs}"
    )

    print(
        "Observed collision occurrences: "
        f"{audit.observed_taxonomy_collision_occurrences}"
    )

    print()

    for family in (
        audit.taxonomy_collision_families
    ):
        labels = ", ".join(
            (
                f"{item.label}="
                f"{item.label_name}/"
                f"{item.coarse_label}"
            )
            for item
            in family.labels
        )

        print(
            "Collision family "
            f"[{family.base_name}]: "
            f"{labels}"
        )

    print()

    print(
        "Observed pairs CSV: "
        f"{pair_csv}"
    )

    print(
        "Report: "
        f"{args.report}"
    )

    print()

    print(
        "Q17-C1 TAXONOMY "
        "COLLISION AUDIT: PASS"
    )


if __name__ == "__main__":
    main()