from typing import Any

import torch
from transformers import (
    DataCollatorWithPadding,
)

from trippamine_ai.datasets.emotion.codebook import (
    COARSE_EMOTION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_dataset import (
    TransformerEmotionDataset,
)


class CoarseEmotionLabelMapping:

    EXPECTED_LABEL_COUNT = 6

    def __init__(
            self,
    ) -> None:
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
                != self.EXPECTED_LABEL_COUNT
        ):
            raise ValueError(
                "Coarse emotion codebook "
                "must contain exactly "
                f"{self.EXPECTED_LABEL_COUNT} "
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

        self.labels = labels

        self.label2id = {
            label: label_id
            for label_id, label
            in enumerate(
                self.labels
            )
        }

        self.id2label = {
            label_id: label
            for label, label_id
            in self.label2id.items()
        }

    @property
    def num_labels(
            self,
    ) -> int:
        return len(
            self.labels
        )

    def encode(
            self,
            label: str,
    ) -> int:
        try:
            return self.label2id[
                label
            ]
        except KeyError as exc:
            raise ValueError(
                "Unknown coarse emotion "
                f"label: {label}"
            ) from exc

    def decode(
            self,
            label_id: int,
    ) -> str:
        try:
            return self.id2label[
                label_id
            ]
        except KeyError as exc:
            raise ValueError(
                "Unknown coarse emotion "
                f"label ID: {label_id}"
            ) from exc


class MultiTaskTransformerEmotionDataset(
    TransformerEmotionDataset,
):

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
            coarse_label_mapping: (
                CoarseEmotionLabelMapping
                | None
            ) = None,
            max_length: int = (
                TransformerEmotionDataset
                .DEFAULT_MAX_LENGTH
            ),
    ) -> None:
        self.coarse_label_mapping = (
            coarse_label_mapping
            if coarse_label_mapping
            is not None
            else (
                CoarseEmotionLabelMapping()
            )
        )

        super().__init__(
            samples=samples,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            max_length=max_length,
        )

        self._validate_coarse_samples()

    def __getitem__(
            self,
            index: int,
    ) -> dict[str, Any]:
        item = super().__getitem__(
            index
        )

        sample = self.samples[
            index
        ]

        item[
            "coarse_labels"
        ] = (
            self.coarse_label_mapping
            .encode(
                sample.coarse_label
            )
        )

        return item

    def _validate_coarse_samples(
            self,
    ) -> None:
        for sample in self.samples:
            expected_coarse = (
                get_coarse_emotion(
                    sample.label
                )
            )

            if (
                    sample.coarse_label
                    != expected_coarse
            ):
                raise ValueError(
                    "Inconsistent coarse "
                    "emotion mapping for "
                    f"{sample.label}: "
                    f"expected "
                    f"'{expected_coarse}', "
                    f"found "
                    f"'{sample.coarse_label}'"
                )

            self.coarse_label_mapping.encode(
                sample.coarse_label
            )


class MultiTaskDataCollatorWithPadding:

    def __init__(
            self,
            tokenizer: Any,
    ) -> None:
        if tokenizer is None:
            raise ValueError(
                "Tokenizer must "
                "not be None."
            )

        self.base_collator = (
            DataCollatorWithPadding(
                tokenizer=tokenizer,
                padding=True,
                return_tensors="pt",
            )
        )

    def __call__(
            self,
            features: list[
                dict[str, Any]
            ],
    ) -> dict[str, Any]:
        if not features:
            raise ValueError(
                "Multi-task batch must "
                "not be empty."
            )

        coarse_labels = []
        base_features = []

        for feature in features:
            if (
                    "coarse_labels"
                    not in feature
            ):
                raise ValueError(
                    "Multi-task feature is "
                    "missing coarse_labels."
                )

            copied = dict(
                feature
            )

            coarse_labels.append(
                int(
                    copied.pop(
                        "coarse_labels"
                    )
                )
            )

            base_features.append(
                copied
            )

        batch = self.base_collator(
            base_features
        )

        batch[
            "coarse_labels"
        ] = torch.tensor(
            coarse_labels,
            dtype=torch.long,
        )

        return batch
