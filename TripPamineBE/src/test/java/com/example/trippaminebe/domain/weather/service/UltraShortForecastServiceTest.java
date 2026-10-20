package com.example.trippaminebe.domain.weather.service;

import com.example.trippaminebe.domain.weather.dto.HourlyForecast;
import com.example.trippaminebe.domain.weather.dto.RainAlert;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UltraShortForecastServiceTest {

  // 현재 테스트는 mock data로 하는 것이기 때문에, api를 가져오지 않음. 따라서 임시로 null값부여
  private final UltraShortForecastService service = new UltraShortForecastService(null);

  @Test
  void 응답_66줄이_6개_시각으로_묶인다() throws Exception {
    String json = Files.readString(Path.of("src/test/resources/ultra-srt-fcst-rain.json"));

    Map<String, Map<String, String>> grouped = service.groupByTime(json);

    assertThat(grouped).hasSize(6);
  }

  @Test
  void 비가_오는_시각의_값들이_맞게_모인다() throws Exception {
    String json = Files.readString(Path.of("src/test/resources/ultra-srt-fcst-rain.json"));

    Map<String, Map<String, String>> grouped = service.groupByTime(json);

    assertThat(grouped).containsKey("202610161200");

    // 샘플 파일에서 PTY가 1인 시각의 키를 직접 찾아서 쓴다.
    Map<String, String> rainySlot = grouped.get("202610161200");

    assertThat(rainySlot.get("PTY")).isEqualTo("1");
    assertThat(rainySlot.get("RN1")).isEqualTo("1mm 미만");
    assertThat(rainySlot.get("POP")).isEqualTo("60");
    assertThat(grouped.get("202610161100").get("PTY")).isEqualTo("0");
  }

  @Test
  void 시각별_맵이_슬롯_리스트로_바뀐다() throws Exception {
    String json = Files.readString(Path.of("src/test/resources/ultra-srt-fcst-rain.json"));

    List<UltraShortForecastService.ForecastSlot> slots =
        service.toSlots(service.groupByTime(json));

    assertThat(slots).hasSize(6);
    assertThat(slots.get(0).time()).isEqualTo("1000");        // 첫 슬롯
    assertThat(slots.get(2).temperature()).isEqualTo(21);   // 12시의 기온
    assertThat(slots.get(2).humidity()).isEqualTo(70);      // 12시의 습도
    assertThat(slots.get(2).rainProbability()).isEqualTo(60); // 12시의 POP
  }

  @Test
  void 앞의_슬롯들이_화면용으로_바뀐다() throws Exception {
    String json = Files.readString(Path.of("src/test/resources/ultra-srt-fcst-rain.json"));
    LocalDate today = LocalDate.of(2026, 10, 16);   // 샘플 JSON의 날짜와 같은 날로 고정

    List<HourlyForecast> hourly =
        service.toHourly(service.toSlots(service.groupByTime(json)), today);

    assertThat(hourly).hasSize(6);
    assertThat(hourly.get(0).time()).isEqualTo("10:00");
    assertThat(hourly.get(0).temperature()).isEqualTo(18);
    assertThat(hourly.get(0).humidity()).isEqualTo(70);
  }

  @Test
  void 처음_비가_오는_슬롯을_찾는다() throws Exception {
    String json = Files.readString(Path.of("src/test/resources/ultra-srt-fcst-rain.json"));
    List<UltraShortForecastService.ForecastSlot> slots =
        service.toSlots(service.groupByTime(json));

    Optional<UltraShortForecastService.ForecastSlot> rain = service.findFirstRain(slots);

    assertThat(rain).isPresent();
    assertThat(rain.get().time()).isEqualTo("1200");
  }

  @Test
  void 비가_없으면_빈_Optional이다() {
    List<UltraShortForecastService.ForecastSlot> slots = List.of(
        new UltraShortForecastService.ForecastSlot("20261016", "1000", 18.0, 70, "0", "강수없음", 20, "1"));

    assertThat(service.findFirstRain(slots)).isEmpty();
  }

  @Test
  void 강수_코드가_이름으로_바뀐다() {
    assertThat(service.precipitationName("1")).isEqualTo("비");
    assertThat(service.precipitationName("3")).isEqualTo("눈");
    assertThat(service.precipitationName("7")).isEqualTo("약한 눈");
    assertThat(service.precipitationName("9")).isEqualTo("강수");
  }

  @Test
  void 어제_비가_오면_알림_메시지가_만들어진다() throws Exception {
    String json = Files.readString(Path.of("src/test/resources/ultra-srt-fcst-rain.json"));
    LocalDate today = LocalDate.of(2026, 10, 15);   // 샘플 날짜의 하루 전
    List<UltraShortForecastService.ForecastSlot> slots =
        service.toSlots(service.groupByTime(json));

    RainAlert alert = service.toRainAlert(service.findFirstRain(slots), today);

    assertThat(alert.rainExpected()).isTrue();
    assertThat(alert.forecastTime()).isEqualTo("내일 12:00");
    assertThat(alert.precipitationType()).isEqualTo("비");
    assertThat(alert.rainProbability()).isEqualTo(60);
    assertThat(alert.rainAmount()).isEqualTo("1mm 미만");
    assertThat(alert.message()).isEqualTo("내일 12:00에 비 예상");
  }

  @Test
  void 비가_없으면_알림이_꺼져_있다() {
    LocalDate today = LocalDate.of(2026, 10, 15);   // 샘플 날짜의 하루 전
    RainAlert alert = service.toRainAlert(Optional.empty(), today);

    assertThat(alert.rainExpected()).isFalse();
    assertThat(alert.message()).isNull();
  }

  @Test
  void 오늘_슬롯과_내일_슬롯의_시각_표시가_다르다() {
    LocalDate today = LocalDate.of(2026, 10, 16);

    assertThat(service.formatTime("20261016", "1000", today)).isEqualTo("10:00");
    assertThat(service.formatTime("20261017", "0000", today)).isEqualTo("내일 00:00");
  }
}