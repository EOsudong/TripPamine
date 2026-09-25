import argparse
import hashlib
from pathlib import Path

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.profile_split import (
    ProfileStratifiedSplitter,
)


def calculate_sha256(
        file_path: Path,
) -> str:
    digest = hashlib.sha256()

    with file_path.open("rb") as file:
        while chunk := file.read(
                1024 * 1024
        ):
            digest.update(chunk)

    return digest.hexdigest()


def read_dataset(
        file_path: Path,
) -> list[EmotionClassificationSample]:
    if not file_path.exists():
        raise FileNotFoundError(
            f"Dataset not found: {file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            f"Dataset is empty: {file_path}"
        )

    records = []

    with file_path.open("rb") as file:
        for line_number, line in enumerate(
                tqdm(
                    file,
                    desc=f"Loading {file_path.name}",
                    unit="record",
                ),
                start=1,
        ):
            if not line.strip():
                continue

            try:
                raw = orjson.loads(line)
            except orjson.JSONDecodeError as exc:
                raise ValueError(
                    "Invalid JSONL at "
                    f"{file_path}:"
                    f"{line_number}"
                ) from exc

            records.append(
                EmotionClassificationSample
                .model_validate(raw)
            )

    return records


def write_split(
        records: list[
            EmotionClassificationSample
        ],
        profile_ids: set[str],
        split_name: str,
        output_path: Path,
) -> int:
    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    written = 0

    with output_path.open("wb") as file:
        for record in records:
            if (
                    record.source.profile_id
                    not in profile_ids
            ):
                continue

            record.source.split = split_name

            file.write(
                orjson.dumps(
                    record.model_dump(
                        mode="json"
                    ),
                    option=(
                        orjson
                        .OPT_APPEND_NEWLINE
                    ),
                )
            )

            written += 1

    return written


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Create profile-safe stratified "
            "TripPamine emotion dataset v2."
        )
    )

    parser.add_argument(
        "--training",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output-dir",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--manifest",
        required=True,
        type=Path,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    training = read_dataset(
        args.training
    )

    validation = read_dataset(
        args.validation
    )

    records = (
            training
            + validation
    )

    record_ids = [
        record.id
        for record in records
    ]

    if len(record_ids) != len(
            set(record_ids)
    ):
        raise ValueError(
            "Duplicate record IDs detected "
            "before v2 split."
        )

    splitter = ProfileStratifiedSplitter(
    )

    split = splitter.split(
        records
    )

    training_ids = set(
        split.training_profile_ids
    )

    validation_ids = set(
        split.validation_profile_ids
    )

    test_ids = set(
        split.test_profile_ids
    )

    training_count = write_split(
        records,
        training_ids,
        "training",
        args.output_dir
        / "training.jsonl",
    )

    validation_count = write_split(
        records,
        validation_ids,
        "validation",
        args.output_dir
        / "validation.jsonl",
    )

    test_count = write_split(
        records,
        test_ids,
        "test",
        args.output_dir
        / "test.jsonl",
    )

    if (
            training_count
            + validation_count
            + test_count
            != len(records)
    ):
        raise RuntimeError(
            "Split record count mismatch."
        )

    manifest = {
        "version": "v2",
        "strategy": (
            "profile-group-fine-emotion-"
            "deterministic"
        ),
        "algorithm_version": (
            ProfileStratifiedSplitter
            .ALGORITHM_VERSION
        ),
        "source": {
            "training": {
                "file": str(
                    args.training
                ),
                "sha256": calculate_sha256(
                    args.training
                ),
            },
            "validation": {
                "file": str(
                    args.validation
                ),
                "sha256": calculate_sha256(
                    args.validation
                ),
            },
        },
        "constraints": {
            "profile_isolation": True,
            "record_isolation": True,
            "fine_emotion_labels": 60,
            "validation_profiles_per_label": (
                ProfileStratifiedSplitter
                .VALIDATION_PROFILES_PER_LABEL
            ),
            "test_profiles_per_label": (
                ProfileStratifiedSplitter
                .TEST_PROFILES_PER_LABEL
            ),
            "target_validation_share": (
                ProfileStratifiedSplitter
                .TARGET_VALIDATION_SHARE
            ),
            "target_test_share": (
                ProfileStratifiedSplitter
                .TARGET_TEST_SHARE
            ),
            "candidate_limit": (
                ProfileStratifiedSplitter
                .CANDIDATE_LIMIT
            ),
            "tie_break": (
                "profile_id_lexicographic"
            ),
        },
        "source_records": len(records),
        "training_records": training_count,
        "validation_records": (
            validation_count
        ),
        "test_records": test_count,
        "training_profiles": len(
            training_ids
        ),
        "validation_profiles": len(
            validation_ids
        ),
        "test_profiles": len(
            test_ids
        ),
        "split": split.model_dump(
            mode="json"
        ),
    }
    args.manifest.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    args.manifest.write_bytes(
        orjson.dumps(
            manifest,
            option=orjson.OPT_INDENT_2,
        )
    )

    print()
    print(
        "Profile stratified split completed"
    )

    print(
        f"Source records: "
        f"{len(records)}"
    )

    print(
        f"Training records: "
        f"{training_count}"
    )

    print(
        f"Validation records: "
        f"{validation_count}"
    )

    print(
        f"Test records: "
        f"{test_count}"
    )

    print(
        f"Training profiles: "
        f"{len(training_ids)}"
    )

    print(
        f"Validation profiles: "
        f"{len(validation_ids)}"
    )

    print(
        f"Test profiles: "
        f"{len(test_ids)}"
    )

    print()
    print(
        f"Output: "
        f"{args.output_dir}"
    )

    print(
        f"Manifest: "
        f"{args.manifest}"
    )


if __name__ == "__main__":
    main()
