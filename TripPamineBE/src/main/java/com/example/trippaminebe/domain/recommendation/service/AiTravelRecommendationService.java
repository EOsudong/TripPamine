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

  /**
   * 지도에서 편집한 일정을 저장한다.
   * 이 방식의 트레이드오프: 저장 후에도 planId가 그대로라서 원본 추천 결과는 남지 않고 덮어써진다
   */
  @Transactional
  public AiTravelRecommendationResponse saveCustomRecommendation(Long planId, String modifiedRecommendJson) {

    TravelPlan travelPlan = travelPlanRepository.findById(planId)
        .orElseThrow(() -> new IllegalArgumentException("여행 계획을 찾을 수 없습니다. planId=" + planId));

    AiTravelRecommendation recommendation =
        recommendationRepository
            .findByTravelPlan_PlanId(planId)
            .orElseGet(() ->
                AiTravelRecommendation.builder()
                    .travelPlan(travelPlan)
                    .build()
            );

    recommendation.setRecommendJson(modifiedRecommendJson);

    AiTravelRecommendation saved =
        recommendationRepository.save(recommendation);

    return AiTravelRecommendationResponse.from(saved);
  }

  /**
   * 사용자 커스텀 경로 저장 (새로운 플랜으로 복제하여 저장)
   */
  @Transactional
  public AiTravelRecommendationResponse createCustomPlan(Long originalPlanId, String modifiedRecommendJson) {

    // 1. 원본 여행 계획 조회
    TravelPlan originalPlan = travelPlanRepository.findById(originalPlanId)
        .orElseThrow(() -> new IllegalArgumentException("원본 여행 계획을 찾을 수 없습니다. planId=" + originalPlanId));

    // 2. 새로운 여행 계획 이름 설정 (이미 '(수정)'이 있으면 중복 방지)
    String newPlanName = originalPlan.getPlanName().startsWith("(수정)")
        ? originalPlan.getPlanName()
        : "(수정) " + originalPlan.getPlanName();

    // 3. 기존 플랜 정보를 바탕으로 새로운 플랜 복제 (Setter 사용)
    TravelPlan newPlan = new TravelPlan();
    newPlan.setUser(originalPlan.getUser());                 // 작성자 동일하게 유지
    newPlan.setPlanName(newPlanName);                        // 이름은 (수정)이 붙은 새 이름
    newPlan.setTotalBudget(originalPlan.getTotalBudget());   // 예산 복사
    newPlan.setCompanionType(originalPlan.getCompanionType());// 동행자 타입 복사
    newPlan.setLocationCd(originalPlan.getLocationCd());     // 지역 코드 복사
    newPlan.setBlindYn(originalPlan.getBlindYn());           // 미스터리 투어 여부 복사
    newPlan.setStartDate(originalPlan.getStartDate());       // 시작일 복사
    newPlan.setEndDate(originalPlan.getEndDate());           // 종료일 복사
    newPlan.setDelYn(originalPlan.getDelYn());               // 삭제 여부(기본값 N) 복사

    // DB에 새 플랜 저장
    TravelPlan savedPlan = travelPlanRepository.save(newPlan);

    // 4. 새로운 플랜에 프론트엔드에서 수정한 JSON을 매핑하여 추천(일정) 데이터 생성 및 저장
    AiTravelRecommendation customRecommendation = AiTravelRecommendation.builder()
        .travelPlan(savedPlan)
        .recommendJson(modifiedRecommendJson)
        .build();

    AiTravelRecommendation savedRecommendation = recommendationRepository.save(customRecommendation);

    // 5. 최종 결과 반환
    return AiTravelRecommendationResponse.from(savedRecommendation);
  }
}