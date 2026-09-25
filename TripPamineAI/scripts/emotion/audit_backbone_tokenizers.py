import argparse
import itertools
import sys
import time
from pathlib import Path
from typing import Any

import numpy as np
import transformers
from transformers import (
    AutoTokenizer,
    PreTrainedTokenizerBase,
)

from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    read_dataset,
    save_report,
    validate_positive_integer,
)
from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)


DEFAULT_BATCH_SIZE = 512
DEFAULT_MAX_LENGTH = 96
DEFAULT_SECONDARY_LENGTH = 128
DEFAULT_EXAMPLE_COUNT = 10


TOKENIZER_SPECS = (
    {
        "key": "koelectra_v3",
        "model": (
            "monologg/"
            "koelectra-base-v3-discriminator"
        ),
        "revision": "main",
    },
    {
        "key": "kcelectra_v2023",
        "model": "beomi/KcELECTRA-base",
        "revision": "main",
    },
    {
        "key": "kcelectra_v2022",
        "model": "beomi/KcELECTRA-base",
        "revision": "v2022",
    },
)


def summarize_lengths(
        lengths: list[int],
) -> dict[str, float | int]:
    if not lengths:
        raise ValueError(
            "Token length list "
            "must not be empty."
        )

    values = np.asarray(
        lengths,
        dtype=np.float64,
    )

    return {
        "samples": len(lengths),
        "minimum": int(
            np.min(values)
        ),
        "mean": float(
            np.mean(values)
        ),
        "p50": float(
            np.percentile(
                values,
                50,
            )
        ),
        "p90": float(
            np.percentile(
                values,
                90,
            )
        ),
        "p95": float(
            np.percentile(
                values,
                95,
            )
        ),
        "p99": float(
            np.percentile(
                values,
                99,
            )
        ),
        "maximum": int(
            np.max(values)
        ),
    }


def build_limit_summary(
        lengths: list[int],
        limit: int,
) -> dict[str, float | int]:
    validate_positive_integer(
        limit,
        "Token length limit",
    )

    if not lengths:
        raise ValueError(
            "Token length list "
            "must not be empty."
        )

    excess = [
        max(
            0,
            length - limit,
        )
        for length
        in lengths
    ]

    affected = [
        value
        for value
        in excess
        if value > 0
    ]

    affected_count = len(
        affected
    )

    return {
        "limit": limit,
        "samples_over_limit": (
            affected_count
        ),
        "share_over_limit": (
            affected_count
            / len(lengths)
        ),
        "total_excess_tokens": int(
            sum(
                affected
            )
        ),
        "mean_excess_tokens": (
            float(
                sum(affected)
                / affected_count
            )
            if affected_count
            else 0.0
        ),
        "maximum_excess_tokens": (
            max(
                affected
            )
            if affected
            else 0
        ),
    }


def build_pairwise_comparison(
        lengths_a: list[int],
        lengths_b: list[int],
        key_a: str,
        key_b: str,
) -> dict[str, Any]:
    if (
            len(lengths_a)
            != len(lengths_b)
    ):
        raise ValueError(
            "Pairwise tokenizer "
            "lengths must match."
        )

    if not lengths_a:
        raise ValueError(
            "Pairwise tokenizer "
            "lengths must not be empty."
        )

    a_shorter = 0
    b_shorter = 0
    equal = 0

    differences = []

    for (
            length_a,
            length_b,
    ) in zip(
            lengths_a,
            lengths_b,
            strict=True,
    ):
        differences.append(
            length_a
            - length_b
        )

        if (
                length_a
                < length_b
        ):
            a_shorter += 1

        elif (
                length_b
                < length_a
        ):
            b_shorter += 1

        else:
            equal += 1

    return {
        "a": key_a,
        "b": key_b,
        "a_shorter": a_shorter,
        "b_shorter": b_shorter,
        "equal": equal,
        "a_shorter_share": (
            a_shorter
            / len(lengths_a)
        ),
        "b_shorter_share": (
            b_shorter
            / len(lengths_a)
        ),
        "equal_share": (
            equal
            / len(lengths_a)
        ),
        "mean_a_minus_b_tokens": (
            float(
                np.mean(
                    np.asarray(
                        differences,
                        dtype=np.float64,
                    )
                )
            )
        ),
    }


def build_shortest_summary(
        lengths_by_key: dict[
            str,
            list[int],
        ],
) -> dict[str, Any]:
    if not lengths_by_key:
        raise ValueError(
            "Tokenizer length mapping "
            "must not be empty."
        )

    lengths = list(
        lengths_by_key.values()
    )

    sample_count = len(
        lengths[
            0
        ]
    )

    if sample_count == 0:
        raise ValueError(
            "Tokenizer length mapping "
            "must contain samples."
        )

    if any(
            len(values)
            != sample_count
            for values
            in lengths
    ):
        raise ValueError(
            "Tokenizer length arrays "
            "must have equal size."
        )

    unique_shortest = {
        key: 0
        for key
        in lengths_by_key
    }

    tied_shortest = 0
    all_equal = 0

    for index in range(
            sample_count
    ):
        values = {
            key: model_lengths[
                index
            ]
            for key, model_lengths
            in lengths_by_key.items()
        }

        minimum = min(
            values.values()
        )

        winners = [
            key
            for key, value
            in values.items()
            if value == minimum
        ]

        if (
                len(winners)
                == 1
        ):
            unique_shortest[
                winners[
                    0
                ]
            ] += 1

        else:
            tied_shortest += 1

            if (
                    len(winners)
                    == len(values)
            ):
                all_equal += 1

    return {
        "unique_shortest": (
            unique_shortest
        ),
        "tied_shortest": (
            tied_shortest
        ),
        "all_equal": all_equal,
    }


def load_tokenizer(
        model_name: str,
        revision: str,
) -> PreTrainedTokenizerBase:
    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            model_name,
            revision=revision,
        )
    )

    if (
            tokenizer.model_max_length
            <= 0
    ):
        raise RuntimeError(
            "Tokenizer has invalid "
            "model_max_length: "
            f"{model_name}@{revision}"
        )

    return tokenizer


def build_tokenizer_metadata(
        tokenizer: PreTrainedTokenizerBase,
        model_name: str,
        revision: str,
) -> dict[str, Any]:
    init_kwargs = getattr(
        tokenizer,
        "init_kwargs",
        {},
    )

    return {
        "model": model_name,
        "requested_revision": (
            revision
        ),
        "resolved_commit_hash": (
            init_kwargs.get(
                "_commit_hash"
            )
        ),
        "tokenizer_class": (
            type(
                tokenizer
            ).__name__
        ),
        "vocab_size": len(
            tokenizer
        ),
        "model_max_length": (
            tokenizer
            .model_max_length
        ),
        "special_tokens": {
            "unk_token": (
                tokenizer.unk_token
            ),
            "unk_token_id": (
                tokenizer.unk_token_id
            ),
            "pad_token": (
                tokenizer.pad_token
            ),
            "pad_token_id": (
                tokenizer.pad_token_id
            ),
            "cls_token": (
                tokenizer.cls_token
            ),
            "cls_token_id": (
                tokenizer.cls_token_id
            ),
            "sep_token": (
                tokenizer.sep_token
            ),
            "sep_token_id": (
                tokenizer.sep_token_id
            ),
        },
        "special_tokens_per_single_input": (
            tokenizer
            .num_special_tokens_to_add(
                pair=False
            )
        ),
    }


def audit_tokenizer(
        tokenizer: PreTrainedTokenizerBase,
        samples: list[
            EmotionClassificationSample
        ],
        batch_size: int,
        max_length: int,
        secondary_length: int,
) -> tuple[
    dict[str, Any],
    list[int],
    list[int],
]:
    validate_positive_integer(
        batch_size,
        "Tokenizer batch size",
    )

    content_lengths: list[int] = []
    model_input_lengths: list[int] = []

    unknown_token_count = 0
    samples_with_unknown = 0
    unknown_per_sample: list[int] = []

    token_character_ratios: list[
        float
    ] = []

    token_segment_ratios: list[
        float
    ] = []

    unk_token_id = (
        tokenizer.unk_token_id
    )

    for start in range(
            0,
            len(samples),
            batch_size,
    ):
        batch = samples[
            start:
            start + batch_size
        ]

        texts = [
            sample.text
            for sample
            in batch
        ]

        content = tokenizer(
            texts,
            add_special_tokens=False,
            truncation=False,
            padding=False,
            return_attention_mask=False,
            return_token_type_ids=False,
        )

        model_inputs = tokenizer(
            texts,
            add_special_tokens=True,
            truncation=False,
            padding=False,
            return_attention_mask=False,
            return_token_type_ids=False,
        )

        content_ids = (
            content[
                "input_ids"
            ]
        )

        model_input_ids = (
            model_inputs[
                "input_ids"
            ]
        )

        if not (
                len(content_ids)
                == len(model_input_ids)
                == len(batch)
        ):
            raise RuntimeError(
                "Tokenizer batch output "
                "size mismatch."
            )

        for (
                sample,
                sample_content_ids,
                sample_model_ids,
        ) in zip(
                batch,
                content_ids,
                model_input_ids,
                strict=True,
        ):
            content_length = len(
                sample_content_ids
            )

            model_input_length = len(
                sample_model_ids
            )

            content_lengths.append(
                content_length
            )

            model_input_lengths.append(
                model_input_length
            )

            if (
                    unk_token_id
                    is None
            ):
                unknown_count = 0
            else:
                unknown_count = (
                    sample_content_ids
                    .count(
                        unk_token_id
                    )
                )

            unknown_per_sample.append(
                unknown_count
            )

            unknown_token_count += (
                unknown_count
            )

            if unknown_count > 0:
                samples_with_unknown += 1

            character_count = max(
                1,
                len(
                    sample.text
                ),
            )

            segment_count = max(
                1,
                len(
                    sample.text.split()
                ),
            )

            token_character_ratios.append(
                content_length
                / character_count
            )

            token_segment_ratios.append(
                content_length
                / segment_count
            )

    if (
            len(content_lengths)
            != len(samples)
    ):
        raise RuntimeError(
            "Tokenizer audit did not "
            "process every sample."
        )

    return (
        {
            "content_tokens": (
                summarize_lengths(
                    content_lengths
                )
            ),
            "model_input_tokens": (
                summarize_lengths(
                    model_input_lengths
                )
            ),
            "length_limits": {
                str(
                    max_length
                ): (
                    build_limit_summary(
                        model_input_lengths,
                        max_length,
                    )
                ),
                str(
                    secondary_length
                ): (
                    build_limit_summary(
                        model_input_lengths,
                        secondary_length,
                    )
                ),
            },
            "unknown_tokens": {
                "total": (
                    unknown_token_count
                ),
                "samples_with_unknown": (
                    samples_with_unknown
                ),
                "sample_share": (
                    samples_with_unknown
                    / len(samples)
                ),
                "maximum_per_sample": (
                    max(
                        unknown_per_sample
                    )
                ),
            },
            "fragmentation": {
                "mean_tokens_per_character": (
                    float(
                        np.mean(
                            np.asarray(
                                token_character_ratios,
                                dtype=np.float64,
                            )
                        )
                    )
                ),
                "mean_tokens_per_whitespace_segment": (
                    float(
                        np.mean(
                            np.asarray(
                                token_segment_ratios,
                                dtype=np.float64,
                            )
                        )
                    )
                ),
            },
        },
        content_lengths,
        model_input_lengths,
    )


def build_spread_examples(
        samples: list[
            EmotionClassificationSample
        ],
        lengths_by_key: dict[
            str,
            list[int],
        ],
        example_count: int,
) -> list[
    dict[str, Any]
]:
    validate_positive_integer(
        example_count,
        "Example count",
    )

    sample_count = len(
        samples
    )

    if any(
            len(lengths)
            != sample_count
            for lengths
            in lengths_by_key.values()
    ):
        raise ValueError(
            "Tokenizer length arrays "
            "must match sample count."
        )

    indices = sorted(
        range(
            sample_count
        ),
        key=lambda index: (
            -(
                max(
                    lengths[
                        index
                    ]
                    for lengths
                    in lengths_by_key.values()
                )
                -
                min(
                    lengths[
                        index
                    ]
                    for lengths
                    in lengths_by_key.values()
                )
            ),
            samples[
                index
            ].id,
        ),
    )[
        :example_count
    ]

    return [
        {
            "sample_id": (
                samples[
                    index
                ].id
            ),
            "split": (
                samples[
                    index
                ].source.split
            ),
            "label": (
                samples[
                    index
                ].label
            ),
            "label_name": (
                samples[
                    index
                ].label_name
            ),
            "characters": len(
                samples[
                    index
                ].text
            ),
            "text": (
                samples[
                    index
                ].text
            ),
            "model_input_tokens": {
                key: lengths[
                    index
                ]
                for key, lengths
                in lengths_by_key.items()
            },
            "spread": (
                max(
                    lengths[
                        index
                    ]
                    for lengths
                    in lengths_by_key.values()
                )
                -
                min(
                    lengths[
                        index
                    ]
                    for lengths
                    in lengths_by_key.values()
                )
            ),
        }
        for index
        in indices
    ]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Audit tokenizer efficiency "
            "for TripPamine emotion "
            "backbone candidates."
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
        "--batch-size",
        type=int,
        default=DEFAULT_BATCH_SIZE,
    )

    parser.add_argument(
        "--max-length",
        type=int,
        default=DEFAULT_MAX_LENGTH,
    )

    parser.add_argument(
        "--secondary-length",
        type=int,
        default=(
            DEFAULT_SECONDARY_LENGTH
        ),
    )

    parser.add_argument(
        "--examples",
        type=int,
        default=DEFAULT_EXAMPLE_COUNT,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    validate_positive_integer(
        args.batch_size,
        "Tokenizer batch size",
    )

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.secondary_length,
        "Secondary token length",
    )

    validate_positive_integer(
        args.examples,
        "Example count",
    )

    training = read_dataset(
        args.training,
        "training",
    )

    validation = read_dataset(
        args.validation,
        "validation",
    )

    samples = (
        training
        + validation
    )

    if not samples:
        raise RuntimeError(
            "Tokenizer audit dataset "
            "is empty."
        )

    audits: dict[
        str,
        dict[str, Any],
    ] = {}

    content_lengths_by_key: dict[
        str,
        list[int],
    ] = {}

    model_lengths_by_key: dict[
        str,
        list[int],
    ] = {}

    total_started_at = (
        time.perf_counter()
    )

    for spec in TOKENIZER_SPECS:
        key = spec[
            "key"
        ]

        print()
        print(
            "Loading tokenizer: "
            f"{key}"
        )

        tokenizer = load_tokenizer(
            model_name=(
                spec[
                    "model"
                ]
            ),
            revision=(
                spec[
                    "revision"
                ]
            ),
        )

        started_at = (
            time.perf_counter()
        )

        (
            audit,
            content_lengths,
            model_input_lengths,
        ) = audit_tokenizer(
            tokenizer=tokenizer,
            samples=samples,
            batch_size=(
                args.batch_size
            ),
            max_length=(
                args.max_length
            ),
            secondary_length=(
                args.secondary_length
            ),
        )

        elapsed = (
            time.perf_counter()
            - started_at
        )

        audits[
            key
        ] = {
            "metadata": (
                build_tokenizer_metadata(
                    tokenizer=tokenizer,
                    model_name=(
                        spec[
                            "model"
                        ]
                    ),
                    revision=(
                        spec[
                            "revision"
                        ]
                    ),
                )
            ),
            "audit": audit,
            "timing": {
                "seconds": elapsed,
                "samples_per_second": (
                    len(samples)
                    / elapsed
                ),
            },
        }

        content_lengths_by_key[
            key
        ] = (
            content_lengths
        )

        model_lengths_by_key[
            key
        ] = (
            model_input_lengths
        )

    pairwise = []

    for (
            key_a,
            key_b,
    ) in itertools.combinations(
            model_lengths_by_key.keys(),
            2,
    ):
        pairwise.append(
            build_pairwise_comparison(
                lengths_a=(
                    model_lengths_by_key[
                        key_a
                    ]
                ),
                lengths_b=(
                    model_lengths_by_key[
                        key_b
                    ]
                ),
                key_a=key_a,
                key_b=key_b,
            )
        )

    shortest_summary = (
        build_shortest_summary(
            model_lengths_by_key
        )
    )

    spread_examples = (
        build_spread_examples(
            samples=samples,
            lengths_by_key=(
                model_lengths_by_key
            ),
            example_count=(
                args.examples
            ),
        )
    )

    total_seconds = (
        time.perf_counter()
        - total_started_at
    )

    report = {
        "experiment": (
            "T0-Q14-0A-"
            "backbone-tokenizer-audit"
        ),
        "versions": {
            "python": sys.version,
            "transformers": (
                transformers.__version__
            ),
            "numpy": (
                np.__version__
            ),
        },
        "dataset": {
            "training": {
                "file": str(
                    args.training
                ),
                "samples": len(
                    training
                ),
                "sha256": (
                    calculate_sha256(
                        args.training
                    )
                ),
            },
            "validation": {
                "file": str(
                    args.validation
                ),
                "samples": len(
                    validation
                ),
                "sha256": (
                    calculate_sha256(
                        args.validation
                    )
                ),
            },
            "combined_samples": len(
                samples
            ),
            "test_split_used": False,
        },
        "protocol": {
            "batch_size": (
                args.batch_size
            ),
            "primary_max_length": (
                args.max_length
            ),
            "secondary_length": (
                args.secondary_length
            ),
            "tokenizer_specs": list(
                TOKENIZER_SPECS
            ),
        },
        "tokenizers": audits,
        "comparison": {
            "shortest": (
                shortest_summary
            ),
            "pairwise": pairwise,
            "largest_length_spread_examples": (
                spread_examples
            ),
        },
        "privacy": {
            "contains_raw_dataset_text": (
                True
            ),
            "raw_text_scope": (
                "largest_length_spread_examples"
            ),
        },
        "timing": {
            "total_seconds": (
                total_seconds
            ),
        },
    }

    save_report(
        report=report,
        output_path=(
            args.output
        ),
    )

    print()
    print(
        "Backbone tokenizer audit "
        "completed"
    )

    print()
    print(
        "Training samples: "
        f"{len(training)}"
    )

    print(
        "Validation samples: "
        f"{len(validation)}"
    )

    print(
        "Combined samples: "
        f"{len(samples)}"
    )

    print(
        "Test split used: False"
    )

    for key in (
            model_lengths_by_key
    ):
        audit = (
            audits[
                key
            ][
                "audit"
            ]
        )

        model_tokens = (
            audit[
                "model_input_tokens"
            ]
        )

        primary_limit = (
            audit[
                "length_limits"
            ][
                str(
                    args.max_length
                )
            ]
        )

        secondary_limit = (
            audit[
                "length_limits"
            ][
                str(
                    args.secondary_length
                )
            ]
        )

        unknown = (
            audit[
                "unknown_tokens"
            ]
        )

        print()
        print(
            f"[{key}]"
        )

        print(
            "  mean="
            f"{model_tokens['mean']:.3f}, "
            "p95="
            f"{model_tokens['p95']:.1f}, "
            "p99="
            f"{model_tokens['p99']:.1f}, "
            "max="
            f"{model_tokens['maximum']}"
        )

        print(
            "  >"
            f"{args.max_length}="
            f"{primary_limit['samples_over_limit']} "
            f"("
            f"{primary_limit['share_over_limit']:.2%}"
            f")"
        )

        print(
            "  >"
            f"{args.secondary_length}="
            f"{secondary_limit['samples_over_limit']} "
            f"("
            f"{secondary_limit['share_over_limit']:.2%}"
            f")"
        )

        print(
            "  UNK samples="
            f"{unknown['samples_with_unknown']} "
            f"("
            f"{unknown['sample_share']:.2%}"
            f")"
        )

    print()
    print(
        "Unique shortest counts"
    )

    for (
            key,
            count,
    ) in (
            shortest_summary[
                "unique_shortest"
            ].items()
    ):
        print(
            f"  {key}: {count}"
        )

    print(
        "  ties: "
        f"{shortest_summary['tied_shortest']}"
    )

    print()
    print(
        "Pairwise comparisons"
    )

    for comparison in pairwise:
        print(
            "  "
            f"{comparison['a']} vs "
            f"{comparison['b']}: "
            f"A shorter="
            f"{comparison['a_shorter']}, "
            f"B shorter="
            f"{comparison['b_shorter']}, "
            f"equal="
            f"{comparison['equal']}, "
            f"mean A-B="
            f"{comparison['mean_a_minus_b_tokens']:.3f}"
        )

    print()
    print(
        "Total seconds: "
        f"{total_seconds:.2f}"
    )

    print(
        "Report: "
        f"{args.output}"
    )

    print()
    print(
        "Q14-0A TOKENIZER AUDIT: PASS"
    )


if __name__ == "__main__":
    main()