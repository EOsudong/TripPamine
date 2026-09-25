from pathlib import Path

from trippamine_ai.runtime.emotion.locked_context_classifier import (
    LayerNormKoKcEnsemblePredictor,
    LockedContextEmotionClassifier,
)


KO_MODEL = Path(
    "artifacts/emotion/transformer/"
    "t0-q13-a-layernorm-es1-b32-lr4e5-e4-ml96-s42-v1/"
    "best-model"
)

KC_MODEL = Path(
    "artifacts/emotion/transformer/"
    "t0-q14-a-kcelectra-v2022-layernorm-es1-b32-lr4e5-e4-ml96-s42-v1/"
    "best-model"
)


def main():
    predictor = (
        LayerNormKoKcEnsemblePredictor(
            ko_model_dir=KO_MODEL,
            kc_model_dir=KC_MODEL,
            device="cuda",
        )
    )

    classifier = (
        LockedContextEmotionClassifier(
            predictor
        )
    )

    result = classifier.predict(
        [
            "요즘 취업 준비 때문에 마음이 계속 불안해.",
            "결과가 좋지 않을까 봐 잠도 잘 안 와.",
            "부모님 기대까지 생각하면 더 초조해지는 것 같아.",
        ]
    )

    print()
    print(
        "===== Q17-C5 RUNTIME SMOKE ====="
    )

    print(
        result.model_dump_json(
            indent=2,
        )
    )

    print()
    print(
        "Q17-C5 RUNTIME SMOKE = PASS"
    )


if __name__ == "__main__":
    main()