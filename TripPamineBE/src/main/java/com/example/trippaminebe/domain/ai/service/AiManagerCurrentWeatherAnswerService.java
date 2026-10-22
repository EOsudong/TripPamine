package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.weather.service.CurrentWeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

/**
 * AI 매니저의 현재 위치 날씨 조회 전용 어댑터.
 * GPS 좌표가 필요한 질문만 기존 CurrentWeatherService로 연결한다.
 * Qwen3는 날씨를 추측하지 않으며 원시 좌표는 대화 기록에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiManagerCurrentWeatherAnswerService {

    private final CurrentWeatherService currentWeatherService;

    /** 직접 처리한 경우에만 답변을 반환하고 그 외에는 기존 라우터로 넘긴다. */
    public String findAnswer(String message, Double latitude, Double longitude) {
        if (!isCurrentLocationWeatherQuestion(message)) {
            return null;
        }

        if (latitude == null || longitude == null) {
            return "현재 위치의 날씨를 확인하려면 브라우저의 위치 권한을 허용해 주세요. "
                    + "위치가 허용된 후 다시 질문하면 현재 위치 기준으로 조회할 수 있어요.";
        }

        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GPS 좌표 범위가 올바르지 않습니다.");
        }

        try {
            // 재사용하는 실제 API: OpenWeatherMap 현재 날씨 조회 서비스.
            Map<String, Object> current = currentWeatherService.getCurrentWeather(latitude, longitude);
            Number temperature = (Number) current.get("temperature");
            Number humidity = (Number) current.get("humidity");
            Number windSpeed = (Number) current.get("windSpeed");
            String description = String.valueOf(current.getOrDefault("description", "날씨 정보 없음"));

            if (temperature == null || humidity == null || windSpeed == null) {
                throw new IllegalStateException("현재 날씨 API 응답에 필수 수치가 없습니다.");
            }

            return String.format(Locale.KOREA,
                    "현재 위치 기준 날씨는 %s입니다. 기온 %.1f℃, 습도 %d%%, 풍속 %.1fm/s예요. "
                            + "현재 날씨 데이터와 실제 환경과 차이가 있을 수 있어요.",
                    description,
                    temperature.doubleValue(),
                    humidity.intValue(),
                    windSpeed.doubleValue());
        } catch (RuntimeException exception) {
            // 외부 API 오류를 정상 날씨처럼 꾸며 답변하지 않는다.
            log.warn("AI 매니저 현재 날씨 서비스 조회 실패", exception);
            return "현재 위치는 받았지만 실시간 날씨 API 응답을 가져오지 못했습니다. "
                    + "네트워크 연결을 확인해 주세요.";
        }
    }

    /** 일반 현재 위치 질문만 처리한다. 여행지 예보·지역 지정 질문은 별도 경로가 담당한다. */
    static boolean isCurrentLocationWeatherQuestion(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("[\\s?!.,]+", "");
        return normalized.matches(
                "(?:지금여기|현재여기|오늘|지금|현재|여기|내주변|내위치|우리동네)?"
                        + "(?:의)?(?:날씨|기온|온도|습도)(?:좀)?"
                        + "(?:알려줘|알려주세요|알려줄래|어때|어때요|확인해줘|봐줘)?");
    }
}
