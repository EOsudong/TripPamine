package com.example.trippaminebe.domain.weather.service;

import com.example.trippaminebe.domain.weather.client.KmaForecastApiClient;
import com.example.trippaminebe.domain.weather.dto.HourlyForecast;
import com.example.trippaminebe.domain.weather.dto.NowWeather;
import com.example.trippaminebe.domain.weather.dto.RainAlert;
import com.example.trippaminebe.domain.weather.dto.UltraShortForecastResult;
import com.example.trippaminebe.domain.weather.utils.KmaBaseTime;
import com.example.trippaminebe.domain.weather.utils.KmaGridConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UltraShortForecastService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ObjectMapper objectMapper = new ObjectMapper();

  // 한 시각의 예측 값을 모아둔 record (이 파일에서만 사용)
  record ForecastSlot(
      String date,                // 예측 날짜 (예: "20261016")
      String time,                // 예측 시각 (예: "1200")
      Double temperature,           // T1H (기온)
      Integer humidity,              // REH (습도)
      String precipitationType,     // PTY (강수형태, 문자형태('0', '1' 등등))
      String rainAmount,            // RN1 (1시간강수량)
      Integer rainProbability,        // POP (강수확률)
      String sky              // SKY (하늘상태: "1" 맑음, "3" 구름많음, "4" 흐림)
  ) {} ///ForecastSlot

  // 응답 JSON의 줄들을 "날짜+시각" 별로 모은다.
  // 예: "202610161200" → {"T1H":"21", "REH":"60", "PTY":"1", ...}
  Map<String, Map<String, String>> groupByTime(String json) {
    Map<String, Map<String, String>> grouped = new TreeMap<>();
    try {
      JsonNode items = objectMapper.readTree(json)
          .path("response").path("body").path("items").path("item");

      for (JsonNode item : items) {
        String key = item.path("fcstDate").asText() + item.path("fcstTime").asText();
        String category = item.path("category").asText();
        String value = item.path("fcstValue").asText();

        grouped.computeIfAbsent(key, k -> new HashMap<>()).put(category, value);
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("초단기예보 JSON 파싱 실패", e);
    }
    return grouped;
  } ///groupByTime

  // 시각별로 모은 맵을 ForecastSlot 리스트로 바꾼다. (글자 → 숫자 변환)
  List<ForecastSlot> toSlots(Map<String, Map<String, String>> grouped) {
    List<ForecastSlot> slots = new ArrayList<>();

    for (Map.Entry<String, Map<String, String>> entry : grouped.entrySet()) {
      String key = entry.getKey();                 // 예: "202610161200"
      Map<String, String> values = entry.getValue();

      String date = key.substring(0, 8);        // "202610161200" 에서, "20261016"
      String time = key.substring(8);           // 나머지 "1200"

      Double temperature = Double.parseDouble(values.get("T1H"));
      Integer humidity = Integer.parseInt(values.get("REH"));
      String precipitationType = values.get("PTY");
      String rainAmount = values.get("RN1");

      // POP(강수확률)은 없을 수 있다. 없으면 null, 있으면 숫자로 바꾼다.
      Integer rainProbability = values.get("POP") == null ? null : Integer.parseInt(values.get("POP"));
      String sky = values.get("SKY");
      slots.add(new ForecastSlot(date, time, temperature, humidity,
          precipitationType, rainAmount, rainProbability, sky));
    }
    return slots;
  } ///toSlots

  // 슬롯의 날짜가 오늘이면 "10:00", 아니면 "내일 00:00" 형태로 만든다.
  String formatTime(String date, String time, LocalDate today) {
    String hhmm = time.substring(0, 2) + ":" + time.substring(2);
    String todayText = today.format(DateTimeFormatter.BASIC_ISO_DATE); // 예: "20261016"
    if (date.equals(todayText)) {
      return hhmm;
    }
    return "내일 " + hhmm;
  } ///formatTime

  // 초단기예보에서 앞으로 보여줄 시간의 수 (1~6)
  private static final int HOURS_TO_SHOW = 6;

  // 슬롯 리스트에서 정한 시간의 수만큼 화면용 HourlyForecast로 바꾼다.
  List<HourlyForecast> toHourly(List<ForecastSlot> slots, LocalDate today) {
    List<HourlyForecast> result = new ArrayList<>();

    for (int i = 0; i < Math.min(HOURS_TO_SHOW, slots.size()); i++) {
      ForecastSlot slot = slots.get(i);
      result.add(new HourlyForecast(
          formatTime(slot.date(), slot.time(), today),
          slot.temperature(),
          slot.humidity())
      );
    }
    return result;
  } ///toHourly

  // 슬롯 중에서 처음으로 예보가 필요한 슬롯을 찾는다. 없으면 빈 값 반환
  Optional<ForecastSlot> findFirstRain(List<ForecastSlot> slots) {
    for (ForecastSlot slot : slots) {
      if (!"0".equals(slot.precipitationType())) { // 0 : 강수없음, 1~ : 비, 눈 등..
        return Optional.of(slot);
      }
    }
    return Optional.empty();
  } ///findFirstRain

  // 강수형태 코드 → 화면에 보여줄 이름
  String precipitationName(String code) {
    return switch (code) {
      case "1" -> "비";
      case "2" -> "비/눈(진눈깨비)";
      case "3" -> "눈";
      case "5" -> "약한 비";
      case "6" -> "약한 눈비";
      case "7" -> "약한 눈";
      default -> { // 4가 떴다면 소나기(단기예보의 데이터가 흘러들어옴), 그 외에는 정의된 코드 없음(확인필요)
        log.warn("알 수 없는 강수형태 코드 : {}", code);
        yield "강수"; // 사용자에겐 '강수' 로 표현
      }
    };
  } ///precipitationName

  // 하늘상태 코드
  String skyName(String sky) {
    return switch (sky == null ? "" : sky) {
      case "1" -> "맑음";
      case "3" -> "구름많음";
      case "4" -> "흐림";
      default -> "알 수 없음";
    };
  } ///skyName

  // 처음 비가 오는 슬롯으로 RainAlert를 만든다. 비가 없으면 "비 없음" 알림을 만든다.
  RainAlert toRainAlert(Optional<ForecastSlot> firstRain, LocalDate today) {
    if (firstRain.isEmpty()) {
      return new RainAlert(false,
          null,
          null,
          null,
          null,
          null);
    } ///toRainAlert

    ForecastSlot slot = firstRain.get();
    String name = precipitationName(slot.precipitationType());
    String time = formatTime(slot.date(), slot.time(), today);
    String message = time + "에 " + name + " 예상";

    return new RainAlert(true, message, name, time, slot.rainProbability(), slot.rainAmount());
  } ///toRainAlert

  private final KmaForecastApiClient kmaForecastApiClient;

  // 위치를 받아서 초단기예보 결과(6시간 기온·습도 + 비 알림)를 돌려준다.
  public UltraShortForecastResult getForecast(double latitude, double longitude) {

    LocalDateTime now = LocalDateTime.now(KST);

    KmaGridConverter.Grid grid = KmaGridConverter.toGrid(latitude, longitude);
    KmaBaseTime.BaseTime baseTime = KmaBaseTime.calculate(now);

    String json = kmaForecastApiClient.fetchUltraShortForecast(
        grid.nx(), grid.ny(), baseTime.baseDate(), baseTime.baseTime());

    List<ForecastSlot> slots = toSlots(groupByTime(json));
    LocalDate today = now.toLocalDate();

    if(slots.isEmpty()) {
      throw new IllegalStateException("초단기예보 데이터가 비어 있습니다.");
    }
    ForecastSlot first = slots.get(0); // 가장 가까운 시각의 예보
    String weather = toWeatherType(first.precipitationType(), first.sky());
    String description = "0".equals(first.precipitationType())
        ? skyName(first.sky())                          // 강수가 없으면 하늘 상태 이름
        : precipitationName(first.precipitationType()); // 있으면 강수 이름
    NowWeather current = new NowWeather(weather, description, first.temperature());

    return new UltraShortForecastResult(
        toHourly(slots, today), toRainAlert(findFirstRain(slots), today), current);
  } ///getForecast

  // PTY(강수형태)와 SKY(하늘상태)를 SOS 모달이 쓰는 4가지 종류로 바꾸기
  String toWeatherType(String pty, String sky) {
    if (pty != null) {
      switch (pty) {
        case "1", "2", "4", "5", "6": return "RAIN";   // 비, 비/눈, 빗방울, 빗방울눈날림 (4는 소나기인데, 초단기예보엔 없는 코드지만 만약을 대비)
        case "3", "7": return "SNOW";             // 눈, 눈날림
      }
    }
    // 비·눈이 없으면 하늘상태로 정한다
    return "1".equals(sky) ? "CLEAR" : "CLOUDY";       // 1이면 맑음, 3(구름많음), 4(흐림), 모르는 값 등은 흐림으로 통일
  } ///toWeatherType

} ///UltraShortForecastService