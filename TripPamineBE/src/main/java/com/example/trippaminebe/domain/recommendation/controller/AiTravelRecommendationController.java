package com.example.trippaminebe.domain.recommendation.controller;

import com.example.trippaminebe.domain.recommendation.dto.AiTravelRecommendationResponse;
import com.example.trippaminebe.domain.recommendation.service.AiTravelRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class AiTravelRecommendationController {

  private final AiTravelRecommendationService recommendationService;

  /**
   * 추천 조회
   * 저장된 추천이 없으면 최초 AI 생성까지 수행
   */
  @GetMapping("/travel-plans/{planId}")
  public ResponseEntity<AiTravelRecommendationResponse> getRecommendation(
      @PathVariable Long planId
  ) {
    return ResponseEntity.ok(
        recommendationService.getOrCreate(planId)
    );
  }

  /**
   * 추천 다시 받기
   */
  @PostMapping("/travel-plans/{planId}/regenerate")
  public ResponseEntity<AiTravelRecommendationResponse> regenerate(
      @PathVariable Long planId
  ) {
    return ResponseEntity.ok(
        recommendationService.regenerate(planId)
    );
  }

  @PostMapping("/travel-plans/{planId}/custom")
  public ResponseEntity<AiTravelRecommendationResponse> saveCustomRecommendation(
      @PathVariable Long planId,
      @RequestBody Map<String, String> request
  ) {
    String modifiedRecommendJson = request.get("modifiedRecommendJson");
    return ResponseEntity.ok(
        recommendationService.createCustomPlan(planId, modifiedRecommendJson)
    );
  }

}