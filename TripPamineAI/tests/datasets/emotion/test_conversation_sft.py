from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTBuilder,
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
                "persona-id": "A02_G01_C01",
                "human": ["A02", "G01"],
                "computer": ["C01"],
            },
            "emotion": {
                "emotion-id": "S06_D02_E31",
                "type": "E31",
                "situation": ["S06", "D02"],
            },
        },
        "talk": {
            "id": {
                "profile-id": "Pro_03802",
                "talk-id": "Pro_03802_00028",
            },
            "content": {
                "HS01": "발표 실수를 해서 정말 미안해.",
                "SS01": "많이 미안한 마음이 드시겠어요.",
                "HS02": "내가 능력이 부족한 것 같아.",
                "SS02": "그렇게 느끼고 계시는군요.",
                "HS03": "앞으로는 더 준비해야겠어.",
                "SS03": "좋은 결과가 있기를 바라요.",
            },
        },
    }

    record = EmotionDialogueRecord.model_validate(
        raw
    )

    return EmotionDialogueNormalizer().normalize(
        record,
        split="training",
    )


def test_three_turn_dialogue_creates_three_samples():
    samples = ConversationSFTBuilder().build(
        create_normalized_record()
    )

    assert len(samples) == 3

    assert samples[0].target_turn == 1
    assert samples[1].target_turn == 2
    assert samples[2].target_turn == 3


def test_first_turn_prompt_completion():
    samples = ConversationSFTBuilder().build(
        create_normalized_record()
    )

    first = samples[0]

    assert len(first.prompt) == 1
    assert first.prompt[0].role == "user"

    assert len(first.completion) == 1
    assert (
        first.completion[0].role
        == "assistant"
    )


def test_second_turn_contains_history():
    samples = ConversationSFTBuilder().build(
        create_normalized_record()
    )

    second = samples[1]

    assert [
        message.role
        for message in second.prompt
    ] == [
        "user",
        "assistant",
        "user",
    ]

    assert (
        second.prompt[0].content
        == "발표 실수를 해서 정말 미안해."
    )

    assert (
        second.prompt[1].content
        == "많이 미안한 마음이 드시겠어요."
    )

    assert (
        second.prompt[2].content
        == "내가 능력이 부족한 것 같아."
    )


def test_third_turn_contains_full_history():
    samples = ConversationSFTBuilder().build(
        create_normalized_record()
    )

    third = samples[2]

    assert [
        message.role
        for message in third.prompt
    ] == [
        "user",
        "assistant",
        "user",
        "assistant",
        "user",
    ]


def test_two_turn_dialogue_creates_two_samples():
    record = create_normalized_record()

    record.turns = record.turns[:2]

    samples = ConversationSFTBuilder().build(
        record
    )

    assert len(samples) == 2


def test_incomplete_third_turn_is_not_generated():
    record = create_normalized_record()

    record.turns[2].assistant = ""

    samples = ConversationSFTBuilder().build(
        record
    )

    assert len(samples) == 2


def test_sample_id_is_deterministic():
    builder = ConversationSFTBuilder()
    record = create_normalized_record()

    first = builder.build(record)
    second = builder.build(record)

    assert first[0].id == second[0].id
    assert first[1].id == second[1].id


def test_content_hash_is_deterministic():
    builder = ConversationSFTBuilder()
    record = create_normalized_record()

    first = builder.build(record)
    second = builder.build(record)

    assert (
        first[0].content_hash
        == second[0].content_hash
    )

    assert len(first[0].content_hash) == 64

    int(first[0].content_hash, 16)


def test_different_targets_have_different_hashes():
    samples = ConversationSFTBuilder().build(
        create_normalized_record()
    )

    hashes = {
        sample.content_hash
        for sample in samples
    }

    assert len(hashes) == 3


def test_metadata_is_preserved():
    sample = ConversationSFTBuilder().build(
        create_normalized_record()
    )[0]

    assert sample.emotion_code == "E31"
    assert sample.emotion_name == "두려운"
    assert sample.coarse_emotion == "불안"

    assert sample.source.split == "training"
    assert sample.source.profile_id == "Pro_03802"