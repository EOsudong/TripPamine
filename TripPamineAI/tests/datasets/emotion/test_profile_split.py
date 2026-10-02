from trippamine_ai.datasets.emotion.classification import (
    ClassificationSource,
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.profile_split import (
    ProfileStratifiedSplitter,
)


def create_sample(
        profile_id: str,
        record_index: int,
        label: str,
) -> EmotionClassificationSample:
    record_id = (
        f"{profile_id}-{record_index}"
    )

    return EmotionClassificationSample(
        id=record_id,
        text="테스트 감정 문장입니다.",
        label=label,
        label_name="테스트",
        coarse_label="분노",
        situation_code="S01",
        situation="테스트",
        quality_status="VALID",
        quality_issue_codes=[],
        source=ClassificationSource(
            dataset="test",
            version="v1",
            split="source",
            profile_id=profile_id,
            talk_id=record_id,
        ),
    )


def build_profiles(
        label: str,
        profile_count: int = 32,
):
    records = []

    for index in range(
            profile_count
    ):
        profile_id = (
            f"{label}-profile-{index:02d}"
        )

        record_count = (
                10 + index
        )

        for record_index in range(
                record_count
        ):
            records.append(
                create_sample(
                    profile_id,
                    record_index,
                    label,
                )
            )

    return records


def test_profile_isolation():
    records = []

    for label_number in range(
            10,
            70,
    ):
        records.extend(
            build_profiles(
                f"E{label_number}"
            )
        )

    result = (
        ProfileStratifiedSplitter()
        .split(records)
    )

    training = set(
        result.training_profile_ids
    )

    validation = set(
        result.validation_profile_ids
    )

    test = set(
        result.test_profile_ids
    )

    assert not (
            training & validation
    )

    assert not (
            training & test
    )

    assert not (
            validation & test
    )


def test_profile_counts_per_label():
    records = []

    for label_number in range(
            10,
            70,
    ):
        records.extend(
            build_profiles(
                f"E{label_number}"
            )
        )

    result = (
        ProfileStratifiedSplitter()
        .split(records)
    )

    for statistics in (
            result.label_statistics
    ):
        assert (
                statistics.training_profiles
                == 26
        )

        assert (
                statistics.validation_profiles
                == 3
        )

        assert (
                statistics.test_profiles
                == 3
        )


def test_deterministic_split():
    records = []

    for label_number in range(
            10,
            70,
    ):
        records.extend(
            build_profiles(
                f"E{label_number}"
            )
        )

    first = (
        ProfileStratifiedSplitter()
        .split(records)
    )

    second = (
        ProfileStratifiedSplitter()
        .split(records)
    )

    assert (
            first.training_profile_ids
            == second.training_profile_ids
    )

    assert (
            first.validation_profile_ids
            == second.validation_profile_ids
    )

    assert (
            first.test_profile_ids
            == second.test_profile_ids
    )

    assert (
            first.label_statistics
            == second.label_statistics
    )
