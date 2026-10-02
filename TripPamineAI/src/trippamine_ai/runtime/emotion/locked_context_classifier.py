from __future__ import annotations

import math
import time
from enum import Enum
from pathlib import Path
from typing import Protocol

import torch
from pydantic import BaseModel
from transformers import AutoTokenizer

from trippamine_ai.datasets.emotion.codebook import (
    EMOTION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.models.emotion.label_mapping import (
    EmotionLabelMapping,
)
from trippamine_ai.models.emotion.layernorm_transformer import (
    LayerNormElectraForSequenceClassification,
)


LOCKED_MARGIN_THRESHOLD = 0.16

DEFAULT_MAX_LENGTH = 96

DEFAULT_POLICY_VERSION = (
    "q17-locked-margin-0.16-v1"
)

DEFAULT_MODEL_VERSION = (
    "ko42-kc42-rawlogit-0.5-0.5"
)


class ContextMode(
    str,
    Enum,
):
    FIRST2 = "FIRST2"
    FULL_FALLBACK = "FULL_FALLBACK"


class EnsemblePrediction(
    BaseModel,
):
    fine_label: str
    fine_label_name: str
    coarse_label: str

    confidence: float
    margin: float


class EmotionRuntimeResult(
    BaseModel,
):
    fine_label: str
    fine_label_name: str
    coarse_label: str

    confidence: float

    first2_confidence: float
    first2_margin: float

    context_mode: ContextMode
    fallback_triggered: bool

    human_turn_count: int
    turns_used: int

    policy_version: str
    model_version: str

    latency_ms: float


class EmotionPredictor(
    Protocol,
):
    @property
    def model_version(
            self,
    ) -> str:
        ...

    def predict(
            self,
            text: str,
    ) -> EnsemblePrediction:
        ...


def normalize_human_turns(
        human_turns: list[str],
) -> list[str]:
    if not isinstance(
            human_turns,
            list,
    ):
        raise TypeError(
            "human_turns must be a list."
        )

    normalized = [
        turn.strip()
        for turn
        in human_turns
        if (
            isinstance(
                turn,
                str,
            )
            and turn.strip()
        )
    ]

    if not normalized:
        raise ValueError(
            "At least one non-empty "
            "human turn is required."
        )

    return normalized


def build_first2_text(
        human_turns: list[str],
) -> str:
    normalized = (
        normalize_human_turns(
            human_turns
        )
    )

    return "\n".join(
        normalized[
            :2
        ]
    )


def build_full_text(
        human_turns: list[str],
) -> str:
    normalized = (
        normalize_human_turns(
            human_turns
        )
    )

    return "\n".join(
        normalized
    )


def build_ensemble_prediction(
        logits: torch.Tensor,
        label_mapping: EmotionLabelMapping,
) -> EnsemblePrediction:
    if logits.ndim == 2:
        if logits.shape[0] != 1:
            raise ValueError(
                "Runtime logits batch "
                "size must be 1."
            )

        logits = logits[
            0
        ]

    if logits.ndim != 1:
        raise ValueError(
            "Runtime logits must be "
            "1D or [1, num_labels]."
        )

    if (
            logits.shape[0]
            != label_mapping.num_labels
    ):
        raise ValueError(
            "Runtime logits label "
            "count mismatch."
        )

    if not torch.isfinite(
            logits
    ).all():
        raise ValueError(
            "Runtime logits contain "
            "non-finite values."
        )

    probabilities = torch.softmax(
        logits
        .detach()
        .float()
        .cpu(),
        dim=-1,
    )

    top2 = torch.topk(
        probabilities,
        k=2,
    )

    top1_probability = float(
        top2.values[
            0
        ].item()
    )

    top2_probability = float(
        top2.values[
            1
        ].item()
    )

    predicted_id = int(
        top2.indices[
            0
        ].item()
    )

    fine_label = (
        label_mapping.decode(
            predicted_id
        )
    )

    return EnsemblePrediction(
        fine_label=fine_label,
        fine_label_name=(
            EMOTION_CODEBOOK[
                fine_label
            ]
        ),
        coarse_label=(
            get_coarse_emotion(
                fine_label
            )
        ),
        confidence=(
            top1_probability
        ),
        margin=(
            top1_probability
            - top2_probability
        ),
    )


class LayerNormKoKcEnsemblePredictor:
    def __init__(
            self,
            ko_model_dir: Path,
            kc_model_dir: Path,
            *,
            model_version: str = (
                DEFAULT_MODEL_VERSION
            ),
            max_length: int = (
                DEFAULT_MAX_LENGTH
            ),
            device: str = "cuda",
    ) -> None:
        if (
                isinstance(
                    max_length,
                    bool,
                )
                or not isinstance(
                    max_length,
                    int,
                )
                or max_length <= 0
        ):
            raise ValueError(
                "max_length must be "
                "a positive integer."
            )

        self._ko_model_dir = (
            Path(
                ko_model_dir
            )
            .expanduser()
            .resolve()
        )

        self._kc_model_dir = (
            Path(
                kc_model_dir
            )
            .expanduser()
            .resolve()
        )

        if not (
            self._ko_model_dir
            .is_dir()
        ):
            raise FileNotFoundError(
                "Ko model directory "
                "does not exist: "
                f"{self._ko_model_dir}"
            )

        if not (
            self._kc_model_dir
            .is_dir()
        ):
            raise FileNotFoundError(
                "Kc model directory "
                "does not exist: "
                f"{self._kc_model_dir}"
            )

        if (
                device.startswith(
                    "cuda"
                )
                and not (
                    torch.cuda
                    .is_available()
                )
        ):
            raise RuntimeError(
                "CUDA was requested "
                "but is not available."
            )

        self._device = (
            torch.device(
                device
            )
        )

        self._max_length = (
            max_length
        )

        self._model_version = (
            model_version
        )

        self._label_mapping = (
            EmotionLabelMapping()
        )

        self._ko_tokenizer = (
            AutoTokenizer
            .from_pretrained(
                self._ko_model_dir,
                local_files_only=True,
            )
        )

        self._kc_tokenizer = (
            AutoTokenizer
            .from_pretrained(
                self._kc_model_dir,
                local_files_only=True,
            )
        )

        self._ko_model = (
            LayerNormElectraForSequenceClassification
            .from_pretrained(
                self._ko_model_dir,
                local_files_only=True,
            )
        )

        self._kc_model = (
            LayerNormElectraForSequenceClassification
            .from_pretrained(
                self._kc_model_dir,
                local_files_only=True,
            )
        )

        self._validate_model(
            self._ko_model,
            "Ko",
        )

        self._validate_model(
            self._kc_model,
            "Kc",
        )

        self._ko_model.to(
            self._device
        )

        self._kc_model.to(
            self._device
        )

        self._ko_model.eval()
        self._kc_model.eval()

    @property
    def model_version(
            self,
    ) -> str:
        return self._model_version

    def _validate_model(
            self,
            model,
            name: str,
    ) -> None:
        if (
                int(
                    model.config.num_labels
                )
                != (
                    self._label_mapping
                    .num_labels
                )
        ):
            raise ValueError(
                f"{name} model label "
                "count mismatch."
            )

    def _predict_logits(
            self,
            *,
            text: str,
            tokenizer,
            model,
    ) -> torch.Tensor:
        encoded = tokenizer(
            text,
            add_special_tokens=True,
            truncation=True,
            max_length=(
                self._max_length
            ),
            padding=False,
            return_tensors="pt",
        )

        encoded = {
            key: value.to(
                self._device
            )
            for key, value
            in encoded.items()
        }

        with torch.inference_mode():
            outputs = model(
                **encoded
            )

        logits = (
            outputs.logits
            .detach()
            .float()
        )

        if logits.shape != (
                1,
                self._label_mapping.num_labels,
        ):
            raise RuntimeError(
                "Unexpected runtime "
                "logits shape: "
                f"{tuple(logits.shape)}"
            )

        return logits

    def predict(
            self,
            text: str,
    ) -> EnsemblePrediction:
        normalized_text = (
            text.strip()
        )

        if not normalized_text:
            raise ValueError(
                "Prediction text must "
                "not be empty."
            )

        ko_logits = (
            self._predict_logits(
                text=normalized_text,
                tokenizer=(
                    self._ko_tokenizer
                ),
                model=(
                    self._ko_model
                ),
            )
        )

        kc_logits = (
            self._predict_logits(
                text=normalized_text,
                tokenizer=(
                    self._kc_tokenizer
                ),
                model=(
                    self._kc_model
                ),
            )
        )

        ensemble_logits = (
            0.5
            * ko_logits
            + 0.5
            * kc_logits
        )

        return (
            build_ensemble_prediction(
                logits=(
                    ensemble_logits
                ),
                label_mapping=(
                    self._label_mapping
                ),
            )
        )


class LockedContextEmotionClassifier:
    def __init__(
            self,
            predictor: EmotionPredictor,
            *,
            margin_threshold: float = (
                LOCKED_MARGIN_THRESHOLD
            ),
            policy_version: str = (
                DEFAULT_POLICY_VERSION
            ),
    ) -> None:
        if not math.isclose(
                float(
                    margin_threshold
                ),
                LOCKED_MARGIN_THRESHOLD,
                rel_tol=0.0,
                abs_tol=1e-15,
        ):
            raise ValueError(
                "Q17 locked margin "
                "threshold must remain "
                "0.16."
            )

        self._predictor = (
            predictor
        )

        self._margin_threshold = (
            LOCKED_MARGIN_THRESHOLD
        )

        self._policy_version = (
            policy_version
        )

    @property
    def margin_threshold(
            self,
    ) -> float:
        return (
            self._margin_threshold
        )

    @property
    def policy_version(
            self,
    ) -> str:
        return (
            self._policy_version
        )

    @property
    def model_version(
            self,
    ) -> str:
        return (
            self._predictor
            .model_version
        )

    def predict(
            self,
            human_turns: list[str],
    ) -> EmotionRuntimeResult:
        started_at = (
            time.perf_counter()
        )

        normalized_turns = (
            normalize_human_turns(
                human_turns
            )
        )

        first2_text = "\n".join(
            normalized_turns[
                :2
            ]
        )

        first2_prediction = (
            self._predictor.predict(
                first2_text
            )
        )

        has_extra_context = (
            len(
                normalized_turns
            )
            > 2
        )

        fallback_triggered = (
            has_extra_context
            and (
                first2_prediction.margin
                <= self._margin_threshold
            )
        )

        if fallback_triggered:
            full_text = "\n".join(
                normalized_turns
            )

            final_prediction = (
                self._predictor.predict(
                    full_text
                )
            )

            context_mode = (
                ContextMode
                .FULL_FALLBACK
            )

            turns_used = len(
                normalized_turns
            )

        else:
            final_prediction = (
                first2_prediction
            )

            context_mode = (
                ContextMode.FIRST2
            )

            turns_used = min(
                2,
                len(
                    normalized_turns
                ),
            )

        latency_ms = (
            time.perf_counter()
            - started_at
        ) * 1000.0

        return EmotionRuntimeResult(
            fine_label=(
                final_prediction
                .fine_label
            ),
            fine_label_name=(
                final_prediction
                .fine_label_name
            ),
            coarse_label=(
                final_prediction
                .coarse_label
            ),
            confidence=(
                final_prediction
                .confidence
            ),
            first2_confidence=(
                first2_prediction
                .confidence
            ),
            first2_margin=(
                first2_prediction
                .margin
            ),
            context_mode=(
                context_mode
            ),
            fallback_triggered=(
                fallback_triggered
            ),
            human_turn_count=len(
                normalized_turns
            ),
            turns_used=(
                turns_used
            ),
            policy_version=(
                self._policy_version
            ),
            model_version=(
                self._predictor
                .model_version
            ),
            latency_ms=(
                latency_ms
            ),
        )