# mosaic_service.py
# YuNet 얼굴 탐지 기반 핀포인트 모자이크 처리 서비스
# venv\Scripts\activate -> python main.py으로 실행

import os
import urllib.request
import cv2
import numpy as np

class MosaicService:
    def __init__(self):
        # OpenCV YuNet 초경량/고성능 얼굴 전용 탐지 모델 (약 335KB)
        self.model_path = "face_detection_yunet_2023mar.onnx"
        if not os.path.exists(self.model_path):
            print("얼굴 전용 탐지 모델(YuNet) 다운로드 중...")
            url = "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
            urllib.request.urlretrieve(url, self.model_path)
            print("다운로드 완료!")

    def _apply_ellipse_blur(self, image, x1, y1, x2, y2):
        """얼굴 위치에 딱 맞춘 타원형 가우시안 블러 적용"""
        roi = image[y1:y2, x1:x2]
        roi_h, roi_w = roi.shape[:2]
        if roi_h <= 0 or roi_w <= 0:
            return

        # 1. 타원 마스크 생성
        mask = np.zeros((roi_h, roi_w), dtype=np.uint8)
        center = (roi_w // 2, roi_h // 2)
        axes = (roi_w // 2, roi_h // 2)
        cv2.ellipse(mask, center, axes, 0, 0, 360, 255, -1)

        # 2. 가우시안 블러 적용
        blurred = cv2.GaussianBlur(roi, (99, 99), 30)

        # 3. 타원 영역에만 블러 합성
        mask_3ch = cv2.merge([mask, mask, mask])
        np.copyto(roi, blurred, where=(mask_3ch == 255))

    def apply_mosaic_with_mask(self, image_path: str, mask_path: str, output_path: str):
        image = cv2.imread(image_path)
        if image is None:
            raise FileNotFoundError(f"원본 이미지를 찾을 수 없습니다: {image_path}")

        img_h, img_w = image.shape[:2]

        # 마스크 이진화 (200 이상 순수 흰색만 추출)
        mask = cv2.imread(mask_path, cv2.IMREAD_GRAYSCALE)
        if mask is None:
            binary_mask = np.zeros((img_h, img_w), dtype=np.uint8)
        else:
            mask_resized = cv2.resize(mask, (img_w, img_h), interpolation=cv2.INTER_NEAREST)
            _, binary_mask = cv2.threshold(mask_resized, 200, 255, cv2.THRESH_BINARY)

        # YuNet 얼굴 탐지기 초기화 (입력 이미지 해상도 맞춤)
        detector = cv2.FaceDetectorYN.create(
            model=self.model_path,
            config="",
            input_size=(img_w, img_h),
            score_threshold=0.40,  # 가려지거나 작게 나온 얼굴도 탐지하도록 임계값 설정
            nms_threshold=0.3
        )

        # 얼굴 위치 정밀 탐지
        _, faces = detector.detect(image)

        if faces is not None:
            for face in faces:
                # 탐지된 실제 얼굴 좌표 (x, y, width, height)
                fx, fy, fw, fh = map(int, face[:4])

                # 여유 영역(Padding 15%) 부여
                pad_w = int(fw * 0.15)
                pad_h = int(fh * 0.15)

                x1 = max(0, fx - pad_w)
                y1 = max(0, fy - pad_h)
                x2 = min(img_w, fx + fw + pad_w)
                y2 = min(img_h, fy + fh + pad_h)

                # 탐지된 '실제 얼굴 영역' 마스크 검사
                face_mask_roi = binary_mask[y1:y2, x1:x2]
                face_area = (y2 - y1) * (x2 - x1)

                if face_area > 0:
                    painted_pixels = np.count_nonzero(face_mask_roi == 255)
                    coverage_ratio = painted_pixels / face_area
                else:
                    coverage_ratio = 0.0

                # 얼굴 영역 5% 이상 브러시 칠이 없으면 모자이크 처리
                if coverage_ratio < 0.05:
                    if x2 > x1 and y2 > y1:
                        self._apply_ellipse_blur(image, x1, y1, x2, y2)
                else:
                    print(f"보존 대상 얼굴 감지 (커버리지: {coverage_ratio*100:.1f}%): 모자이크 제외")

        cv2.imwrite(output_path, image)
        print(f"YuNet 얼굴 핀포인트 모자이크 처리 완료: {output_path}")

if __name__ == "__main__":
    os.makedirs("output", exist_ok=True)
    service = MosaicService()