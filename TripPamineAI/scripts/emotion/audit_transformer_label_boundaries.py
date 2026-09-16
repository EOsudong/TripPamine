import argparse
import sys
import time
from pathlib import Path
from typing import Any

import torch
import transformers
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
)

from trippamine_ai.datasets.emotion.classification import (
    EmotionClassificationSample,
)
from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)

if __package__:
    from .analyze_transformer_classifier import (
        calculate_sha256,
        predict_validation,
        read_validation_dataset,
        save_report,
        validate_model_config,
        validate_positive_integer,
        validate_validation_samples,
    )
else:
    from analyze_transformer_classifier import (
        calculate_sha256,
        predict_validation,
        read_validation_dataset,
        save_report,
        validate_model_config,
        validate_positive_integer,
        validate_validation_samples,
    )


DEFAULT_MAX_LENGTH = 96

DEFAULT_EVAL_BATCH_SIZE = 64

DEFAULT_EXAMPLES_PER_BUCKET = 15

DEFAULT_AUDIT_PAIRS = (
    ("E34", "E59"),
    ("E43", "E51"),
    ("E10", "E18"),
    ("E65", "E68"),
    ("E55", "E56"),
    ("E41", "E54"),
    ("E20", "E49"),
    ("E11", "E13"),
)


def validate_audit_pairs(
        audit_pairs: tuple[
            tuple[str, str],
            ...,
        ],
        label_mapping: EmotionLabelMapping,
) -> None:
    if not audit_pairs:
        raise ValueError(
            "At least one audit pair "
            "is required."
        )

    seen_pairs: set[
        tuple[str, str]
    ] = set()

    for (
            label_a,
            label_b,
    ) in audit_pairs:
        if label_a == label_b:
            raise ValueError(
                "Audit pair labels must "
                "be different."
            )

        label_mapping.encode(
            label_a
        )

        label_mapping.encode(
            label_b
        )

        canonical_pair = tuple(
            sorted(
                (
                    label_a,
                    label_b,
                )
            )
        )

        if canonical_pair in seen_pairs:
            raise ValueError(
                "Duplicate audit pair: "
                f"{label_a}, {label_b}"
            )

        seen_pairs.add(
            canonical_pair
        )


def build_audit_record(
        sample: EmotionClassificationSample,
        predicted_label: str,
        confidence: float,
) -> dict[str, Any]:
    return {
        "sample_id": sample.id,
        "text": sample.text,
        "true": {
            "label": sample.label,
            "label_name": (
                EMOTION_CODEBOOK[
                    sample.label
                ]
            ),
            "coarse_label": (
                get_coarse_emotion(
                    sample.label
                )
            ),
        },
        "prediction": {
            "label": predicted_label,
            "label_name": (
                EMOTION_CODEBOOK[
                    predicted_label
                ]
            ),
            "coarse_label": (
                get_coarse_emotion(
                    predicted_label
                )
            ),
            "confidence": confidence,
        },
        "context": {
            "situation_code": (
                sample.situation_code
            ),
            "situation": (
                sample.situation
            ),
            "quality_status": (
                sample.quality_status
            ),
            "quality_issue_codes": (
                sample.quality_issue_codes
            ),
        },
        "source": {
            "dataset": (
                sample.source.dataset
            ),
            "version": (
                sample.source.version
            ),
            "split": (
                sample.source.split
            ),
        },
    }


def sort_and_limit_records(
        records: list[
            dict[str, Any]
        ],
        limit: int,
) -> list[
    dict[str, Any]
]:
    return sorted(
        records,
        key=lambda record: (
            -record[
                "prediction"
            ][
                "confidence"
            ],
            record[
                "sample_id"
            ],
        ),
    )[:limit]


def build_pair_audit(
        samples: list[
            EmotionClassificationSample
        ],
        predicted_labels: list[str],
        confidences: list[float],
        label_a: str,
        label_b: str,
        examples_per_bucket: int,
) -> dict[str, Any]:
    if not (
            len(samples)
            == len(predicted_labels)
            == len(confidences)
    ):
        raise ValueError(
            "Audit input lengths "
            "must match."
        )

    validate_positive_integer(
        examples_per_bucket,
        "Examples per bucket",
    )

    buckets: dict[
        str,
        list[
            dict[str, Any]
        ],
    ] = {
        "a_correct": [],
        "a_to_b": [],
        "b_correct": [],
        "b_to_a": [],
    }

    counts = {
        "a_support": 0,
        "b_support": 0,
        "a_correct": 0,
        "a_to_b": 0,
        "a_other_errors": 0,
        "b_correct": 0,
        "b_to_a": 0,
        "b_other_errors": 0,
    }

    for (
            sample,
            predicted_label,
            confidence,
    ) in zip(
            samples,
            predicted_labels,
            confidences,
            strict=True,
    ):
        if sample.label == label_a:
            counts[
                "a_support"
            ] += 1

            if predicted_label == label_a:
                bucket_name = (
                    "a_correct"
                )

            elif predicted_label == label_b:
                bucket_name = (
                    "a_to_b"
                )

            else:
                counts[
                    "a_other_errors"
                ] += 1

                continue

        elif sample.label == label_b:
            counts[
                "b_support"
            ] += 1

            if predicted_label == label_b:
                bucket_name = (
                    "b_correct"
                )

            elif predicted_label == label_a:
                bucket_name = (
                    "b_to_a"
                )

            else:
                counts[
                    "b_other_errors"
                ] += 1

                continue

        else:
            continue

        counts[
            bucket_name
        ] += 1

        buckets[
            bucket_name
        ].append(
            build_audit_record(
                sample=sample,
                predicted_label=(
                    predicted_label
                ),
                confidence=confidence,
            )
        )

    selected = {
        bucket_name: (
            sort_and_limit_records(
                records=records,
                limit=(
                    examples_per_bucket
                ),
            )
        )
        for bucket_name, records
        in buckets.items()
    }

    selected_counts = {
        bucket_name: len(
            records
        )
        for bucket_name, records
        in selected.items()
    }

    return {
        "pair": {
            "label_a": label_a,
            "label_a_name": (
                EMOTION_CODEBOOK[
                    label_a
                ]
            ),
            "coarse_a": (
                get_coarse_emotion(
                    label_a
                )
            ),
            "label_b": label_b,
            "label_b_name": (
                EMOTION_CODEBOOK[
                    label_b
                ]
            ),
            "coarse_b": (
                get_coarse_emotion(
                    label_b
                )
            ),
        },
        "counts": counts,
        "selected_counts": (
            selected_counts
        ),
        "examples": selected,
    }


def build_label_boundary_audit(
        samples: list[
            EmotionClassificationSample
        ],
        predicted_labels: list[str],
        confidences: list[float],
        label_mapping: EmotionLabelMapping,
        audit_pairs: tuple[
            tuple[str, str],
            ...,
        ] = DEFAULT_AUDIT_PAIRS,
        examples_per_bucket: int = (
            DEFAULT_EXAMPLES_PER_BUCKET
        ),
) -> list[
    dict[str, Any]
]:
    if not (
            len(samples)
            == len(predicted_labels)
            == len(confidences)
    ):
        raise ValueError(
            "Audit input lengths "
            "must match."
        )

    validate_positive_integer(
        examples_per_bucket,
        "Examples per bucket",
    )

    validate_audit_pairs(
        audit_pairs=(
            audit_pairs
        ),
        label_mapping=(
            label_mapping
        ),
    )

    return [
        build_pair_audit(
            samples=samples,
            predicted_labels=(
                predicted_labels
            ),
            confidences=confidences,
            label_a=label_a,
            label_b=label_b,
            examples_per_bucket=(
                examples_per_bucket
            ),
        )
        for (
                label_a,
                label_b,
        ) in audit_pairs
    ]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Export deterministic "
            "validation examples for "
            "TripPamine emotion label "
            "boundary auditing."
        )
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--model-dir",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--max-length",
        type=int,
        default=DEFAULT_MAX_LENGTH,
    )

    parser.add_argument(
        "--eval-batch-size",
        type=int,
        default=DEFAULT_EVAL_BATCH_SIZE,
    )

    parser.add_argument(
        "--examples-per-bucket",
        type=int,
        default=(
            DEFAULT_EXAMPLES_PER_BUCKET
        ),
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output.exists():
        raise FileExistsError(
            "Audit report already "
            "exists: "
            f"{args.output}"
        )

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.eval_batch_size,
        "Evaluation batch size",
    )

    validate_positive_integer(
        args.examples_per_bucket,
        "Examples per bucket",
    )

    if not args.model_dir.exists():
        raise FileNotFoundError(
            "Model directory not found: "
            f"{args.model_dir}"
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. "
            "Label boundary audit "
            "cannot run."
        )

    device_index = (
        torch.cuda.current_device()
    )

    device = torch.device(
        "cuda",
        device_index,
    )

    label_mapping = (
        EmotionLabelMapping()
    )

    validation = (
        read_validation_dataset(
            args.validation
        )
    )

    validate_validation_samples(
        samples=validation,
        label_mapping=label_mapping,
    )

    validate_audit_pairs(
        audit_pairs=(
            DEFAULT_AUDIT_PAIRS
        ),
        label_mapping=label_mapping,
    )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model_dir,
            local_files_only=True,
        )
    )

    model = (
        AutoModelForSequenceClassification
        .from_pretrained(
            args.model_dir,
            local_files_only=True,
        )
    )

    validate_model_config(
        model.config,
        label_mapping,
    )

    model.float()

    model.to(
        device
    )

    model_device = (
        next(
            model.parameters()
        )
        .device
    )

    if model_device.type != "cuda":
        raise RuntimeError(
            "Audit model is not on "
            "a CUDA device: "
            f"{model_device}"
        )

    torch.cuda.empty_cache()

    torch.cuda.synchronize(
        device_index
    )

    started_at = (
        time.perf_counter()
    )

    (
        true_labels,
        predicted_labels,
        confidences,
    ) = predict_validation(
        model=model,
        tokenizer=tokenizer,
        samples=validation,
        label_mapping=label_mapping,
        max_length=(
            args.max_length
        ),
        eval_batch_size=(
            args.eval_batch_size
        ),
        device=device,
    )

    if true_labels != [
        sample.label
        for sample in validation
    ]:
        raise RuntimeError(
            "Prediction order does not "
            "match validation samples."
        )

    torch.cuda.synchronize(
        device_index
    )

    inference_seconds = (
        time.perf_counter()
        - started_at
    )

    pair_audits = (
        build_label_boundary_audit(
            samples=validation,
            predicted_labels=(
                predicted_labels
            ),
            confidences=(
                confidences
            ),
            label_mapping=(
                label_mapping
            ),
            audit_pairs=(
                DEFAULT_AUDIT_PAIRS
            ),
            examples_per_bucket=(
                args.examples_per_bucket
            ),
        )
    )

    report = {
        "experiment": (
            "T0-Q8-A-"
            "label-boundary-audit"
        ),
        "versions": {
            "python": sys.version,
            "torch": (
                torch.__version__
            ),
            "transformers": (
                transformers.__version__
            ),
            "cuda_runtime": (
                torch.version.cuda
            ),
        },
        "source": {
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
            "model": {
                "directory": str(
                    args.model_dir
                ),
            },
            "test_split_used": False,
        },
        "protocol": {
            "max_length": (
                args.max_length
            ),
            "eval_batch_size": (
                args.eval_batch_size
            ),
            "precision": "fp32",
            "padding": "dynamic",
            "examples_per_bucket": (
                args.examples_per_bucket
            ),
            "selection": (
                "top1_confidence_desc_"
                "then_sample_id"
            ),
            "audit_pairs": [
                list(
                    pair
                )
                for pair
                in DEFAULT_AUDIT_PAIRS
            ],
        },
        "privacy": {
            "contains_raw_validation_text": (
                True
            ),
            "profile_id_included": False,
            "talk_id_included": False,
        },
        "pairs": pair_audits,
        "timing": {
            "inference_seconds": (
                inference_seconds
            ),
            "samples_per_second": (
                len(validation)
                / inference_seconds
            ),
        },
    }

    save_report(
        report=report,
        output_path=args.output,
    )

    print()
    print(
        "KoELECTRA label boundary "
        "audit export completed"
    )

    print()
    print(
        "Validation samples: "
        f"{len(validation)}"
    )

    print(
        "Test split used: False"
    )

    print(
        "Audit pairs: "
        f"{len(pair_audits)}"
    )

    print(
        "Examples per bucket: "
        f"{args.examples_per_bucket}"
    )

    print()

    for pair_audit in pair_audits:
        pair = (
            pair_audit[
                "pair"
            ]
        )

        counts = (
            pair_audit[
                "counts"
            ]
        )

        print(
            f"{pair['label_a']} "
            f"{pair['label_a_name']} "
            "<-> "
            f"{pair['label_b']} "
            f"{pair['label_b_name']}"
        )

        print(
            "  "
            f"A correct={counts['a_correct']}, "
            f"A->B={counts['a_to_b']}, "
            f"A other="
            f"{counts['a_other_errors']}"
        )

        print(
            "  "
            f"B correct={counts['b_correct']}, "
            f"B->A={counts['b_to_a']}, "
            f"B other="
            f"{counts['b_other_errors']}"
        )

    print()
    print(
        "Inference seconds: "
        f"{inference_seconds:.2f}"
    )

    print(
        "Report: "
        f"{args.output}"
    )

    print()
    print(
        "LABEL BOUNDARY AUDIT: PASS"
    )


if __name__ == "__main__":
    main()