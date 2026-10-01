from typing import Any

from torch.utils.data import Dataset

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


class TransformerEmotionDataset(
    Dataset,
):

    DEFAULT_MAX_LENGTH = 96

    def __init__(
            self,
            samples: list[
                EmotionClassificationSample
            ],
            tokenizer: Any,
            label_mapping: (
                EmotionLabelMapping
                | None
            ) = None,
            max_length: int = (
                DEFAULT_MAX_LENGTH
            ),
    ) -> None:
        if not samples:
            raise ValueError(
                "Transformer emotion "
                "dataset must not "
                "be empty."
            )

        if tokenizer is None:
            raise ValueError(
                "Tokenizer must "
                "not be None."
            )

        if (
                isinstance(
                    max_length,
                    bool,
                )
                or not isinstance(
                    max_length,
                    int,
                )
                or max_length <= 0
        ):
            raise ValueError(
                "Maximum token length "
                "must be a positive "
                "integer."
            )

        self.samples = list(
            samples
        )

        self.tokenizer = tokenizer

        self.label_mapping = (
            label_mapping
            if label_mapping
            is not None
            else EmotionLabelMapping()
        )

        self.max_length = (
            max_length
        )

        self._validate_samples()

    def __len__(
            self,
    ) -> int:
        return len(
            self.samples
        )

    def __getitem__(
            self,
            index: int,
    ) -> dict[str, Any]:
        sample = self.samples[
            index
        ]

        encoded = self.tokenizer(
            sample.text,
            add_special_tokens=True,
            truncation=True,
            max_length=(
                self.max_length
            ),
            padding=False,
        )

        item = dict(
            encoded
        )

        item["labels"] = (
            self.label_mapping
            .encode(
                sample.label
            )
        )

        return item

    def _validate_samples(
            self,
    ) -> None:
        for sample in self.samples:
            if not sample.text.strip():
                raise ValueError(
                    "Classification "
                    "sample text must "
                    "not be empty."
                )

            self.label_mapping.encode(
                sample.label
            )