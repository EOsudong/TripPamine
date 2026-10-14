# multi_mosaic_service.py
import os
import urllib.request
import cv2
import numpy as np
from ultralytics import YOLO

class MultiMosaicService:
    def __init__(self):
        # 1. YOLOv8 사람 탐지 모델
        self.yolo_model = YOLO('yolov8n.pt')

        # 2. YuNet 정밀 얼굴 탐지 모델
        self.yunet_path = "face_detection_yunet_2023mar.onnx"
        if not os.path.exists(self.yunet_path):
            print("YuNet 모델 다운로드 중...")
            url = "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
            urllib.request.urlretrieve(url, self.yunet_path)

        # 3. SFace 얼굴 특징점 추출 모델
        self.sface_path = "face_recognition_sface_2021dec.onnx"
        if not os.path.exists(self.sface_path):
            print("SFace 모델 다운로드 중...")
            url = "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx"
            urllib.request.urlretrieve(url, self.sface_path)

        self.recognizer = cv2.FaceRecognizerSF.create(self.sface_path, "")

    def _apply_ellipse_blur(self, image, x1, y1, x2, y2):
        """얼굴 형태에 맞춘 타원형 가우시안 블러"""
        roi = image[y1:y2, x1:x2]
        roi_h, roi_w = roi.shape[:2]
        if roi_h <= 0 or roi_w <= 0:
            return

        mask = np.zeros((roi_h, roi_w), dtype=np.uint8)
        cv2.ellipse(mask, (roi_w // 2, roi_h // 2), (roi_w // 2, roi_h // 2), 0, 0, 360, 255, -1)
        blurred = cv2.GaussianBlur(roi, (99, 99), 30)
        mask_3ch = cv2.merge([mask, mask, mask])
        np.copyto(roi, blurred, where=(mask_3ch == 255))

    def process_multiple_images(self, image_paths: list[str], min_occurrences: int = 2) -> list[str]:
        images_data = []

        for img_idx, img_path in enumerate(image_paths):
            image = cv2.imread(img_path)
            if image is None:
                continue

            img_h, img_w = image.shape[:2]
            img_area = img_h * img_w

            # Step 1: YOLOv8로 실제 사람(class 0) 영역 추출
            yolo_results = self.yolo_model(image, classes=[0], conf=0.15, verbose=False)
            person_boxes = []
            for res in yolo_results:
                for box in res.boxes:
                    px1, py1, px2, py2 = map(int, box.xyxy[0])
                    person_boxes.append((max(0, px1), max(0, py1), min(img_w, px2), min(img_h, py2)))

            # Step 2: YuNet 정밀 얼굴 탐지
            detector = cv2.FaceDetectorYN.create(
                model=self.yunet_path, config="", input_size=(img_w, img_h),
                score_threshold=0.50, nms_threshold=0.3
            )
            _, faces = detector.detect(image)

            valid_faces = []
            if faces is not None and len(person_boxes) > 0:
                for face in faces:
                    fx, fy, fw, fh = map(int, face[:4])
                    if fw <= 0 or fh <= 0:
                        continue

                    fcx, fcy = fx + fw // 2, fy + fh // 2

                    # [핵심 1] 얼굴이 사람 상자 상단 50% 영역(머리/상반신)에 존재하는지 검증
                    in_valid_upper_person = False
                    for px1, py1, px2, py2 in person_boxes:
                        person_height = py2 - py1
                        if person_height <= 0:
                            continue

                        # 얼굴 중심점 Y좌표가 사람 상자의 상단 50% 이내인지 확인
                        rel_y = (fcy - py1) / float(person_height)
                        if px1 <= fcx <= px2 and py1 <= fcy <= py2 and rel_y <= 0.50:
                            in_valid_upper_person = True
                            break

                    # 허리, 다리, 팔꿈치 등 하단 노이즈에서 잡힌 가짜 얼굴 거르기
                    if not in_valid_upper_person:
                        continue

                    # SFace 특징점 추출
                    aligned_face = self.recognizer.alignCrop(image, face)
                    feature = self.recognizer.feature(aligned_face)

                    pad_w, pad_h = int(fw * 0.15), int(fh * 0.15)
                    coords = (
                        max(0, fx - pad_w), max(0, fy - pad_h),
                        min(img_w, fx + fw + pad_w), min(img_h, fy + fh + pad_h)
                    )
                    face_ratio = (fw * fh) / img_area

                    valid_faces.append({
                        "coords": coords,
                        "feature": feature,
                        "face_ratio": face_ratio,
                        "is_protagonist": False
                    })

            # 크기순 정렬
            valid_faces.sort(key=lambda x: x["face_ratio"], reverse=True)

            # 전면 메인 인물(상위 2명 대형 얼굴) 주인공 지정
            for idx, f in enumerate(valid_faces[:2]):
                if f["face_ratio"] >= 0.015 or idx == 0:
                    f["is_protagonist"] = True

            images_data.append({
                "img_idx": img_idx,
                "path": img_path,
                "image": image,
                "faces": valid_faces
            })

        # Step 3: 주인공 임베딩 매칭
        protagonist_features = []
        for img_obj in images_data:
            for f in img_obj["faces"]:
                if f["is_protagonist"]:
                    protagonist_features.append(f["feature"])

        COSINE_THRESHOLD = 0.30

        for img_obj in images_data:
            for f in img_obj["faces"]:
                if not f["is_protagonist"] and len(protagonist_features) > 0:
                    max_score = max([
                        self.recognizer.match(p_feat, f["feature"], cv2.FaceRecognizerSF_FR_COSINE)
                        for p_feat in protagonist_features
                    ])
                    if max_score >= COSINE_THRESHOLD:
                        f["is_protagonist"] = True

        # Step 4: 주인공 보호 영역 강화 및 모자이크 적용
        output_paths = []
        for img_obj in images_data:
            image = img_obj["image"].copy()
            faces = img_obj["faces"]
            
            # [핵심 2] 주인공 좌표에 안전 여유 패딩(Guard Margin) 적용
            protagonist_coords = []
            for f in faces:
                if f["is_protagonist"]:
                    px1, py1, px2, py2 = f["coords"]
                    pw, ph = px2 - px1, py2 - py1
                    # 주인공 주변 안전 지대 확장
                    protagonist_coords.append((
                        max(0, px1 - int(pw * 0.3)),
                        max(0, py1 - int(ph * 0.3)),
                        min(image.shape[1], px2 + int(pw * 0.3)),
                        min(image.shape[0], py2 + int(ph * 0.6))  # 상반신 아래쪽 패딩 확장
                    ))

            for f in faces:
                if not f["is_protagonist"]:
                    x1, y1, x2, y2 = f["coords"]

                    # 주인공 보호 영역과 교차하는지 검사
                    overlap = False
                    for px1, py1, px2, py2 in protagonist_coords:
                        inter_x1 = max(x1, px1)
                        inter_y1 = max(y1, py1)
                        inter_x2 = min(x2, px2)
                        inter_y2 = min(y2, py2)

                        if inter_x2 > inter_x1 and inter_y2 > inter_y1:
                            overlap = True
                            break

                    if not overlap:
                        self._apply_ellipse_blur(image, x1, y1, x2, y2)

            out_path = img_obj["path"].replace(".", "_multi_result.")
            cv2.imwrite(out_path, image)
            output_paths.append(out_path)

        return output_paths

if __name__ == "__main__":
    os.makedirs("output", exist_ok=True)
    service = MultiMosaicService()