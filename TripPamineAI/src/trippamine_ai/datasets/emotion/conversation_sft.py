import hashlib
from typing import Literal

import orjson
from pydantic import BaseModel

from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


class SFTMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class ConversationSFTSource(BaseModel):
    dataset: str
    version: str
    split: str

    record_id: str
    profile_id: str
    talk_id: str


class ConversationSFTSample(BaseModel):
    id: str
    content_hash: str

    prompt: list[SFTMessage]
    completion: list[SFTMessage]

    target_turn: int

    emotion_code: str
    emotion_name: str
    coarse_emotion: str

    situation_code: str
    situation: str

    source_quality_status: str
    source_quality_issue_codes: list[str]

    source: ConversationSFTSource


class ConversationSFTBuilder:

    def build(
        self,
        record: NormalizedEmotionDialogue,
    ) -> list[ConversationSFTSample]:
        samples: list[ConversationSFTSample] = []
        history: list[SFTMessage] = []

        for turn in record.turns:
            human = turn.human.strip()
            assistant = turn.assistant.strip()

            # 대화 중간에 불완전한 Turn이 등장하면
            # 이후 Context를 신뢰할 수 없으므로 중단한다.
            if not human or not assistant:
                break

            user_message = SFTMessage(
                role="user",
                content=human,
            )

            assistant_message = SFTMessage(
                role="assistant",
                content=assistant,
            )

            prompt = [
                *history,
                user_message,
            ]

            completion = [
                assistant_message,
            ]

            content_hash = self._create_content_hash(
                prompt=prompt,
                completion=completion,
            )

            samples.append(
                ConversationSFTSample(
                    id=(
                        f"{record.record_id}:"
                        f"turn-{turn.turn}"
                    ),
                    content_hash=content_hash,
                    prompt=prompt,
                    completion=completion,
                    target_turn=turn.turn,
                    emotion_code=(
                        record.emotion.emotion_code
                    ),
                    emotion_name=(
                        record.emotion.emotion
                    ),
                    coarse_emotion=(
                        record.emotion.coarse_emotion
                    ),
                    situation_code=(
                        record.emotion.situation_code
                    ),
                    situation=(
                        record.emotion.situation
                    ),
                    source_quality_status=(
                        record.quality.status.value
                    ),
                    source_quality_issue_codes=[
                        issue.code
                        for issue
                        in record.quality.issues
                    ],
                    source=ConversationSFTSource(
                        dataset=(
                            record.source.dataset
                        ),
                        version=(
                            record.source.version
                        ),
                        split=(
                            record.source.split
                        ),
                        record_id=record.record_id,
                        profile_id=(
                            record.source.profile_id
                        ),
                        talk_id=(
                            record.source.talk_id
                        ),
                    ),
                )
            )

            history.extend(
                [
                    user_message,
                    assistant_message,
                ]
            )

        return samples

    @staticmethod
    def _create_content_hash(
        prompt: list[SFTMessage],
        completion: list[SFTMessage],
    ) -> str:
        canonical_data = orjson.dumps(
            {
                "prompt": [
                    message.model_dump(
                        mode="json"
                    )
                    for message in prompt
                ],
                "completion": [
                    message.model_dump(
                        mode="json"
                    )
                    for message in completion
                ],
            },
            option=orjson.OPT_SORT_KEYS,
        )

        return hashlib.sha256(
            canonical_data
        ).hexdigest()