import torch
from torch import nn
from transformers.activations import (
    get_activation,
)
from transformers.models.electra.configuration_electra import (
    ElectraConfig,
)
from transformers.models.electra.modeling_electra import (
    ElectraForSequenceClassification,
)


EXPECTED_FINE_LABEL_COUNT = 60


class LayerNormElectraClassificationHead(
    nn.Module,
):

    def __init__(
            self,
            config: ElectraConfig,
    ) -> None:
        super().__init__()

        if (
                config.num_labels
                != EXPECTED_FINE_LABEL_COUNT
        ):
            raise ValueError(
                "LayerNorm ELECTRA "
                "classifier requires "
                f"{EXPECTED_FINE_LABEL_COUNT} "
                "fine emotion labels, "
                f"found {config.num_labels}."
            )

        classifier_dropout = (
            config.classifier_dropout
            if (
                config.classifier_dropout
                is not None
            )
            else (
                config.hidden_dropout_prob
            )
        )

        self.layer_norm = nn.LayerNorm(
            config.hidden_size,
            eps=config.layer_norm_eps,
        )

        self.dropout = nn.Dropout(
            classifier_dropout
        )

        self.dense = nn.Linear(
            config.hidden_size,
            config.hidden_size,
        )

        self.activation = (
            get_activation(
                "gelu"
            )
        )

        self.out_proj = nn.Linear(
            config.hidden_size,
            config.num_labels,
        )

    def forward(
            self,
            features: torch.Tensor,
            **kwargs,
    ) -> torch.Tensor:
        del kwargs

        hidden = features[
            :,
            0,
            :,
        ]

        hidden = self.layer_norm(
            hidden
        )

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


class LayerNormElectraForSequenceClassification(
    ElectraForSequenceClassification,
):

    def __init__(
            self,
            config: ElectraConfig,
    ) -> None:
        super().__init__(
            config
        )

        self.classifier = (
            LayerNormElectraClassificationHead(
                config
            )
        )

        self.classifier.apply(
            self._init_weights
        )
