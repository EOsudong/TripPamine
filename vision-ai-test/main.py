# FastAPI 웹 API 서버 엔트리포인트

# main.py
import os
import uuid
import shutil
import base64
import logging
from contextlib import contextmanager
from threading import BoundedSemaphore
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.concurrency import run_in_threadpool
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from starlette.background import BackgroundTask
from mosaic_service import MosaicService
from multi_mosaic_service import MultiMosaicService
from best_shot_service import BestShotService
from vision_metrics import measure_vision_request

app = FastAPI(title="Vision AI Studio API")

# CORS 허용 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

mosaic_service = MosaicService()
best_shot_service = BestShotService()
multi_mosaic_service = MultiMosaicService()

TEMP_DIR = "temp"
OUTPUT_DIR = "output"
os.makedirs(TEMP_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)

LOGGER = logging.getLogger("uvicorn.error")
# CPU 추론 모델의 공유 상태와 프로세스 메모리를 보호하기 위해 한 번에 한 요청만 실행한다.
VISION_EXECUTION_SLOT = BoundedSemaphore(value=1)


@contextmanager
def limit_vision_execution():
    """대기 요청을 무한히 쌓지 않고 처리 중에는 즉시 429를 반환한다."""
    if not VISION_EXECUTION_SLOT.acquire(blocking=False):
        raise HTTPException(
            status_code=429,
            detail="다른 사진을 처리 중입니다. 잠시 후 다시 시도해 주세요.",
            headers={"Retry-After": "2"},
        )
    try:
        yield
    finally:
        VISION_EXECUTION_SLOT.release()


def remove_generated_file(path):
    """이번 요청에서 생성한 파일만 정리하고 기존 output 파일은 유지한다."""
    try:
        os.remove(path)
    except FileNotFoundError:
        pass
    except OSError as exc:
        # 파일 이름/개인 정보는 서버 로그에 남기지 않는다.
        LOGGER.warning("[Vision AI] 임시 파일 정리 실패 (%s)", type(exc).__name__)


# 세 모델 초기화와 필수 파일 상태를 확인해 Java 서버가 다른 8000번 포트 사용자를 구별한다.
@app.get("/health")
def health():
    models = {
        "yolo": multi_mosaic_service.yolo_model is not None,
        "yunet": os.path.isfile(mosaic_service.model_path),
        "sface": os.path.isfile(multi_mosaic_service.sface_path)
                 and multi_mosaic_service.recognizer is not None,
    }
    ready = all(models.values())
    return JSONResponse(
        status_code=200 if ready else 503,
        content={
            "service": "trippamine-vision",
            "status": "ready" if ready else "unavailable",
            "models": models,
        },
    )


# 1. 단일 사진 수동 브러시 모자이크 API
@app.post("/api/mosaic")
async def process_mosaic(
    image: UploadFile = File(...),
    mask: UploadFile = File(...)
):
    with measure_vision_request("mosaic", 1) as metrics:
        with limit_vision_execution():
            # 파일 저장·OpenCV 추론은 스레드풀에서 실행해 FastAPI 이벤트 루프를 보호한다.
            return await run_in_threadpool(process_mosaic_sync, image, mask, metrics)


def process_mosaic_sync(image, mask, metrics):
    file_id = str(uuid.uuid4())
    img_path = os.path.join(TEMP_DIR, f"{file_id}_orig.jpg")
    mask_path = os.path.join(TEMP_DIR, f"{file_id}_mask.png")
    out_path = os.path.join(OUTPUT_DIR, f"{file_id}_result.jpg")
    response_created = False
    try:
        with metrics.stage("upload_ms"):
            with open(img_path, "wb") as f:
                shutil.copyfileobj(image.file, f)
            metrics.input_file(img_path)
            with open(mask_path, "wb") as f:
                shutil.copyfileobj(mask.file, f)
            metrics.input_file(mask_path)

        with metrics.stage("inference_ms"):
            mosaic_service.apply_mosaic_with_mask(img_path, mask_path, out_path)

        metrics.output_file(out_path)
        # FileResponse가 결과를 모두 전송한 후 자신의 JPEG만 삭제한다.
        response = FileResponse(
            out_path,
            media_type="image/jpeg",
            background=BackgroundTask(remove_generated_file, out_path),
        )
        response_created = True
        return response
    finally:
        for path in (img_path, mask_path):
            remove_generated_file(path)
        # 추론 실패 등으로 응답을 생성하지 못했다면 미완성 출력도 즉시 정리한다.
        if not response_created:
            remove_generated_file(out_path)


# 2. 다중 사진 자동 공통 인물 모자이크 API (2장 이상 등장 시 보존)
@app.post("/api/multi-mosaic")
async def process_multi_mosaic(
    images: list[UploadFile] = File(...)
):
    with measure_vision_request("multi-mosaic", len(images)) as metrics:
        with limit_vision_execution():
            return await run_in_threadpool(process_multi_mosaic_sync, images, metrics)


def process_multi_mosaic_sync(images, metrics):
    saved_paths = []
    result_paths = []
    try:
        with metrics.stage("upload_ms"):
            for img in images:
                file_id = str(uuid.uuid4())
                # 사용자 파일명을 임시 경로에 직접 넣지 않으며 확장자도 허용된 값으로만 사용한다.
                ext = (img.filename or "").rsplit(".", 1)[-1].lower()
                if ext not in {"jpg", "jpeg", "png", "webp", "bmp"}:
                    ext = "jpg"
                img_path = os.path.join(TEMP_DIR, f"{file_id}.{ext}")
                saved_paths.append(img_path)
                with open(img_path, "wb") as f:
                    shutil.copyfileobj(img.file, f)
                metrics.input_file(img_path)

        with metrics.stage("inference_ms"):
            # 기존 YOLO·YuNet·SFace 추론 로직은 변경하지 않는다.
            result_paths = multi_mosaic_service.process_multiple_images(saved_paths, min_occurrences=2)

        encoded_results = []
        with metrics.stage("encode_ms"):
            for r_path in result_paths:
                with open(r_path, "rb") as f:
                    raw_image = f.read()
                metrics.output_bytes += len(raw_image)
                b64_str = base64.b64encode(raw_image).decode("utf-8")
                encoded_results.append(f"data:image/jpeg;base64,{b64_str}")

        return {"results": encoded_results}
    finally:
        # 서비스 실행 도중 예외가 발생해도 부분 생성된 결과 파일을 찾을 수 있도록 한다.
        for path in saved_paths:
            remove_generated_file(path.replace(".", "_multi_result."))
        for path in result_paths:
            remove_generated_file(path)
        for path in saved_paths:
            remove_generated_file(path)


@app.post("/api/best-shot")
async def evaluate_best_shot(
    images: list[UploadFile] = File(...)
):
    with measure_vision_request("best-shot", len(images)) as metrics:
        with limit_vision_execution():
            return await run_in_threadpool(evaluate_best_shot_sync, images, metrics)


def evaluate_best_shot_sync(images, metrics):
    saved_paths = []
    original_filenames = []
    try:
        with metrics.stage("upload_ms"):
            for img in images:
                file_id = str(uuid.uuid4())
                ext = (img.filename or "").rsplit(".", 1)[-1].lower()
                if ext not in {"jpg", "jpeg", "png", "webp", "bmp"}:
                    ext = "jpg"
                img_path = os.path.join(TEMP_DIR, f"{file_id}.{ext}")
                saved_paths.append(img_path)
                with open(img_path, "wb") as f:
                    shutil.copyfileobj(img.file, f)
                metrics.input_file(img_path)
                original_filenames.append(img.filename)

        with metrics.stage("inference_ms"):
            ranked_results = best_shot_service.rank_best_shots(saved_paths)

        response_data = []
        with metrics.stage("encode_ms"):
            for item in ranked_results:
                with open(item["path"], "rb") as f:
                    raw_image = f.read()
                metrics.output_bytes += len(raw_image)
                b64_str = base64.b64encode(raw_image).decode("utf-8")

                response_data.append({
                    "rank": item["rank"],
                    "total_score": item["total_score"],
                    "blur_score": item["blur_score"],
                    "color_score": item["color_score"],
                    "comp_score": item["comp_score"],
                    "tags": item["tags"],
                    "original_index": item["original_index"],
                    "filename": original_filenames[item["original_index"]],
                    "image_data": f"data:image/jpeg;base64,{b64_str}"
                })

        return {"results": response_data}
    finally:
        for path in saved_paths:
            remove_generated_file(path)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=False)
