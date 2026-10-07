import argparse
import math
import random
import statistics
import sys
import time
from copy import deepcopy
from pathlib import Path
from typing import Any

import numpy as np
import torch
import transformers
from sklearn.metrics import (
    accuracy_score,
    f1_score,
)
from torch import nn
from torch.utils.data import (
    DataLoader,
    TensorDataset,
)

from scripts.emotion.train_transformer_classifier import (
    calculate_sha256,
    save_report,
    validate_positive_float,
    validate_positive_integer,
)


EXPECTED_HIDDEN_SIZE = 768
EXPECTED_LABEL_COUNT = 60

DEFAULT_TRAIN_BATCH_SIZE = 512
DEFAULT_EVAL_BATCH_SIZE = 2048

DEFAULT_LEARNING_RATE = 1e-3
DEFAULT_WEIGHT_DECAY = 0.01
DEFAULT_DROPOUT = 0.1

DEFAULT_MAX_EPOCHS = 15
DEFAULT_PATIENCE = 2
DEFAULT_THRESHOLD = 0.0005

DEFAULT_SEEDS = (
    42,
    123,
    2026,
)


class FrozenFeatureProbe(
    nn.Module,
):

    def __init__(
            self,
            hidden_size: int = (
                EXPECTED_HIDDEN_SIZE
            ),
            num_labels: int = (
                EXPECTED_LABEL_COUNT
            ),
            dropout: float = (
                DEFAULT_DROPOUT
            ),
            layer_norm_eps: float = 1e-12,
    ) -> None:
        super().__init__()

        self.layer_norm = nn.LayerNorm(
            hidden_size,
            eps=layer_norm_eps,
        )

        self.dropout = nn.Dropout(
            dropout
        )

        self.dense = nn.Linear(
            hidden_size,
            hidden_size,
        )

        self.activation = nn.GELU()

        self.out_proj = nn.Linear(
            hidden_size,
            num_labels,
        )

    def forward(
            self,
            features: torch.Tensor,
    ) -> torch.Tensor:
        hidden = self.layer_norm(
            features
        )

        hidden = self.dropout(
            hidden
        )

        hidden = self.dense(
            hidden
        )

        hidden = self.activation(
            hidden
        )

        hidden = self.dropout(
            hidden
        )

        return self.out_proj(
            hidden
        )


def set_seed(
        seed: int,
) -> None:
    random.seed(
        seed
    )

    np.random.seed(
        seed
    )

    torch.manual_seed(
        seed
    )

    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(
            seed
        )

    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True


def load_feature_payload(
        path: Path,
        expected_split: str,
) -> dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(
            "Feature file not found: "
            f"{path}"
        )

    payload = torch.load(
        path,
        map_location="cpu",
        weights_only=True,
    )

    if not isinstance(
            payload,
            dict,
    ):
        raise ValueError(
            "Feature payload must be "
            "a dictionary."
        )

    required_keys = {
        "backbone_key",
        "model_name",
        "revision",
        "split",
        "max_length",
        "features",
        "labels",
        "sample_ids",
    }

    missing = (
        required_keys
        - set(
            payload.keys()
        )
    )

    if missing:
        raise ValueError(
            "Feature payload is "
            "missing keys: "
            f"{sorted(missing)}"
        )

    if (
            payload[
                "split"
            ]
            != expected_split
    ):
        raise ValueError(
            "Feature split mismatch: "
            f"expected "
            f"{expected_split}, "
            f"found "
            f"{payload['split']}."
        )

    features = payload[
        "features"
    ]

    labels = payload[
        "labels"
    ]

    sample_ids = payload[
        "sample_ids"
    ]

    if not torch.is_tensor(
            features
    ):
        raise ValueError(
            "Features must be a tensor."
        )

    if not torch.is_tensor(
            labels
    ):
        raise ValueError(
            "Labels must be a tensor."
        )

    if features.ndim != 2:
        raise ValueError(
            "Features must be "
            "2-dimensional."
        )

    if (
            features.shape[
                1
            ]
            != EXPECTED_HIDDEN_SIZE
    ):
        raise ValueError(
            "Unexpected feature hidden "
            "size: "
            f"{features.shape[1]}."
        )

    if labels.ndim != 1:
        raise ValueError(
            "Labels must be "
            "1-dimensional."
        )

    sample_count = int(
        features.shape[
            0
        ]
    )

    if not (
            labels.shape[
                0
            ]
            == len(
                sample_ids
            )
            == sample_count
    ):
        raise ValueError(
            "Feature, label, and "
            "sample ID counts differ."
        )

    if not torch.isfinite(
            features
    ).all():
        raise ValueError(
            "Feature tensor contains "
            "non-finite values."
        )

    if sample_count > 0:
        minimum_label = int(
            labels.min().item()
        )

        maximum_label = int(
            labels.max().item()
        )

        if (
                minimum_label < 0
                or maximum_label
                >= EXPECTED_LABEL_COUNT
        ):
            raise ValueError(
                "Labels are outside the "
                "valid 60-class range."
            )

    payload[
        "features"
    ] = (
        features
        .float()
        .contiguous()
    )

    payload[
        "labels"
    ] = (
        labels
        .long()
        .contiguous()
    )

    return payload


def validate_pair_alignment(
        first: dict[str, Any],
        second: dict[str, Any],
        split_name: str,
) -> None:
    if (
            first[
                "sample_ids"
            ]
            != second[
                "sample_ids"
            ]
    ):
        raise ValueError(
            "Backbone sample ID "
            "alignment mismatch for "
            f"{split_name}."
        )

    if not torch.equal(
            first[
                "labels"
            ],
            second[
                "labels"
            ],
    ):
        raise ValueError(
            "Backbone label alignment "
            "mismatch for "
            f"{split_name}."
        )

    if (
            first[
                "max_length"
            ]
            != second[
                "max_length"
            ]
    ):
        raise ValueError(
            "Backbone max_length "
            "mismatch for "
            f"{split_name}."
        )


def build_loader(
        features: torch.Tensor,
        labels: torch.Tensor,
        batch_size: int,
        shuffle: bool,
        seed: int,
) -> DataLoader:
    validate_positive_integer(
        batch_size,
        "Probe batch size",
    )

    dataset = TensorDataset(
        features,
        labels,
    )

    generator = torch.Generator()

    generator.manual_seed(
        seed
    )

    return DataLoader(
        dataset,
        batch_size=batch_size,
        shuffle=shuffle,
        generator=(
            generator
            if shuffle
            else None
        ),
        num_workers=0,
        pin_memory=True,
        drop_last=False,
    )


def evaluate_probe(
        model: FrozenFeatureProbe,
        data_loader: DataLoader,
        device: torch.device,
) -> dict[str, float]:
    model.eval()

    true_labels = []
    predictions = []

    loss_function = (
        nn.CrossEntropyLoss()
    )

    total_loss = 0.0
    total_samples = 0

    with torch.inference_mode():
        for (
                features,
                labels,
        ) in data_loader:
            features = features.to(
                device,
                non_blocking=True,
            )

            labels = labels.to(
                device,
                non_blocking=True,
            )

            logits = model(
                features
            )

            loss = loss_function(
                logits,
                labels,
            )

            batch_size = int(
                labels.shape[
                    0
                ]
            )

            total_loss += (
                float(
                    loss.item()
                )
                * batch_size
            )

            total_samples += (
                batch_size
            )

            predicted = (
                torch.argmax(
                    logits,
                    dim=-1,
                )
            )

            true_labels.extend(
                labels
                .detach()
                .cpu()
                .tolist()
            )

            predictions.extend(
                predicted
                .detach()
                .cpu()
                .tolist()
            )

    if total_samples == 0:
        raise RuntimeError(
            "Probe evaluation "
            "processed zero samples."
        )

    return {
        "loss": (
            total_loss
            / total_samples
        ),
        "fine_accuracy": float(
            accuracy_score(
                true_labels,
                predictions,
            )
        ),
        "fine_macro_f1": float(
            f1_score(
                true_labels,
                predictions,
                labels=list(
                    range(
                        EXPECTED_LABEL_COUNT
                    )
                ),
                average="macro",
                zero_division=0,
            )
        ),
    }


def train_probe(
        training_features: torch.Tensor,
        training_labels: torch.Tensor,
        validation_features: torch.Tensor,
        validation_labels: torch.Tensor,
        seed: int,
        device: torch.device,
        train_batch_size: int,
        eval_batch_size: int,
        learning_rate: float,
        weight_decay: float,
        dropout: float,
        max_epochs: int,
        patience: int,
        threshold: float,
) -> dict[str, Any]:
    set_seed(
        seed
    )

    model = FrozenFeatureProbe(
        dropout=dropout,
    )

    model.to(
        device
    )

    training_loader = build_loader(
        features=training_features,
        labels=training_labels,
        batch_size=train_batch_size,
        shuffle=True,
        seed=seed,
    )

    validation_loader = build_loader(
        features=validation_features,
        labels=validation_labels,
        batch_size=eval_batch_size,
        shuffle=False,
        seed=seed,
    )

    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=learning_rate,
        weight_decay=weight_decay,
    )

    loss_function = (
        nn.CrossEntropyLoss()
    )

    best_f1 = (
        -math.inf
    )

    best_epoch = None
    best_state = None

    patience_counter = 0
    history = []

    started_at = (
        time.perf_counter()
    )

    for epoch in range(
            1,
            max_epochs + 1,
    ):
        model.train()

        running_loss = 0.0
        processed_samples = 0

        for (
                features,
                labels,
        ) in training_loader:
            features = features.to(
                device,
                non_blocking=True,
            )

            labels = labels.to(
                device,
                non_blocking=True,
            )

            optimizer.zero_grad(
                set_to_none=True
            )

            logits = model(
                features
            )

            loss = loss_function(
                logits,
                labels,
            )

            if not torch.isfinite(
                    loss
            ):
                raise RuntimeError(
                    "Probe training loss "
                    "is not finite."
                )

            loss.backward()

            optimizer.step()

            batch_size = int(
                labels.shape[
                    0
                ]
            )

            running_loss += (
                float(
                    loss.item()
                )
                * batch_size
            )

            processed_samples += (
                batch_size
            )

        validation_metrics = (
            evaluate_probe(
                model=model,
                data_loader=(
                    validation_loader
                ),
                device=device,
            )
        )

        epoch_result = {
            "epoch": epoch,
            "train_loss": (
                running_loss
                / processed_samples
            ),
            **validation_metrics,
        }

        history.append(
            epoch_result
        )

        current_f1 = (
            validation_metrics[
                "fine_macro_f1"
            ]
        )

        if (
                current_f1
                > best_f1
                + threshold
        ):
            best_f1 = (
                current_f1
            )

            best_epoch = epoch

            best_state = {
                key: value
                .detach()
                .cpu()
                .clone()
                for key, value
                in model.state_dict().items()
            }

            patience_counter = 0

        else:
            patience_counter += 1

            if (
                    patience_counter
                    >= patience
            ):
                break

    if (
            best_state is None
            or best_epoch is None
    ):
        raise RuntimeError(
            "Probe training did not "
            "produce a best model."
        )

    model.load_state_dict(
        best_state
    )

    final_metrics = evaluate_probe(
        model=model,
        data_loader=validation_loader,
        device=device,
    )

    elapsed_seconds = (
        time.perf_counter()
        - started_at
    )

    return {
        "seed": seed,
        "best_epoch": (
            best_epoch
        ),
        "epochs_completed": (
            len(history)
        ),
        "stopped_early": (
            len(history)
            < max_epochs
        ),
        "best_fine_macro_f1": (
            final_metrics[
                "fine_macro_f1"
            ]
        ),
        "best_fine_accuracy": (
            final_metrics[
                "fine_accuracy"
            ]
        ),
        "best_validation_loss": (
            final_metrics[
                "loss"
            ]
        ),
        "seconds": (
            elapsed_seconds
        ),
        "history": history,
    }


def summarize_seed_results(
        results: list[
            dict[str, Any]
        ],
) -> dict[str, Any]:
    if not results:
        raise ValueError(
            "Probe seed results "
            "must not be empty."
        )

    fine_f1_values = [
        float(
            result[
                "best_fine_macro_f1"
            ]
        )
        for result
        in results
    ]

    accuracy_values = [
        float(
            result[
                "best_fine_accuracy"
            ]
        )
        for result
        in results
    ]

    return {
        "seeds": [
            result[
                "seed"
            ]
            for result
            in results
        ],
        "fine_macro_f1_mean": (
            statistics.mean(
                fine_f1_values
            )
        ),
        "fine_macro_f1_sample_std": (
            statistics.stdev(
                fine_f1_values
            )
            if len(
                fine_f1_values
            ) > 1
            else 0.0
        ),
        "fine_accuracy_mean": (
            statistics.mean(
                accuracy_values
            )
        ),
        "fine_accuracy_sample_std": (
            statistics.stdev(
                accuracy_values
            )
            if len(
                accuracy_values
            ) > 1
            else 0.0
        ),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Compare cached frozen "
            "ELECTRA backbone features "
            "using an identical "
            "LayerNorm classification "
            "probe."
        )
    )

    parser.add_argument(
        "--ko-training",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--ko-validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc-training",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--kc-validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--output",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--train-batch-size",
        type=int,
        default=(
            DEFAULT_TRAIN_BATCH_SIZE
        ),
    )

    parser.add_argument(
        "--eval-batch-size",
        type=int,
        default=(
            DEFAULT_EVAL_BATCH_SIZE
        ),
    )

    parser.add_argument(
        "--learning-rate",
        type=float,
        default=(
            DEFAULT_LEARNING_RATE
        ),
    )

    parser.add_argument(
        "--weight-decay",
        type=float,
        default=(
            DEFAULT_WEIGHT_DECAY
        ),
    )

    parser.add_argument(
        "--dropout",
        type=float,
        default=(
            DEFAULT_DROPOUT
        ),
    )

    parser.add_argument(
        "--max-epochs",
        type=int,
        default=(
            DEFAULT_MAX_EPOCHS
        ),
    )

    parser.add_argument(
        "--patience",
        type=int,
        default=(
            DEFAULT_PATIENCE
        ),
    )

    parser.add_argument(
        "--threshold",
        type=float,
        default=(
            DEFAULT_THRESHOLD
        ),
    )

    parser.add_argument(
        "--seeds",
        type=int,
        nargs="+",
        default=list(
            DEFAULT_SEEDS
        ),
    )

    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.output.exists():
        raise FileExistsError(
            "Probe report already "
            "exists: "
            f"{args.output}"
        )

    validate_positive_integer(
        args.train_batch_size,
        "Training batch size",
    )

    validate_positive_integer(
        args.eval_batch_size,
        "Evaluation batch size",
    )

    validate_positive_integer(
        args.max_epochs,
        "Maximum epochs",
    )

    validate_positive_integer(
        args.patience,
        "Early stopping patience",
    )

    validate_positive_float(
        args.learning_rate,
        "Learning rate",
    )

    if (
            args.weight_decay < 0.0
            or not math.isfinite(
                args.weight_decay
            )
    ):
        raise ValueError(
            "Weight decay must be "
            "finite and non-negative."
        )

    if (
            args.dropout < 0.0
            or args.dropout >= 1.0
            or not math.isfinite(
                args.dropout
            )
    ):
        raise ValueError(
            "Dropout must be finite "
            "and in [0, 1)."
        )

    if (
            args.threshold < 0.0
            or not math.isfinite(
                args.threshold
            )
    ):
        raise ValueError(
            "Early stopping threshold "
            "must be finite and "
            "non-negative."
        )

    if not args.seeds:
        raise ValueError(
            "At least one probe seed "
            "is required."
        )

    if len(
            set(
                args.seeds
            )
    ) != len(
            args.seeds
    ):
        raise ValueError(
            "Probe seeds must be "
            "unique."
        )

    if any(
            seed < 0
            for seed
            in args.seeds
    ):
        raise ValueError(
            "Probe seeds must be "
            "non-negative."
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available."
        )

    ko_training = (
        load_feature_payload(
            args.ko_training,
            "training",
        )
    )

    ko_validation = (
        load_feature_payload(
            args.ko_validation,
            "validation",
        )
    )

    kc_training = (
        load_feature_payload(
            args.kc_training,
            "training",
        )
    )

    kc_validation = (
        load_feature_payload(
            args.kc_validation,
            "validation",
        )
    )

    validate_pair_alignment(
        first=ko_training,
        second=kc_training,
        split_name="training",
    )

    validate_pair_alignment(
        first=ko_validation,
        second=kc_validation,
        split_name="validation",
    )

    if (
            ko_training[
                "sample_ids"
            ]
            == ko_validation[
                "sample_ids"
            ]
    ):
        raise ValueError(
            "Training and validation "
            "sample IDs must differ."
        )

    device_index = (
        torch.cuda.current_device()
    )

    device = torch.device(
        "cuda",
        device_index,
    )

    backbone_payloads = {
        "koelectra-v3": (
            ko_training,
            ko_validation,
        ),
        "kcelectra-v2022": (
            kc_training,
            kc_validation,
        ),
    }

    backbone_results = {}

    total_started_at = (
        time.perf_counter()
    )

    for (
            backbone_key,
            (
                training_payload,
                validation_payload,
            ),
    ) in (
            backbone_payloads.items()
    ):
        print()
        print(
            "Probing backbone: "
            f"{backbone_key}"
        )

        seed_results = []

        for seed in args.seeds:
            print(
                "  seed="
                f"{seed}"
            )

            result = train_probe(
                training_features=(
                    training_payload[
                        "features"
                    ]
                ),
                training_labels=(
                    training_payload[
                        "labels"
                    ]
                ),
                validation_features=(
                    validation_payload[
                        "features"
                    ]
                ),
                validation_labels=(
                    validation_payload[
                        "labels"
                    ]
                ),
                seed=seed,
                device=device,
                train_batch_size=(
                    args.train_batch_size
                ),
                eval_batch_size=(
                    args.eval_batch_size
                ),
                learning_rate=(
                    args.learning_rate
                ),
                weight_decay=(
                    args.weight_decay
                ),
                dropout=args.dropout,
                max_epochs=(
                    args.max_epochs
                ),
                patience=args.patience,
                threshold=(
                    args.threshold
                ),
            )

            seed_results.append(
                result
            )

            print(
                "    F1="
                f"{result['best_fine_macro_f1']:.6f}, "
                "accuracy="
                f"{result['best_fine_accuracy']:.6f}, "
                "best_epoch="
                f"{result['best_epoch']}, "
                "seconds="
                f"{result['seconds']:.2f}"
            )

        backbone_results[
            backbone_key
        ] = {
            "source": {
                "training_file": str(
                    args.ko_training
                    if (
                        backbone_key
                        == "koelectra-v3"
                    )
                    else args.kc_training
                ),
                "validation_file": str(
                    args.ko_validation
                    if (
                        backbone_key
                        == "koelectra-v3"
                    )
                    else args.kc_validation
                ),
                "training_sha256": (
                    calculate_sha256(
                        args.ko_training
                        if (
                            backbone_key
                            == "koelectra-v3"
                        )
                        else args.kc_training
                    )
                ),
                "validation_sha256": (
                    calculate_sha256(
                        args.ko_validation
                        if (
                            backbone_key
                            == "koelectra-v3"
                        )
                        else args.kc_validation
                    )
                ),
            },
            "seeds": (
                seed_results
            ),
            "summary": (
                summarize_seed_results(
                    seed_results
                )
            ),
        }

    ko_summary = (
        backbone_results[
            "koelectra-v3"
        ][
            "summary"
        ]
    )

    kc_summary = (
        backbone_results[
            "kcelectra-v2022"
        ][
            "summary"
        ]
    )

    fine_f1_delta = (
        kc_summary[
            "fine_macro_f1_mean"
        ]
        -
        ko_summary[
            "fine_macro_f1_mean"
        ]
    )

    accuracy_delta = (
        kc_summary[
            "fine_accuracy_mean"
        ]
        -
        ko_summary[
            "fine_accuracy_mean"
        ]
    )

    total_seconds = (
        time.perf_counter()
        - total_started_at
    )

    report = {
        "experiment": (
            "T0-Q14-0B-"
            "cached-feature-"
            "three-seed-probe"
        ),
        "versions": {
            "python": sys.version,
            "torch": (
                torch.__version__
            ),
            "transformers": (
                transformers.__version__
            ),
            "numpy": (
                np.__version__
            ),
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
        "protocol": {
            "encoder_execution": False,
            "cached_features": True,
            "feature_type": (
                "frozen CLS"
            ),
            "hidden_size": (
                EXPECTED_HIDDEN_SIZE
            ),
            "fine_num_labels": (
                EXPECTED_LABEL_COUNT
            ),
            "probe_head": [
                "layer_norm",
                "dropout",
                "dense",
                "gelu",
                "dropout",
                "out_proj",
            ],
            "dropout": (
                args.dropout
            ),
            "loss": (
                "cross_entropy"
            ),
            "class_weighting": False,
            "learning_rate": (
                args.learning_rate
            ),
            "weight_decay": (
                args.weight_decay
            ),
            "optimizer": "AdamW",
            "train_batch_size": (
                args.train_batch_size
            ),
            "eval_batch_size": (
                args.eval_batch_size
            ),
            "max_epochs": (
                args.max_epochs
            ),
            "early_stopping": {
                "patience": (
                    args.patience
                ),
                "threshold": (
                    args.threshold
                ),
                "metric": (
                    "fine_macro_f1"
                ),
            },
            "seeds": (
                args.seeds
            ),
            "test_split_used": False,
        },
        "backbones": (
            backbone_results
        ),
        "comparison": {
            "kcelectra_minus_koelectra": {
                "fine_macro_f1_mean": (
                    fine_f1_delta
                ),
                "fine_accuracy_mean": (
                    accuracy_delta
                ),
            }
        },
        "timing": {
            "total_seconds": (
                total_seconds
            ),
        },
    }

    save_report(
        report=report,
        output_path=args.output,
    )

    print()
    print(
        "Cached frozen-backbone "
        "probe completed"
    )

    print()

    print(
        "KoELECTRA Fine Macro F1 "
        "mean: "
        f"{ko_summary['fine_macro_f1_mean']:.6f}"
    )

    print(
        "KoELECTRA F1 std: "
        f"{ko_summary['fine_macro_f1_sample_std']:.6f}"
    )

    print()

    print(
        "KcELECTRA-v2022 Fine "
        "Macro F1 mean: "
        f"{kc_summary['fine_macro_f1_mean']:.6f}"
    )

    print(
        "KcELECTRA-v2022 F1 std: "
        f"{kc_summary['fine_macro_f1_sample_std']:.6f}"
    )

    print()

    print(
        "Kc - Ko Fine Macro F1: "
        f"{fine_f1_delta:+.6f}"
    )

    print(
        "Kc - Ko Fine accuracy: "
        f"{accuracy_delta:+.6f}"
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
        "Q14-0B CACHED FEATURE "
        "PROBE: PASS"
    )


if __name__ == "__main__":
    main()