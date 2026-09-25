import hashlib

import orjson
from pydantic import BaseModel

from trippamine_ai.datasets.emotion.codebook import (
    AGE_CODEBOOK,
    COMPUTER_CODEBOOK,
    DISEASE_CODEBOOK,
    EMOTION_CODEBOOK,
    GENDER_CODEBOOK,
    SITUATION_CODEBOOK,
    get_coarse_emotion,
)
from trippamine_ai.datasets.emotion.models import (
    EmotionDialogueRecord,
)
from trippamine_ai.datasets.emotion.validator import (
    EmotionDatasetValidator,
    ValidationResult,
)


class NormalizedSource(BaseModel):
    dataset: str
    version: str
    split: str
    profile_id: str
    talk_id: str


class NormalizedPersona(BaseModel):
    age_code: str
    age: str

    gender_code: str
    gender: str

    computer_code: str
    computer: str


class NormalizedEmotion(BaseModel):
    emotion_code: str
    emotion: str

    coarse_emotion: str

    situation_code: str
    situation: str

    disease_code: str
    disease: str


class NormalizedTurn(BaseModel):
    turn: int
    human: str
    assistant: str


class NormalizedEmotionDialogue(BaseModel):
    record_id: str

    source: NormalizedSource

    persona: NormalizedPersona
    emotion: NormalizedEmotion

    turns: list[NormalizedTurn]

    quality: ValidationResult


class EmotionDialogueNormalizer:
    DATASET_NAME = "aihub-emotional-dialogue"

    def __init__(
            self,
            dataset_version: str = "v1",
    ) -> None:
        self.dataset_version = dataset_version
        self.validator = EmotionDatasetValidator()

    def normalize(
            self,
            record: EmotionDialogueRecord,
            split: str,
    ) -> NormalizedEmotionDialogue:
        quality = self.validator.validate(record)

        persona = record.profile.persona
        emotion = record.profile.emotion
        content = record.talk.content

        age_code = persona.human[0]
        gender_code = persona.human[1]
        computer_code = persona.computer[0]

        situation_code = emotion.situation[0]
        disease_code = emotion.situation[1]
        emotion_code = emotion.type

        turns = [
            NormalizedTurn(
                turn=1,
                human=content.HS01.strip(),
                assistant=content.SS01.strip(),
            ),
            NormalizedTurn(
                turn=2,
                human=content.HS02.strip(),
                assistant=content.SS02.strip(),
            ),
        ]

        if content.HS03.strip() or content.SS03.strip():
            turns.append(
                NormalizedTurn(
                    turn=3,
                    human=content.HS03.strip(),
                    assistant=content.SS03.strip(),
                )
            )

        return NormalizedEmotionDialogue(
            record_id=self._create_record_id(record),
            source=NormalizedSource(
                dataset=self.DATASET_NAME,
                version=self.dataset_version,
                split=split,
                profile_id=record.profile.persona_id,
                talk_id=record.talk.id.talk_id,
            ),
            persona=NormalizedPersona(
                age_code=age_code,
                age=AGE_CODEBOOK[age_code],
                gender_code=gender_code,
                gender=GENDER_CODEBOOK[gender_code],
                computer_code=computer_code,
                computer=COMPUTER_CODEBOOK[computer_code],
            ),
            emotion=NormalizedEmotion(
                emotion_code=emotion_code,
                emotion=EMOTION_CODEBOOK[emotion_code],
                coarse_emotion=get_coarse_emotion(
                    emotion_code
                ),
                situation_code=situation_code,
                situation=SITUATION_CODEBOOK[
                    situation_code
                ],
                disease_code=disease_code,
                disease=DISEASE_CODEBOOK[
                    disease_code
                ],
            ),
            turns=turns,
            quality=quality,
        )

    @staticmethod
    def _create_record_id(
            record: EmotionDialogueRecord,
    ) -> str:
        canonical_data = orjson.dumps(
            record.model_dump(
                by_alias=True,
                mode="json",
            ),
            option=orjson.OPT_SORT_KEYS,
        )

        return hashlib.sha256(
            canonical_data
        ).hexdigest()
