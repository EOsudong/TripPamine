"""Vision AI 요청 처리 시간과 입출력 크기를 기록한다.

외부 패키지 없이 main.py의 measure_vision_request 인터페이스를 제공한다.
"""

import logging
import os
import time
from contextlib import contextmanager


LOGGER = logging.getLogger("uvicorn.error")


class VisionRequestMetrics:
    """단일 Vision AI 요청의 처리 단계별 지표를 수집한다."""

    def __init__(self, operation, image_count):
        self.operation = operation
        self.image_count = image_count
        self.input_bytes = 0
        self.output_bytes = 0
        self.stage_times_ms = {}

    @contextmanager
    def stage(self, name):
        """업로드·추론·인코딩 구간의 소요 시간을 밀리초로 누적한다."""
        started_at = time.perf_counter()
        try:
            yield
        finally:
            elapsed_ms = (time.perf_counter() - started_at) * 1000
            self.stage_times_ms[name] = self.stage_times_ms.get(name, 0) + elapsed_ms

    def input_file(self, path):
        """입력 파일 크기만 더한다. 파일명이나 경로는 로그에 남기지 않는다."""
        self.input_bytes += self._file_size(path)

    def output_file(self, path):
        """생성된 출력 파일의 크기를 바이트 단위로 누적한다."""
        self.output_bytes += self._file_size(path)

    @staticmethod
    def _file_size(path):
        try:
            return os.path.getsize(path)
        except OSError as exc:
            # 부가적인 측정 실패로 정상적인 이미지 처리를 중단시키지 않는다.
            LOGGER.warning("[Vision AI] 파일 크기 측정 실패 (%s)", type(exc).__name__)
            return 0


@contextmanager
def measure_vision_request(operation, image_count):
    """요청 종료 시 성공·실패 및 단계별 처리 시간을 한 줄로 기록한다."""
    metrics = VisionRequestMetrics(operation, image_count)
    started_at = time.perf_counter()
    error_type = "none"
    try:
        yield metrics
    except BaseException as exc:
        # 호출부의 HTTP 오류 처리에는 관여하지 않고 예외를 그대로 전달한다.
        error_type = type(exc).__name__
        raise
    finally:
        total_ms = (time.perf_counter() - started_at) * 1000
        LOGGER.info(
            "[Vision AI] metrics operation=%s images=%d total_ms=%.2f "
            "upload_ms=%.2f inference_ms=%.2f encode_ms=%.2f "
            "input_bytes=%d output_bytes=%d error=%s",
            metrics.operation,
            metrics.image_count,
            total_ms,
            metrics.stage_times_ms.get("upload_ms", 0),
            metrics.stage_times_ms.get("inference_ms", 0),
            metrics.stage_times_ms.get("encode_ms", 0),
            metrics.input_bytes,
            metrics.output_bytes,
            error_type,
        )
