import argparse
from pathlib import Path
from typing import Any

import orjson


def load_group_ids(
    file_path: Path,
) -> dict[str, set[str]]:
    if not file_path.exists():
        raise FileNotFoundError(
            f"Dataset not found: {file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            f"Dataset is empty: {file_path}"
        )

    profile_ids: set[str] = set()
    talk_ids: set[str] = set()
    record_ids: set[str] = set()

    with file_path.open("rb") as file:
        for line_number, line in enumerate(
            file,
            start=1,
        ):
            if not line.strip():
                continue

            try:
                data = orjson.loads(line)
            except orjson.JSONDecodeError as exc:
                raise ValueError(
                    "Invalid JSONL: "
                    f"{file_path}:{line_number}"
                ) from exc

            source = data["source"]

            profile_ids.add(
                source["profile_id"]
            )

            talk_ids.add(
                source["talk_id"]
            )

            record_ids.add(
                source["record_id"]
            )

    return {
        "profile_ids": profile_ids,
        "talk_ids": talk_ids,
        "record_ids": record_ids,
    }


def save_report(
    report: dict[str, Any],
    output_path: Path,
) -> None:
    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    output_path.write_bytes(
        orjson.dumps(
            report,
            option=orjson.OPT_INDENT_2,
        )
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Check group-level overlap between "
            "SFT training and validation datasets."
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
        "--report",
        required=True,
        type=Path,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    training = load_group_ids(
        args.training
    )

    validation = load_group_ids(
        args.validation
    )

    profile_overlap = (
        training["profile_ids"]
        & validation["profile_ids"]
    )

    talk_overlap = (
        training["talk_ids"]
        & validation["talk_ids"]
    )

    record_overlap = (
        training["record_ids"]
        & validation["record_ids"]
    )

    report = {
        "training": {
            "unique_profiles": len(
                training["profile_ids"]
            ),
            "unique_talk_ids": len(
                training["talk_ids"]
            ),
            "unique_record_ids": len(
                training["record_ids"]
            ),
        },
        "validation": {
            "unique_profiles": len(
                validation["profile_ids"]
            ),
            "unique_talk_ids": len(
                validation["talk_ids"]
            ),
            "unique_record_ids": len(
                validation["record_ids"]
            ),
        },
        "overlap": {
            "profile_ids": {
                "count": len(
                    profile_overlap
                ),
                "examples": sorted(
                    profile_overlap
                )[:20],
            },
            "talk_ids": {
                "count": len(
                    talk_overlap
                ),
                "examples": sorted(
                    talk_overlap
                )[:20],
            },
            "record_ids": {
                "count": len(
                    record_overlap
                ),
                "examples": sorted(
                    record_overlap
                )[:20],
            },
        },
    }

    save_report(
        report=report,
        output_path=args.report,
    )

    print()
    print("SFT group overlap check completed")

    print(
        "Overlapping profile IDs: "
        f"{len(profile_overlap)}"
    )

    print(
        "Overlapping talk IDs: "
        f"{len(talk_overlap)}"
    )

    print(
        "Overlapping record IDs: "
        f"{len(record_overlap)}"
    )

    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()