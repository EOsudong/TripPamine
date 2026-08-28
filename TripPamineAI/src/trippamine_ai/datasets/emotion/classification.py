from pydantic import BaseModel

from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


class ClassificationSource(BaseModel):
    dataset: str
    version: str
    split: str
    profile_id: str
    talk_id: str


class EmotionClassificationSample(BaseModel):
    id: str

    text: str

    label: str
    label_name: str
    coarse_label: str

    situation_code: str
    situation: str

    quality_status: str
    quality_issue_codes: list[str]

    source: ClassificationSource


class EmotionClassificationBuilder:

    def build(
            self,
            record: NormalizedEmotionDialogue,
    ) -> EmotionClassificationSample:
        first_turn = record.turns[0]

        return EmotionClassificationSample(
            id=record.record_id,
            text=first_turn.human,
            label=record.emotion.emotion_code,
            label_name=record.emotion.emotion,
            coarse_label=record.emotion.coarse_emotion,
            situation_code=record.emotion.situation_code,
            situation=record.emotion.situation,
            quality_status=record.quality.status.value,
            quality_issue_codes=[
                issue.code
                for issue in record.quality.issues
            ],
            source=ClassificationSource(
                dataset=record.source.dataset,
                version=record.source.version,
                split=record.source.split,
                profile_id=record.source.profile_id,
                talk_id=record.source.talk_id,
            ),
        )
