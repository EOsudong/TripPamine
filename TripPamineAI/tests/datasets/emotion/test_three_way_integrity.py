from trippamine_ai.datasets.emotion.classification import (
    ClassificationSource,
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.three_way_integrity import (
    ThreeWaySplitIntegrityAnalyzer,
)


def create_sample(
    split: str,
    label: str,
    profile_id: str,
    index: int,
):
    record_id = (
        f"{split}-{profile_id}-{index}"
    )

    return EmotionClassificationSample(
        id=record_id,
        text="테스트 감정 문장입니다.",
        label=label,
        label_name="테스트",
        coarse_label="테스트",
        situation_code="S01",
        situation="테스트",
        quality_status="VALID",
        quality_issue_codes=[],
        source=ClassificationSource(
            dataset="test",
            version="v1",
            split=split,
            profile_id=profile_id,
            talk_id=record_id,
        ),
    )


def build_split(
    split: str,
    profiles_per_label: int,
):
    records = []

    for number in range(10, 70):
        label = f"E{number}"

        for profile_index in range(
            profiles_per_label
        ):
            profile_id = (
                f"{split}-{label}-"
                f"{profile_index}"
            )

            records.append(
                create_sample(
                    split=split,
                    label=label,
                    profile_id=profile_id,
                    index=0,
                )
            )

    return records


def test_valid_three_way_split():
    training = build_split(
        "training",
        26,
    )

    validation = build_split(
        "validation",
        3,
    )

    test = build_split(
        "test",
        3,
    )

    manifest = {
        "source_records": (
            len(training)
            + len(validation)
            + len(test)
        ),
        "training_records": len(
            training
        ),
        "validation_records": len(
            validation
        ),
        "test_records": len(test),
        "training_profiles": 26 * 60,
        "validation_profiles": 3 * 60,
        "test_profiles": 3 * 60,
    }

    report = (
        ThreeWaySplitIntegrityAnalyzer()
        .analyze(
            training=training,
            validation=validation,
            test=test,
            manifest=manifest,
        )
    )

    assert report.all_records_isolated
    assert report.all_profiles_isolated
    assert report.all_labels_present
    assert report.all_profile_targets_met
    assert report.all_source_splits_correct
    assert report.manifest.all_match

    assert report.integrity_pass


def test_detects_profile_overlap():
    training = build_split(
        "training",
        26,
    )

    validation = build_split(
        "validation",
        3,
    )

    test = build_split(
        "test",
        3,
    )

    validation[
        0
    ].source.profile_id = (
        training[0]
        .source.profile_id
    )

    manifest = {
        "source_records": (
            len(training)
            + len(validation)
            + len(test)
        ),
        "training_records": len(
            training
        ),
        "validation_records": len(
            validation
        ),
        "test_records": len(test),
        "training_profiles": 26 * 60,
        "validation_profiles": 3 * 60,
        "test_profiles": 3 * 60,
    }

    report = (
        ThreeWaySplitIntegrityAnalyzer()
        .analyze(
            training,
            validation,
            test,
            manifest,
        )
    )

    assert not report.all_profiles_isolated
    assert not report.integrity_pass