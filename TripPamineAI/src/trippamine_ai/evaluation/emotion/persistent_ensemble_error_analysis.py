from collections import Counter

import torch
from pydantic import BaseModel


EXPECTED_SEED_COUNT = 3


class ConfidenceDistribution(BaseModel):
    count: int

    mean: float | None
    minimum: float | None

    p50: float | None
    p90: float | None
    p95: float | None

    maximum: float | None


class PersistentConfusion(BaseModel):
    true_label: str
    predicted_label: str

    true_coarse: str
    predicted_coarse: str

    same_coarse: bool

    count: int


class PersistentLabelSummary(BaseModel):
    label: str

    support: int

    stable_correct: int
    persistent_error: int
    mixed_outcome: int

    stable_correct_rate: float
    persistent_error_rate: float
    mixed_outcome_rate: float


class PersistentErrorExample(BaseModel):
    sample_id: str

    true_label: str
    true_coarse: str

    predicted_labels: dict[
        str,
        str,
    ]

    predicted_coarse: dict[
        str,
        str,
    ]

    confidences: dict[
        str,
        float,
    ]

    mean_confidence: float
    minimum_confidence: float
    maximum_confidence: float

    same_wrong_prediction: bool

    coarse_status: str


class PersistentEnsembleErrorAnalysis(
    BaseModel
):
    total_samples: int

    seed_names: list[str]

    stable_correct: int
    persistent_errors: int
    mixed_outcomes: int

    stable_correct_rate: float
    persistent_error_rate: float
    mixed_outcome_rate: float

    persistent_same_wrong_prediction: int
    persistent_varying_wrong_prediction: int

    persistent_fine_only_errors: int
    persistent_cross_coarse_errors: int
    persistent_mixed_coarse_errors: int

    confidence: dict[
        str,
        ConfidenceDistribution,
    ]

    top_persistent_confusions: list[
        PersistentConfusion
    ]

    label_summary: list[
        PersistentLabelSummary
    ]

    highest_confidence_persistent_errors: list[
        PersistentErrorExample
    ]


def summarize_confidences(
        values: list[float],
) -> ConfidenceDistribution:
    if not values:
        return ConfidenceDistribution(
            count=0,
            mean=None,
            minimum=None,
            p50=None,
            p90=None,
            p95=None,
            maximum=None,
        )

    tensor = torch.tensor(
        values,
        dtype=torch.float64,
    )

    if not torch.isfinite(
            tensor
    ).all():
        raise ValueError(
            "Confidence values must "
            "be finite."
        )

    if (
            torch.any(
                tensor < 0.0
            )
            or torch.any(
                tensor > 1.0
            )
    ):
        raise ValueError(
            "Confidence values must "
            "be between 0 and 1."
        )

    return ConfidenceDistribution(
        count=len(
            values
        ),
        mean=float(
            torch.mean(
                tensor
            ).item()
        ),
        minimum=float(
            torch.min(
                tensor
            ).item()
        ),
        p50=float(
            torch.quantile(
                tensor,
                0.50,
            ).item()
        ),
        p90=float(
            torch.quantile(
                tensor,
                0.90,
            ).item()
        ),
        p95=float(
            torch.quantile(
                tensor,
                0.95,
            ).item()
        ),
        maximum=float(
            torch.max(
                tensor
            ).item()
        ),
    )


def validate_analysis_inputs(
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
        fine_to_coarse: dict[
            str,
            str,
        ],
) -> None:
    if labels.ndim != 1:
        raise ValueError(
            "Labels must be rank 1."
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

    valid_label_ids = set(
        id2label.keys()
    )

    for label_id in labels.tolist():
        if (
                int(
                    label_id
                )
                not in valid_label_ids
        ):
            raise ValueError(
                "Label ID is missing "
                "from id2label: "
                f"{label_id}."
            )

    for fine_label in id2label.values():
        if (
                fine_label
                not in fine_to_coarse
        ):
            raise ValueError(
                "Fine label is missing "
                "from fine_to_coarse: "
                f"{fine_label}."
            )


def analyze_persistent_ensemble_errors(
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
        fine_to_coarse: dict[
            str,
            str,
        ],
        top_k: int = 20,
) -> PersistentEnsembleErrorAnalysis:
    if top_k <= 0:
        raise ValueError(
            "top_k must be "
            "greater than zero."
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

    validate_analysis_inputs(
        labels=labels,
        sample_ids=sample_ids,
        ensemble_logits=(
            normalized_logits
        ),
        id2label=id2label,
        fine_to_coarse=(
            fine_to_coarse
        ),
    )

    seed_names = list(
        normalized_logits.keys()
    )

    predictions: dict[
        str,
        torch.Tensor,
    ] = {}

    confidences: dict[
        str,
        torch.Tensor,
    ] = {}

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

    label_statistics = {
        label: {
            "support": 0,
            "stable_correct": 0,
            "persistent_error": 0,
            "mixed_outcome": 0,
        }
        for label
        in id2label.values()
    }

    persistent_confusions: Counter[
        tuple[
            str,
            str,
        ]
    ] = Counter()

    stable_confidences: list[
        float
    ] = []

    persistent_confidences: list[
        float
    ] = []

    mixed_confidences: list[
        float
    ] = []

    persistent_examples: list[
        PersistentErrorExample
    ] = []

    stable_correct = 0
    persistent_errors = 0
    mixed_outcomes = 0

    persistent_same_wrong_prediction = 0
    persistent_varying_wrong_prediction = 0

    persistent_fine_only_errors = 0
    persistent_cross_coarse_errors = 0
    persistent_mixed_coarse_errors = 0

    true_ids = labels.tolist()

    for (
            sample_index,
            true_id,
    ) in enumerate(
        true_ids
    ):
        true_label = id2label[
            int(
                true_id
            )
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

        seed_prediction_labels = {
            seed_name: id2label[
                prediction_id
            ]
            for (
                seed_name,
                prediction_id,
            )
            in seed_prediction_ids.items()
        }

        seed_prediction_coarse = {
            seed_name: (
                fine_to_coarse[
                    predicted_label
                ]
            )
            for (
                seed_name,
                predicted_label,
            )
            in seed_prediction_labels.items()
        }

        seed_confidence_values = {
            seed_name: float(
                confidences[
                    seed_name
                ][
                    sample_index
                ].item()
            )
            for seed_name
            in seed_names
        }

        mean_confidence = (
            sum(
                seed_confidence_values
                .values()
            )
            / len(
                seed_confidence_values
            )
        )

        minimum_confidence = min(
            seed_confidence_values
            .values()
        )

        maximum_confidence = max(
            seed_confidence_values
            .values()
        )

        correct_by_seed = [
            seed_prediction_ids[
                seed_name
            ]
            == int(
                true_id
            )
            for seed_name
            in seed_names
        ]

        statistics = (
            label_statistics[
                true_label
            ]
        )

        statistics[
            "support"
        ] += 1

        if all(
            correct_by_seed
        ):
            stable_correct += 1

            statistics[
                "stable_correct"
            ] += 1

            stable_confidences.append(
                mean_confidence
            )

            continue

        if any(
            correct_by_seed
        ):
            mixed_outcomes += 1

            statistics[
                "mixed_outcome"
            ] += 1

            mixed_confidences.append(
                mean_confidence
            )

            continue

        persistent_errors += 1

        statistics[
            "persistent_error"
        ] += 1

        persistent_confidences.append(
            mean_confidence
        )

        unique_prediction_ids = set(
            seed_prediction_ids.values()
        )

        same_wrong_prediction = (
            len(
                unique_prediction_ids
            )
            == 1
        )

        if same_wrong_prediction:
            persistent_same_wrong_prediction += 1

            persistent_prediction_label = (
                next(
                    iter(
                        seed_prediction_labels
                        .values()
                    )
                )
            )

            persistent_confusions[
                (
                    true_label,
                    persistent_prediction_label,
                )
            ] += 1

        else:
            persistent_varying_wrong_prediction += 1

        coarse_correct_by_seed = [
            seed_prediction_coarse[
                seed_name
            ]
            == true_coarse
            for seed_name
            in seed_names
        ]

        if all(
            coarse_correct_by_seed
        ):
            coarse_status = (
                "FINE_ONLY"
            )

            persistent_fine_only_errors += 1

        elif not any(
            coarse_correct_by_seed
        ):
            coarse_status = (
                "CROSS_COARSE"
            )

            persistent_cross_coarse_errors += 1

        else:
            coarse_status = (
                "MIXED_COARSE"
            )

            persistent_mixed_coarse_errors += 1

        persistent_examples.append(
            PersistentErrorExample(
                sample_id=(
                    sample_ids[
                        sample_index
                    ]
                ),
                true_label=(
                    true_label
                ),
                true_coarse=(
                    true_coarse
                ),
                predicted_labels=(
                    seed_prediction_labels
                ),
                predicted_coarse=(
                    seed_prediction_coarse
                ),
                confidences=(
                    seed_confidence_values
                ),
                mean_confidence=(
                    mean_confidence
                ),
                minimum_confidence=(
                    minimum_confidence
                ),
                maximum_confidence=(
                    maximum_confidence
                ),
                same_wrong_prediction=(
                    same_wrong_prediction
                ),
                coarse_status=(
                    coarse_status
                ),
            )
        )

    total_samples = int(
        labels.shape[
            0
        ]
    )

    if (
            stable_correct
            + persistent_errors
            + mixed_outcomes
            != total_samples
    ):
        raise RuntimeError(
            "Persistent error category "
            "counts do not sum to the "
            "total sample count."
        )

    if (
            persistent_fine_only_errors
            + persistent_cross_coarse_errors
            + persistent_mixed_coarse_errors
            != persistent_errors
    ):
        raise RuntimeError(
            "Persistent coarse error "
            "counts do not sum to the "
            "persistent error count."
        )

    label_summary = []

    for label_id in sorted(
        id2label.keys()
    ):
        label = id2label[
            label_id
        ]

        statistics = (
            label_statistics[
                label
            ]
        )

        support = int(
            statistics[
                "support"
            ]
        )

        if support:
            stable_rate = (
                statistics[
                    "stable_correct"
                ]
                / support
            )

            persistent_rate = (
                statistics[
                    "persistent_error"
                ]
                / support
            )

            mixed_rate = (
                statistics[
                    "mixed_outcome"
                ]
                / support
            )

        else:
            stable_rate = 0.0
            persistent_rate = 0.0
            mixed_rate = 0.0

        label_summary.append(
            PersistentLabelSummary(
                label=label,
                support=support,
                stable_correct=int(
                    statistics[
                        "stable_correct"
                    ]
                ),
                persistent_error=int(
                    statistics[
                        "persistent_error"
                    ]
                ),
                mixed_outcome=int(
                    statistics[
                        "mixed_outcome"
                    ]
                ),
                stable_correct_rate=(
                    stable_rate
                ),
                persistent_error_rate=(
                    persistent_rate
                ),
                mixed_outcome_rate=(
                    mixed_rate
                ),
            )
        )

    top_persistent_confusions = [
        PersistentConfusion(
            true_label=(
                true_label
            ),
            predicted_label=(
                predicted_label
            ),
            true_coarse=(
                fine_to_coarse[
                    true_label
                ]
            ),
            predicted_coarse=(
                fine_to_coarse[
                    predicted_label
                ]
            ),
            same_coarse=(
                fine_to_coarse[
                    true_label
                ]
                == fine_to_coarse[
                    predicted_label
                ]
            ),
            count=count,
        )
        for (
            (
                true_label,
                predicted_label,
            ),
            count,
        )
        in sorted(
            persistent_confusions.items(),
            key=lambda item: (
                -item[1],
                item[0][0],
                item[0][1],
            ),
        )[:top_k]
    ]

    highest_confidence_persistent_errors = (
        sorted(
            persistent_examples,
            key=lambda example: (
                -example.mean_confidence,
                -example.maximum_confidence,
                example.sample_id,
            ),
        )[:top_k]
    )

    return PersistentEnsembleErrorAnalysis(
        total_samples=(
            total_samples
        ),
        seed_names=(
            seed_names
        ),
        stable_correct=(
            stable_correct
        ),
        persistent_errors=(
            persistent_errors
        ),
        mixed_outcomes=(
            mixed_outcomes
        ),
        stable_correct_rate=(
            stable_correct
            / total_samples
        ),
        persistent_error_rate=(
            persistent_errors
            / total_samples
        ),
        mixed_outcome_rate=(
            mixed_outcomes
            / total_samples
        ),
        persistent_same_wrong_prediction=(
            persistent_same_wrong_prediction
        ),
        persistent_varying_wrong_prediction=(
            persistent_varying_wrong_prediction
        ),
        persistent_fine_only_errors=(
            persistent_fine_only_errors
        ),
        persistent_cross_coarse_errors=(
            persistent_cross_coarse_errors
        ),
        persistent_mixed_coarse_errors=(
            persistent_mixed_coarse_errors
        ),
        confidence={
            "stable_correct": (
                summarize_confidences(
                    stable_confidences
                )
            ),
            "persistent_error": (
                summarize_confidences(
                    persistent_confidences
                )
            ),
            "mixed_outcome": (
                summarize_confidences(
                    mixed_confidences
                )
            ),
        },
        top_persistent_confusions=(
            top_persistent_confusions
        ),
        label_summary=(
            label_summary
        ),
        highest_confidence_persistent_errors=(
            highest_confidence_persistent_errors
        ),
    )