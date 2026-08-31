import argparse
from collections import Counter
from pathlib import Path
from typing import Any

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)
from trippamine_ai.datasets.emotion.split_dataset import (
    DatasetSplit,
    ProfileDatasetSplitter,
)


def load_records(
    file_path: Path,
) -> list[NormalizedEmotionDialogue]:
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
            file,
            start=1,
        ):
            if not line.strip():
                continue

            try:
                raw = orjson.loads(line)
            except orjson.JSONDecodeError as exc:
                raise ValueError(
                    "Invalid JSONL: "
                    f"{file_path}:{line_number}"
                ) from exc

            records.append(
                NormalizedEmotionDialogue
                .model_validate(raw)
            )

    return records


def calculate_distribution(
    records: list[
        NormalizedEmotionDialogue
    ],
) -> dict[str, Any]:
    emotion_counts: Counter[str] = Counter()
    coarse_counts: Counter[str] = Counter()
    source_split_counts: Counter[str] = Counter()

    profiles: set[str] = set()

    for record in records:
        emotion_counts[
            record.emotion.emotion_code
        ] += 1

        coarse_counts[
            record.emotion.coarse_emotion
        ] += 1

        source_split_counts[
            record.source.split
        ] += 1

        profiles.add(
            record.source.profile_id
        )

    return {
        "records": len(records),
        "profiles": len(profiles),
        "fine_emotion": dict(
            sorted(emotion_counts.items())
        ),
        "coarse_emotion": dict(
            sorted(coarse_counts.items())
        ),
        "source_split": dict(
            sorted(source_split_counts.items())
        ),
    }


def write_jsonl(
    records: list[
        NormalizedEmotionDialogue
    ],
    output_path: Path,
) -> None:
    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with output_path.open("wb") as file:
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


def save_json(
    data: Any,
    output_path: Path,
) -> None:
    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    output_path.write_bytes(
        orjson.dumps(
            data,
            option=orjson.OPT_INDENT_2,
        )
    )


def create_split(
    training_path: Path,
    validation_path: Path,
    output_dir: Path,
    manifest_path: Path,
    report_path: Path,
    salt: str,
) -> None:
    source_records = []

    source_records.extend(
        load_records(training_path)
    )

    source_records.extend(
        load_records(validation_path)
    )

    splitter = ProfileDatasetSplitter(
        salt=salt,
    )

    seen_record_ids: set[str] = set()

    split_records = {
        DatasetSplit.TRAINING: [],
        DatasetSplit.VALIDATION: [],
        DatasetSplit.TEST: [],
    }

    split_profiles = {
        DatasetSplit.TRAINING: set(),
        DatasetSplit.VALIDATION: set(),
        DatasetSplit.TEST: set(),
    }

    manifest_entries = []

    for record in tqdm(
        source_records,
        desc="Creating v2 profile split",
        unit="record",
    ):
        if record.record_id in seen_record_ids:
            raise ValueError(
                "Duplicate record_id detected: "
                f"{record.record_id}"
            )

        seen_record_ids.add(
            record.record_id
        )

        entry = (
            splitter.create_manifest_entry(
                record
            )
        )

        split = entry.dataset_split

        split_records[
            split
        ].append(record)

        split_profiles[
            split
        ].add(entry.profile_id)

        manifest_entries.append(
            entry
        )

    profile_overlap = {
        "training_validation": len(
            split_profiles[
                DatasetSplit.TRAINING
            ]
            & split_profiles[
                DatasetSplit.VALIDATION
            ]
        ),
        "training_test": len(
            split_profiles[
                DatasetSplit.TRAINING
            ]
            & split_profiles[
                DatasetSplit.TEST
            ]
        ),
        "validation_test": len(
            split_profiles[
                DatasetSplit.VALIDATION
            ]
            & split_profiles[
                DatasetSplit.TEST
            ]
        ),
    }

    if any(
        profile_overlap.values()
    ):
        raise RuntimeError(
            "Profile leakage detected "
            "during v2 split creation."
        )

    output_paths = {
        DatasetSplit.TRAINING: (
            output_dir / "training.jsonl"
        ),
        DatasetSplit.VALIDATION: (
            output_dir / "validation.jsonl"
        ),
        DatasetSplit.TEST: (
            output_dir / "test.jsonl"
        ),
    }

    for split, records in (
        split_records.items()
    ):
        write_jsonl(
            records=records,
            output_path=output_paths[
                split
            ],
        )

    manifest_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with manifest_path.open("wb") as file:
        for entry in manifest_entries:
            file.write(
                orjson.dumps(
                    entry.model_dump(
                        mode="json"
                    ),
                    option=(
                        orjson.OPT_APPEND_NEWLINE
                    ),
                )
            )

    total_records = len(
        source_records
    )

    total_profiles = len(
        set().union(
            *split_profiles.values()
        )
    )

    report = {
        "configuration": {
            "strategy": (
                "profile_id_sha256"
            ),
            "salt": salt,
            "ratios": {
                "training": 0.8,
                "validation": 0.1,
                "test": 0.1,
            },
        },
        "source": {
            "training_file": str(
                training_path
            ),
            "validation_file": str(
                validation_path
            ),
            "total_records": (
                total_records
            ),
            "total_profiles": (
                total_profiles
            ),
        },
        "splits": {
            split.value: {
                **calculate_distribution(
                    split_records[split]
                ),
                "record_share": (
                    len(split_records[split])
                    / total_records
                ),
                "profile_share": (
                    len(
                        split_profiles[split]
                    )
                    / total_profiles
                ),
            }
            for split
            in DatasetSplit
        },
        "leakage": {
            "profile_overlap": (
                profile_overlap
            )
        },
    }

    save_json(
        report,
        report_path,
    )

    print()
    print("TripPamine v2 split completed")
    print(
        f"Total records: {total_records}"
    )
    print(
        f"Total profiles: {total_profiles}"
    )

    for split in DatasetSplit:
        records = split_records[split]
        profiles = split_profiles[split]

        print(
            f"{split.value}: "
            f"{len(records)} records / "
            f"{len(profiles)} profiles"
        )

    print(
        "Profile overlap: "
        f"{profile_overlap}"
    )

    print(
        f"Manifest: {manifest_path}"
    )

    print(
        f"Report: {report_path}"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Create TripPamine v2 deterministic "
            "profile-level dataset split."
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

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--salt",
        default="trippamine-emotion-v2",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    create_split(
        training_path=args.training,
        validation_path=args.validation,
        output_dir=args.output_dir,
        manifest_path=args.manifest,
        report_path=args.report,
        salt=args.salt,
    )


if __name__ == "__main__":
    main()