from typing import Any, Literal

from trippamine_ai.datasets.emotion.conversation_sft import (
    ConversationSFTBuilder,
    ConversationSFTSample,
)
from trippamine_ai.datasets.emotion.normalizer import (
    NormalizedEmotionDialogue,
)


SplitName = Literal[
    "training",
    "validation",
    "test",
]

SPLIT_NAMES: tuple[
    SplitName,
    ...
] = (
    "training",
    "validation",
    "test",
)


def build_profile_split_map(
    manifest: dict[str, Any],
) -> dict[str, SplitName]:
    split = manifest.get(
        "split"
    )

    if not isinstance(
        split,
        dict,
    ):
        raise ValueError(
            "Manifest does not contain "
            "a valid split section."
        )

    profile_ids_by_split: dict[
        SplitName,
        set[str],
    ] = {}

    for split_name in SPLIT_NAMES:
        key = (
            f"{split_name}_profile_ids"
        )

        raw_profile_ids = split.get(
            key
        )

        if not isinstance(
            raw_profile_ids,
            list,
        ):
            raise ValueError(
                "Manifest does not contain "
                f"a valid '{key}' list."
            )

        if any(
            not isinstance(
                profile_id,
                str,
            )
            or not profile_id
            for profile_id
            in raw_profile_ids
        ):
            raise ValueError(
                "Manifest contains an "
                "invalid profile ID in "
                f"'{key}'."
            )

        if len(
            raw_profile_ids
        ) != len(
            set(raw_profile_ids)
        ):
            raise ValueError(
                "Manifest contains "
                "duplicate profile IDs "
                f"in '{key}'."
            )

        profile_ids_by_split[
            split_name
        ] = set(
            raw_profile_ids
        )

    training = (
        profile_ids_by_split[
            "training"
        ]
    )

    validation = (
        profile_ids_by_split[
            "validation"
        ]
    )

    test = (
        profile_ids_by_split[
            "test"
        ]
    )

    if training & validation:
        raise ValueError(
            "Training/validation "
            "profile overlap detected "
            "in manifest."
        )

    if training & test:
        raise ValueError(
            "Training/test profile "
            "overlap detected "
            "in manifest."
        )

    if validation & test:
        raise ValueError(
            "Validation/test profile "
            "overlap detected "
            "in manifest."
        )

    profile_split_map: dict[
        str,
        SplitName,
    ] = {}

    for split_name in SPLIT_NAMES:
        for profile_id in (
            profile_ids_by_split[
                split_name
            ]
        ):
            profile_split_map[
                profile_id
            ] = split_name

    if not profile_split_map:
        raise ValueError(
            "Manifest contains no "
            "profile assignments."
        )

    return profile_split_map


class ConversationSFTV2Builder:

    def __init__(
        self,
        profile_split_map: dict[
            str,
            SplitName,
        ],
    ) -> None:
        if not profile_split_map:
            raise ValueError(
                "Profile split map "
                "must not be empty."
            )

        self.profile_split_map = dict(
            profile_split_map
        )

        self.builder = (
            ConversationSFTBuilder()
        )

    @classmethod
    def from_manifest(
        cls,
        manifest: dict[str, Any],
    ) -> "ConversationSFTV2Builder":
        return cls(
            profile_split_map=(
                build_profile_split_map(
                    manifest
                )
            )
        )

    def build(
        self,
        record: NormalizedEmotionDialogue,
    ) -> tuple[
        SplitName,
        list[ConversationSFTSample],
    ]:
        profile_id = (
            record.source.profile_id
        )

        target_split = (
            self.profile_split_map.get(
                profile_id
            )
        )

        if target_split is None:
            raise ValueError(
                "Profile is missing from "
                "the v2 split manifest: "
                f"{profile_id}"
            )

        routed_record = (
            record.model_copy(
                deep=True
            )
        )

        routed_record.source.split = (
            target_split
        )

        samples = self.builder.build(
            routed_record
        )

        return (
            target_split,
            samples,
        )