import argparse
import hashlib
from collections import Counter
from pathlib import Path
from typing import Any

import orjson
from tqdm import tqdm

from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTBuilder,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


def calculate_sha256(
    file_path: Path,
) -> str:
    digest = hashlib.sha256()

    with file_path.open("rb") as file:
        while chunk := file.read(1024 * 1024):
            digest.update(chunk)

    return digest.hexdigest()


def build_dataset(
    input_path: Path,
    output_path: Path,
    expected_split: str,
) -> dict[str, Any]:
    if not input_path.exists():
        raise FileNotFoundError(
            f"Input dataset not found: {input_path}"
        )

    if input_path.stat().st_size == 0:
        raise ValueError(
            f"Input dataset is empty: {input_path}"
        )

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    builder = ConversationSFTBuilder()

    source_dialogues = 0
    generated_samples = 0
    skipped_incomplete_turns = 0

    target_turn_counts: Counter[int] = Counter()

    dialogue_quality_counts: Counter[str] = Counter()
    sample_quality_counts: Counter[str] = Counter()

    sample_ids: set[str] = set()
    content_hashes: set[str] = set()

    duplicate_content_hashes = 0

    with (
        input_path.open("rb") as input_file,
        output_path.open("wb") as output_file,
    ):
        for line in tqdm(
            input_file,
            desc=f"Building SFT {expected_split}",
            unit="dialogue",
        ):
            if not line.strip():
                continue

            raw_record = orjson.loads(line)

            record = (
                NormalizedEmotionDialogue.model_validate(
                    raw_record
                )
            )

            if record.source.split != expected_split:
                raise ValueError(
                    "Dataset split mismatch: "
                    f"expected '{expected_split}', "
                    f"found '{record.source.split}'"
                )

            source_dialogues += 1

            dialogue_quality_counts[
                record.quality.status.value
            ] += 1

            skipped_incomplete_turns += sum(
                1
                for turn in record.turns
                if (
                    not turn.human.strip()
                    or not turn.assistant.strip()
                )
            )

            samples = builder.build(record)

            for sample in samples:
                if sample.id in sample_ids:
                    raise ValueError(
                        "Duplicate SFT sample ID: "
                        f"{sample.id}"
                    )

                sample_ids.add(sample.id)

                if (
                    sample.content_hash
                    in content_hashes
                ):
                    duplicate_content_hashes += 1
                else:
                    content_hashes.add(
                        sample.content_hash
                    )

                output_file.write(
                    orjson.dumps(
                        sample.model_dump(
                            mode="json"
                        ),
                        option=(
                            orjson.OPT_APPEND_NEWLINE
                        ),
                    )
                )

                generated_samples += 1

                target_turn_counts[
                    sample.target_turn
                ] += 1

                sample_quality_counts[
                    sample.source_quality_status
                ] += 1

    return {
        "source": {
            "file": str(input_path),
            "file_size_bytes": (
                input_path.stat().st_size
            ),
            "sha256": calculate_sha256(
                input_path
            ),
            "split": expected_split,
        },
        "output": {
            "file": str(output_path),
            "file_size_bytes": (
                output_path.stat().st_size
            ),
            "sha256": calculate_sha256(
                output_path
            ),
        },
        "summary": {
            "source_dialogues": source_dialogues,
            "generated_samples": (
                generated_samples
            ),
            "skipped_incomplete_turns": (
                skipped_incomplete_turns
            ),
            "duplicate_content_hashes": (
                duplicate_content_hashes
            ),
        },
        "target_turns": {
            str(turn): count
            for turn, count
            in sorted(
                target_turn_counts.items()
            )
        },
        "quality": {
            "dialogues": dict(
                sorted(
                    dialogue_quality_counts.items()
                )
            ),
            "samples": dict(
                sorted(
                    sample_quality_counts.items()
                )
            ),
        },
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
            "Build TripPamine conversational "
            "prompt-completion SFT dataset."
        )
    )

    parser.add_argument(
        "--input",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--report",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--expected-split",
        required=True,
        choices=[
            "training",
            "validation",
        ],
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    report = build_dataset(
        input_path=args.input,
        output_path=args.output,
        expected_split=args.expected_split,
    )

    save_report(
        report=report,
        output_path=args.report,
    )

    summary = report["summary"]

    print()
    print("Conversation SFT dataset completed")

    print(
        "Source dialogues: "
        f"{summary['source_dialogues']}"
    )

    print(
        "Generated samples: "
        f"{summary['generated_samples']}"
    )

    print(
        "Skipped incomplete turns: "
        f"{summary['skipped_incomplete_turns']}"
    )

    print(
        "Duplicate content hashes: "
        f"{summary['duplicate_content_hashes']}"
    )

    print(
        f"Target turns: "
        f"{report['target_turns']}"
    )

    print(f"Output: {args.output}")
    print(f"Report: {args.report}")


if __name__ == "__main__":
    main()