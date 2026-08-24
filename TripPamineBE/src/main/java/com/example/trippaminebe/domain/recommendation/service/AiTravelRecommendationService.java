package com.example.trippaminebe.domain.recommendation.service;

import com.example.trippaminebe.domain.recommendation.dto.AiTravelRecommendationResponse;
import com.example.trippaminebe.domain.recommendation.entity.AiTravelRecommendation;
import com.example.trippaminebe.domain.recommendation.repository.AiTravelRecommendationRepository;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiTravelRecommendationService {

  private final AiTravelRecommendationRepository recommendationRepository;
  private final TravelPlanRepository travelPlanRepository;
  private final OpenAiRecommendationService openAiRecommendationService;

  /**
   * 저장된 추천이 있으면 DB 결과 반환
   * 없으면 OpenAI 호출 → 저장 → 반환
   */
  @Transactional
  public AiTravelRecommendationResponse getOrCreate(Long planId) {

    // 1. 기존 추천 결과 확인
    return recommendationRepository
        .findByTravelPlan_PlanId(planId)
        .map(AiTravelRecommendationResponse::from)
        .orElseGet(() -> createRecommendation(planId));
  }

  /**
   * 최초 AI 추천 생성
   */
  private AiTravelRecommendationResponse createRecommendation(Long planId) {

    // 2. 여행 계획 조회
    TravelPlan travelPlan = travelPlanRepository.findById(planId)
        .orElseThrow(() ->
            new IllegalArgumentException(
                "여행 계획을 찾을 수 없습니다. planId=" + planId
            )
        );

    // 3. OpenAI 호출
    String recommendJson =
        openAiRecommendationService.generateRecommendation(travelPlan);

    // 4. AI 결과 저장
    AiTravelRecommendation recommendation =
        AiTravelRecommendation.builder()
            .travelPlan(travelPlan)
            .recommendJson(recommendJson)
            .build();

    AiTravelRecommendation saved =
        recommendationRepository.save(recommendation);

    // 5. 프론트에 반환
    return AiTravelRecommendationResponse.from(saved);
  }

  /**
   * 추천 다시 받기
   */
  @Transactional
  public AiTravelRecommendationResponse regenerate(Long planId) {

    TravelPlan travelPlan = travelPlanRepository.findById(planId)
        .orElseThrow(() ->
            new IllegalArgumentException(
                "여행 계획을 찾을 수 없습니다. planId=" + planId
            )
        );

    String newRecommendJson =
        openAiRecommendationService.generateRecommendation(travelPlan);

    AiTravelRecommendation recommendation =
        recommendationRepository
            .findByTravelPlan_PlanId(planId)
            .orElseGet(() ->
                AiTravelRecommendation.builder()
                    .travelPlan(travelPlan)
                    .build()
            );

    recommendation.setRecommendJson(newRecommendJson);

    AiTravelRecommendation saved =
        recommendationRepository.save(recommendation);

    return AiTravelRecommendationResponse.from(saved);
  }
}