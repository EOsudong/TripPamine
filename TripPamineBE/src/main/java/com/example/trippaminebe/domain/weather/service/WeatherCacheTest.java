// 제대로 작동하는지 확인용 파일, 완료시 지워도 됨.


package com.example.trippaminebe.domain.weather.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class WeatherCacheTest implements CommandLineRunner {
  private final WeatherSummaryService weatherSummaryService;
  @Override
  public void run(String... args){
    System.out.println("요약 10/10 : " + weatherSummaryService.getDailySummary(37.5665, 126.9780, LocalDate.of(2026, 10, 10)));
    System.out.println("요약 10/20 : " + weatherSummaryService.getDailySummary(37.5665, 126.9780, LocalDate.of(2026, 10, 20)));
  }
}
