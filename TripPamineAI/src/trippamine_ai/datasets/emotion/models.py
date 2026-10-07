from pydantic import BaseModel, ConfigDict, Field


class StrictBaseModel(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        populate_by_name=True,
    )


class PersonaInfo(StrictBaseModel):
    persona_id: str = Field(alias="persona-id")
    human: list[str]
    computer: list[str]


class EmotionInfo(StrictBaseModel):
    emotion_id: str = Field(alias="emotion-id")
    type: str
    situation: list[str]


class Profile(StrictBaseModel):
    persona_id: str = Field(alias="persona-id")
    persona: PersonaInfo
    emotion: EmotionInfo


class TalkId(StrictBaseModel):
    profile_id: str = Field(alias="profile-id")
    talk_id: str = Field(alias="talk-id")


class TalkContent(StrictBaseModel):
    HS01: str
    SS01: str
    HS02: str
    SS02: str
    HS03: str
    SS03: str


class Talk(StrictBaseModel):
    id: TalkId
    content: TalkContent


class EmotionDialogueRecord(StrictBaseModel):
    profile: Profile
    talk: Talk