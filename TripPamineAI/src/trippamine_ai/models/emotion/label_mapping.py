from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
)


class EmotionLabelMapping:

    EXPECTED_LABEL_COUNT = 60

    def __init__(
            self,
    ) -> None:
        labels = sorted(
            EMOTION_CODEBOOK.keys()
        )

        if (
                len(labels)
                != self.EXPECTED_LABEL_COUNT
        ):
            raise ValueError(
                "Emotion codebook must "
                "contain exactly "
                f"{self.EXPECTED_LABEL_COUNT} "
                "fine emotion labels, "
                f"found {len(labels)}."
            )

        if (
                len(labels)
                != len(set(labels))
        ):
            raise ValueError(
                "Emotion codebook contains "
                "duplicate labels."
            )

        self.labels = tuple(
            labels
        )

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
                "Unknown fine emotion "
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
                "Unknown fine emotion "
                f"label ID: {label_id}"
            ) from exc

    def get_transformers_config(
            self,
    ) -> dict:
        return {
            "num_labels": (
                self.num_labels
            ),
            "label2id": dict(
                self.label2id
            ),
            "id2label": dict(
                self.id2label
            ),
        }
