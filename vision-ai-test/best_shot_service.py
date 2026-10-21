# best_shot_service.py
import os
import urllib.request
import cv2
import numpy as np

class BestShotService:
    def __init__(self):
        self.yunet_path = "face_detection_yunet_2023mar.onnx"
        if not os.path.exists(self.yunet_path):
            url = "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
            urllib.request.urlretrieve(url, self.yunet_path)

    def _evaluate_resolution(self, img) -> float:
        """1. 원본 해상도(픽셀 수) 화질 평가"""
        h, w = img.shape[:2]
        pixels = w * h
        # FHD (1920x1080 = 2,073,600 픽셀) 기준 점수산출
        score = min(100.0, (pixels / 2073600.0) * 100.0)
        return score

    def _evaluate_blur_roi(self, gray_img, faces) -> float:
        """2. 얼굴 영역(ROI) 정밀 선명도 측정"""
        h, w = gray_img.shape
        
        # 얼굴이 감지된 경우 배경을 제외한 얼굴 ROI 선명도 계산
        if faces is not None and len(faces) > 0:
            roi_scores = []
            for face in faces:
                fx, fy, fw, fh = map(int, face[:4])
                fx, fy = max(0, fx), max(0, fy)
                fw, fh = min(w - fx, fw), min(h - fy, fh)
                if fw > 10 and fh > 10:
                    face_roi = gray_img[fy:fy+fh, fx:fx+fw]
                    lap = cv2.Laplacian(face_roi, cv2.CV_64F).var()
                    roi_scores.append(lap)
            if roi_scores:
                avg_lap = sum(roi_scores) / len(roi_scores)
                return min(100.0, (avg_lap / 200.0) * 100.0)

        # 얼굴 미감지 시 표준 가우시안 필터링 적용 전체 측정
        resized = cv2.resize(gray_img, (600, int(h * (600.0 / w))), interpolation=cv2.INTER_AREA)
        blurred = cv2.GaussianBlur(resized, (3, 3), 0)
        lap = cv2.Laplacian(blurred, cv2.CV_64F).var()
        return min(100.0, (lap / 120.0) * 100.0)

    def _evaluate_color_and_exposure(self, bgr_img) -> float:
        """3. 색감 및 적정 노출 평가"""
        gray = cv2.cvtColor(bgr_img, cv2.COLOR_BGR2GRAY)
        bright_score = max(0.0, 100.0 - abs(np.mean(gray) - 130.0) * 1.1)
        hsv = cv2.cvtColor(bgr_img, cv2.COLOR_BGR2HSV)
        sat_score = min(100.0, (np.mean(hsv[:, :, 1]) / 75.0) * 100.0)
        return (bright_score * 0.5) + (sat_score * 0.5)

    def _evaluate_composition(self, bgr_img, faces) -> tuple[float, int]:
        """4. 인물 구도 평가"""
        img_h, img_w = bgr_img.shape[:2]
        img_area = img_h * img_w

        if faces is None or len(faces) == 0:
            return 50.0, 0

        scores = []
        for face in faces:
            fx, fy, fw, fh = map(int, face[:4])
            if face[14] < 0.35 or fw < 15 or fh < 15:
                continue

            face_ratio = (fw * fh) / float(img_area)
            ratio_score = 100.0 if 0.02 <= face_ratio <= 0.35 else max(30.0, 100.0 - abs(face_ratio - 0.15) * 350.0)

            fcx, fcy = fx + fw / 2.0, fy + fh / 2.0
            dist_from_center = np.sqrt(((fcx - img_w / 2.0) / img_w) ** 2 + ((fcy - img_h / 2.0) / img_h) ** 2)
            comp_score = max(40.0, 100.0 - (dist_from_center * 140.0))

            scores.append((ratio_score * 0.5) + (comp_score * 0.5))

        if not scores:
            return 50.0, 0

        return sum(scores) / len(scores), len(scores)

    def evaluate_image(self, img_path: str) -> dict:
        image = cv2.imread(img_path)
        if image is None:
            raise FileNotFoundError(f"오류: {img_path}")

        img_h, img_w = image.shape[:2]
        detector = cv2.FaceDetectorYN.create(
            model=self.yunet_path, config="", input_size=(img_w, img_h),
            score_threshold=0.35, nms_threshold=0.3
        )
        _, faces = detector.detect(image)

        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        
        raw_res = self._evaluate_resolution(image)
        raw_sharp = self._evaluate_blur_roi(gray, faces)
        raw_color = self._evaluate_color_and_exposure(image)
        raw_comp, face_count = self._evaluate_composition(image, faces)

        # 화질 점수 = 원본 해상도 점수(50%) + 얼굴 ROI 선명도(50%)
        raw_quality = (raw_res * 0.5) + (raw_sharp * 0.5)

        # 압축 스케일링 (중앙 수렴 보정: 30 ~ 70점 대 분포)
        blur_score = round(30.0 + (raw_quality * 0.4), 1)
        color_score = round(30.0 + (raw_color * 0.4), 1)
        comp_score = round(30.0 + (raw_comp * 0.4), 1)
        total_score = round((blur_score * 0.4) + (color_score * 0.3) + (comp_score * 0.3), 1)

        tags = []
        if blur_score >= 50: tags.append("고화질 📸")
        if color_score >= 50: tags.append("풍부한 색감 🎨")
        if face_count > 0 and comp_score >= 50: tags.append("안정적 구도 👤")

        return {
            "total_score": total_score,
            "blur_score": blur_score,
            "color_score": color_score,
            "comp_score": comp_score,
            "face_count": face_count,
            "tags": tags
        }

    def rank_best_shots(self, image_paths: list[str]) -> list[dict]:
        results = []
        for idx, path in enumerate(image_paths):
            try:
                eval_res = self.evaluate_image(path)
                eval_res["path"] = path
                eval_res["original_index"] = idx
                results.append(eval_res)
            except Exception as exc:
                # 한 장만 실패하면 나머지 사진은 계속 평가한다. 실패 이유는 서버 로그에 기록한다.
                print(f"[BestShot] {idx + 1}번째 사진 평가 실패: {type(exc).__name__}: {exc}")

        if not results:
            # 전부 실패했는데 빈 결과를 HTTP 200으로 보내는 상황을 막는다.
            raise RuntimeError("베스트 샷으로 분석 가능한 사진이 없습니다.")

        results.sort(key=lambda x: x["total_score"], reverse=True)
        for rank, item in enumerate(results, start=1):
            item["rank"] = rank
            
        return results