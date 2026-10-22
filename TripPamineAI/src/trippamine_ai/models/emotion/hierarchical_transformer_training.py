import math
from dataclasses import dataclass
from typing import Any

import torch
from torch.nn import CrossEntropyLoss
from transformers import Trainer

from trippamine_ai.datasets.emotion.codebook import (
    COARSE_EMOTION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


EXPECTED_FINE_LABEL_COUNT = 60
EXPECTED_COARSE_LABEL_COUNT = 6


@dataclass(
    frozen=True,
)
class HierarchicalLossResult:
    total_loss: torch.Tensor
    fine_loss: torch.Tensor
    coarse_loss: torch.Tensor
    coarse_logits: torch.Tensor


def validate_coarse_loss_weight(
        value: float,
) -> float:
    if (
            isinstance(
                value,
                bool,
            )
            or not isinstance(
                value,
                (int, float),
            )
    ):
        raise ValueError(
            "Coarse loss weight must be "
            "a finite positive value."
        )

    numeric_value = float(
        value
    )

    if (
            not math.isfinite(
                numeric_value
            )
            or numeric_value <= 0.0
    ):
        raise ValueError(
            "Coarse loss weight must be "
            "a finite positive value."
        )

    return numeric_value


def validate_fine_to_coarse_ids(
        fine_to_coarse_ids: tuple[
            int,
            ...,
        ],
) -> tuple[
    int,
    ...,
]:
    if (
            len(
                fine_to_coarse_ids
            )
            != EXPECTED_FINE_LABEL_COUNT
    ):
        raise ValueError(
            "Fine-to-coarse mapping must "
            f"contain exactly "
            f"{EXPECTED_FINE_LABEL_COUNT} "
            "fine labels."
        )

    expected_coarse_ids = set(
        range(
            EXPECTED_COARSE_LABEL_COUNT
        )
    )

    actual_coarse_ids = set()

    for coarse_id in (
            fine_to_coarse_ids
    ):
        if (
                isinstance(
                    coarse_id,
                    bool,
                )
                or not isinstance(
                    coarse_id,
                    int,
                )
        ):
            raise ValueError(
                "Fine-to-coarse mapping "
                "must contain integer "
                "coarse IDs."
            )

        if (
                coarse_id < 0
                or coarse_id
                >= EXPECTED_COARSE_LABEL_COUNT
        ):
            raise ValueError(
                "Fine-to-coarse mapping "
                "contains an invalid "
                "coarse ID: "
                f"{coarse_id}"
            )

        actual_coarse_ids.add(
            coarse_id
        )

    if (
            actual_coarse_ids
            != expected_coarse_ids
    ):
        raise ValueError(
            "Fine-to-coarse mapping "
            "must cover all "
            f"{EXPECTED_COARSE_LABEL_COUNT} "
            "coarse labels."
        )

    return tuple(
        fine_to_coarse_ids
    )


def get_coarse_labels() -> tuple[
    str,
    ...,
]:
    labels = tuple(
        COARSE_EMOTION_CODEBOOK[
            code
        ]
        for code in sorted(
            COARSE_EMOTION_CODEBOOK
        )
    )

    if (
            len(labels)
            != EXPECTED_COARSE_LABEL_COUNT
    ):
        raise ValueError(
            "Coarse emotion codebook "
            "must contain exactly "
            f"{EXPECTED_COARSE_LABEL_COUNT} "
            "labels."
        )

    if (
            len(labels)
            != len(
                set(
                    labels
                )
            )
    ):
        raise ValueError(
            "Coarse emotion codebook "
            "contains duplicate labels."
        )

    return labels


def build_fine_to_coarse_ids(
        label_mapping: EmotionLabelMapping,
) -> tuple[
    int,
    ...,
]:
    if (
            label_mapping.num_labels
            != EXPECTED_FINE_LABEL_COUNT
    ):
        raise ValueError(
            "Fine emotion mapping must "
            f"contain exactly "
            f"{EXPECTED_FINE_LABEL_COUNT} "
            "labels."
        )

    coarse_labels = (
        get_coarse_labels()
    )

    coarse_label2id = {
        label: label_id
        for label_id, label
        in enumerate(
            coarse_labels
        )
    }

    fine_to_coarse_ids = []

    for fine_label in (
            label_mapping.labels
    ):
        coarse_label = (
            get_coarse_emotion(
                fine_label
            )
        )

        try:
            coarse_id = (
                coarse_label2id[
                    coarse_label
                ]
            )
        except KeyError as exc:
            raise ValueError(
                "Unknown coarse emotion "
                "label derived from "
                f"{fine_label}: "
                f"{coarse_label}"
            ) from exc

        fine_to_coarse_ids.append(
            coarse_id
        )

    return (
        validate_fine_to_coarse_ids(
            tuple(
                fine_to_coarse_ids
            )
        )
    )


def aggregate_fine_logits_to_coarse(
        fine_logits: torch.Tensor,
        fine_to_coarse_ids: tuple[
            int,
            ...,
        ],
) -> torch.Tensor:
    validated_mapping = (
        validate_fine_to_coarse_ids(
            fine_to_coarse_ids
        )
    )

    if fine_logits.ndim != 2:
        raise ValueError(
            "Fine logits must be a "
            "two-dimensional tensor."
        )

    if (
            fine_logits.shape[
                1
            ]
            != EXPECTED_FINE_LABEL_COUNT
    ):
        raise ValueError(
            "Fine logits must contain "
            f"{EXPECTED_FINE_LABEL_COUNT} "
            "classes."
        )

    mapping_tensor = (
        torch.tensor(
            validated_mapping,
            dtype=torch.long,
            device=(
                fine_logits.device
            ),
        )
    )

    coarse_logits = []

    for coarse_id in range(
            EXPECTED_COARSE_LABEL_COUNT
    ):
        mask = (
            mapping_tensor
            == coarse_id
        )

        if not bool(
                mask.any().item()
        ):
            raise RuntimeError(
                "Coarse label has no "
                "fine-label members: "
                f"{coarse_id}"
            )

        coarse_logits.append(
            torch.logsumexp(
                fine_logits[
                    :,
                    mask,
                ],
                dim=-1,
            )
        )

    return torch.stack(
        coarse_logits,
        dim=-1,
    )


def calculate_hierarchical_loss(
        fine_logits: torch.Tensor,
        labels: torch.Tensor,
        fine_to_coarse_ids: tuple[
            int,
            ...,
        ],
        coarse_loss_weight: float,
) -> HierarchicalLossResult:
    weight = (
        validate_coarse_loss_weight(
            coarse_loss_weight
        )
    )

    validated_mapping = (
        validate_fine_to_coarse_ids(
            fine_to_coarse_ids
        )
    )

    if labels.ndim != 1:
        raise ValueError(
            "Fine labels must be a "
            "one-dimensional tensor."
        )

    if (
            fine_logits.shape[
                0
            ]
            != labels.shape[
                0
            ]
    ):
        raise ValueError(
            "Fine logits/label sample "
            "count mismatch."
        )

    if labels.numel() == 0:
        raise ValueError(
            "Fine labels must not "
            "be empty."
        )

    if (
            int(
                labels.min().item()
            ) < 0
            or int(
                labels.max().item()
            ) >= EXPECTED_FINE_LABEL_COUNT
    ):
        raise ValueError(
            "Fine labels contain an "
            "out-of-range label ID."
        )

    coarse_logits = (
        aggregate_fine_logits_to_coarse(
            fine_logits=(
                fine_logits
            ),
            fine_to_coarse_ids=(
                validated_mapping
            ),
        )
    )

    mapping_tensor = (
        torch.tensor(
            validated_mapping,
            dtype=torch.long,
            device=labels.device,
        )
    )

    coarse_labels = (
        mapping_tensor[
            labels
        ]
    )

    fine_loss = (
        CrossEntropyLoss()(
            fine_logits,
            labels,
        )
    )

    coarse_loss = (
        CrossEntropyLoss()(
            coarse_logits,
            coarse_labels,
        )
    )

    total_loss = (
        fine_loss
        + weight
        * coarse_loss
    )

    return HierarchicalLossResult(
        total_loss=total_loss,
        fine_loss=fine_loss,
        coarse_loss=coarse_loss,
        coarse_logits=(
            coarse_logits
        ),
    )


class HierarchicalEmotionTrainer(
    Trainer
):

    def __init__(
            self,
            *args,
            fine_to_coarse_ids: tuple[
                int,
                ...,
            ],
            coarse_loss_weight: float,
            **kwargs,
    ) -> None:
        self.fine_to_coarse_ids = (
            validate_fine_to_coarse_ids(
                fine_to_coarse_ids
            )
        )

        self.coarse_loss_weight = (
            validate_coarse_loss_weight(
                coarse_loss_weight
            )
        )

        super().__init__(
            *args,
            **kwargs,
        )

        self.model_accepts_loss_kwargs = (
            False
        )

    def compute_loss(
            self,
            model: torch.nn.Module,
            inputs: dict[
                str,
                torch.Tensor
                | Any,
            ],
            return_outputs: bool = False,
            num_items_in_batch: (
                torch.Tensor
                | int
                | None
            ) = None,
    ):
        del num_items_in_batch

        labels = inputs.get(
            "labels"
        )

        if labels is None:
            raise ValueError(
                "Hierarchical training "
                "requires fine labels."
            )

        model_inputs = {
            key: value
            for key, value
            in inputs.items()
            if key != "labels"
        }

        outputs = model(
            **model_inputs
        )

        fine_logits = getattr(
            outputs,
            "logits",
            None,
        )

        if (
                fine_logits is None
                and isinstance(
                    outputs,
                    dict,
                )
        ):
            fine_logits = (
                outputs.get(
                    "logits"
                )
            )

        if (
                fine_logits is None
                and isinstance(
                    outputs,
                    tuple,
                )
                and outputs
        ):
            fine_logits = (
                outputs[
                    0
                ]
            )

        if fine_logits is None:
            raise RuntimeError(
                "Model output does not "
                "contain fine logits."
            )

        loss_result = (
            calculate_hierarchical_loss(
                fine_logits=(
                    fine_logits
                ),
                labels=labels,
                fine_to_coarse_ids=(
                    self.fine_to_coarse_ids
                ),
                coarse_loss_weight=(
                    self.coarse_loss_weight
                ),
            )
        )

        if return_outputs:
            return (
                loss_result.total_loss,
                outputs,
            )

        return loss_result.total_loss
