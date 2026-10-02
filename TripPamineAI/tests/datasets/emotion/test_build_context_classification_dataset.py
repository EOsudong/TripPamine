from pathlib import Path

import orjson
import pytest

from scripts.emotion.build_context_classification_dataset import (
    build_context_samples,
    load_normalized_records_for_ids,
    read_reference_dataset,
    validate_reference_match,
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
                "human": ["A02", "G02"],
                "computer": ["C01"],
            },
            "emotion": {
                "emotion-id": "S06_D02_E18",
                "type": "E18",
                "situation": ["S06", "D02"],
            },
        },
        "talk": {
            "id": {
                "profile-id": profile_id,
                "talk-id": talk_id,
            },
            "content": {
                "HS01": "첫 번째 발화야.",
                "SS01": "첫 번째 응답이야.",
                "HS02": "두 번째 발화야.",
                "SS02": "두 번째 응답이야.",
                "HS03": "",
                "SS03": "",
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


def create_reference(
        normalized,
        split: str,
):
    sample = (
        EmotionClassificationBuilder()
        .build(normalized)
    )

    return sample.model_copy(
        update={
            "source": sample.source.model_copy(
                update={
                    "split": split,
                }
            )
        }
    )


def write_jsonl(
        path: Path,
        records,
) -> None:
    with path.open("wb") as file:
        for record in records:
            file.write(
                orjson.dumps(
                    record.model_dump(
                        mode="json"
                    ),
                    option=(
                        orjson.OPT_APPEND_NEWLINE
                    ),
                )
            )


def test_load_normalized_records_returns_only_required_ids(
        tmp_path: Path,
):
    required = create_normalized_record()
    unused = create_normalized_record(
        profile_id="Pro_99999",
        talk_id="Pro_99999_00001",
    )

    normalized_path = (
        tmp_path / "normalized.jsonl"
    )

    write_jsonl(
        normalized_path,
        [required, unused],
    )

    result = load_normalized_records_for_ids(
        [normalized_path],
        {required.record_id},
    )

    assert set(result) == {
        required.record_id
    }


def test_read_reference_dataset_requires_expected_split(
        tmp_path: Path,
):
    normalized = create_normalized_record()
    reference = create_reference(
        normalized,
        "training",
    )

    reference_path = (
        tmp_path / "reference.jsonl"
    )

    write_jsonl(
        reference_path,
        [reference],
    )

    with pytest.raises(
        ValueError,
        match="Reference split mismatch",
    ):
        read_reference_dataset(
            reference_path,
            "validation",
        )


def test_build_context_samples_preserves_reference_order():
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
            first,
            "training",
        ),
        create_reference(
            second,
            "training",
        ),
    ]

    normalized = {
        first.record_id: first,
        second.record_id: second,
    }

    samples, summary = build_context_samples(
        references=references,
        normalized_by_id=normalized,
        split="training",
    )

    assert [
        sample.id
        for sample in samples
    ] == [
        first.record_id,
        second.record_id,
    ]

    assert summary[
        "samples"
    ] == 2

    assert summary[
        "changed_text_samples"
    ] == 2

    assert summary[
        "human_turn_count"
    ] == {
        "2": 2
    }


def test_validate_reference_match_rejects_label_mismatch():
    normalized = create_normalized_record()
    reference = create_reference(
        normalized,
        "training",
    ).model_copy(
        update={
            "label": "E10",
        }
    )

    with pytest.raises(
        ValueError,
        match="label",
    ):
        validate_reference_match(
            reference,
            normalized,
        )


def test_build_context_samples_rejects_test_split():
    normalized = create_normalized_record()
    reference = create_reference(
        normalized,
        "test",
    )

    with pytest.raises(
        ValueError,
        match=(
            "training and validation"
        ),
    ):
        build_context_samples(
            references=[reference],
            normalized_by_id={
                normalized.record_id: (
                    normalized
                )
            },
            split="test",
        )
