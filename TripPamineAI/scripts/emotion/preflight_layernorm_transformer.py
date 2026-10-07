import argparse
import math
import tempfile
from pathlib import Path

import torch
from transformers import (
    AutoTokenizer,
    DataCollatorWithPadding,
    TrainingArguments,
)

from scripts.emotion.train_layernorm_transformer_classifier import (
    build_early_stopping_callback,
)
from scripts.emotion.train_transformer_classifier import (
    DEFAULT_MODEL,
    read_dataset,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.layernorm_transformer import (
    LayerNormElectraClassificationHead,
    LayerNormElectraForSequenceClassification,
)
from trippamine_ai.models.emotion.transformer_dataset import (
    TransformerEmotionDataset,
)
from trippamine_ai.models.emotion.transformer_training import (
    build_compute_metrics,
    build_fine_to_coarse,
)

DEFAULT_MAX_LENGTH = 96
DEFAULT_EVAL_BATCH_SIZE = 64
DEFAULT_REVISION = "main"

DEFAULT_EARLY_STOPPING_PATIENCE = 1
DEFAULT_EARLY_STOPPING_THRESHOLD = 0.0005


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run a CUDA preflight for "
            "the TripPamine LayerNorm "
            "ELECTRA classifier."
        )
    )

    parser.add_argument(
        "--validation",
        required=True,
        type=Path,
    )

    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
    )

    parser.add_argument(
        "--revision",
        default=DEFAULT_REVISION,
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
        "--early-stopping-patience",
        type=int,
        default=DEFAULT_EARLY_STOPPING_PATIENCE,
    )

    parser.add_argument(
        "--early-stopping-threshold",
        type=float,
        default=DEFAULT_EARLY_STOPPING_THRESHOLD,
    )

    return parser.parse_args()


def has_finite_nonzero_gradient(
        module: torch.nn.Module,
) -> bool:
    found_nonzero = False

    for parameter in module.parameters():
        gradient = parameter.grad

        if gradient is None:
            continue

        if not torch.isfinite(
                gradient
        ).all():
            raise RuntimeError(
                "Non-finite classifier "
                "gradient detected."
            )

        if torch.count_nonzero(
                gradient
        ).item() > 0:
            found_nonzero = True

    return found_nonzero


def main() -> None:
    args = parse_args()

    if (
            isinstance(
                args.max_length,
                bool,
            )
            or not isinstance(
        args.max_length,
        int,
    )
            or args.max_length <= 0
    ):
        raise ValueError(
            "Maximum token length "
            "must be a positive integer."
        )

    if (
            isinstance(
                args.eval_batch_size,
                bool,
            )
            or not isinstance(
        args.eval_batch_size,
        int,
    )
            or args.eval_batch_size <= 0
    ):
        raise ValueError(
            "Evaluation batch size "
            "must be a positive integer."
        )

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA is not available."
        )

    validation = read_dataset(
        args.validation,
        "validation",
    )

    if (
            len(validation)
            < args.eval_batch_size
    ):
        raise ValueError(
            "Validation dataset is "
            "smaller than the requested "
            "preflight batch."
        )

    label_mapping = (
        EmotionLabelMapping()
    )

    fine_to_coarse = (
        build_fine_to_coarse(
            samples=validation,
            label_mapping=(
                label_mapping
            ),
        )
    )

    tokenizer = (
        AutoTokenizer
        .from_pretrained(
            args.model,
            revision=args.revision,
        )
    )

    preflight_samples = (
        validation[
            :args.eval_batch_size
        ]
    )

    dataset = (
        TransformerEmotionDataset(
            samples=preflight_samples,
            tokenizer=tokenizer,
            label_mapping=(
                label_mapping
            ),
            max_length=(
                args.max_length
            ),
        )
    )

    collator = (
        DataCollatorWithPadding(
            tokenizer=tokenizer,
            padding=True,
            return_tensors="pt",
        )
    )

    model = (
        LayerNormElectraForSequenceClassification
        .from_pretrained(
            args.model,
            revision=args.revision,
            num_labels=(
                label_mapping.num_labels
            ),
            label2id=dict(
                label_mapping.label2id
            ),
            id2label=dict(
                label_mapping.id2label
            ),
        )
    )

    tokenizer_vocab_size = len(
        tokenizer
    )

    model_vocab_size = (
        model.config.vocab_size
    )

    embedding_vocab_size = (
        model
        .get_input_embeddings()
        .weight
        .shape[
            0
        ]
    )

    if not (
            tokenizer_vocab_size
            == model_vocab_size
            == embedding_vocab_size
    ):
        raise RuntimeError(
            "Tokenizer/model vocabulary "
            "mismatch: "
            f"tokenizer={tokenizer_vocab_size}, "
            f"config={model_vocab_size}, "
            f"embedding={embedding_vocab_size}"
        )

    special_token_count = (
        tokenizer
        .num_special_tokens_to_add(
            pair=False
        )
    )

    if special_token_count != 2:
        raise RuntimeError(
            "Expected exactly 2 "
            "special tokens for a "
            "single input, found "
            f"{special_token_count}."
        )

    if not isinstance(
            model.classifier,
            LayerNormElectraClassificationHead,
    ):
        raise RuntimeError(
            "Loaded model does not use "
            "the LayerNorm classifier."
        )

    if (
            model.config.num_hidden_layers
            != 12
    ):
        raise RuntimeError(
            "Unexpected encoder layer "
            "count: "
            f"{model.config.num_hidden_layers}"
        )

    if (
            model.config.hidden_size
            != 768
    ):
        raise RuntimeError(
            "Unexpected hidden size: "
            f"{model.config.hidden_size}"
        )

    if (
            model.config.intermediate_size
            != 3072
    ):
        raise RuntimeError(
            "Unexpected intermediate "
            "size: "
            f"{model.config.intermediate_size}"
        )

    if (
            model.config.num_attention_heads
            != 12
    ):
        raise RuntimeError(
            "Unexpected attention head "
            "count: "
            f"{model.config.num_attention_heads}"
        )

    if (
            not math.isclose(
                model.classifier.dropout.p,
                0.1,
                rel_tol=0.0,
                abs_tol=1e-12,
            )
    ):
        raise RuntimeError(
            "Unexpected classifier "
            "dropout: "
            f"{model.classifier.dropout.p}"
        )

    if (
            not math.isclose(
                model.classifier.layer_norm.eps,
                1e-12,
                rel_tol=0.0,
                abs_tol=1e-15,
            )
    ):
        raise RuntimeError(
            "Unexpected LayerNorm eps: "
            f"{model.classifier.layer_norm.eps}"
        )

    compute_metrics = (
        build_compute_metrics(
            label_mapping=(
                label_mapping
            ),
            fine_to_coarse=(
                fine_to_coarse
            ),
        )
    )

    history_callback = None

    early_stopping_callback = (
        build_early_stopping_callback(
            patience=(
                args.early_stopping_patience
            ),
            threshold=(
                args.early_stopping_threshold
            ),
        )
    )

    with tempfile.TemporaryDirectory() as (
            temporary_directory
    ):
        training_arguments = (
            TrainingArguments(
                output_dir=(
                    temporary_directory
                ),
                per_device_eval_batch_size=(
                    args.eval_batch_size
                ),
                eval_strategy="epoch",
                save_strategy="epoch",
                load_best_model_at_end=True,
                metric_for_best_model=(
                    "fine_macro_f1"
                ),
                greater_is_better=True,
                report_to="none",
                use_cpu=False,
                fp16=False,
                bf16=False,
                dataloader_num_workers=0,
                dataloader_pin_memory=True,
                do_train=False,
                do_eval=True,
            )
        )

        from transformers import Trainer

        trainer = Trainer(
            model=model,
            args=training_arguments,
            data_collator=(
                collator
            ),
            eval_dataset=(
                dataset
            ),
            processing_class=(
                tokenizer
            ),
            compute_metrics=(
                compute_metrics
            ),
            callbacks=[
                early_stopping_callback
            ],
        )

        model_device = next(
            trainer.model.parameters()
        ).device

        if model_device.type != "cuda":
            raise RuntimeError(
                "Trainer model is not "
                "on CUDA: "
                f"{model_device}"
            )

        if (
                trainer.args.metric_for_best_model
                != "fine_macro_f1"
        ):
            raise RuntimeError(
                "Unexpected best-model "
                "metric: "
                f"{trainer.args.metric_for_best_model}"
            )

        if (
                not trainer.args
                        .load_best_model_at_end
        ):
            raise RuntimeError(
                "load_best_model_at_end "
                "must be enabled."
            )

        if (
                early_stopping_callback
                        .early_stopping_patience
                != (
                args
                        .early_stopping_patience
        )
        ):
            raise RuntimeError(
                "Early stopping patience "
                "configuration mismatch."
            )

        if (
                not math.isclose(
                    float(
                        early_stopping_callback
                                .early_stopping_threshold
                    ),
                    float(
                        args
                                .early_stopping_threshold
                    ),
                    rel_tol=0.0,
                    abs_tol=1e-12,
                )
        ):
            raise RuntimeError(
                "Early stopping threshold "
                "configuration mismatch."
            )

        batch = collator(
            [
                dataset[index]
                for index in range(
                len(dataset)
            )
            ]
        )

        device_batch = {
            key: (
                value.to(
                    model_device
                )
                if torch.is_tensor(
                    value
                )
                else value
            )
            for key, value
            in batch.items()
        }

        trainer.model.train()

        trainer.model.zero_grad(
            set_to_none=True
        )

        outputs = trainer.model(
            **device_batch
        )

        if outputs.loss is None:
            raise RuntimeError(
                "LayerNorm model did not "
                "produce a loss."
            )

        if not torch.isfinite(
                outputs.loss
        ):
            raise RuntimeError(
                "LayerNorm model loss "
                "is not finite."
            )

        expected_shape = (
            args.eval_batch_size,
            label_mapping.num_labels,
        )

        if (
                tuple(
                    outputs.logits.shape
                )
                != expected_shape
        ):
            raise RuntimeError(
                "Unexpected logits shape: "
                f"{tuple(outputs.logits.shape)}"
            )

        outputs.loss.backward()

        classifier_gradient_ok = (
            has_finite_nonzero_gradient(
                trainer.model.classifier
            )
        )

        if not classifier_gradient_ok:
            raise RuntimeError(
                "LayerNorm classifier "
                "did not receive a "
                "non-zero gradient."
            )

        layer_norm_gradient_ok = (
            has_finite_nonzero_gradient(
                trainer.model
                .classifier
                .layer_norm
            )
        )

        if not layer_norm_gradient_ok:
            raise RuntimeError(
                "LayerNorm parameters "
                "did not receive a "
                "non-zero gradient."
            )

        trainer.model.zero_grad(
            set_to_none=True
        )

        trainer_metrics = (
            trainer.evaluate()
        )

    required_metrics = (
        "eval_loss",
        "eval_fine_accuracy",
        "eval_fine_macro_f1",
        "eval_coarse_accuracy",
        "eval_coarse_macro_f1",
    )

    for metric_name in (
            required_metrics
    ):
        value = trainer_metrics.get(
            metric_name
        )

        if value is None:
            raise RuntimeError(
                "Trainer preflight metric "
                "is missing: "
                f"{metric_name}"
            )

        if not math.isfinite(
                float(
                    value
                )
        ):
            raise RuntimeError(
                "Trainer preflight metric "
                "is not finite: "
                f"{metric_name}={value}"
            )

    print()
    print(
        "LayerNorm ELECTRA "
        "CUDA preflight completed"
    )

    print()
    print(
        "Model: "
        f"{args.model}"
    )

    print(
        "Revision: "
        f"{args.revision}"
    )

    print(
        "Tokenizer class: "
        f"{type(tokenizer).__name__}"
    )

    print(
        "Tokenizer vocab: "
        f"{tokenizer_vocab_size}"
    )

    print(
        "Model vocab: "
        f"{model_vocab_size}"
    )

    print(
        "Embedding vocab: "
        f"{embedding_vocab_size}"
    )

    print(
        "Special tokens/input: "
        f"{special_token_count}"
    )

    print()
    print(
        "Device: "
        f"{model_device}"
    )

    print(
        "Device name: "
        f"{torch.cuda.get_device_name(model_device)}"
    )

    print()
    print(
        "Encoder layers: "
        f"{model.config.num_hidden_layers}"
    )

    print(
        "Hidden size: "
        f"{model.config.hidden_size}"
    )

    print(
        "Intermediate size: "
        f"{model.config.intermediate_size}"
    )

    print(
        "Attention heads: "
        f"{model.config.num_attention_heads}"
    )

    print()
    print(
        "LayerNorm eps: "
        f"{model.classifier.layer_norm.eps}"
    )

    print(
        "Classifier dropout: "
        f"{model.classifier.dropout.p}"
    )

    print()
    print(
        "Samples: "
        f"{len(dataset)}"
    )

    print(
        "Fine logits: "
        f"{tuple(outputs.logits.shape)}"
    )

    print(
        "Loss: "
        f"{float(outputs.loss.detach().cpu()):.6f}"
    )

    print(
        "Classifier gradient: "
        f"{classifier_gradient_ok}"
    )

    print(
        "LayerNorm gradient: "
        f"{layer_norm_gradient_ok}"
    )

    print()
    print(
        "Trainer eval loss: "
        f"{trainer_metrics['eval_loss']:.6f}"
    )

    print(
        "Trainer Fine accuracy: "
        f"{trainer_metrics['eval_fine_accuracy']:.6f}"
    )

    print(
        "Trainer Fine Macro F1: "
        f"{trainer_metrics['eval_fine_macro_f1']:.6f}"
    )

    print(
        "Trainer derived Coarse "
        "Macro F1: "
        f"{trainer_metrics['eval_coarse_macro_f1']:.6f}"
    )

    print()
    print(
        "Early stopping patience: "
        f"{args.early_stopping_patience}"
    )

    print(
        "Early stopping threshold: "
        f"{args.early_stopping_threshold}"
    )

    print(
        "Best-model metric: "
        f"{trainer.args.metric_for_best_model}"
    )

    print(
        "Load best model at end: "
        f"{trainer.args.load_best_model_at_end}"
    )

    print()
    print(
        "LAYERNORM CUDA PREFLIGHT: PASS"
    )


if __name__ == "__main__":
    main()
