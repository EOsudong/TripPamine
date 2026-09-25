import argparse
from pathlib import Path

import orjson


def load_identifiers(
    file_path: Path,
) -> tuple[
    set[str],
    set[str],
]:
    record_ids: set[str] = set()
    content_hashes: set[str] = set()

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

            record_ids.add(
                data["source"]["record_id"]
            )

            content_hashes.add(
                data["content_hash"]
            )

    return record_ids, content_hashes


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Check SFT train/validation "
            "dataset leakage."
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

    (
        training_record_ids,
        training_hashes,
    ) = load_identifiers(
        args.training
    )

    (
        validation_record_ids,
        validation_hashes,
    ) = load_identifiers(
        args.validation
    )

    overlapping_record_ids = (
        training_record_ids
        & validation_record_ids
    )

    overlapping_content_hashes = (
        training_hashes
        & validation_hashes
    )

    report = {
        "training": {
            "unique_record_ids": len(
                training_record_ids
            ),
            "unique_content_hashes": len(
                training_hashes
            ),
        },
        "validation": {
            "unique_record_ids": len(
                validation_record_ids
            ),
            "unique_content_hashes": len(
                validation_hashes
            ),
        },
        "leakage": {
            "overlapping_record_ids": len(
                overlapping_record_ids
            ),
            "overlapping_content_hashes": len(
                overlapping_content_hashes
            ),
            "record_id_examples": sorted(
                overlapping_record_ids
            )[:20],
            "content_hash_examples": sorted(
                overlapping_content_hashes
            )[:20],
        },
    }

    args.report.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    args.report.write_bytes(
        orjson.dumps(
            report,
            option=orjson.OPT_INDENT_2,
        )
    )

    print()
    print("SFT split leakage check completed")

    print(
        "Overlapping record IDs: "
        f"{len(overlapping_record_ids)}"
    )

    print(
        "Overlapping content hashes: "
        f"{len(overlapping_content_hashes)}"
    )

    print(f"Report: {args.report}")

    if (
        overlapping_record_ids
        or overlapping_content_hashes
    ):
        raise SystemExit(
            "Dataset leakage detected. "
            "Do not start training yet."
        )


if __name__ == "__main__":
    main()