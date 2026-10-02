from collections import defaultdict
from typing import Any

import torch
from pydantic import BaseModel, Field


EXPECTED_SEED_COUNT = 3

HIGH_CONFIDENCE_CROSS_COARSE = (
    "HIGH_CONFIDENCE_CROSS_COARSE"
)

TARGET_LABEL_PERSISTENT_ERROR = (
    "TARGET_LABEL_PERSISTENT_ERROR"
)

REVIEW_CATEGORIES = (
    "DIRECT_CUE_CONFLICT",
    "GOLD_SEMANTIC_MISMATCH",
    "FINE_BOUNDARY_AMBIGUITY",
    "TRUE_MODEL_ERROR",
    "UNCLEAR",
)


class SeedAuditPrediction(BaseModel):
    label: str
    label_name: str
    coarse: str
    confidence: float


class StructuralAuditCandidate(BaseModel):
    sample_id: str
    text: str

    gold_label: str
    gold_label_name: str
    source_label_name: str

    gold_coarse: str
    source_coarse_label: str

    situation_code: str
    situation: str

    quality_status: str
    quality_issue_codes: list[str]

    source_dataset: str
    source_version: str
    source_split: str
    profile_id: str
    talk_id: str

    predictions: dict[
        str,
        SeedAuditPrediction,
    ]

    mean_confidence: float
    minimum_confidence: float
    maximum_confidence: float

    same_wrong_prediction: bool
    coarse_status: str

    candidate_reasons: list[
        str
    ] = Field(
        default_factory=list
    )

    review_category: str | None = None
    review_gold_label_correct: bool | None = None
    review_notes: str | None = None


class StructuralAuditSelection(BaseModel):
    total_samples: int

    persistent_errors: int
    cross_coarse_persistent_errors: int

    cross_coarse_selected: int

    target_selected_counts: dict[
        str,
        int,
    ]

    unique_selected: int

    candidates: list[
        StructuralAuditCandidate
    ]


def validate_inputs(
        records: list[
            dict[str, Any]
        ],
        labels: torch.Tensor,
        sample_ids: list[str],
        ensemble_logits: dict[
            str,
            torch.Tensor,
        ],
        id2label: dict[
            int,
            str,
        ],
        label_names: dict[
            str,
            str,
        ],
        fine_to_coarse: dict[
            str,
            str,
        ],
        target_labels: list[str],
) -> None:
    if labels.ndim != 1:
        raise ValueError(
            "Labels must be rank 1."
        )

    if (
            len(
                records
            )
            != labels.shape[
                0
            ]
    ):
        raise ValueError(
            "Validation records do not "
            "match the label count."
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
            "Sample IDs do not match "
            "the label count."
        )

    if (
            len(
                ensemble_logits
            )
            != EXPECTED_SEED_COUNT
    ):
        raise ValueError(
            "Expected exactly "
            f"{EXPECTED_SEED_COUNT} "
            "seed ensembles."
        )

    if not id2label:
        raise ValueError(
            "id2label must not be empty."
        )

    if (
            len(
                set(
                    target_labels
                )
            )
            != len(
                target_labels
            )
    ):
        raise ValueError(
            "Target labels must "
            "not contain duplicates."
        )

    expected_shape = (
        labels.shape[
            0
        ],
        len(
            id2label
        ),
    )

    for (
            seed_name,
            logits,
    ) in ensemble_logits.items():
        if not torch.is_tensor(
                logits
        ):
            raise ValueError(
                f"{seed_name} logits "
                "must be a tensor."
            )

        if (
                tuple(
                    logits.shape
                )
                != expected_shape
        ):
            raise ValueError(
                f"{seed_name} logits "
                "shape mismatch: "
                f"expected={expected_shape}, "
                f"actual={tuple(logits.shape)}."
            )

        if not torch.isfinite(
                logits
        ).all():
            raise ValueError(
                f"{seed_name} logits "
                "contain non-finite values."
            )

    all_labels = set(
        id2label.values()
    )

    for label in all_labels:
        if label not in label_names:
            raise ValueError(
                "Label name is missing "
                f"for {label}."
            )

        if label not in fine_to_coarse:
            raise ValueError(
                "Coarse label is missing "
                f"for {label}."
            )

    for target_label in target_labels:
        if target_label not in all_labels:
            raise ValueError(
                "Unknown target label: "
                f"{target_label}."
            )

    required_record_keys = {
        "id",
        "text",
        "label",
        "label_name",
        "coarse_label",
        "situation_code",
        "situation",
        "quality_status",
        "quality_issue_codes",
        "source",
    }

    required_source_keys = {
        "dataset",
        "version",
        "split",
        "profile_id",
        "talk_id",
    }

    seen_ids = set()

    for (
            index,
            (
                record,
                sample_id,
                true_id,
            ),
    ) in enumerate(
        zip(
            records,
            sample_ids,
            labels.tolist(),
            strict=True,
        )
    ):
        if not isinstance(
                record,
                dict,
        ):
            raise ValueError(
                "Validation record must "
                "be a dictionary at index "
                f"{index}."
            )

        missing = (
            required_record_keys
            - set(
                record.keys()
            )
        )

        if missing:
            raise ValueError(
                "Validation record is "
                "missing required keys: "
                f"{sorted(missing)}."
            )

        record_id = record[
            "id"
        ]

        if record_id != sample_id:
            raise ValueError(
                "Validation record/sample "
                "ID mismatch at index "
                f"{index}."
            )

        if record_id in seen_ids:
            raise ValueError(
                "Duplicate validation "
                f"sample ID: {record_id}."
            )

        seen_ids.add(
            record_id
        )

        true_id = int(
            true_id
        )

        if true_id not in id2label:
            raise ValueError(
                "Unknown true label ID: "
                f"{true_id}."
            )

        true_label = id2label[
            true_id
        ]

        if (
                record[
                    "label"
                ]
                != true_label
        ):
            raise ValueError(
                "Validation record label "
                "does not match artifact "
                "label at index "
                f"{index}: "
                f"record={record['label']}, "
                f"artifact={true_label}."
            )

        if not isinstance(
                record[
                    "quality_issue_codes"
                ],
                list,
        ):
            raise ValueError(
                "quality_issue_codes must "
                "be a list."
            )

        source = record[
            "source"
        ]

        if not isinstance(
                source,
                dict,
        ):
            raise ValueError(
                "Validation source must "
                "be a dictionary."
            )

        missing_source = (
            required_source_keys
            - set(
                source.keys()
            )
        )

        if missing_source:
            raise ValueError(
                "Validation source is "
                "missing required keys: "
                f"{sorted(missing_source)}."
            )


def build_structural_audit_candidates(
        records: list[
            dict[str, Any]
        ],
        labels: torch.Tensor,
        sample_ids: list[str],
        ensemble_logits: dict[
            str,
            torch.Tensor,
        ],
        id2label: dict[
            int,
            str,
        ],
        label_names: dict[
            str,
            str,
        ],
        fine_to_coarse: dict[
            str,
            str,
        ],
        target_labels: list[str],
        cross_coarse_limit: int,
        target_limit_per_label: int,
) -> StructuralAuditSelection:
    if cross_coarse_limit < 0:
        raise ValueError(
            "cross_coarse_limit must "
            "be non-negative."
        )

    if target_limit_per_label < 0:
        raise ValueError(
            "target_limit_per_label must "
            "be non-negative."
        )

    labels = (
        labels
        .detach()
        .long()
        .cpu()
    )

    normalized_logits = {
        seed_name: (
            logits
            .detach()
            .float()
            .cpu()
        )
        for seed_name, logits
        in ensemble_logits.items()
    }

    validate_inputs(
        records=records,
        labels=labels,
        sample_ids=sample_ids,
        ensemble_logits=(
            normalized_logits
        ),
        id2label=id2label,
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
        target_labels=(
            target_labels
        ),
    )

    seed_names = list(
        normalized_logits.keys()
    )

    predictions = {}
    confidences = {}

    for (
            seed_name,
            logits,
    ) in normalized_logits.items():
        probabilities = torch.softmax(
            logits,
            dim=-1,
        )

        (
            seed_confidences,
            seed_predictions,
        ) = torch.max(
            probabilities,
            dim=-1,
        )

        if not torch.isfinite(
                seed_confidences
        ).all():
            raise RuntimeError(
                "Non-finite confidence "
                f"detected for {seed_name}."
            )

        predictions[
            seed_name
        ] = seed_predictions

        confidences[
            seed_name
        ] = seed_confidences

    persistent_candidates = []

    true_ids = labels.tolist()

    for (
            sample_index,
            true_id,
    ) in enumerate(
        true_ids
    ):
        true_id = int(
            true_id
        )

        true_label = id2label[
            true_id
        ]

        true_coarse = (
            fine_to_coarse[
                true_label
            ]
        )

        seed_prediction_ids = {
            seed_name: int(
                predictions[
                    seed_name
                ][
                    sample_index
                ].item()
            )
            for seed_name
            in seed_names
        }

        correct_by_seed = [
            seed_prediction_ids[
                seed_name
            ]
            == true_id
            for seed_name
            in seed_names
        ]

        if any(
            correct_by_seed
        ):
            continue

        record = records[
            sample_index
        ]

        seed_predictions = {}

        for seed_name in seed_names:
            predicted_id = (
                seed_prediction_ids[
                    seed_name
                ]
            )

            predicted_label = (
                id2label[
                    predicted_id
                ]
            )

            seed_predictions[
                seed_name
            ] = SeedAuditPrediction(
                label=(
                    predicted_label
                ),
                label_name=(
                    label_names[
                        predicted_label
                    ]
                ),
                coarse=(
                    fine_to_coarse[
                        predicted_label
                    ]
                ),
                confidence=float(
                    confidences[
                        seed_name
                    ][
                        sample_index
                    ].item()
                ),
            )

        confidence_values = [
            prediction.confidence
            for prediction
            in seed_predictions.values()
        ]

        mean_confidence = (
            sum(
                confidence_values
            )
            / len(
                confidence_values
            )
        )

        predicted_labels = {
            prediction.label
            for prediction
            in seed_predictions.values()
        }

        coarse_correct_by_seed = [
            prediction.coarse
            == true_coarse
            for prediction
            in seed_predictions.values()
        ]

        if all(
            coarse_correct_by_seed
        ):
            coarse_status = (
                "FINE_ONLY"
            )

        elif not any(
            coarse_correct_by_seed
        ):
            coarse_status = (
                "CROSS_COARSE"
            )

        else:
            coarse_status = (
                "MIXED_COARSE"
            )

        source = record[
            "source"
        ]

        persistent_candidates.append(
            StructuralAuditCandidate(
                sample_id=(
                    record[
                        "id"
                    ]
                ),
                text=(
                    record[
                        "text"
                    ]
                ),
                gold_label=(
                    true_label
                ),
                gold_label_name=(
                    label_names[
                        true_label
                    ]
                ),
                source_label_name=(
                    record[
                        "label_name"
                    ]
                ),
                gold_coarse=(
                    true_coarse
                ),
                source_coarse_label=(
                    record[
                        "coarse_label"
                    ]
                ),
                situation_code=(
                    record[
                        "situation_code"
                    ]
                ),
                situation=(
                    record[
                        "situation"
                    ]
                ),
                quality_status=(
                    record[
                        "quality_status"
                    ]
                ),
                quality_issue_codes=[
                    str(
                        code
                    )
                    for code
                    in record[
                        "quality_issue_codes"
                    ]
                ],
                source_dataset=(
                    source[
                        "dataset"
                    ]
                ),
                source_version=(
                    source[
                        "version"
                    ]
                ),
                source_split=(
                    source[
                        "split"
                    ]
                ),
                profile_id=(
                    source[
                        "profile_id"
                    ]
                ),
                talk_id=(
                    source[
                        "talk_id"
                    ]
                ),
                predictions=(
                    seed_predictions
                ),
                mean_confidence=(
                    mean_confidence
                ),
                minimum_confidence=min(
                    confidence_values
                ),
                maximum_confidence=max(
                    confidence_values
                ),
                same_wrong_prediction=(
                    len(
                        predicted_labels
                    )
                    == 1
                ),
                coarse_status=(
                    coarse_status
                ),
            )
        )

    cross_coarse_candidates = sorted(
        [
            candidate
            for candidate
            in persistent_candidates
            if (
                candidate.coarse_status
                == "CROSS_COARSE"
            )
        ],
        key=lambda candidate: (
            -candidate.mean_confidence,
            -candidate.maximum_confidence,
            candidate.sample_id,
        ),
    )

    selected_cross_coarse = (
        cross_coarse_candidates[
            :cross_coarse_limit
        ]
    )

    reasons_by_sample: dict[
        str,
        set[str],
    ] = defaultdict(
        set
    )

    candidate_by_sample = {
        candidate.sample_id: candidate
        for candidate
        in persistent_candidates
    }

    for candidate in selected_cross_coarse:
        reasons_by_sample[
            candidate.sample_id
        ].add(
            HIGH_CONFIDENCE_CROSS_COARSE
        )

    target_selected_counts = {}

    for target_label in target_labels:
        target_candidates = sorted(
            [
                candidate
                for candidate
                in persistent_candidates
                if (
                    candidate.gold_label
                    == target_label
                )
            ],
            key=lambda candidate: (
                -candidate.mean_confidence,
                -candidate.maximum_confidence,
                candidate.sample_id,
            ),
        )[
            :target_limit_per_label
        ]

        target_selected_counts[
            target_label
        ] = len(
            target_candidates
        )

        for candidate in target_candidates:
            reasons_by_sample[
                candidate.sample_id
            ].add(
                TARGET_LABEL_PERSISTENT_ERROR
            )

    selected_candidates = []

    for (
            sample_id,
            reasons,
    ) in reasons_by_sample.items():
        candidate = candidate_by_sample[
            sample_id
        ]

        selected_candidates.append(
            candidate.model_copy(
                update={
                    "candidate_reasons": sorted(
                        reasons
                    )
                }
            )
        )

    selected_candidates = sorted(
        selected_candidates,
        key=lambda candidate: (
            0
            if (
                HIGH_CONFIDENCE_CROSS_COARSE
                in candidate.candidate_reasons
            )
            else 1,
            -candidate.mean_confidence,
            candidate.gold_label,
            candidate.sample_id,
        ),
    )

    return StructuralAuditSelection(
        total_samples=int(
            labels.shape[
                0
            ]
        ),
        persistent_errors=len(
            persistent_candidates
        ),
        cross_coarse_persistent_errors=len(
            cross_coarse_candidates
        ),
        cross_coarse_selected=len(
            selected_cross_coarse
        ),
        target_selected_counts=(
            target_selected_counts
        ),
        unique_selected=len(
            selected_candidates
        ),
        candidates=(
            selected_candidates
        ),
    )