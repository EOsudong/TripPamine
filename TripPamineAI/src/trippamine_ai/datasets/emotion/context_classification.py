from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationBuilder,
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


DEFAULT_TURN_SEPARATOR = "\n"

ALLOWED_DATASET_SPLITS = {
    "training",
    "validation",
    "test",
}


class EmotionContextClassificationBuilder:

    def __init__(
            self,
            turn_separator: str = DEFAULT_TURN_SEPARATOR,
    ) -> None:
        if not isinstance(
                turn_separator,
                str,
        ):
            raise TypeError(
                "turn_separator must be a string."
            )

        if not turn_separator:
            raise ValueError(
                "turn_separator must not be empty."
            )

        self.turn_separator = turn_separator
        self.base_builder = (
            EmotionClassificationBuilder()
        )

    def build(
            self,
            record: NormalizedEmotionDialogue,
            split: str,
    ) -> EmotionClassificationSample:
        if split not in ALLOWED_DATASET_SPLITS:
            raise ValueError(
                "Unsupported dataset split: "
                f"{split}"
            )

        human_turns = [
            turn.human.strip()
            for turn in record.turns
            if turn.human.strip()
        ]

        if not human_turns:
            raise ValueError(
                "Context classification record "
                "contains no human turns."
            )

        base_sample = (
            self.base_builder.build(
                record
            )
        )

        context_text = (
            self.turn_separator.join(
                human_turns
            )
        )

        assigned_source = (
            base_sample.source.model_copy(
                update={
                    "split": split,
                }
            )
        )

        return base_sample.model_copy(
            update={
                "text": context_text,
                "source": assigned_source,
            }
        )
