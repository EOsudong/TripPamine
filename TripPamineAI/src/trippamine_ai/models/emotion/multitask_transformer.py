import math
from dataclasses import dataclass

import torch
from torch import nn
from torch.nn import CrossEntropyLoss
from transformers.models.electra.configuration_electra import (
    ElectraConfig,
)
from transformers.models.electra.modeling_electra import (
    ElectraModel,
    ElectraPreTrainedModel,
)
from transformers.utils import ModelOutput


EXPECTED_FINE_LABEL_COUNT = 60
EXPECTED_COARSE_LABEL_COUNT = 6


@dataclass
class MultiTaskSequenceClassifierOutput(
    ModelOutput,
):
    loss: torch.FloatTensor | None = None
    logits: torch.FloatTensor | None = None
    coarse_logits: torch.FloatTensor | None = None
    hidden_states: (
        tuple[torch.FloatTensor, ...]
        | None
    ) = None
    attentions: (
        tuple[torch.FloatTensor, ...]
        | None
    ) = None


class MultiTaskElectraClassificationHead(
    nn.Module,
):

    def __init__(
            self,
            config: ElectraConfig,
            output_size: int,
    ) -> None:
        super().__init__()

        if (
                isinstance(
                    output_size,
                    bool,
                )
                or not isinstance(
                    output_size,
                    int,
                )
                or output_size <= 0
        ):
            raise ValueError(
                "Classification output size "
                "must be a positive integer."
            )

        classifier_dropout = (
            config.classifier_dropout
            if config.classifier_dropout
            is not None
            else config.hidden_dropout_prob
        )

        self.dropout = nn.Dropout(
            classifier_dropout
        )

        self.dense = nn.Linear(
            config.hidden_size,
            config.hidden_size,
        )

        self.activation = nn.GELU()

        self.out_proj = nn.Linear(
            config.hidden_size,
            output_size,
        )

    def forward(
            self,
            features: torch.Tensor,
    ) -> torch.Tensor:
        hidden = features[
            :,
            0,
            :,
        ]

        hidden = self.dropout(
            hidden
        )

        hidden = self.dense(
            hidden
        )

        hidden = self.activation(
            hidden
        )

        hidden = self.dropout(
            hidden
        )

        return self.out_proj(
            hidden
        )


class MultiTaskElectraForSequenceClassification(
    ElectraPreTrainedModel,
):

    accepts_loss_kwargs = False

    def __init__(
            self,
            config: ElectraConfig,
    ) -> None:
        super().__init__(
            config
        )

        self.num_labels = int(
            config.num_labels
        )

        if (
                self.num_labels
                != EXPECTED_FINE_LABEL_COUNT
        ):
            raise ValueError(
                "Multi-task ELECTRA requires "
                f"{EXPECTED_FINE_LABEL_COUNT} "
                "fine emotion labels, found "
                f"{self.num_labels}."
            )

        raw_coarse_num_labels = (
            getattr(
                config,
                "coarse_num_labels",
                None,
            )
        )

        if (
                isinstance(
                    raw_coarse_num_labels,
                    bool,
                )
                or not isinstance(
                    raw_coarse_num_labels,
                    int,
                )
                or raw_coarse_num_labels
                != EXPECTED_COARSE_LABEL_COUNT
        ):
            raise ValueError(
                "Multi-task ELECTRA requires "
                f"{EXPECTED_COARSE_LABEL_COUNT} "
                "coarse emotion labels."
            )

        raw_coarse_loss_weight = (
            getattr(
                config,
                "coarse_loss_weight",
                None,
            )
        )

        if (
                isinstance(
                    raw_coarse_loss_weight,
                    bool,
                )
                or not isinstance(
                    raw_coarse_loss_weight,
                    (int, float),
                )
        ):
            raise ValueError(
                "Coarse loss weight must be "
                "a finite positive value."
            )

        coarse_loss_weight = float(
            raw_coarse_loss_weight
        )

        if (
                not math.isfinite(
                    coarse_loss_weight
                )
                or coarse_loss_weight <= 0.0
        ):
            raise ValueError(
                "Coarse loss weight must be "
                "a finite positive value."
            )

        self.coarse_num_labels = (
            raw_coarse_num_labels
        )

        self.coarse_loss_weight = (
            coarse_loss_weight
        )

        self.config.coarse_num_labels = (
            self.coarse_num_labels
        )

        self.config.coarse_loss_weight = (
            self.coarse_loss_weight
        )

        self.electra = ElectraModel(
            config
        )

        self.fine_classifier = (
            MultiTaskElectraClassificationHead(
                config=config,
                output_size=(
                    self.num_labels
                ),
            )
        )

        self.coarse_classifier = (
            MultiTaskElectraClassificationHead(
                config=config,
                output_size=(
                    self.coarse_num_labels
                ),
            )
        )

        self.post_init()

    def forward(
            self,
            input_ids: (
                torch.Tensor
                | None
            ) = None,
            attention_mask: (
                torch.Tensor
                | None
            ) = None,
            token_type_ids: (
                torch.Tensor
                | None
            ) = None,
            position_ids: (
                torch.Tensor
                | None
            ) = None,
            inputs_embeds: (
                torch.Tensor
                | None
            ) = None,
            labels: (
                torch.Tensor
                | None
            ) = None,
            coarse_labels: (
                torch.Tensor
                | None
            ) = None,
            **kwargs,
    ) -> MultiTaskSequenceClassifierOutput:
        if (
                (labels is None)
                != (
                    coarse_labels
                    is None
                )
        ):
            raise ValueError(
                "Fine and coarse labels "
                "must be provided together."
            )

        kwargs.pop(
            "return_dict",
            None,
        )

        outputs = self.electra(
            input_ids=input_ids,
            attention_mask=(
                attention_mask
            ),
            token_type_ids=(
                token_type_ids
            ),
            position_ids=(
                position_ids
            ),
            inputs_embeds=(
                inputs_embeds
            ),
            return_dict=True,
            **kwargs,
        )

        sequence_output = (
            outputs.last_hidden_state
        )

        fine_logits = (
            self.fine_classifier(
                sequence_output
            )
        )

        coarse_logits = (
            self.coarse_classifier(
                sequence_output
            )
        )

        loss = None

        if labels is not None:
            fine_loss = (
                CrossEntropyLoss()(
                    fine_logits.view(
                        -1,
                        self.num_labels,
                    ),
                    labels.view(
                        -1
                    ),
                )
            )

            coarse_loss = (
                CrossEntropyLoss()(
                    coarse_logits.view(
                        -1,
                        self.coarse_num_labels,
                    ),
                    coarse_labels.view(
                        -1
                    ),
                )
            )

            loss = (
                fine_loss
                + self.coarse_loss_weight
                * coarse_loss
            )

        return (
            MultiTaskSequenceClassifierOutput(
                loss=loss,
                logits=fine_logits,
                coarse_logits=(
                    coarse_logits
                ),
                hidden_states=(
                    outputs.hidden_states
                ),
                attentions=(
                    outputs.attentions
                ),
            )
        )
