package com.example.trippaminebe.domain.weather.service;

import com.example.trippaminebe.domain.weather.dto.DailyWeatherSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor

public class WeatherSummaryService {

  // 안내 문구들 미리 정의

  // 강수확률 안내 기준
  private static final double RAIN_NOTICE = 0.3;
  private static final double RAIN_HIGH = 0.6;
  // 풍속(m/s) 안내 기준
  private static final double WIND_NOTICE = 9;
  private static final double WIND_HIGH = 14;
  // 안내 문구 (경계/위험)
  private static final String MSG_RAIN_NOTICE = "강수 확률이 조금 있습니다. 접이식 우산을 준비하세요.";
  private static final String MSG_RAIN_HIGH = "강수 확률이 높습니다. 실내 활동을 권장합니다.";
  private static final String MSG_WIND_NOTICE = "바람이 강합니다.";
  private static final String MSG_WIND_HIGH = "강풍주의보 수준입니다. 야외활동 일정을 체크해주세요.";
  // 안내 문구 (openweathermap은 5일 동안의 데이터만 제공함)
  private static final String MSG_OUT_OF_RANGE = "여행 5일 전부터 데이터를 제공합니다.";


  private final WeatherCacheService weatherCacheService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final ZoneId KST = ZoneId.of("Asia/Seoul"); //한국기준

  // 한국시간 기준 여행날에 해당하는 칸만 골라내기 (openweathermap에서 3시간 단위로 예보하는 값들)
  private List<JsonNode> pickSlots(String json, LocalDate date) {
    List<JsonNode> result = new ArrayList<>();
    try {
      JsonNode root = objectMapper.readTree(json);
      for (JsonNode slot : root.path("list")) {
        long dt = slot.path("dt").asLong();
        LocalDate slotDate = Instant.ofEpochSecond(dt).atZone(KST).toLocalDate();
        if(slotDate.isEqual(date)) {
          result.add(slot);
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("날씨 JSON 파싱 실패", e);
    }
    return result;
  } ///pickSlots

  // 예보를 받아오지 못하는 날 (5일의 범위 밖)
  private DailyWeatherSummary unavailable() {
    return DailyWeatherSummary.builder()
        .available(false)
        .notices(List.of(MSG_OUT_OF_RANGE))
        .build();
  }

  public DailyWeatherSummary summarize(String json, LocalDate date) {
    List<JsonNode> slots = pickSlots(json, date);
    if (slots.isEmpty()) {
      return unavailable();
    }
    // 온도가 영하/영상일 경우를 모두 대비하여 최저/최고를 각각 갱신
    double tempMin = Double.MAX_VALUE;
    double tempMax = -Double.MAX_VALUE;

    double feelsSum = 0; // 관측한 체감온도 합계 (평균내기 위함)
    double humiditySum = 0; // 관측한 습도 합계 (평균내기 위함)
    double windMax = 0; // 풍속 최댓값
    double popMax = 0; // 강수확률 최댓값 (0~1) 이후 %로 변환
    double rainSum = 0; // 강수량 합계

    // 자료수집
    for (JsonNode slot : slots) {
      double temp = slot.path("main").path("temp").asDouble();
      tempMin = Math.min(tempMin, temp);
      tempMax = Math.max(tempMax, temp);

      feelsSum += slot.path("main").path("feels_like").asDouble();
      humiditySum += slot.path("main").path("humidity").asDouble();
      windMax = Math.max(windMax, slot.path("wind").path("speed").asDouble());
      popMax = Math.max(popMax, slot.path("pop").asDouble());
      rainSum += slot.path("rain").path("3h").asDouble();
    }

    double feelsLike = Math.round((feelsSum / slots.size()) * 100) / 100.0; // 체감온도평균 (소숫점 둘째자리까지)
    int humidity = (int) Math.round(humiditySum/slots.size()); // 습도평균(%)
    int rainProbability = (int) Math.round(popMax * 100);// 0~1 -> 0~100(%)
    Double rainMm = popMax >= RAIN_NOTICE ? Math.round(rainSum * 100) / 100.0 : null; // 강수확률 30%이상일 때만 표시 (소숫점 둘째자리까지)

    List<String> notices = new ArrayList<>();
    // 강수량 안내
    if(popMax >= RAIN_HIGH) {
      notices.add(MSG_RAIN_HIGH);
    } else if(popMax >= RAIN_NOTICE) {
      notices.add(MSG_RAIN_NOTICE);
    }
    // 풍속주의 안내
    if (windMax >= WIND_HIGH) {
      notices.add(MSG_WIND_HIGH);
    } else if(windMax >= WIND_NOTICE) {
      notices.add(MSG_WIND_NOTICE);
    }

    return DailyWeatherSummary.builder()
        .available(true)
        .tempMin(tempMin) // 최저기온
        .tempMax(tempMax) // 최고기온
        .feelsLike(feelsLike) // 체감온도
        .humidity(humidity) // 습도
        .rainProbability(rainProbability) // 강수확률
        .rainMm(rainMm) // 강수량
        .windSpeed(windMax) // 풍속
        .notices(notices)
        .build();
  } ///summarize

  // 얻어온 정보 호출
  public DailyWeatherSummary getDailySummary(double latitude, double longitude, LocalDate date) {
    LocalDate today = LocalDate.now(KST);
    if(date.isBefore(today) || date.isAfter(today.plusDays(4))) {
      return unavailable();
    }

    String json = weatherCacheService.getWeather(latitude, longitude, date);
    return summarize(json, date);
  }///getDailySummary

} ///WeatherSummaryService
