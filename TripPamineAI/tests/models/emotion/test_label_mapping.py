import pytest

from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


def test_contains_all_60_labels():
    mapping = EmotionLabelMapping()

    assert mapping.num_labels == 60

    assert len(
        mapping.labels
    ) == 60

    assert len(
        mapping.label2id
    ) == 60

    assert len(
        mapping.id2label
    ) == 60


def test_label_order_is_deterministic():
    mapping = EmotionLabelMapping()

    assert (
        mapping.labels[0]
        == "E10"
    )

    assert (
        mapping.labels[-1]
        == "E69"
    )

    assert (
        mapping.label2id["E10"]
        == 0
    )

    assert (
        mapping.label2id["E69"]
        == 59
    )


def test_encode_decode_round_trip():
    mapping = EmotionLabelMapping()

    for label in mapping.labels:
        label_id = (
            mapping.encode(
                label
            )
        )

        assert (
            mapping.decode(
                label_id
            )
            == label
        )


def test_unknown_label_is_rejected():
    mapping = EmotionLabelMapping()

    with pytest.raises(
        ValueError,
        match=(
            "Unknown fine emotion "
            "label"
        ),
    ):
        mapping.encode(
            "E99"
        )


def test_unknown_label_id_is_rejected():
    mapping = EmotionLabelMapping()

    with pytest.raises(
        ValueError,
        match=(
            "Unknown fine emotion "
            "label ID"
        ),
    ):
        mapping.decode(
            60
        )


def test_transformers_config():
    mapping = EmotionLabelMapping()

    config = (
        mapping
        .get_transformers_config()
    )

    assert (
        config["num_labels"]
        == 60
    )

    assert (
        config["label2id"]["E10"]
        == 0
    )

    assert (
        config["label2id"]["E69"]
        == 59
    )

    assert (
        config["id2label"][0]
        == "E10"
    )

    assert (
        config["id2label"][59]
        == "E69"
    )