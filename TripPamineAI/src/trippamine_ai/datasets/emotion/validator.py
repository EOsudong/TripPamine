import re
from enum import Enum

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.codebook import (
    AGE_CODEBOOK,
    COMPUTER_CODEBOOK,
    DISEASE_CODEBOOK,
    EMOTION_CODEBOOK,
    GENDER_CODEBOOK,
    SITUATION_CODEBOOK,
)
from trippamine_ai.datasets.emotion.models import EmotionDialogueRecord


class ValidationStatus(str, Enum):
    VALID = "VALID"
    WARNING = "WARNING"
    REJECTED = "REJECTED"


class IssueSeverity(str, Enum):
    WARNING = "WARNING"
    ERROR = "ERROR"


class ValidationIssue(BaseModel):
    severity: IssueSeverity
    code: str
    field: str
    message: str


class ValidationResult(BaseModel):
    status: ValidationStatus
    issues: list[ValidationIssue]


class EmotionDatasetValidator:

    _SENTENCE_END_PATTERN = re.compile(r"[.!?]+")
    _ENGLISH_PATTERN = re.compile(r"[A-Za-z]")
    _DIGIT_PATTERN = re.compile(r"\d")

    def validate(
        self,
        record: EmotionDialogueRecord,
    ) -> ValidationResult:
        issues: list[ValidationIssue] = []

        self._validate_profile_ids(record, issues)
        self._validate_persona(record, issues)
        self._validate_emotion(record, issues)
        self._validate_required_turns(record, issues)
        self._validate_third_turn(record, issues)
        self._validate_text_quality(record, issues)

        return ValidationResult(
            status=self._resolve_status(issues),
            issues=issues,
        )

    def _validate_profile_ids(
        self,
        record: EmotionDialogueRecord,
        issues: list[ValidationIssue],
    ) -> None:
        profile_id = record.profile.persona_id
        talk_profile_id = record.talk.id.profile_id

        if profile_id != talk_profile_id:
            self._add_error(
                issues,
                code="PROFILE_ID_MISMATCH",
                field="talk.id.profile-id",
                message=(
                    f"profile.persona-id '{profile_id}'와 "
                    f"talk.id.profile-id '{talk_profile_id}'가 일치하지 않습니다."
                ),
            )

    def _validate_persona(
        self,
        record: EmotionDialogueRecord,
        issues: list[ValidationIssue],
    ) -> None:
        persona = record.profile.persona

        if len(persona.human) != 2:
            self._add_error(
                issues,
                code="INVALID_HUMAN_PERSONA",
                field="profile.persona.human",
                message="human은 연령 코드와 성별 코드 2개로 구성되어야 합니다.",
            )
            return

        if len(persona.computer) != 1:
            self._add_error(
                issues,
                code="INVALID_COMPUTER_PERSONA",
                field="profile.persona.computer",
                message="computer는 시스템 응답 코드 1개로 구성되어야 합니다.",
            )
            return

        age_code = persona.human[0]
        gender_code = persona.human[1]
        computer_code = persona.computer[0]

        if age_code not in AGE_CODEBOOK:
            self._add_error(
                issues,
                code="UNKNOWN_AGE_CODE",
                field="profile.persona.human[0]",
                message=f"알 수 없는 연령 코드입니다: {age_code}",
            )

        if gender_code not in GENDER_CODEBOOK:
            self._add_error(
                issues,
                code="UNKNOWN_GENDER_CODE",
                field="profile.persona.human[1]",
                message=f"알 수 없는 성별 코드입니다: {gender_code}",
            )

        if computer_code not in COMPUTER_CODEBOOK:
            self._add_error(
                issues,
                code="UNKNOWN_COMPUTER_CODE",
                field="profile.persona.computer[0]",
                message=f"알 수 없는 시스템 응답 코드입니다: {computer_code}",
            )

        expected_persona_id = (
            f"{age_code}_{gender_code}_{computer_code}"
        )

        if persona.persona_id != expected_persona_id:
            self._add_error(
                issues,
                code="PERSONA_ID_MISMATCH",
                field="profile.persona.persona-id",
                message=(
                    f"persona-id '{persona.persona_id}'가 "
                    f"human/computer 조합 '{expected_persona_id}'와 "
                    "일치하지 않습니다."
                ),
            )

    def _validate_emotion(
        self,
        record: EmotionDialogueRecord,
        issues: list[ValidationIssue],
    ) -> None:
        emotion = record.profile.emotion

        if len(emotion.situation) != 2:
            self._add_error(
                issues,
                code="INVALID_SITUATION_STRUCTURE",
                field="profile.emotion.situation",
                message="situation은 상황 코드와 질병 코드 2개로 구성되어야 합니다.",
            )
            return

        situation_code = emotion.situation[0]
        disease_code = emotion.situation[1]
        emotion_code = emotion.type

        if situation_code not in SITUATION_CODEBOOK:
            self._add_error(
                issues,
                code="UNKNOWN_SITUATION_CODE",
                field="profile.emotion.situation[0]",
                message=f"알 수 없는 상황 코드입니다: {situation_code}",
            )

        if disease_code not in DISEASE_CODEBOOK:
            self._add_error(
                issues,
                code="UNKNOWN_DISEASE_CODE",
                field="profile.emotion.situation[1]",
                message=f"알 수 없는 질병 코드입니다: {disease_code}",
            )

        if emotion_code not in EMOTION_CODEBOOK:
            self._add_error(
                issues,
                code="UNKNOWN_EMOTION_CODE",
                field="profile.emotion.type",
                message=f"알 수 없는 감정 코드입니다: {emotion_code}",
            )

        expected_emotion_id = (
            f"{situation_code}_{disease_code}_{emotion_code}"
        )

        if emotion.emotion_id != expected_emotion_id:
            self._add_error(
                issues,
                code="EMOTION_ID_MISMATCH",
                field="profile.emotion.emotion-id",
                message=(
                    f"emotion-id '{emotion.emotion_id}'가 "
                    f"situation/type 조합 '{expected_emotion_id}'와 "
                    "일치하지 않습니다."
                ),
            )

    def _validate_required_turns(
        self,
        record: EmotionDialogueRecord,
        issues: list[ValidationIssue],
    ) -> None:
        content = record.talk.content

        required_turns = {
            "HS01": content.HS01,
            "SS01": content.SS01,
            "HS02": content.HS02,
            "SS02": content.SS02,
        }

        for field_name, value in required_turns.items():
            if not value.strip():
                self._add_error(
                    issues,
                    code="EMPTY_REQUIRED_TURN",
                    field=f"talk.content.{field_name}",
                    message=f"{field_name} 발화가 비어 있습니다.",
                )

    def _validate_third_turn(
        self,
        record: EmotionDialogueRecord,
        issues: list[ValidationIssue],
    ) -> None:
        content = record.talk.content

        has_hs03 = bool(content.HS03.strip())
        has_ss03 = bool(content.SS03.strip())

        if has_hs03 != has_ss03:
            self._add_warning(
                issues,
                code="INCOMPLETE_THIRD_TURN",
                field="talk.content",
                message=(
                    "HS03와 SS03 중 한쪽만 존재합니다. "
                    "3번째 대화 턴의 짝을 확인해야 합니다."
                ),
            )

    def _validate_text_quality(
        self,
        record: EmotionDialogueRecord,
        issues: list[ValidationIssue],
    ) -> None:
        content = record.talk.content

        utterances = {
            "HS01": content.HS01,
            "SS01": content.SS01,
            "HS02": content.HS02,
            "SS02": content.SS02,
            "HS03": content.HS03,
            "SS03": content.SS03,
        }

        for field_name, text in utterances.items():
            stripped = text.strip()

            if not stripped:
                continue

            self._validate_word_count(
                field_name,
                stripped,
                issues,
            )
            self._validate_sentence_count(
                field_name,
                stripped,
                issues,
            )
            self._validate_punctuation_count(
                field_name,
                stripped,
                issues,
            )
            self._validate_english_and_digits(
                field_name,
                stripped,
                issues,
            )

    def _validate_word_count(
        self,
        field_name: str,
        text: str,
        issues: list[ValidationIssue],
    ) -> None:
        word_count = len(text.split())

        if word_count < 2 or word_count > 17:
            self._add_warning(
                issues,
                code="WORD_COUNT_OUT_OF_RANGE",
                field=f"talk.content.{field_name}",
                message=(
                    f"{field_name}의 어절 수가 {word_count}개입니다. "
                    "가이드 기준은 2~17어절입니다."
                ),
            )

    def _validate_sentence_count(
        self,
        field_name: str,
        text: str,
        issues: list[ValidationIssue],
    ) -> None:
        sentence_count = len(
            self._SENTENCE_END_PATTERN.findall(text)
        )

        if sentence_count > 2:
            self._add_warning(
                issues,
                code="TOO_MANY_SENTENCES",
                field=f"talk.content.{field_name}",
                message=(
                    f"{field_name}에 문장 종결 부호 그룹이 "
                    f"{sentence_count}개 있습니다."
                ),
            )

    def _validate_punctuation_count(
        self,
        field_name: str,
        text: str,
        issues: list[ValidationIssue],
    ) -> None:
        punctuation_count = sum(
            text.count(mark)
            for mark in (".", "!", "?")
        )

        if punctuation_count > 3:
            self._add_warning(
                issues,
                code="TOO_MANY_PUNCTUATION_MARKS",
                field=f"talk.content.{field_name}",
                message=(
                    f"{field_name}에 온점/느낌표/물음표가 "
                    f"{punctuation_count}개 있습니다."
                ),
            )

    def _validate_english_and_digits(
        self,
        field_name: str,
        text: str,
        issues: list[ValidationIssue],
    ) -> None:
        if self._ENGLISH_PATTERN.search(text):
            self._add_warning(
                issues,
                code="CONTAINS_ENGLISH",
                field=f"talk.content.{field_name}",
                message=f"{field_name}에 영문자가 포함되어 있습니다.",
            )

        if self._DIGIT_PATTERN.search(text):
            self._add_warning(
                issues,
                code="CONTAINS_DIGIT",
                field=f"talk.content.{field_name}",
                message=f"{field_name}에 숫자가 포함되어 있습니다.",
            )

    @staticmethod
    def _resolve_status(
        issues: list[ValidationIssue],
    ) -> ValidationStatus:
        if any(
            issue.severity == IssueSeverity.ERROR
            for issue in issues
        ):
            return ValidationStatus.REJECTED

        if issues:
            return ValidationStatus.WARNING

        return ValidationStatus.VALID

    @staticmethod
    def _add_error(
        issues: list[ValidationIssue],
        code: str,
        field: str,
        message: str,
    ) -> None:
        issues.append(
            ValidationIssue(
                severity=IssueSeverity.ERROR,
                code=code,
                field=field,
                message=message,
            )
        )

    @staticmethod
    def _add_warning(
        issues: list[ValidationIssue],
        code: str,
        field: str,
        message: str,
    ) -> None:
        issues.append(
            ValidationIssue(
                severity=IssueSeverity.WARNING,
                code=code,
                field=field,
                message=message,
            )
        )