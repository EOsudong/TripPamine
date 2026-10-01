import numpy as np
import pytest
from transformers.trainer_utils import (
    EvalPrediction,
)

from trippamine_ai.datasets.emotion.classification import (
    ClassificationSource,
    EmotionClassificationSample,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_compute_metrics,
    build_fine_to_coarse,
    select_balanced_samples,
)


def create_sample(
        label: str,
        index: int,
        coarse_label: str | None = None,
) -> EmotionClassificationSample:
    if coarse_label is None:
        coarse_label = (
            f"coarse-{label[1]}"
        )

    sample_id = (
        f"{label}-{index}"
    )

    return EmotionClassificationSample(
        id=sample_id,
        text=(
            f"{label} 테스트 문장 "
            f"{index}"
        ),
        label=label,
        label_name=label,
        coarse_label=(
            coarse_label
        ),
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
            profile_id=(
                f"profile-{sample_id}"
            ),
            talk_id=sample_id,
        ),
    )


def create_all_labels(
        samples_per_label: int,
) -> list[
    EmotionClassificationSample
]:
    mapping = (
        EmotionLabelMapping()
    )

    return [
        create_sample(
            label=label,
            index=index,
        )
        for label in mapping.labels
        for index in range(
            samples_per_label
        )
    ]


def test_select_balanced_samples():
    mapping = (
        EmotionLabelMapping()
    )

    samples = (
        create_all_labels(
            samples_per_label=3
        )
    )

    selected = (
        select_balanced_samples(
            samples=samples,
            label_mapping=mapping,
            samples_per_label=2,
        )
    )

    assert len(
        selected
    ) == 120

    counts = {
        label: 0
        for label in mapping.labels
    }

    for sample in selected:
        counts[
            sample.label
        ] += 1

    assert set(
        counts.values()
    ) == {
        2
    }


def test_balanced_selection_is_deterministic():
    mapping = (
        EmotionLabelMapping()
    )

    samples = (
        create_all_labels(
            samples_per_label=3
        )
    )

    first = (
        select_balanced_samples(
            samples=samples,
            label_mapping=mapping,
            samples_per_label=2,
        )
    )

    second = (
        select_balanced_samples(
            samples=samples,
            label_mapping=mapping,
            samples_per_label=2,
        )
    )

    assert [
        sample.id
        for sample in first
    ] == [
        sample.id
        for sample in second
    ]


def test_insufficient_balanced_samples_are_rejected():
    mapping = (
        EmotionLabelMapping()
    )

    samples = (
        create_all_labels(
            samples_per_label=1
        )
    )

    with pytest.raises(
        ValueError,
        match=(
            "Insufficient balanced "
            "samples"
        ),
    ):
        select_balanced_samples(
            samples=samples,
            label_mapping=mapping,
            samples_per_label=2,
        )


def test_build_fine_to_coarse():
    mapping = (
        EmotionLabelMapping()
    )

    samples = (
        create_all_labels(
            samples_per_label=1
        )
    )

    result = (
        build_fine_to_coarse(
            samples=samples,
            label_mapping=mapping,
        )
    )

    assert len(
        result
    ) == 60

    assert "E10" in result
    assert "E69" in result


def test_inconsistent_coarse_mapping_is_rejected():
    mapping = (
        EmotionLabelMapping()
    )

    samples = (
        create_all_labels(
            samples_per_label=1
        )
    )

    samples.append(
        create_sample(
            label="E10",
            index=99,
            coarse_label=(
                "different-coarse"
            ),
        )
    )

    with pytest.raises(
        ValueError,
        match=(
            "Inconsistent coarse "
            "emotion mapping"
        ),
    ):
        build_fine_to_coarse(
            samples=samples,
            label_mapping=mapping,
        )


def test_compute_metrics_for_perfect_predictions():
    mapping = (
        EmotionLabelMapping()
    )

    samples = (
        create_all_labels(
            samples_per_label=1
        )
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            samples=samples,
            label_mapping=mapping,
        )
    )

    logits = np.eye(
        60,
        dtype=np.float32,
    )

    label_ids = np.arange(
        60,
        dtype=np.int64,
    )

    prediction = EvalPrediction(
        predictions=logits,
        label_ids=label_ids,
    )

    compute_metrics = (
        build_compute_metrics(
            label_mapping=mapping,
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    result = compute_metrics(
        prediction
    )

    assert (
        result[
            "fine_accuracy"
        ]
        == pytest.approx(
            1.0
        )
    )

    assert (
        result[
            "fine_macro_f1"
        ]
        == pytest.approx(
            1.0
        )
    )

    assert (
        result[
            "coarse_accuracy"
        ]
        == pytest.approx(
            1.0
        )
    )

    assert (
        result[
            "coarse_macro_f1"
        ]
        == pytest.approx(
            1.0
        )
    )