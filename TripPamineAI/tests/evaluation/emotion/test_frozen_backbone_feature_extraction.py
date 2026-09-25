from types import SimpleNamespace

import pytest
import torch

from scripts.emotion.extract_frozen_backbone_features import (
    freeze_backbone,
    validate_backbone_contract,
    validate_feature_tensors,
    validate_special_token_contract,
)


class FakeTokenizer:

    cls_token_id = 2
    sep_token_id = 3

    def __len__(
            self,
    ) -> int:
        return 35000

    def num_special_tokens_to_add(
            self,
            pair=False,
    ) -> int:
        assert pair is False

        return 2

    def __call__(
            self,
            text,
            add_special_tokens=True,
            truncation=False,
            padding=False,
    ):
        assert text
        assert add_special_tokens
        assert not truncation
        assert not padding

        return {
            "input_ids": [
                2,
                100,
                101,
                3,
            ]
        }


class FakeEmbedding:

    def __init__(
            self,
    ) -> None:
        self.weight = torch.zeros(
            35000,
            768,
        )


class FakeModel(
    torch.nn.Module,
):

    def __init__(
            self,
    ) -> None:
        super().__init__()

        self.projection = (
            torch.nn.Linear(
                768,
                768,
            )
        )

        self.config = (
            SimpleNamespace(
                model_type="electra",
                num_hidden_layers=12,
                hidden_size=768,
                intermediate_size=3072,
                num_attention_heads=12,
                vocab_size=35000,
            )
        )

        self.embedding = (
            FakeEmbedding()
        )

    def get_input_embeddings(
            self,
    ):
        return self.embedding


def test_validate_backbone_contract():
    tokenizer = FakeTokenizer()
    model = FakeModel()

    result = (
        validate_backbone_contract(
            tokenizer=tokenizer,
            model=model,
        )
    )

    assert result[
        "model_type"
    ] == "electra"

    assert result[
        "num_hidden_layers"
    ] == 12

    assert result[
        "hidden_size"
    ] == 768

    assert result[
        "tokenizer_vocab_size"
    ] == 35000

    assert result[
        "special_tokens_per_input"
    ] == 2


def test_validate_special_token_contract():
    tokenizer = FakeTokenizer()

    result = (
        validate_special_token_contract(
            tokenizer=tokenizer,
            text="테스트 문장",
        )
    )

    assert result[
        "first_token_id"
    ] == 2

    assert result[
        "last_token_id"
    ] == 3


def test_freeze_backbone():
    model = FakeModel()

    assert any(
        parameter.requires_grad
        for parameter
        in model.parameters()
    )

    freeze_backbone(
        model
    )

    assert model.training is False

    assert all(
        not parameter.requires_grad
        for parameter
        in model.parameters()
    )


def test_validate_feature_tensors():
    features = torch.randn(
        4,
        768,
    )

    labels = torch.tensor(
        [
            0,
            1,
            58,
            59,
        ],
        dtype=torch.long,
    )

    result = (
        validate_feature_tensors(
            features=features,
            labels=labels,
            expected_samples=4,
            expected_hidden_size=768,
            num_labels=60,
        )
    )

    assert result[
        "feature_shape"
    ] == [
        4,
        768,
    ]

    assert result[
        "label_shape"
    ] == [
        4,
    ]

    assert result[
        "finite"
    ] is True


def test_rejects_invalid_feature_hidden_size():
    with pytest.raises(
        ValueError,
        match=(
            "Feature hidden size "
            "mismatch"
        ),
    ):
        validate_feature_tensors(
            features=torch.randn(
                4,
                767,
            ),
            labels=torch.tensor(
                [
                    0,
                    1,
                    2,
                    3,
                ]
            ),
            expected_samples=4,
            expected_hidden_size=768,
            num_labels=60,
        )


def test_rejects_out_of_range_label():
    with pytest.raises(
        ValueError,
        match=(
            "outside the valid "
            "label range"
        ),
    ):
        validate_feature_tensors(
            features=torch.randn(
                2,
                768,
            ),
            labels=torch.tensor(
                [
                    0,
                    60,
                ]
            ),
            expected_samples=2,
            expected_hidden_size=768,
            num_labels=60,
        )