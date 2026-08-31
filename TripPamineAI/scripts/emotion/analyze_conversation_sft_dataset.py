import argparse
import hashlib
from collections import defaultdict
from pathlib import Path
from typing import Any

import orjson

from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTSample,
)
from trippamine_ai.datasets.emotion.sft_statistics import (
    ConversationSFTStatisticsAnalyzer,
)


def load_dataset(
    file_path: Path,
) -> list[ConversationSFTSample]:
    if not file_path.exists():
        raise FileNotFoundError(
            f"Dataset not found: {file_path}"
        )

    if file_path.stat().st_size == 0:
        raise ValueError(
            f"Dataset is empty: {file_path}"
        )

    samples = []

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

            samples.append(
                ConversationSFTSample.model_validate(
                    raw
                )
            )

    return samples


def audit_sort_key(
    sample: ConversationSFTSample,
) -> str:
    return hashlib.sha256(
        sample.id.encode("utf-8")
    ).hexdigest()


def select_audit_samples(
    samples: list[ConversationSFTSample],
    samples_per_group: int,
) -> list[dict[str, Any]]:
    groups = defaultdict(list)

    for sample in samples:
        key = (
            sample.coarse_emotion,
            sample.target_turn,
        )

        groups[key].append(sample)

    selected = []

    for key in sorted(groups):
        group = sorted(
            groups[key],
            key=audit_sort_key,
        )

        for sample in group[
            :samples_per_group
        ]:
            selected.append(
                sample.model_dump(
                    mode="json"
                )
            )

    return selected


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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Analyze TripPamine conversational "
            "SFT datasets."
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
        "--output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--audit-output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--samples-per-group",
        type=int,
        default=2,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    training = load_dataset(
        args.training
    )

    validation = load_dataset(
        args.validation
    )

    analyzer = (
        ConversationSFTStatisticsAnalyzer()
    )

    training_stats = analyzer.analyze(
        training
    )

    validation_stats = analyzer.analyze(
        validation
    )

    report = {
        "training": training_stats.model_dump(
            mode="json"
        ),
        "validation": (
            validation_stats.model_dump(
                mode="json"
            )
        ),
    }

    audit = {
        "training": select_audit_samples(
            training,
            args.samples_per_group,
        ),
        "validation": select_audit_samples(
            validation,
            args.samples_per_group,
        ),
    }

    save_json(
        report,
        args.output,
    )

    save_json(
        audit,
        args.audit_output,
    )

    print()
    print("Conversation SFT analysis completed")

    print(
        "Training samples: "
        f"{training_stats.total_samples}"
    )

    print(
        "Validation samples: "
        f"{validation_stats.total_samples}"
    )

    print(
        "Training duplicate content hashes: "
        f"{training_stats.duplicate_content_hashes}"
    )

    print(
        "Validation duplicate content hashes: "
        f"{validation_stats.duplicate_content_hashes}"
    )

    print(
        "Training empty completions: "
        f"{training_stats.empty_completion_messages}"
    )

    print(
        "Validation empty completions: "
        f"{validation_stats.empty_completion_messages}"
    )

    print(
        "Training prompt p95 chars: "
        f"{training_stats.prompt_characters.p95}"
    )

    print(
        "Training completion p95 chars: "
        f"{training_stats.completion_characters.p95}"
    )

    print(f"Report: {args.output}")
    print(f"Audit: {args.audit_output}")


if __name__ == "__main__":
    main()