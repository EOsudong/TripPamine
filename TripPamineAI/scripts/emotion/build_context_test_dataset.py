import argparse
from collections import Counter
from pathlib import Path
from typing import Any

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.context_classification import (
    EmotionContextClassificationBuilder,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)

from scripts.emotion.build_context_classification_dataset import (
    calculate_sha256,
    ensure_outputs_absent,
    load_normalized_records_for_ids,
    read_reference_dataset,
    save_report,
    validate_reference_match,
    write_jsonl,
)


TEST_SPLIT = "test"


def build_test_context_samples(
        references: list[
            EmotionClassificationSample
        ],
        normalized_by_id: dict[
            str,
            NormalizedEmotionDialogue,
        ],
) -> tuple[
    list[EmotionClassificationSample],
    dict[str, Any],
]:
    if not references:
        raise ValueError(
            "Test references must not "
            "be empty."
        )

    builder = (
        EmotionContextClassificationBuilder()
    )

    samples = []
    turn_counts: Counter[int] = Counter()
    changed_text_count = 0

    for reference in references:
        if (
                reference.source.split
                != TEST_SPLIT
        ):
            raise ValueError(
                "Reference split mismatch: "
                "expected 'test', found "
                f"'{reference.source.split}'."
            )

        record = normalized_by_id.get(
            reference.id
        )

        if record is None:
            raise ValueError(
                "Normalized record missing "
                "during test context build: "
                f"{reference.id}"
            )

        validate_reference_match(
            reference,
            record,
        )

        sample = builder.build(
            record=record,
            split=TEST_SPLIT,
        )

        if sample.id != reference.id:
            raise RuntimeError(
                "Context test sample record ID "
                "does not match reference."
            )

        if sample.source.split != TEST_SPLIT:
            raise RuntimeError(
                "Context test sample split "
                "is not 'test'."
            )

        human_turn_count = sum(
            1
            for turn in record.turns
            if turn.human.strip()
        )

        turn_counts[
            human_turn_count
        ] += 1

        if sample.text != reference.text:
            changed_text_count += 1

        samples.append(
            sample
        )

    return (
        samples,
        {
            "samples": len(
                samples
            ),
            "changed_text_samples": (
                changed_text_count
            ),
            "unchanged_text_samples": (
                len(samples)
                - changed_text_count
            ),
            "human_turn_count": {
                str(turn_count): count
                for turn_count, count
                in sorted(
                    turn_counts.items()
                )
            },
        },
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Build the TripPamine "
            "context-aware emotion "
            "classification sealed test "
            "dataset while preserving "
            "the existing v2 test split."
        )
    )

    parser.add_argument(
        "--normalized-training",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--normalized-validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--reference-test",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output-dir",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    output_test = (
        args.output_dir
        / "test.jsonl"
    )

    ensure_outputs_absent(
        [
            output_test,
            args.report,
        ]
    )

    test_references = (
        read_reference_dataset(
            args.reference_test,
            TEST_SPLIT,
        )
    )

    required_ids = {
        sample.id
        for sample in test_references
    }

    if len(
            required_ids
    ) != len(
            test_references
    ):
        raise ValueError(
            "Duplicate test reference "
            "record IDs detected."
        )

    normalized_by_id = (
        load_normalized_records_for_ids(
            file_paths=[
                args.normalized_training,
                args.normalized_validation,
            ],
            required_ids=required_ids,
        )
    )

    (
        test_samples,
        test_summary,
    ) = build_test_context_samples(
        references=test_references,
        normalized_by_id=(
            normalized_by_id
        ),
    )

    write_jsonl(
        test_samples,
        output_test,
    )

    report = {
        "experiment": (
            "T0-Q10-A-context-"
            "sealed-test-dataset"
        ),
        "protocol": {
            "context": (
                "all_non_empty_human_turns"
            ),
            "assistant_turns_included": False,
            "turn_separator": "newline",
            "reference_split_preserved": True,
            "test_split_used": True,
            "test_output_created": True,
            "training_output_created": False,
            "validation_output_created": False,
        },
        "source": {
            "normalized_training": {
                "file": str(
                    args.normalized_training
                ),
                "sha256": calculate_sha256(
                    args.normalized_training
                ),
            },
            "normalized_validation": {
                "file": str(
                    args.normalized_validation
                ),
                "sha256": calculate_sha256(
                    args.normalized_validation
                ),
            },
            "reference_test": {
                "file": str(
                    args.reference_test
                ),
                "samples": len(
                    test_references
                ),
                "sha256": calculate_sha256(
                    args.reference_test
                ),
            },
        },
        "output": {
            "test": {
                "file": str(
                    output_test
                ),
                "sha256": calculate_sha256(
                    output_test
                ),
                **test_summary,
            },
        },
    }

    save_report(
        report,
        args.report,
    )

    print()
    print(
        "Context sealed test dataset "
        "completed"
    )

    print()
    print(
        "Test samples: "
        f"{len(test_samples)}"
    )

    print(
        "Test split used: True"
    )

    print(
        "Test output created: True"
    )

    print(
        "Training output created: False"
    )

    print(
        "Validation output created: False"
    )

    print()
    print(
        "Test changed text: "
        f"{test_summary['changed_text_samples']}"
    )

    print()
    print(
        "Output: "
        f"{output_test}"
    )

    print(
        "Report: "
        f"{args.report}"
    )

    print()
    print(
        "CONTEXT TEST DATASET BUILD: PASS"
    )


if __name__ == "__main__":
    main()