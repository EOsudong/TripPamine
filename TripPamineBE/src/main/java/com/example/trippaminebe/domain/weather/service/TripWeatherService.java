package com.example.trippaminebe.domain.weather.service;

import com.example.trippaminebe.domain.mysterytour.client.KakaoLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import com.example.trippaminebe.domain.recommendation.entity.AiTravelRecommendation;
import com.example.trippaminebe.domain.recommendation.repository.AiTravelRecommendationRepository;
import com.example.trippaminebe.domain.weather.dto.DailyWeatherSummary;
import com.example.trippaminebe.domain.weather.dto.TripDayWeather;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor

public class TripWeatherService {

  private final AiTravelRecommendationRepository aiTravelRecommendationRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // 하루간의 정보 담기용 빈 그릇
  public record DayPlace(int day, LocalDate date, String placeName) {}

  private List<DayPlace> extractFirstPlaces(String json, LocalDate startDate) {
    List<DayPlace> result = new ArrayList<>();
    try {
      JsonNode root = objectMapper.readTree(json);
      for (JsonNode dayNode : root.path("days")) {
        int day = dayNode.path("day").asInt();
        JsonNode places = dayNode.path("places");
        if (places.isEmpty()) continue; // 장소가 없는 날은 건너뛴다
        String name = places.get(0).path("name").asText();
        LocalDate date = startDate.plusDays(day-1);
        result.add(new DayPlace(day, date, name));
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("추천 JSON 파싱 실패", e);
    } return result;
  } ///extractFirstPlaces

  private final KakaoLocalClient kakaoLocalClient;

  private final WeatherSummaryService weatherSummaryService;
  @Transactional
  public List<TripDayWeather> getTripDayWeather(Long planId) {
    AiTravelRecommendation rec = aiTravelRecommendationRepository
        .findByTravelPlan_PlanId(planId)
        .orElseThrow(()->new IllegalArgumentException(
            "추천 일정이 아직 등록되지 않았습니다. planId= " + planId));
    String json = rec.getRecommendJson();
    LocalDate startDate = rec.getTravelPlan().getStartDate().toLocalDate();

    List<TripDayWeather> result = new ArrayList<>();
    for (DayPlace p : extractFirstPlaces(json, startDate)) {
      Optional<KakaoPlace> found = kakaoLocalClient.searchFirstPlace(p.placeName());
      DailyWeatherSummary summary;
      if (found.isPresent()) {
        KakaoPlace k = found.get();
        summary = weatherSummaryService.getDailySummary(
            k.latitude().doubleValue(),
            k.longitude().doubleValue(),
            p.date());
      } else {
        summary = DailyWeatherSummary.builder()
            .available(false)
            .notices(List.of("장소 위치를 찾지 못해 날씨를 제공할 수 없습니다."))
            .build();
      }
      result.add(new TripDayWeather(p.day(), p.date(), p.placeName(), summary));
    }
    return result;
  } ///getTripDayWeather

}///TripWeatherService
