from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTBuilder,
)
from trippamine_ai.datasets.emotion.conversation_sft_v2_integrity import (
    ConversationSFTV2IntegrityAnalyzer,
)
from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.normalizer import (
    EmotionDialogueNormalizer,
)


def create_samples(
    profile_id: str,
    split: str,
):
    raw = {
        "profile": {
            "persona-id": profile_id,
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
                "profile-id": profile_id,
                "talk-id": (
                    f"{profile_id}_00001"
                ),
            },
            "content": {
                "HS01": (
                    f"{profile_id} 첫 번째 질문"
                ),
                "SS01": (
                    f"{profile_id} 첫 번째 답변"
                ),
                "HS02": (
                    f"{profile_id} 두 번째 질문"
                ),
                "SS02": (
                    f"{profile_id} 두 번째 답변"
                ),
                "HS03": (
                    f"{profile_id} 세 번째 질문"
                ),
                "SS03": (
                    f"{profile_id} 세 번째 답변"
                ),
            },
        },
    }

    record = (
        EmotionDialogueRecord
        .model_validate(raw)
    )

    normalized = (
        EmotionDialogueNormalizer()
        .normalize(
            record,
            split=split,
        )
    )

    return (
        ConversationSFTBuilder()
        .build(normalized)
    )


def test_valid_three_way_sft_integrity():
    training = create_samples(
        "Pro_00001",
        "training",
    )

    validation = create_samples(
        "Pro_00002",
        "validation",
    )

    test = create_samples(
        "Pro_00003",
        "test",
    )

    manifest = {
        "source_records": 3,
        "training_records": 1,
        "validation_records": 1,
        "test_records": 1,
        "split": {
            "training_profile_ids": [
                "Pro_00001",
            ],
            "validation_profile_ids": [
                "Pro_00002",
            ],
            "test_profile_ids": [
                "Pro_00003",
            ],
        },
    }

    build_report = {
        "summary": {
            "source_dialogues": 3,
            "generated_samples": 9,
            "duplicate_content_hashes": 0,
        },
        "splits": {
            "training": {
                "source_dialogues": 1,
                "generated_samples": 3,
                "profiles": 1,
            },
            "validation": {
                "source_dialogues": 1,
                "generated_samples": 3,
                "profiles": 1,
            },
            "test": {
                "source_dialogues": 1,
                "generated_samples": 3,
                "profiles": 1,
            },
        },
    }

    report = (
        ConversationSFTV2IntegrityAnalyzer()
        .analyze(
            training=training,
            validation=validation,
            test=test,
            manifest=manifest,
            build_report=build_report,
        )
    )

    assert report.all_sample_ids_isolated
    assert report.all_record_ids_isolated
    assert report.all_profiles_isolated
    assert report.all_content_hashes_unique

    assert (
        report.all_source_splits_correct
    )

    assert (
        report.all_target_sequences_valid
    )

    assert report.all_messages_non_empty

    assert report.manifest.all_match
    assert report.build_report.all_match

    assert report.integrity_pass