from collections.abc import Iterable
from math import ceil
from math import floor

from pydantic import BaseModel


class TokenLengthThreshold(BaseModel):
    threshold: int
    exceeded_count: int
    exceeded_rate: float


class TokenLengthStatistics(BaseModel):
    total_samples: int

    minimum: int
    maximum: int
    mean: float

    p50: float
    p90: float
    p95: float
    p99: float

    thresholds: list[
        TokenLengthThreshold
    ]


class TokenizerStatisticsAnalyzer:

    DEFAULT_THRESHOLDS = (
        32,
        64,
        96,
        128,
        256,
        512,
    )

    def analyze(
        self,
        lengths: Iterable[int],
        thresholds: Iterable[int] | None = None,
    ) -> TokenLengthStatistics:
        values = list(lengths)

        if not values:
            raise ValueError(
                "Token lengths must "
                "not be empty."
            )

        if any(
            isinstance(length, bool)
            or not isinstance(length, int)
            or length <= 0
            for length in values
        ):
            raise ValueError(
                "Token lengths must be "
                "positive integers."
            )

        threshold_values = (
            list(
                self.DEFAULT_THRESHOLDS
            )
            if thresholds is None
            else list(thresholds)
        )

        if not threshold_values:
            raise ValueError(
                "Thresholds must "
                "not be empty."
            )

        if any(
            isinstance(threshold, bool)
            or not isinstance(
                threshold,
                int,
            )
            or threshold <= 0
            for threshold
            in threshold_values
        ):
            raise ValueError(
                "Thresholds must be "
                "positive integers."
            )

        if (
            len(threshold_values)
            != len(set(threshold_values))
        ):
            raise ValueError(
                "Thresholds must "
                "not contain duplicates."
            )

        sorted_lengths = sorted(values)

        total_samples = len(
            sorted_lengths
        )

        threshold_statistics = [
            self._build_threshold(
                lengths=sorted_lengths,
                threshold=threshold,
            )
            for threshold in sorted(
                threshold_values
            )
        ]

        return TokenLengthStatistics(
            total_samples=total_samples,
            minimum=sorted_lengths[0],
            maximum=sorted_lengths[-1],
            mean=(
                sum(sorted_lengths)
                / total_samples
            ),
            p50=self._percentile(
                sorted_lengths,
                0.50,
            ),
            p90=self._percentile(
                sorted_lengths,
                0.90,
            ),
            p95=self._percentile(
                sorted_lengths,
                0.95,
            ),
            p99=self._percentile(
                sorted_lengths,
                0.99,
            ),
            thresholds=(
                threshold_statistics
            ),
        )

    @staticmethod
    def _build_threshold(
        lengths: list[int],
        threshold: int,
    ) -> TokenLengthThreshold:
        exceeded_count = sum(
            1
            for length in lengths
            if length > threshold
        )

        return TokenLengthThreshold(
            threshold=threshold,
            exceeded_count=(
                exceeded_count
            ),
            exceeded_rate=(
                exceeded_count
                / len(lengths)
            ),
        )

    @staticmethod
    def _percentile(
        sorted_values: list[int],
        percentile: float,
    ) -> float:
        if (
            percentile < 0.0
            or percentile > 1.0
        ):
            raise ValueError(
                "Percentile must be "
                "between 0 and 1."
            )

        if len(sorted_values) == 1:
            return float(
                sorted_values[0]
            )

        position = (
            (len(sorted_values) - 1)
            * percentile
        )

        lower_index = floor(
            position
        )

        upper_index = ceil(
            position
        )

        lower_value = (
            sorted_values[
                lower_index
            ]
        )

        upper_value = (
            sorted_values[
                upper_index
            ]
        )

        if (
            lower_index
            == upper_index
        ):
            return float(
                lower_value
            )

        fraction = (
            position
            - lower_index
        )

        return float(
            lower_value
            + (
                upper_value
                - lower_value
            )
            * fraction
        )