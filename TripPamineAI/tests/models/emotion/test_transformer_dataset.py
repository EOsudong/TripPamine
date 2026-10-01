import pytest

from trippamine_ai.datasets.emotion.classification import (
    ClassificationSource,
    EmotionClassificationSample,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_dataset import (
    TransformerEmotionDataset,
)


class FakeTokenizer:

    def __init__(
            self,
    ) -> None:
        self.calls = []

    def __call__(
            self,
            text,
            *,
            add_special_tokens,
            truncation,
            max_length,
            padding,
    ):
        self.calls.append(
            {
                "text": text,
                "add_special_tokens": (
                    add_special_tokens
                ),
                "truncation": (
                    truncation
                ),
                "max_length": (
                    max_length
                ),
                "padding": padding,
            }
        )

        return {
            "input_ids": [
                2,
                100,
                101,
                3,
            ],
            "attention_mask": [
                1,
                1,
                1,
                1,
            ],
            "token_type_ids": [
                0,
                0,
                0,
                0,
            ],
        }


def create_sample(
        sample_id: str = "sample-1",
        text: str = (
            "테스트 감정 문장입니다."
        ),
        label: str = "E10",
) -> EmotionClassificationSample:
    return EmotionClassificationSample(
        id=sample_id,
        text=text,
        label=label,
        label_name="test",
        coarse_label="test",
        situation_code="S06",
        situation="test",
        quality_status="VALID",
        quality_issue_codes=[],
        source=ClassificationSource(
            dataset=(
                "aihub-emotional-dialogue"
            ),
            version="v1",
            split="training",
            profile_id="Pro_00001",
            talk_id=sample_id,
        ),
    )


def test_dataset_length():
    tokenizer = (
        FakeTokenizer()
    )

    dataset = (
        TransformerEmotionDataset(
            samples=[
                create_sample(
                    "1"
                ),
                create_sample(
                    "2"
                ),
            ],
            tokenizer=tokenizer,
        )
    )

    assert len(dataset) == 2


def test_dataset_encodes_sample():
    tokenizer = (
        FakeTokenizer()
    )

    mapping = (
        EmotionLabelMapping()
    )

    dataset = (
        TransformerEmotionDataset(
            samples=[
                create_sample(
                    label="E10"
                )
            ],
            tokenizer=tokenizer,
            label_mapping=mapping,
        )
    )

    item = dataset[0]

    assert item[
        "input_ids"
    ] == [
        2,
        100,
        101,
        3,
    ]

    assert item[
        "attention_mask"
    ] == [
        1,
        1,
        1,
        1,
    ]

    assert (
        item["labels"]
        == mapping.label2id[
            "E10"
        ]
    )


def test_dataset_uses_expected_tokenizer_options():
    tokenizer = (
        FakeTokenizer()
    )

    dataset = (
        TransformerEmotionDataset(
            samples=[
                create_sample()
            ],
            tokenizer=tokenizer,
        )
    )

    dataset[0]

    assert len(
        tokenizer.calls
    ) == 1

    call = (
        tokenizer.calls[0]
    )

    assert (
        call["text"]
        == "테스트 감정 문장입니다."
    )

    assert (
        call["add_special_tokens"]
        is True
    )

    assert (
        call["truncation"]
        is True
    )

    assert (
        call["max_length"]
        == 96
    )

    assert (
        call["padding"]
        is False
    )


def test_custom_max_length():
    tokenizer = (
        FakeTokenizer()
    )

    dataset = (
        TransformerEmotionDataset(
            samples=[
                create_sample()
            ],
            tokenizer=tokenizer,
            max_length=64,
        )
    )

    dataset[0]

    assert (
        tokenizer.calls[0][
            "max_length"
        ]
        == 64
    )


def test_unknown_label_is_rejected():
    tokenizer = (
        FakeTokenizer()
    )

    with pytest.raises(
        ValueError,
        match=(
            "Unknown fine emotion "
            "label"
        ),
    ):
        TransformerEmotionDataset(
            samples=[
                create_sample(
                    label="E99"
                )
            ],
            tokenizer=tokenizer,
        )


def test_empty_dataset_is_rejected():
    tokenizer = (
        FakeTokenizer()
    )

    with pytest.raises(
        ValueError,
        match=(
            "must not be empty"
        ),
    ):
        TransformerEmotionDataset(
            samples=[],
            tokenizer=tokenizer,
        )


def test_empty_text_is_rejected():
    tokenizer = (
        FakeTokenizer()
    )

    with pytest.raises(
        ValueError,
        match=(
            "sample text must "
            "not be empty"
        ),
    ):
        TransformerEmotionDataset(
            samples=[
                create_sample(
                    text="   "
                )
            ],
            tokenizer=tokenizer,
        )


def test_invalid_max_length_is_rejected():
    tokenizer = (
        FakeTokenizer()
    )

    with pytest.raises(
        ValueError,
        match=(
            "Maximum token length "
            "must be a positive "
            "integer"
        ),
    ):
        TransformerEmotionDataset(
            samples=[
                create_sample()
            ],
            tokenizer=tokenizer,
            max_length=0,
        )