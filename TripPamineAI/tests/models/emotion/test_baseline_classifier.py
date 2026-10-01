from pathlib import Path

from trippamine_ai.models.emotion.baseline_classifier import (
    BaselineEmotionClassifier,
)


def create_training_data():
    texts = [
        "너무 화가 나고 짜증이 난다",
        "정말 화가 나서 견딜 수 없다",
        "마음이 너무 슬프고 눈물이 난다",
        "정말 슬퍼서 아무것도 하기 싫다",
        "오늘 정말 기쁘고 행복하다",
        "좋은 일이 생겨서 너무 기쁘다",
    ]

    labels = [
        "E10",
        "E10",
        "E20",
        "E20",
        "E60",
        "E60",
    ]

    return texts, labels


def test_fit_and_predict():
    texts, labels = (
        create_training_data()
    )

    classifier = (
        BaselineEmotionClassifier()
    )

    classifier.fit(
        texts,
        labels,
    )

    predictions = classifier.predict(
        [
            "오늘 너무 화가 난다",
            "좋은 일이 있어서 기쁘다",
        ]
    )

    assert len(predictions) == 2

    assert all(
        prediction
        in {
            "E10",
            "E20",
            "E60",
        }
        for prediction
        in predictions
    )


def test_model_can_be_saved(
        tmp_path: Path,
):
    texts, labels = (
        create_training_data()
    )

    classifier = (
        BaselineEmotionClassifier()
    )

    classifier.fit(
        texts,
        labels,
    )

    output_path = (
            tmp_path
            / "model.pkl"
    )

    classifier.save(
        output_path
    )

    assert output_path.exists()

    assert (
            output_path.stat().st_size
            > 0
    )


def test_parameters_are_exposed():
    classifier = (
        BaselineEmotionClassifier()
    )

    parameters = (
        classifier.get_parameters()
    )

    assert (
            parameters["tfidf"][
                "ngram_range"
            ]
            == [1, 2]
    )

    assert (
            parameters["classifier"][
                "class_weight"
            ]
            is None
    )


def test_balanced_class_weight():
    classifier = (
        BaselineEmotionClassifier(
            class_weight="balanced"
        )
    )

    parameters = (
        classifier.get_parameters()
    )

    assert (
            parameters["classifier"][
                "class_weight"
            ]
            == "balanced"
    )

def test_character_vectorizer_parameters():
    classifier = (
        BaselineEmotionClassifier(
            vectorizer_type="char"
        )
    )

    parameters = (
        classifier.get_parameters()
    )

    assert (
        parameters["tfidf"][
            "analyzer"
        ]
        == "char"
    )

    assert (
        parameters["tfidf"][
            "ngram_range"
        ]
        == [2, 5]
    )

    assert (
        parameters["classifier"][
            "class_weight"
        ]
        is None
    )

def test_character_fit_and_predict():
    texts, labels = (
        create_training_data()
    )

    classifier = (
        BaselineEmotionClassifier(
            vectorizer_type="char"
        )
    )

    classifier.fit(
        texts,
        labels,
    )

    predictions = classifier.predict(
        [
            "오늘 정말 화가 나",
            "기분이 너무 기쁘다",
        ]
    )

    assert len(predictions) == 2

def test_hybrid_vectorizer_parameters():
    classifier = (
        BaselineEmotionClassifier(
            vectorizer_type="hybrid"
        )
    )

    parameters = (
        classifier.get_parameters()
    )

    assert (
        parameters["vectorizer_type"]
        == "hybrid"
    )

    assert (
        parameters["tfidf"]["type"]
        == "hybrid"
    )

    assert (
        parameters["tfidf"]["word"][
            "analyzer"
        ]
        == "word"
    )

    assert (
        parameters["tfidf"]["word"][
            "ngram_range"
        ]
        == [1, 2]
    )

    assert (
        parameters["tfidf"]["char"][
            "analyzer"
        ]
        == "char"
    )

    assert (
        parameters["tfidf"]["char"][
            "ngram_range"
        ]
        == [2, 5]
    )

    assert (
        parameters["classifier"][
            "class_weight"
        ]
        is None
    )

def test_hybrid_fit_and_predict():
    texts, labels = (
        create_training_data()
    )

    classifier = (
        BaselineEmotionClassifier(
            vectorizer_type="hybrid"
        )
    )

    classifier.fit(
        texts,
        labels,
    )

    predictions = classifier.predict(
        [
            "오늘 너무 화가 나",
            "좋은 일이 생겨서 기쁘다",
        ]
    )

    assert len(predictions) == 2

    assert all(
        prediction
        in {
            "E10",
            "E20",
            "E60",
        }
        for prediction
        in predictions
    )