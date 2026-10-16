package com.example.trippaminebe.domain.weather.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class KmaBaseTime {

  // 발표 날짜와 시각을 담는 빈 함수
  public record BaseTime(String baseDate, String baseTime) {}

  // 기상청 초단기예보는 매시 30분에 발표, 45분에 api를 받아올 수 있어서, 이에 맞추어 함수 작성
  // LocalDateTime.now() 대신 now를 매개변수로 받는 이유 : 테스트에서 원하는 시각을 넣어 확인하기 위해
  // 실제 서비스에서는 호출하는 쪽이 ZoneId.of("Asia/Seoul") 등으로 현재시간을 넘겨주면 된다
  public static BaseTime calculate(LocalDateTime now) {
    LocalDateTime t = now.minusMinutes(45); // 현재시간 - 45분
    String baseDate = t.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String baseTime = String.format("%02d30", t.getHour());
    return new BaseTime(baseDate, baseTime);
  } ///calculate
}