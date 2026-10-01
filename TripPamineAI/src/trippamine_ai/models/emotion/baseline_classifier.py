import pickle
from pathlib import Path
from typing import Literal

from sklearn.feature_extraction.text import (
    TfidfVectorizer,
)
from sklearn.linear_model import (
    LogisticRegression,
)
from sklearn.pipeline import (
    FeatureUnion,
    Pipeline,
)


VectorizerType = Literal[
    "word",
    "char",
    "hybrid",
]


class BaselineEmotionClassifier:

    def __init__(
        self,
        class_weight: str | None = None,
        vectorizer_type: VectorizerType = "word",
    ) -> None:
        if vectorizer_type == "word":
            vectorizer = TfidfVectorizer(
                analyzer="word",
                ngram_range=(
                    1,
                    2,
                ),
                min_df=2,
                max_features=100_000,
                lowercase=False,
                token_pattern=(
                    r"(?u)\b\w+\b"
                ),
                sublinear_tf=True,
            )

        elif vectorizer_type == "char":
            vectorizer = TfidfVectorizer(
                analyzer="char",
                ngram_range=(
                    2,
                    5,
                ),
                min_df=2,
                max_features=100_000,
                lowercase=False,
                sublinear_tf=True,
            )

        elif vectorizer_type == "hybrid":
            vectorizer = FeatureUnion(
                [
                    (
                        "word",
                        TfidfVectorizer(
                            analyzer="word",
                            ngram_range=(
                                1,
                                2,
                            ),
                            min_df=2,
                            max_features=100_000,
                            lowercase=False,
                            token_pattern=(
                                r"(?u)\b\w+\b"
                            ),
                            sublinear_tf=True,
                        ),
                    ),
                    (
                        "char",
                        TfidfVectorizer(
                            analyzer="char",
                            ngram_range=(
                                2,
                                5,
                            ),
                            min_df=2,
                            max_features=100_000,
                            lowercase=False,
                            sublinear_tf=True,
                        ),
                    ),
                ]
            )

        else:
            raise ValueError(
                "Unsupported vectorizer "
                f"type: {vectorizer_type}"
            )

        self.vectorizer_type = (
            vectorizer_type
        )

        self.pipeline = Pipeline(
            [
                (
                    "tfidf",
                    vectorizer,
                ),
                (
                    "classifier",
                    LogisticRegression(
                        solver="saga",
                        C=1.0,
                        l1_ratio=0.0,
                        max_iter=500,
                        random_state=42,
                        class_weight=class_weight,
                    ),
                ),
            ]
        )

    def fit(
        self,
        texts: list[str],
        labels: list[str],
    ) -> None:
        self._validate_training_data(
            texts=texts,
            labels=labels,
        )

        self.pipeline.fit(
            texts,
            labels,
        )

    def predict(
        self,
        texts: list[str],
    ) -> list[str]:
        if not texts:
            raise ValueError(
                "Prediction texts "
                "must not be empty."
            )

        predictions = (
            self.pipeline.predict(
                texts
            )
        )

        return [
            str(label)
            for label in predictions
        ]

    def save(
        self,
        output_path: Path,
    ) -> None:
        output_path.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        if output_path.exists():
            raise FileExistsError(
                "Model output already exists: "
                f"{output_path}"
            )

        with output_path.open(
            "wb"
        ) as file:
            pickle.dump(
                self.pipeline,
                file,
                protocol=(
                    pickle.HIGHEST_PROTOCOL
                ),
            )

    def get_parameters(
        self,
    ) -> dict:
        vectorizer = (
            self.pipeline.named_steps[
                "tfidf"
            ]
        )

        classifier = (
            self.pipeline.named_steps[
                "classifier"
            ]
        )

        if self.vectorizer_type == "hybrid":
            transformers = {
                name: transformer
                for name, transformer
                in vectorizer.transformer_list
            }

            tfidf_parameters = {
                "type": "hybrid",
                "word": (
                    self._get_tfidf_parameters(
                        transformers["word"]
                    )
                ),
                "char": (
                    self._get_tfidf_parameters(
                        transformers["char"]
                    )
                ),
            }

        else:
            tfidf_parameters = (
                self._get_tfidf_parameters(
                    vectorizer
                )
            )

        return {
            "vectorizer_type": (
                self.vectorizer_type
            ),
            "tfidf": (
                tfidf_parameters
            ),
            "classifier": {
                "type": (
                    "LogisticRegression"
                ),
                "solver": (
                    classifier.solver
                ),
                "C": classifier.C,
                "l1_ratio": (
                    classifier.l1_ratio
                ),
                "max_iter": (
                    classifier.max_iter
                ),
                "random_state": (
                    classifier.random_state
                ),
                "class_weight": (
                    classifier.class_weight
                ),
            },
        }

    @staticmethod
    def _get_tfidf_parameters(
        vectorizer: TfidfVectorizer,
    ) -> dict:
        return {
            "analyzer": (
                vectorizer.analyzer
            ),
            "ngram_range": list(
                vectorizer.ngram_range
            ),
            "min_df": (
                vectorizer.min_df
            ),
            "max_features": (
                vectorizer.max_features
            ),
            "lowercase": (
                vectorizer.lowercase
            ),
            "token_pattern": (
                vectorizer.token_pattern
            ),
            "sublinear_tf": (
                vectorizer.sublinear_tf
            ),
        }

    @staticmethod
    def _validate_training_data(
        texts: list[str],
        labels: list[str],
    ) -> None:
        if not texts:
            raise ValueError(
                "Training texts "
                "must not be empty."
            )

        if len(texts) != len(labels):
            raise ValueError(
                "Training text/label "
                "count mismatch."
            )

        if any(
            not text.strip()
            for text in texts
        ):
            raise ValueError(
                "Training text contains "
                "an empty value."
            )

        if any(
            not label.strip()
            for label in labels
        ):
            raise ValueError(
                "Training label contains "
                "an empty value."
            )

        if len(set(labels)) < 2:
            raise ValueError(
                "Training requires at "
                "least two classes."
            )