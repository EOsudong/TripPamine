# FastAPI 웹 API 서버 엔트리포인트

# main.py
import os
import uuid
import shutil
import base64
from fastapi import FastAPI, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
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
        file_id = str(uuid.uuid4())
        img_path = os.path.join(TEMP_DIR, f"{file_id}_orig.jpg")
        mask_path = os.path.join(TEMP_DIR, f"{file_id}_mask.png")
        out_path = os.path.join(OUTPUT_DIR, f"{file_id}_result.jpg")

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
            # FileResponse 전송 시간은 이 계측에 포함되지 않으며 클라이언트 벤치마크에서 측정한다.
            return FileResponse(out_path, media_type="image/jpeg")
        finally:
            for path in (img_path, mask_path):
                if os.path.exists(path):
                    os.remove(path)


# 2. 다중 사진 자동 공통 인물 모자이크 API (2장 이상 등장 시 보존)
@app.post("/api/multi-mosaic")
async def process_multi_mosaic(
    images: list[UploadFile] = File(...)
):
    with measure_vision_request("multi-mosaic", len(images)) as metrics:
        saved_paths = []
        try:
            with metrics.stage("upload_ms"):
                for img in images:
                    file_id = str(uuid.uuid4())
                    ext = img.filename.split(".")[-1] if "." in img.filename else "jpg"
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
                    if os.path.exists(r_path):
                        os.remove(r_path)

            return {"results": encoded_results}
        finally:
            for path in saved_paths:
                if os.path.exists(path):
                    os.remove(path)


@app.post("/api/best-shot")
async def evaluate_best_shot(
    images: list[UploadFile] = File(...)
):
    with measure_vision_request("best-shot", len(images)) as metrics:
        saved_paths = []
        original_filenames = []
        try:
            with metrics.stage("upload_ms"):
                for img in images:
                    file_id = str(uuid.uuid4())
                    ext = img.filename.split(".")[-1] if "." in img.filename else "jpg"
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
                if os.path.exists(path):
                    os.remove(path)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=False)