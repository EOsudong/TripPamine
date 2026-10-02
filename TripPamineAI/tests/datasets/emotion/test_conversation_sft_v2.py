import pytest

from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTBuilder,
)
from trippamine_ai.datasets.emotion.conversation_sft_v2 import (
    ConversationSFTV2Builder,
    build_profile_split_map,
)
from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)


def create_normalized_record():
    raw = {
        "profile": {
            "persona-id": "Pro_03802",
            "persona": {
                "persona-id": (
                    "A02_G01_C01"
                ),
                "human": [
                    "A02",
                    "G01",
                ],
                "computer": [
                    "C01",
                ],
            },
            "emotion": {
                "emotion-id": (
                    "S06_D02_E31"
                ),
                "type": "E31",
                "situation": [
                    "S06",
                    "D02",
                ],
            },
        },
        "talk": {
            "id": {
                "profile-id": (
                    "Pro_03802"
                ),
                "talk-id": (
                    "Pro_03802_00028"
                ),
            },
            "content": {
                "HS01": (
                    "발표 실수를 해서 "
                    "정말 미안해."
                ),
                "SS01": (
                    "많이 미안한 "
                    "마음이 드시겠어요."
                ),
                "HS02": (
                    "내가 능력이 부족한 "
                    "것 같아."
                ),
                "SS02": (
                    "그렇게 느끼고 "
                    "계시는군요."
                ),
                "HS03": (
                    "앞으로는 더 "
                    "준비해야겠어."
                ),
                "SS03": (
                    "좋은 결과가 "
                    "있기를 바라요."
                ),
            },
        },
    }

    record = (
        EmotionDialogueRecord
        .model_validate(raw)
    )

    return (
        EmotionDialogueNormalizer()
        .normalize(
            record,
            split="training",
        )
    )


def create_manifest():
    return {
        "split": {
            "training_profile_ids": [
                "Pro_00001",
            ],
            "validation_profile_ids": [
                "Pro_03802",
            ],
            "test_profile_ids": [
                "Pro_00003",
            ],
        }
    }


def test_build_profile_split_map():
    result = build_profile_split_map(
        create_manifest()
    )

    assert (
        result["Pro_00001"]
        == "training"
    )

    assert (
        result["Pro_03802"]
        == "validation"
    )

    assert (
        result["Pro_00003"]
        == "test"
    )


def test_routes_profile_to_v2_split():
    record = (
        create_normalized_record()
    )

    builder = (
        ConversationSFTV2Builder
        .from_manifest(
            create_manifest()
        )
    )

    target_split, samples = (
        builder.build(record)
    )

    assert (
        target_split
        == "validation"
    )

    assert len(samples) == 3

    assert all(
        sample.source.split
        == "validation"
        for sample in samples
    )


def test_original_record_is_not_modified():
    record = (
        create_normalized_record()
    )

    assert (
        record.source.split
        == "training"
    )

    builder = (
        ConversationSFTV2Builder
        .from_manifest(
            create_manifest()
        )
    )

    builder.build(record)

    assert (
        record.source.split
        == "training"
    )


def test_existing_sft_content_is_preserved():
    record = (
        create_normalized_record()
    )

    original_samples = (
        ConversationSFTBuilder()
        .build(record)
    )

    _, routed_samples = (
        ConversationSFTV2Builder
        .from_manifest(
            create_manifest()
        )
        .build(record)
    )

    assert [
        sample.id
        for sample in original_samples
    ] == [
        sample.id
        for sample in routed_samples
    ]

    assert [
        sample.content_hash
        for sample in original_samples
    ] == [
        sample.content_hash
        for sample in routed_samples
    ]

    assert [
        sample.prompt
        for sample in original_samples
    ] == [
        sample.prompt
        for sample in routed_samples
    ]

    assert [
        sample.completion
        for sample in original_samples
    ] == [
        sample.completion
        for sample in routed_samples
    ]


def test_missing_profile_is_rejected():
    record = (
        create_normalized_record()
    )

    manifest = {
        "split": {
            "training_profile_ids": [
                "Pro_00001",
            ],
            "validation_profile_ids": [],
            "test_profile_ids": [],
        }
    }

    builder = (
        ConversationSFTV2Builder
        .from_manifest(
            manifest
        )
    )

    with pytest.raises(
        ValueError,
        match=(
            "Profile is missing"
        ),
    ):
        builder.build(record)


def test_profile_overlap_is_rejected():
    manifest = {
        "split": {
            "training_profile_ids": [
                "Pro_03802",
            ],
            "validation_profile_ids": [
                "Pro_03802",
            ],
            "test_profile_ids": [],
        }
    }

    with pytest.raises(
        ValueError,
        match=(
            "Training/validation "
            "profile overlap"
        ),
    ):
        build_profile_split_map(
            manifest
        )


def test_duplicate_profile_is_rejected():
    manifest = {
        "split": {
            "training_profile_ids": [
                "Pro_03802",
                "Pro_03802",
            ],
            "validation_profile_ids": [],
            "test_profile_ids": [],
        }
    }

    with pytest.raises(
        ValueError,
        match=(
            "duplicate profile IDs"
        ),
    ):
        build_profile_split_map(
            manifest
        )