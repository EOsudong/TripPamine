from types import SimpleNamespace

import orjson
import pytest

from scripts.emotion.evaluate_transformer_classifier import (
    build_codebook_fine_to_coarse,
    read_test_dataset,
    save_report,
    validate_model_config,
    validate_test_samples,
)
from trippamine_ai.datasets.emotion.classification import (
    ClassificationSource,
    EmotionClassificationSample,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)


def create_sample(
        label: str = "E10",
        split: str = "test",
        coarse_label: str = "분노",
) -> EmotionClassificationSample:
    return EmotionClassificationSample(
        id="sample-1",
        text="테스트 문장",
        label=label,
        label_name=label,
        coarse_label=coarse_label,
        situation_code="S06",
        situation="test",
        quality_status="VALID",
        quality_issue_codes=[],
        source=ClassificationSource(
            dataset=(
                "aihub-emotional-dialogue"
            ),
            version="v2",
            split=split,
            profile_id="profile-1",
            talk_id="talk-1",
        ),
    )


def test_codebook_fine_to_coarse_covers_all_labels():
    mapping = EmotionLabelMapping()

    result = build_codebook_fine_to_coarse(
        mapping
    )

    assert len(
        result
    ) == 60

    assert result[
        "E10"
    ] == "분노"

    assert result[
        "E69"
    ] == "기쁨"


def test_test_validation_does_not_require_all_fine_labels():
    mapping = EmotionLabelMapping()

    samples = [
        create_sample()
    ]

    validate_test_samples(
        samples=samples,
        label_mapping=mapping,
    )


def test_read_test_dataset_rejects_non_test_split(
        tmp_path,
):
    dataset_path = (
        tmp_path
        / "synthetic.jsonl"
    )

    sample = create_sample(
        split="validation"
    )

    dataset_path.write_bytes(
        orjson.dumps(
            sample.model_dump()
        )
        + b"\n"
    )

    with pytest.raises(
        ValueError,
        match=(
            "Classification split "
            "mismatch"
        ),
    ):
        read_test_dataset(
            dataset_path
        )


def test_non_test_split_is_rejected():
    mapping = EmotionLabelMapping()

    samples = [
        create_sample(
            split="validation"
        )
    ]

    with pytest.raises(
        ValueError,
        match=(
            "Classification split "
            "mismatch"
        ),
    ):
        validate_test_samples(
            samples=samples,
            label_mapping=mapping,
        )


def test_inconsistent_coarse_label_is_rejected():
    mapping = EmotionLabelMapping()

    samples = [
        create_sample(
            coarse_label="슬픔"
        )
    ]

    with pytest.raises(
        ValueError,
        match=(
            "Inconsistent coarse "
            "emotion mapping"
        ),
    ):
        validate_test_samples(
            samples=samples,
            label_mapping=mapping,
        )


def test_model_config_accepts_expected_mapping():
    mapping = EmotionLabelMapping()

    config = SimpleNamespace(
        num_labels=(
            mapping.num_labels
        ),
        label2id=dict(
            mapping.label2id
        ),
        id2label=dict(
            mapping.id2label
        ),
    )

    validate_model_config(
        config=config,
        label_mapping=mapping,
    )


def test_model_config_rejects_label_mapping_mismatch():
    mapping = EmotionLabelMapping()

    label2id = dict(
        mapping.label2id
    )

    label2id[
        "E10"
    ] = 99

    config = SimpleNamespace(
        num_labels=(
            mapping.num_labels
        ),
        label2id=label2id,
        id2label=dict(
            mapping.id2label
        ),
    )

    with pytest.raises(
        ValueError,
        match=(
            "label2id mapping"
        ),
    ):
        validate_model_config(
            config=config,
            label_mapping=mapping,
        )


def test_report_overwrite_is_rejected(
        tmp_path,
):
    report_path = (
        tmp_path
        / "report.json"
    )

    save_report(
        report={
            "result": "first"
        },
        output_path=report_path,
    )

    with pytest.raises(
        FileExistsError,
        match=(
            "Evaluation report already "
            "exists"
        ),
    ):
        save_report(
            report={
                "result": "second"
            },
            output_path=report_path,
        )