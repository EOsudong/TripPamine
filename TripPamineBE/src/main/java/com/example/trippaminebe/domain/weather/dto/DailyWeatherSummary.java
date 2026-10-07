package com.example.trippaminebe.domain.weather.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@Builder
@ToString

public class DailyWeatherSummary {
  private Double tempMin; //최저기온
  private Double tempMax; //최고기온
  private Double feelsLike; //체감온도
  private Double windSpeed; //풍속
  private Integer humidity; //습도(%)
  private Integer rainProbability; //강수확률(%)
  private Double rainMm; //강수량
  private List<String> notices; //안내문구
  private boolean available; //예보를 제공하는 날인지 (5일 안팎)
}
