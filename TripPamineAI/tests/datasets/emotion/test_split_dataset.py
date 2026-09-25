import pytest

from trippamine_ai.datasets.emotion.split_dataset import (
    DatasetSplit,
    ProfileDatasetSplitter,
)


def test_same_profile_always_has_same_split():
    splitter = ProfileDatasetSplitter(
        salt="trippamine-emotion-v2",
    )

    first = splitter.assign_profile(
        "Pro_00061"
    )

    second = splitter.assign_profile(
        "Pro_00061"
    )

    assert first == second


def test_same_salt_is_reproducible():
    first_splitter = ProfileDatasetSplitter(
        salt="trippamine-emotion-v2",
    )

    second_splitter = ProfileDatasetSplitter(
        salt="trippamine-emotion-v2",
    )

    assert (
        first_splitter.assign_profile(
            "Pro_01234"
        )
        ==
        second_splitter.assign_profile(
            "Pro_01234"
        )
    )


def test_split_is_valid_enum():
    splitter = ProfileDatasetSplitter(
        salt="trippamine-emotion-v2",
    )

    result = splitter.assign_profile(
        "Pro_00001"
    )

    assert result in {
        DatasetSplit.TRAINING,
        DatasetSplit.VALIDATION,
        DatasetSplit.TEST,
    }


def test_empty_salt_is_rejected():
    with pytest.raises(
        ValueError,
        match="must not be empty",
    ):
        ProfileDatasetSplitter(
            salt="",
        )


def test_invalid_ratios_are_rejected():
    with pytest.raises(
        ValueError,
        match="must sum to 1.0",
    ):
        ProfileDatasetSplitter(
            salt="test",
            training_ratio=0.7,
            validation_ratio=0.2,
            test_ratio=0.2,
        )


def test_distribution_is_reasonable():
    splitter = ProfileDatasetSplitter(
        salt="trippamine-emotion-v2",
    )

    counts = {
        DatasetSplit.TRAINING: 0,
        DatasetSplit.VALIDATION: 0,
        DatasetSplit.TEST: 0,
    }

    total = 10_000

    for index in range(total):
        split = splitter.assign_profile(
            f"Pro_{index:05d}"
        )

        counts[split] += 1

    training_ratio = (
        counts[DatasetSplit.TRAINING]
        / total
    )

    validation_ratio = (
        counts[DatasetSplit.VALIDATION]
        / total
    )

    test_ratio = (
        counts[DatasetSplit.TEST]
        / total
    )

    assert 0.77 <= training_ratio <= 0.83
    assert 0.08 <= validation_ratio <= 0.12
    assert 0.08 <= test_ratio <= 0.12