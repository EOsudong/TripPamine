import argparse
import math
import sys
import time
from pathlib import Path
from typing import Any

import torch
import transformers
from torch.utils.data import DataLoader
from tqdm import tqdm
from transformers import (
    AutoModel,
    AutoTokenizer,
    DataCollatorWithPadding,
)

from scripts.emotion.train_transformer_classifier import (
    bytes_to_gib,
    calculate_sha256,
    read_dataset,
    save_report,
    validate_label_sets,
    validate_positive_integer,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.transformer_dataset import (
    TransformerEmotionDataset,
)


DEFAULT_MAX_LENGTH = 96
DEFAULT_BATCH_SIZE = 64

EXPECTED_MODEL_TYPE = "electra"
EXPECTED_HIDDEN_LAYERS = 12
EXPECTED_HIDDEN_SIZE = 768
EXPECTED_INTERMEDIATE_SIZE = 3072
EXPECTED_ATTENTION_HEADS = 12
EXPECTED_SPECIAL_TOKENS = 2


def validate_backbone_contract(
        tokenizer: Any,
        model: torch.nn.Module,
) -> dict[str, Any]:
    config = model.config

    checks = {
        "model_type": (
            config.model_type,
            EXPECTED_MODEL_TYPE,
        ),
        "num_hidden_layers": (
            config.num_hidden_layers,
            EXPECTED_HIDDEN_LAYERS,
        ),
        "hidden_size": (
            config.hidden_size,
            EXPECTED_HIDDEN_SIZE,
        ),
        "intermediate_size": (
            config.intermediate_size,
            EXPECTED_INTERMEDIATE_SIZE,
        ),
        "num_attention_heads": (
            config.num_attention_heads,
            EXPECTED_ATTENTION_HEADS,
        ),
    }

    for (
            name,
            (
                actual,
                expected,
            ),
    ) in checks.items():
        if actual != expected:
            raise ValueError(
                "Backbone contract "
                f"mismatch for {name}: "
                f"expected {expected}, "
                f"found {actual}."
            )

    tokenizer_vocab_size = len(
        tokenizer
    )

    config_vocab_size = int(
        config.vocab_size
    )

    embedding = (
        model
        .get_input_embeddings()
    )

    if embedding is None:
        raise RuntimeError(
            "Backbone does not expose "
            "input embeddings."
        )

    embedding_vocab_size = int(
        embedding.weight.shape[0]
    )

    if not (
            tokenizer_vocab_size
            == config_vocab_size
            == embedding_vocab_size
    ):
        raise ValueError(
            "Tokenizer/config/embedding "
            "vocabulary sizes differ: "
            f"tokenizer="
            f"{tokenizer_vocab_size}, "
            f"config="
            f"{config_vocab_size}, "
            f"embedding="
            f"{embedding_vocab_size}."
        )

    special_token_count = (
        tokenizer
        .num_special_tokens_to_add(
            pair=False
        )
    )

    if (
            special_token_count
            != EXPECTED_SPECIAL_TOKENS
    ):
        raise ValueError(
            "Single-input tokenizer "
            "must add exactly "
            f"{EXPECTED_SPECIAL_TOKENS} "
            "special tokens, found "
            f"{special_token_count}."
        )

    return {
        "model_type": (
            config.model_type
        ),
        "num_hidden_layers": (
            config.num_hidden_layers
        ),
        "hidden_size": (
            config.hidden_size
        ),
        "intermediate_size": (
            config.intermediate_size
        ),
        "num_attention_heads": (
            config.num_attention_heads
        ),
        "tokenizer_vocab_size": (
            tokenizer_vocab_size
        ),
        "config_vocab_size": (
            config_vocab_size
        ),
        "embedding_vocab_size": (
            embedding_vocab_size
        ),
        "special_tokens_per_input": (
            special_token_count
        ),
    }


def validate_special_token_contract(
        tokenizer: Any,
        text: str,
) -> dict[str, Any]:
    if not text.strip():
        raise ValueError(
            "Special-token validation "
            "text must not be empty."
        )

    if (
            tokenizer.cls_token_id
            is None
            or tokenizer.sep_token_id
            is None
    ):
        raise ValueError(
            "Tokenizer must define "
            "CLS and SEP token IDs."
        )

    encoded = tokenizer(
        text,
        add_special_tokens=True,
        truncation=False,
        padding=False,
    )

    input_ids = encoded[
        "input_ids"
    ]

    if len(input_ids) < 2:
        raise ValueError(
            "Tokenized sample is too "
            "short for CLS/SEP."
        )

    if (
            input_ids[0]
            != tokenizer.cls_token_id
    ):
        raise ValueError(
            "First token is not CLS: "
            f"expected "
            f"{tokenizer.cls_token_id}, "
            f"found {input_ids[0]}."
        )

    if (
            input_ids[-1]
            != tokenizer.sep_token_id
    ):
        raise ValueError(
            "Last token is not SEP: "
            f"expected "
            f"{tokenizer.sep_token_id}, "
            f"found {input_ids[-1]}."
        )

    return {
        "first_token_id": (
            input_ids[0]
        ),
        "last_token_id": (
            input_ids[-1]
        ),
        "cls_token_id": (
            tokenizer.cls_token_id
        ),
        "sep_token_id": (
            tokenizer.sep_token_id
        ),
        "input_length": len(
            input_ids
        ),
    }


def freeze_backbone(
        model: torch.nn.Module,
) -> None:
    model.eval()

    for parameter in (
            model.parameters()
    ):
        parameter.requires_grad_(
            False
        )

    trainable_parameters = [
        name
        for name, parameter
        in model.named_parameters()
        if parameter.requires_grad
    ]

    if trainable_parameters:
        raise RuntimeError(
            "Frozen backbone still "
            "contains trainable "
            "parameters: "
            f"{trainable_parameters[:5]}"
        )


def validate_feature_tensors(
        features: torch.Tensor,
        labels: torch.Tensor,
        expected_samples: int,
        expected_hidden_size: int,
        num_labels: int,
) -> dict[str, Any]:
    if features.ndim != 2:
        raise ValueError(
            "Features must be a "
            "2-dimensional tensor."
        )

    if labels.ndim != 1:
        raise ValueError(
            "Labels must be a "
            "1-dimensional tensor."
        )

    if (
            features.shape[0]
            != expected_samples
    ):
        raise ValueError(
            "Feature sample count "
            "mismatch: expected "
            f"{expected_samples}, found "
            f"{features.shape[0]}."
        )

    if (
            labels.shape[0]
            != expected_samples
    ):
        raise ValueError(
            "Label sample count "
            "mismatch: expected "
            f"{expected_samples}, found "
            f"{labels.shape[0]}."
        )

    if (
            features.shape[1]
            != expected_hidden_size
    ):
        raise ValueError(
            "Feature hidden size "
            "mismatch: expected "
            f"{expected_hidden_size}, "
            "found "
            f"{features.shape[1]}."
        )

    if not torch.isfinite(
            features
    ).all():
        raise ValueError(
            "Features contain "
            "non-finite values."
        )

    if expected_samples > 0:
        minimum_label = int(
            labels.min().item()
        )

        maximum_label = int(
            labels.max().item()
        )

        if (
                minimum_label < 0
                or maximum_label
                >= num_labels
        ):
            raise ValueError(
                "Feature labels are "
                "outside the valid "
                "label range."
            )

    return {
        "feature_shape": list(
            features.shape
        ),
        "label_shape": list(
            labels.shape
        ),
        "feature_dtype": str(
            features.dtype
        ),
        "label_dtype": str(
            labels.dtype
        ),
        "finite": True,
    }


def extract_features(
        model: torch.nn.Module,
        dataset: TransformerEmotionDataset,
        data_collator: DataCollatorWithPadding,
        device: torch.device,
        batch_size: int,
        num_labels: int,
        split_name: str,
) -> tuple[
    torch.Tensor,
    torch.Tensor,
    dict[str, Any],
]:
    validate_positive_integer(
        batch_size,
        "Feature extraction batch size",
    )

    if len(dataset) == 0:
        raise ValueError(
            "Feature extraction dataset "
            "must not be empty."
        )

    data_loader = DataLoader(
        dataset,
        batch_size=batch_size,
        shuffle=False,
        collate_fn=data_collator,
        num_workers=0,
        pin_memory=True,
        drop_last=False,
    )

    feature_batches = []
    label_batches = []

    device_index = (
        device.index
        if device.index is not None
        else torch.cuda.current_device()
    )

    torch.cuda.reset_peak_memory_stats(
        device_index
    )

    torch.cuda.synchronize(
        device_index
    )

    started_at = (
        time.perf_counter()
    )

    model.eval()

    with torch.inference_mode():
        for batch in tqdm(
                data_loader,
                desc=(
                    f"Extracting "
                    f"{split_name}"
                ),
                unit="batch",
        ):
            labels = batch.pop(
                "labels"
            )

            model_inputs = {
                key: value.to(
                    device,
                    non_blocking=True,
                )
                for key, value
                in batch.items()
            }

            outputs = model(
                **model_inputs,
                return_dict=True,
            )

            hidden_state = getattr(
                outputs,
                "last_hidden_state",
                None,
            )

            if hidden_state is None:
                raise RuntimeError(
                    "Backbone output does "
                    "not contain "
                    "last_hidden_state."
                )

            if (
                    hidden_state.ndim
                    != 3
            ):
                raise RuntimeError(
                    "Backbone hidden state "
                    "must be "
                    "3-dimensional."
                )

            cls_features = (
                hidden_state[
                    :,
                    0,
                    :,
                ]
                .detach()
                .float()
                .cpu()
            )

            feature_batches.append(
                cls_features
            )

            label_batches.append(
                labels
                .detach()
                .long()
                .cpu()
            )

    torch.cuda.synchronize(
        device_index
    )

    elapsed_seconds = (
        time.perf_counter()
        - started_at
    )

    features = torch.cat(
        feature_batches,
        dim=0,
    )

    labels = torch.cat(
        label_batches,
        dim=0,
    )

    validation = (
        validate_feature_tensors(
            features=features,
            labels=labels,
            expected_samples=len(
                dataset
            ),
            expected_hidden_size=(
                EXPECTED_HIDDEN_SIZE
            ),
            num_labels=num_labels,
        )
    )

    peak_allocated_gib = (
        bytes_to_gib(
            torch.cuda
            .max_memory_allocated(
                device_index
            )
        )
    )

    peak_reserved_gib = (
        bytes_to_gib(
            torch.cuda
            .max_memory_reserved(
                device_index
            )
        )
    )

    return (
        features,
        labels,
        {
            **validation,
            "seconds": (
                elapsed_seconds
            ),
            "samples_per_second": (
                len(dataset)
                / elapsed_seconds
            ),
            "peak_allocated_gib": (
                peak_allocated_gib
            ),
            "peak_reserved_gib": (
                peak_reserved_gib
            ),
        },
    )


def save_feature_file(
        output_path: Path,
        features: torch.Tensor,
        labels: torch.Tensor,
        sample_ids: list[str],
        backbone_key: str,
        model_name: str,
        revision: str,
        split_name: str,
        max_length: int,
) -> dict[str, Any]:
    if output_path.exists():
        raise FileExistsError(
            "Feature file already "
            "exists: "
            f"{output_path}"
        )

    if (
            len(sample_ids)
            != features.shape[0]
    ):
        raise ValueError(
            "Sample ID count does not "
            "match feature count."
        )

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    payload = {
        "backbone_key": (
            backbone_key
        ),
        "model_name": (
            model_name
        ),
        "revision": (
            revision
        ),
        "split": (
            split_name
        ),
        "max_length": (
            max_length
        ),
        "features": (
            features.contiguous()
        ),
        "labels": (
            labels.contiguous()
        ),
        "sample_ids": list(
            sample_ids
        ),
    }

    torch.save(
        payload,
        output_path,
    )

    return {
        "file": str(
            output_path
        ),
        "bytes": (
            output_path
            .stat()
            .st_size
        ),
        "sha256": (
            calculate_sha256(
                output_path
            )
        ),
        "feature_shape": list(
            features.shape
        ),
        "label_shape": list(
            labels.shape
        ),
        "feature_dtype": str(
            features.dtype
        ),
        "label_dtype": str(
            labels.dtype
        ),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Extract frozen ELECTRA "
            "CLS features for the "
            "TripPamine emotion "
            "backbone probe."
        )
    )

    parser.add_argument(
        "--backbone-key",
        required=True,
    )

    parser.add_argument(
        "--model",
        required=True,
    )

    parser.add_argument(
        "--revision",
        default="main",
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
        "--max-length",
        type=int,
        default=DEFAULT_MAX_LENGTH,
    )

    parser.add_argument(
        "--batch-size",
        type=int,
        default=DEFAULT_BATCH_SIZE,
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    validate_positive_integer(
        args.max_length,
        "Maximum token length",
    )

    validate_positive_integer(
        args.batch_size,
        "Feature extraction batch size",
    )

    if args.output_dir.exists():
        raise FileExistsError(
            "Feature output directory "
            "already exists: "
            f"{args.output_dir}"
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available. "
            "Frozen feature extraction "
            "cannot run."
        )

    training = read_dataset(
        args.training,
        "training",
    )

    validation = read_dataset(
        args.validation,
        "validation",
    )

    label_mapping = (
        EmotionLabelMapping()
    )

    validate_label_sets(
        training=training,
        validation=validation,
        label_mapping=label_mapping,
    )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model,
            revision=args.revision,
        )
    )

    model = (
        AutoModel
        .from_pretrained(
            args.model,
            revision=args.revision,
        )
    )

    backbone_contract = (
        validate_backbone_contract(
            tokenizer=tokenizer,
            model=model,
        )
    )

    special_token_contract = (
        validate_special_token_contract(
            tokenizer=tokenizer,
            text=training[
                0
            ].text,
        )
    )

    freeze_backbone(
        model
    )

    if model.training:
        raise RuntimeError(
            "Frozen backbone must be "
            "in evaluation mode."
        )

    trainable_parameter_count = sum(
        parameter.numel()
        for parameter
        in model.parameters()
        if parameter.requires_grad
    )

    if (
            trainable_parameter_count
            != 0
    ):
        raise RuntimeError(
            "Frozen backbone contains "
            "trainable parameters."
        )

    device_index = (
        torch.cuda.current_device()
    )

    device = torch.device(
        "cuda",
        device_index,
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
            "Backbone model is not "
            "on CUDA: "
            f"{model_device}"
        )

    data_collator = (
        DataCollatorWithPadding(
            tokenizer=tokenizer,
            padding=True,
            return_tensors="pt",
        )
    )

    train_dataset = (
        TransformerEmotionDataset(
            samples=training,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    validation_dataset = (
        TransformerEmotionDataset(
            samples=validation,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    args.output_dir.mkdir(
        parents=True,
        exist_ok=False,
    )

    (
        training_features,
        training_labels,
        training_metrics,
    ) = extract_features(
        model=model,
        dataset=train_dataset,
        data_collator=data_collator,
        device=device,
        batch_size=args.batch_size,
        num_labels=(
            label_mapping.num_labels
        ),
        split_name="training",
    )

    training_file = (
            args.output_dir
            / "training-features.pt"
    )

    training_artifact = (
        save_feature_file(
            output_path=(
                training_file
            ),
            features=(
                training_features
            ),
            labels=(
                training_labels
            ),
            sample_ids=[
                sample.id
                for sample
                in training
            ],
            backbone_key=(
                args.backbone_key
            ),
            model_name=args.model,
            revision=args.revision,
            split_name="training",
            max_length=(
                args.max_length
            ),
        )
    )

    del training_features
    del training_labels

    (
        validation_features,
        validation_labels,
        validation_metrics,
    ) = extract_features(
        model=model,
        dataset=validation_dataset,
        data_collator=data_collator,
        device=device,
        batch_size=args.batch_size,
        num_labels=(
            label_mapping.num_labels
        ),
        split_name="validation",
    )

    validation_file = (
            args.output_dir
            / "validation-features.pt"
    )

    validation_artifact = (
        save_feature_file(
            output_path=(
                validation_file
            ),
            features=(
                validation_features
            ),
            labels=(
                validation_labels
            ),
            sample_ids=[
                sample.id
                for sample
                in validation
            ],
            backbone_key=(
                args.backbone_key
            ),
            model_name=args.model,
            revision=args.revision,
            split_name="validation",
            max_length=(
                args.max_length
            ),
        )
    )

    del validation_features
    del validation_labels

    model_commit_hash = getattr(
        model.config,
        "_commit_hash",
        None,
    )

    tokenizer_commit_hash = (
        getattr(
            tokenizer,
            "init_kwargs",
            {},
        )
        .get(
            "_commit_hash"
        )
    )

    metadata_path = (
            args.output_dir
            / "metadata.json"
    )

    report = {
        "experiment": (
            "T0-Q14-0B-"
            "frozen-backbone-"
            "feature-extraction"
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
        "backbone": {
            "key": (
                args.backbone_key
            ),
            "model": (
                args.model
            ),
            "requested_revision": (
                args.revision
            ),
            "model_commit_hash": (
                model_commit_hash
            ),
            "tokenizer_commit_hash": (
                tokenizer_commit_hash
            ),
            "contract": (
                backbone_contract
            ),
            "special_token_contract": (
                special_token_contract
            ),
            "encoder_frozen": True,
            "trainable_parameters": 0,
            "evaluation_mode": True,
            "precision": "fp32",
            "cls_hidden_index": 0,
        },
        "hardware": {
            "device_index": (
                device_index
            ),
            "device_name": (
                torch.cuda
                .get_device_name(
                    device_index
                )
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
            "test_split_used": False,
        },
        "protocol": {
            "max_length": (
                args.max_length
            ),
            "batch_size": (
                args.batch_size
            ),
            "padding": "dynamic",
            "add_special_tokens": True,
            "truncation": True,
            "gradient_enabled": False,
            "feature": (
                "last_hidden_state"
                "[:, 0, :]"
            ),
        },
        "features": {
            "training": {
                "extraction": (
                    training_metrics
                ),
                "artifact": (
                    training_artifact
                ),
            },
            "validation": {
                "extraction": (
                    validation_metrics
                ),
                "artifact": (
                    validation_artifact
                ),
            },
        },
    }

    save_report(
        report=report,
        output_path=metadata_path,
    )

    if (
            not math.isfinite(
                float(
                    training_metrics[
                        "seconds"
                    ]
                )
            )
            or not math.isfinite(
                float(
                    validation_metrics[
                        "seconds"
                    ]
                )
            )
    ):
        raise RuntimeError(
            "Feature extraction timing "
            "is not finite."
        )

    print()
    print(
        "Frozen ELECTRA feature "
        "extraction completed"
    )

    print()
    print(
        "Backbone: "
        f"{args.backbone_key}"
    )

    print(
        "Model: "
        f"{args.model}"
    )

    print(
        "Revision: "
        f"{args.revision}"
    )

    print(
        "Device: "
        f"{torch.cuda.get_device_name(device_index)}"
    )

    print()
    print(
        "Encoder layers: "
        f"{backbone_contract['num_hidden_layers']}"
    )

    print(
        "Hidden size: "
        f"{backbone_contract['hidden_size']}"
    )

    print(
        "Tokenizer vocab: "
        f"{backbone_contract['tokenizer_vocab_size']}"
    )

    print(
        "Special tokens/input: "
        f"{backbone_contract['special_tokens_per_input']}"
    )

    print(
        "CLS token check: PASS"
    )

    print(
        "SEP token check: PASS"
    )

    print()
    print(
        "Encoder frozen: True"
    )

    print(
        "Trainable parameters: 0"
    )

    print(
        "Test split used: False"
    )

    print()
    print(
        "Training feature shape: "
        f"{training_artifact['feature_shape']}"
    )

    print(
        "Validation feature shape: "
        f"{validation_artifact['feature_shape']}"
    )

    print()
    print(
        "Training extraction seconds: "
        f"{training_metrics['seconds']:.2f}"
    )

    print(
        "Validation extraction seconds: "
        f"{validation_metrics['seconds']:.2f}"
    )

    print()
    print(
        "Training features: "
        f"{training_file}"
    )

    print(
        "Validation features: "
        f"{validation_file}"
    )

    print(
        "Metadata: "
        f"{metadata_path}"
    )

    print()
    print(
        "Q14-0B FEATURE EXTRACTION: PASS"
    )


if __name__ == "__main__":
    main()