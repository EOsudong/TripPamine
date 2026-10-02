import pytest

from scripts.emotion.build_context_test_dataset import (
    build_test_context_samples,
)
from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationBuilder,
)
from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)


def create_normalized_record(
        profile_id: str = "Pro_05349",
        talk_id: str = "Pro_05349_00053",
):
    raw = {
        "profile": {
            "persona-id": profile_id,
            "persona": {
                "persona-id": "A02_G02_C01",
                "human": [
                    "A02",
                    "G02",
                ],
                "computer": [
                    "C01",
                ],
            },
            "emotion": {
                "emotion-id": "S06_D02_E18",
                "type": "E18",
                "situation": [
                    "S06",
                    "D02",
                ],
            },
        },
        "talk": {
            "id": {
                "profile-id": profile_id,
                "talk-id": talk_id,
            },
            "content": {
                "HS01": (
                    "첫 번째 사용자 "
                    "발화야."
                ),
                "SS01": (
                    "첫 번째 상담자 "
                    "응답이야."
                ),
                "HS02": (
                    "두 번째 사용자 "
                    "발화야."
                ),
                "SS02": (
                    "두 번째 상담자 "
                    "응답이야."
                ),
                "HS03": (
                    "세 번째 사용자 "
                    "발화야."
                ),
                "SS03": (
                    "세 번째 상담자 "
                    "응답이야."
                ),
            },
        },
    }

    record = (
        EmotionDialogueRecord
        .model_validate(
            raw
        )
    )

    return (
        EmotionDialogueNormalizer()
        .normalize(
            record,
            split="training",
        )
    )


def create_reference(
        normalized,
        split: str = "test",
):
    sample = (
        EmotionClassificationBuilder()
        .build(
            normalized
        )
    )

    return sample.model_copy(
        update={
            "source": (
                sample.source.model_copy(
                    update={
                        "split": split,
                    }
                )
            )
        }
    )


def test_build_preserves_reference_order_and_test_split():
    first = create_normalized_record(
        profile_id="Pro_00001",
        talk_id="Pro_00001_00001",
    )

    second = create_normalized_record(
        profile_id="Pro_00002",
        talk_id="Pro_00002_00001",
    )

    references = [
        create_reference(
            first
        ),
        create_reference(
            second
        ),
    ]

    samples, summary = (
        build_test_context_samples(
            references=references,
            normalized_by_id={
                first.record_id: first,
                second.record_id: second,
            },
        )
    )

    assert [
        sample.id
        for sample in samples
    ] == [
        first.record_id,
        second.record_id,
    ]

    assert all(
        sample.source.split
        == "test"
        for sample in samples
    )

    assert summary[
        "samples"
    ] == 2


def test_build_uses_all_human_turns_and_excludes_assistant():
    normalized = (
        create_normalized_record()
    )

    reference = (
        create_reference(
            normalized
        )
    )

    samples, summary = (
        build_test_context_samples(
            references=[
                reference
            ],
            normalized_by_id={
                normalized.record_id: (
                    normalized
                )
            },
        )
    )

    sample = samples[
        0
    ]

    assert sample.text == (
        "첫 번째 사용자 발화야.\n"
        "두 번째 사용자 발화야.\n"
        "세 번째 사용자 발화야."
    )

    assert (
        "상담자 응답"
        not in sample.text
    )

    assert summary[
        "human_turn_count"
    ] == {
        "3": 1
    }

    assert summary[
        "changed_text_samples"
    ] == 1

    assert summary[
        "unchanged_text_samples"
    ] == 0


def test_build_preserves_reference_metadata():
    normalized = (
        create_normalized_record()
    )

    reference = (
        create_reference(
            normalized
        )
    )

    samples, _ = (
        build_test_context_samples(
            references=[
                reference
            ],
            normalized_by_id={
                normalized.record_id: (
                    normalized
                )
            },
        )
    )

    sample = samples[
        0
    ]

    assert (
        sample.id
        == reference.id
    )

    assert (
        sample.label
        == reference.label
    )

    assert (
        sample.label_name
        == reference.label_name
    )

    assert (
        sample.coarse_label
        == reference.coarse_label
    )

    assert (
        sample.situation_code
        == reference.situation_code
    )

    assert (
        sample.source.profile_id
        == reference.source.profile_id
    )

    assert (
        sample.source.talk_id
        == reference.source.talk_id
    )


def test_build_rejects_non_test_reference():
    normalized = (
        create_normalized_record()
    )

    reference = (
        create_reference(
            normalized,
            split="validation",
        )
    )

    with pytest.raises(
        ValueError,
        match=(
            "Reference split mismatch"
        ),
    ):
        build_test_context_samples(
            references=[
                reference
            ],
            normalized_by_id={
                normalized.record_id: (
                    normalized
                )
            },
        )


def test_build_rejects_missing_normalized_record():
    normalized = (
        create_normalized_record()
    )

    reference = (
        create_reference(
            normalized
        )
    )

    with pytest.raises(
        ValueError,
        match=(
            "Normalized record missing"
        ),
    ):
        build_test_context_samples(
            references=[
                reference
            ],
            normalized_by_id={},
        )


def test_build_rejects_empty_references():
    with pytest.raises(
        ValueError,
        match=(
            "Test references must not "
            "be empty"
        ),
    ):
        build_test_context_samples(
            references=[],
            normalized_by_id={},
        )