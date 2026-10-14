# FastAPI 웹 API 서버 엔트리포인트

# main.py
import os
import uuid
import shutil
import base64
from fastapi import FastAPI, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from mosaic_service import MosaicService
from multi_mosaic_service import MultiMosaicService
from best_shot_service import BestShotService

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

# 1. 단일 사진 수동 브러시 모자이크 API
@app.post("/api/mosaic")
async def process_mosaic(
    image: UploadFile = File(...),
    mask: UploadFile = File(...)
):
    file_id = str(uuid.uuid4())
    img_path = os.path.join(TEMP_DIR, f"{file_id}_orig.jpg")
    mask_path = os.path.join(TEMP_DIR, f"{file_id}_mask.png")
    out_path = os.path.join(OUTPUT_DIR, f"{file_id}_result.jpg")

    with open(img_path, "wb") as f:
        shutil.copyfileobj(image.file, f)
    with open(mask_path, "wb") as f:
        shutil.copyfileobj(mask.file, f)

    try:
        mosaic_service.apply_mosaic_with_mask(img_path, mask_path, out_path)
        return FileResponse(out_path, media_type="image/jpeg")
    finally:
        if os.path.exists(img_path): os.remove(img_path)
        if os.path.exists(mask_path): os.remove(mask_path)

# 2. 다중 사진 자동 공통 인물 모자이크 API (2장 이상 등장 시 보존)
@app.post("/api/multi-mosaic")
async def process_multi_mosaic(
    images: list[UploadFile] = File(...)
):
    saved_paths = []
    
    for img in images:
        file_id = str(uuid.uuid4())
        ext = img.filename.split(".")[-1] if "." in img.filename else "jpg"
        img_path = os.path.join(TEMP_DIR, f"{file_id}.{ext}")
        
        with open(img_path, "wb") as f:
            shutil.copyfileobj(img.file, f)
        saved_paths.append(img_path)

    try:
        # 2장 이상 등장한 인물 추출 및 행인 자동 모자이크 수행
        result_paths = multi_mosaic_service.process_multiple_images(saved_paths, min_occurrences=2)

        # 결과 이미지들을 Base64 데이터 문자열 리스트로 변환
        encoded_results = []
        for r_path in result_paths:
            with open(r_path, "rb") as f:
                b64_str = base64.b64encode(f.read()).decode("utf-8")
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
    saved_paths = []
    original_filenames = []

    for img in images:
        file_id = str(uuid.uuid4())
        ext = img.filename.split(".")[-1] if "." in img.filename else "jpg"
        img_path = os.path.join(TEMP_DIR, f"{file_id}.{ext}")

        with open(img_path, "wb") as f:
            shutil.copyfileobj(img.file, f)
        saved_paths.append(img_path)
        original_filenames.append(img.filename)

    try:
        # 베스트 샷 분석 및 순위 산출
        ranked_results = best_shot_service.rank_best_shots(saved_paths)

        # 이미지 데이터(Base64) 및 평가 결과 결합
        response_data = []
        for item in ranked_results:
            with open(item["path"], "rb") as f:
                b64_str = base64.b64encode(f.read()).decode("utf-8")

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
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)