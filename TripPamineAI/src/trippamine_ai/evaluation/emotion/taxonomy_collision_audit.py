import re
import unicodedata
from collections import Counter
from collections import defaultdict
from typing import Any

from pydantic import BaseModel


NO_COLLISION = "NONE"

EXACT_NAME_COLLISION = (
    "EXACT_NAME_COLLISION"
)

BASE_NAME_COLLISION = (
    "BASE_NAME_COLLISION"
)


class TaxonomyCollisionLabel(BaseModel):
    label: str
    label_name: str
    coarse_label: str


class TaxonomyCollisionFamily(BaseModel):
    base_name: str

    labels: list[
        TaxonomyCollisionLabel
    ]

    cross_coarse: bool


class ObservedConfusionPair(BaseModel):
    gold_label: str
    gold_label_name: str
    gold_coarse: str

    predicted_label: str
    predicted_label_name: str
    predicted_coarse: str

    count: int

    same_coarse: bool

    coarse_status_counts: dict[
        str,
        int,
    ]

    mean_confidence: float
    minimum_confidence: float
    maximum_confidence: float

    taxonomy_collision_type: str

    candidate_reason_counts: dict[
        str,
        int,
    ]


class SourceLabelMismatch(BaseModel):
    sample_id: str
    label: str

    codebook_name: str
    source_label_name: str


class TaxonomyCollisionAudit(BaseModel):
    total_candidates: int

    consensus_error_candidates: int
    varying_prediction_candidates: int

    distinct_consensus_pairs: int

    taxonomy_collision_family_count: int

    cross_coarse_taxonomy_family_count: int

    observed_taxonomy_collision_pairs: int

    observed_taxonomy_collision_occurrences: int

    source_label_name_mismatches: list[
        SourceLabelMismatch
    ]

    taxonomy_collision_families: list[
        TaxonomyCollisionFamily
    ]

    observed_confusion_pairs: list[
        ObservedConfusionPair
    ]


def normalize_label_name(
        value: str,
) -> str:
    if not isinstance(
            value,
            str,
    ):
        raise TypeError(
            "Label name must be a string."
        )

    normalized = unicodedata.normalize(
        "NFKC",
        value,
    )

    return " ".join(
        normalized
        .strip()
        .split()
    )


def base_label_name(
        value: str,
) -> str:
    normalized = normalize_label_name(
        value
    )

    without_qualifier = re.sub(
        r"\s*\([^()]*\)\s*$",
        "",
        normalized,
    )

    return without_qualifier.strip()


def detect_collision_type(
        first_label: str,
        second_label: str,
        label_names: dict[
            str,
            str,
        ],
) -> str:
    if first_label == second_label:
        return NO_COLLISION

    first_name = normalize_label_name(
        label_names[
            first_label
        ]
    )

    second_name = normalize_label_name(
        label_names[
            second_label
        ]
    )

    if first_name == second_name:
        return EXACT_NAME_COLLISION

    first_base = base_label_name(
        first_name
    )

    second_base = base_label_name(
        second_name
    )

    if first_base == second_base:
        return BASE_NAME_COLLISION

    return NO_COLLISION


def build_collision_families(
        label_names: dict[
            str,
            str,
        ],
        fine_to_coarse: dict[
            str,
            str,
        ],
) -> list[
    TaxonomyCollisionFamily
]:
    if not label_names:
        raise ValueError(
            "label_names must not be empty."
        )

    missing_coarse = (
        set(
            label_names
        )
        - set(
            fine_to_coarse
        )
    )

    if missing_coarse:
        raise ValueError(
            "Missing coarse mapping for "
            "labels: "
            f"{sorted(missing_coarse)}."
        )

    grouped: dict[
        str,
        list[str],
    ] = defaultdict(
        list
    )

    for (
            label,
            label_name,
    ) in label_names.items():
        grouped[
            base_label_name(
                label_name
            )
        ].append(
            label
        )

    families = []

    for (
            base_name,
            labels,
    ) in sorted(
        grouped.items()
    ):
        if len(
                labels
        ) < 2:
            continue

        sorted_labels = sorted(
            labels
        )

        coarse_labels = {
            fine_to_coarse[
                label
            ]
            for label
            in sorted_labels
        }

        families.append(
            TaxonomyCollisionFamily(
                base_name=(
                    base_name
                ),
                labels=[
                    TaxonomyCollisionLabel(
                        label=label,
                        label_name=(
                            label_names[
                                label
                            ]
                        ),
                        coarse_label=(
                            fine_to_coarse[
                                label
                            ]
                        ),
                    )
                    for label
                    in sorted_labels
                ],
                cross_coarse=(
                    len(
                        coarse_labels
                    )
                    > 1
                ),
            )
        )

    return families


def validate_candidate(
        candidate: dict[
            str,
            Any,
        ],
        label_names: dict[
            str,
            str,
        ],
        fine_to_coarse: dict[
            str,
            str,
        ],
) -> None:
    required_keys = {
        "sample_id",
        "gold_label",
        "gold_label_name",
        "source_label_name",
        "gold_coarse",
        "predictions",
        "mean_confidence",
        "minimum_confidence",
        "maximum_confidence",
        "same_wrong_prediction",
        "coarse_status",
        "candidate_reasons",
    }

    missing = (
        required_keys
        - set(
            candidate
        )
    )

    if missing:
        raise ValueError(
            "Audit candidate is missing "
            "required keys: "
            f"{sorted(missing)}."
        )

    gold_label = candidate[
        "gold_label"
    ]

    if gold_label not in label_names:
        raise ValueError(
            "Unknown gold label: "
            f"{gold_label}."
        )

    if gold_label not in fine_to_coarse:
        raise ValueError(
            "Missing gold coarse mapping: "
            f"{gold_label}."
        )

    predictions = candidate[
        "predictions"
    ]

    if not isinstance(
            predictions,
            dict,
    ):
        raise ValueError(
            "Candidate predictions must "
            "be a dictionary."
        )

    if len(
            predictions
    ) != 3:
        raise ValueError(
            "Expected exactly 3 seed "
            "predictions."
        )

    predicted_labels = []

    for (
            seed_name,
            prediction,
    ) in predictions.items():
        if not isinstance(
                prediction,
                dict,
        ):
            raise ValueError(
                "Seed prediction must "
                "be a dictionary."
            )

        try:
            predicted_label = (
                prediction[
                    "label"
                ]
            )

        except KeyError as error:
            raise ValueError(
                "Seed prediction is "
                "missing label: "
                f"{seed_name}."
            ) from error

        if (
                predicted_label
                not in label_names
        ):
            raise ValueError(
                "Unknown predicted label: "
                f"{predicted_label}."
            )

        predicted_labels.append(
            predicted_label
        )

    unique_predictions = set(
        predicted_labels
    )

    same_wrong_prediction = bool(
        candidate[
            "same_wrong_prediction"
        ]
    )

    if (
            same_wrong_prediction
            and len(
                unique_predictions
            )
            != 1
    ):
        raise ValueError(
            "same_wrong_prediction=True "
            "but seed predictions differ."
        )

    for confidence_key in (
        "mean_confidence",
        "minimum_confidence",
        "maximum_confidence",
    ):
        confidence = float(
            candidate[
                confidence_key
            ]
        )

        if (
                confidence < 0.0
                or confidence > 1.0
        ):
            raise ValueError(
                f"{confidence_key} must "
                "be between 0 and 1."
            )


def analyze_taxonomy_collisions(
        candidates: list[
            dict[str, Any]
        ],
        label_names: dict[
            str,
            str,
        ],
        fine_to_coarse: dict[
            str,
            str,
        ],
) -> TaxonomyCollisionAudit:
    if not candidates:
        raise ValueError(
            "Audit candidates must "
            "not be empty."
        )

    families = build_collision_families(
        label_names=label_names,
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    pair_samples: dict[
        tuple[
            str,
            str,
        ],
        list[
            dict[str, Any]
        ],
    ] = defaultdict(
        list
    )

    source_mismatches = []

    consensus_count = 0
    varying_count = 0

    for candidate in candidates:
        validate_candidate(
            candidate=candidate,
            label_names=label_names,
            fine_to_coarse=(
                fine_to_coarse
            ),
        )

        gold_label = candidate[
            "gold_label"
        ]

        expected_name = label_names[
            gold_label
        ]

        source_name = candidate[
            "source_label_name"
        ]

        if (
                normalize_label_name(
                    expected_name
                )
                != normalize_label_name(
                    source_name
                )
        ):
            source_mismatches.append(
                SourceLabelMismatch(
                    sample_id=(
                        candidate[
                            "sample_id"
                        ]
                    ),
                    label=(
                        gold_label
                    ),
                    codebook_name=(
                        expected_name
                    ),
                    source_label_name=(
                        source_name
                    ),
                )
            )

        if not candidate[
            "same_wrong_prediction"
        ]:
            varying_count += 1
            continue

        consensus_count += 1

        predicted_labels = {
            prediction[
                "label"
            ]
            for prediction
            in candidate[
                "predictions"
            ].values()
        }

        if len(
                predicted_labels
        ) != 1:
            raise RuntimeError(
                "Consensus candidate contains "
                "multiple predicted labels."
            )

        predicted_label = next(
            iter(
                predicted_labels
            )
        )

        pair_samples[
            (
                gold_label,
                predicted_label,
            )
        ].append(
            candidate
        )

    observed_pairs = []

    taxonomy_pair_count = 0
    taxonomy_occurrences = 0

    for (
            (
                gold_label,
                predicted_label,
            ),
            pair_candidates,
    ) in pair_samples.items():
        collision_type = (
            detect_collision_type(
                first_label=(
                    gold_label
                ),
                second_label=(
                    predicted_label
                ),
                label_names=(
                    label_names
                ),
            )
        )

        if (
                collision_type
                != NO_COLLISION
        ):
            taxonomy_pair_count += 1
            taxonomy_occurrences += len(
                pair_candidates
            )

        confidences = [
            float(
                candidate[
                    "mean_confidence"
                ]
            )
            for candidate
            in pair_candidates
        ]

        status_counts = Counter(
            candidate[
                "coarse_status"
            ]
            for candidate
            in pair_candidates
        )

        reason_counts = Counter()

        for candidate in pair_candidates:
            for reason in candidate[
                "candidate_reasons"
            ]:
                reason_counts[
                    reason
                ] += 1

        gold_coarse = (
            fine_to_coarse[
                gold_label
            ]
        )

        predicted_coarse = (
            fine_to_coarse[
                predicted_label
            ]
        )

        observed_pairs.append(
            ObservedConfusionPair(
                gold_label=(
                    gold_label
                ),
                gold_label_name=(
                    label_names[
                        gold_label
                    ]
                ),
                gold_coarse=(
                    gold_coarse
                ),
                predicted_label=(
                    predicted_label
                ),
                predicted_label_name=(
                    label_names[
                        predicted_label
                    ]
                ),
                predicted_coarse=(
                    predicted_coarse
                ),
                count=len(
                    pair_candidates
                ),
                same_coarse=(
                    gold_coarse
                    == predicted_coarse
                ),
                coarse_status_counts=dict(
                    sorted(
                        status_counts.items()
                    )
                ),
                mean_confidence=(
                    sum(
                        confidences
                    )
                    / len(
                        confidences
                    )
                ),
                minimum_confidence=min(
                    confidences
                ),
                maximum_confidence=max(
                    confidences
                ),
                taxonomy_collision_type=(
                    collision_type
                ),
                candidate_reason_counts=dict(
                    sorted(
                        reason_counts.items()
                    )
                ),
            )
        )

    observed_pairs = sorted(
        observed_pairs,
        key=lambda pair: (
            -pair.count,
            (
                0
                if (
                    pair.taxonomy_collision_type
                    != NO_COLLISION
                )
                else 1
            ),
            -pair.mean_confidence,
            pair.gold_label,
            pair.predicted_label,
        ),
    )

    cross_coarse_family_count = sum(
        1
        for family
        in families
        if family.cross_coarse
    )

    return TaxonomyCollisionAudit(
        total_candidates=len(
            candidates
        ),
        consensus_error_candidates=(
            consensus_count
        ),
        varying_prediction_candidates=(
            varying_count
        ),
        distinct_consensus_pairs=len(
            observed_pairs
        ),
        taxonomy_collision_family_count=(
            len(
                families
            )
        ),
        cross_coarse_taxonomy_family_count=(
            cross_coarse_family_count
        ),
        observed_taxonomy_collision_pairs=(
            taxonomy_pair_count
        ),
        observed_taxonomy_collision_occurrences=(
            taxonomy_occurrences
        ),
        source_label_name_mismatches=(
            source_mismatches
        ),
        taxonomy_collision_families=(
            families
        ),
        observed_confusion_pairs=(
            observed_pairs
        ),
    )