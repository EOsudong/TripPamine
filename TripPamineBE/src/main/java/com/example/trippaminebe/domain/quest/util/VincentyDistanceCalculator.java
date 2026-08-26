package com.example.trippaminebe.domain.quest.util;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

@Service
@Component
public class VincentyDistanceCalculator {

	// 타원체 상수 정의
	private static final double SEMI_MAJOR_AXIS_A = 6378137.0; // 지구 적도 반지름 (a)
	private static final double SEMI_MINOR_AXIS_B = 6356752.314245; // 지구 극 반지름 (b)
	private static final double FLATTENING_F = 1 / 298.257223563; // 편평률 (f)
	private static final double MAX_ITERATIONS = 200;
	private static final double CONVERGENCE_THRESHOLD = 1e-12;

	/**
	 * 두 지점의 위경도(BigDecimal)를 입력받아 Vincenty 공식을 사용하여 정밀한 거리(m)를 계산합니다.
	 */
	public double calculateDistance(
			BigDecimal targetLat,
			BigDecimal targetLng,
			@NotNull(message = "현재 위도를 전달해주세요.") BigDecimal currentLat,
			@NotNull(message = "현재 경도를 전달해주세요.") BigDecimal currentLng) {

		if (targetLat == null || targetLng == null) {
			throw new IllegalArgumentException("목표 위치 좌표가 올바르지 않습니다.");
		}

		double lat1 = Math.toRadians(targetLat.doubleValue());
		double lon1 = Math.toRadians(targetLng.doubleValue());
		double lat2 = Math.toRadians(currentLat.doubleValue());
		double lon2 = Math.toRadians(currentLng.doubleValue());

		// 출발지와 목적지가 동일한 좌표인 경우 0m 반환
		if (lat1 == lat2 && lon1 == lon2) {
			return 0.0;
		}

		double U1 = Math.atan((1 - FLATTENING_F) * Math.tan(lat1));
		double U2 = Math.atan((1 - FLATTENING_F) * Math.tan(lat2));
		double sinU1 = Math.sin(U1), cosU1 = Math.cos(U1);
		double sinU2 = Math.sin(U2), cosU2 = Math.cos(U2);

		double L = lon2 - lon1;
		double lambda = L;
		double lambdaP;
		double iterLimit = MAX_ITERATIONS;

		double cosSqAlpha, sinSigma, cos2SigmaM, cosSigma, sigma;

		do {
			double sinLambda = Math.sin(lambda);
			double cosLambda = Math.cos(lambda);

			sinSigma = Math.sqrt(
					(cosU2 * sinLambda) * (cosU2 * sinLambda) +
							(cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
			);

			if (sinSigma == 0) {
				return 0.0; // 경계 조건 예외 처리
			}

			cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
			sigma = Math.atan2(sinSigma, cosSigma);

			double sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
			cosSqAlpha = 1 - sinAlpha * sinAlpha;

			cos2SigmaM = (cosSqAlpha != 0) ? (cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha) : 0;

			double C = FLATTENING_F / 16 * cosSqAlpha * (4 + FLATTENING_F * (4 - 3 * cosSqAlpha));
			lambdaP = lambda;
			lambda = L + (1 - C) * FLATTENING_F * sinAlpha * (
					sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM))
			);

		} while (Math.abs(lambda - lambdaP) > CONVERGENCE_THRESHOLD && --iterLimit > 0);

		// 루프 내 수렴에 실패한 경우 (대척점 등 무한 루프 가능성 방지)
		if (iterLimit == 0) {
			return Double.NaN;
		}

		double uSq = cosSqAlpha * (SEMI_MAJOR_AXIS_A * SEMI_MAJOR_AXIS_A - SEMI_MINOR_AXIS_B * SEMI_MINOR_AXIS_B) / (SEMI_MINOR_AXIS_B * SEMI_MINOR_AXIS_B);
		double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
		double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));

		double deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (
				cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
						B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)
		));

		// 최종 거리 계산 (단위: 미터)
		return SEMI_MINOR_AXIS_B * A * (sigma - deltaSigma);
	}
}