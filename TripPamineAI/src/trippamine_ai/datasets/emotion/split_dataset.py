import hashlib
from enum import Enum

from pydantic import BaseModel

from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


class DatasetSplit(str, Enum):
    TRAINING = "training"
    VALIDATION = "validation"
    TEST = "test"


class SplitManifestEntry(BaseModel):
    record_id: str
    profile_id: str
    talk_id: str

    source_split: str
    dataset_split: DatasetSplit


class ProfileDatasetSplitter:

    def __init__(
        self,
        salt: str,
        training_ratio: float = 0.8,
        validation_ratio: float = 0.1,
        test_ratio: float = 0.1,
    ) -> None:
        total_ratio = (
            training_ratio
            + validation_ratio
            + test_ratio
        )

        if abs(total_ratio - 1.0) > 1e-9:
            raise ValueError(
                "Split ratios must sum to 1.0."
            )

        if not salt.strip():
            raise ValueError(
                "Split salt must not be empty."
            )

        self.salt = salt

        self.training_threshold = (
            training_ratio
        )

        self.validation_threshold = (
            training_ratio
            + validation_ratio
        )

    def assign_profile(
        self,
        profile_id: str,
    ) -> DatasetSplit:
        value = self._hash_to_ratio(
            profile_id
        )

        if value < self.training_threshold:
            return DatasetSplit.TRAINING

        if value < self.validation_threshold:
            return DatasetSplit.VALIDATION

        return DatasetSplit.TEST

    def create_manifest_entry(
        self,
        record: NormalizedEmotionDialogue,
    ) -> SplitManifestEntry:
        return SplitManifestEntry(
            record_id=record.record_id,
            profile_id=(
                record.source.profile_id
            ),
            talk_id=(
                record.source.talk_id
            ),
            source_split=(
                record.source.split
            ),
            dataset_split=self.assign_profile(
                record.source.profile_id
            ),
        )

    def _hash_to_ratio(
        self,
        profile_id: str,
    ) -> float:
        raw = (
            f"{self.salt}:{profile_id}"
            .encode("utf-8")
        )

        digest = hashlib.sha256(
            raw
        ).digest()

        value = int.from_bytes(
            digest[:8],
            byteorder="big",
            signed=False,
        )

        return value / (2**64)