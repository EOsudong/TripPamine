package com.example.trippaminebe.domain.recommendation.service;

import com.example.trippaminebe.domain.recommendation.dto.AiTravelRecommendationResponse;
import com.example.trippaminebe.domain.recommendation.entity.AiTravelRecommendation;
import com.example.trippaminebe.domain.recommendation.repository.AiTravelRecommendationRepository;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.exception.TravelPlanNotFoundException;
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
  public AiTravelRecommendationResponse getOrCreate(Long planId, Long userId) {

    // 추천 캐시 조회 전에 여행 소유권 검증. 타 사용자 기존 결과도 반환 금지.
    TravelPlan travelPlan = requireOwnedPlan(planId, userId);

    // 1. 기존 추천 결과 확인
    return recommendationRepository
        .findByTravelPlan_PlanId(planId)
        .map(AiTravelRecommendationResponse::from)
        .orElseGet(() -> createRecommendation(travelPlan));
  }

  /**
   * 최초 AI 추천 생성
   */
  private AiTravelRecommendationResponse createRecommendation(TravelPlan travelPlan) {

    // 2. 로그인 사용자의 소유로 확인된 여행 계획만 AI 생성에 사용한다.

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
  public AiTravelRecommendationResponse regenerate(Long planId, Long userId) {

    // 기존 추천이 있어도 먼저 소유권 검사, 무단 재생성에 따른 AI 비용 발생 차단.
    TravelPlan travelPlan = requireOwnedPlan(planId, userId);

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
   * 사용자 커스텀 경로 저장 (새로운 플랜으로 복제하여 저장)
   */
  @Transactional
  public AiTravelRecommendationResponse createCustomPlan(Long originalPlanId, Long userId, String modifiedRecommendJson) {

    // 1. 본인 소유·미삭제 원본 여행만 복제할 수 있다.
    TravelPlan originalPlan = requireOwnedPlan(originalPlanId, userId);

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

  @Transactional
  public String regenerateDay(Long planId, Long userId, int day, java.util.List<String> currentPlaces) {
    // 요청한 특정 일자의 AI 재계산 역시 소유권 검증 후 호출한다.
    TravelPlan travelPlan = requireOwnedPlan(planId, userId);

    return openAiRecommendationService.generateDayRecommendation(travelPlan, day, currentPlaces);
  }

  /**
   * 모든 여행 추천 동작에서 공유하는 소유권 검사.
   * ID가 존재하더라도 다른 사용자의 여행이거나 삭제된 여행이면 동일하게 404를 반환한다.
   * 응답 차이를 통해 타 사용자의 여행 존재 여부를 추측하기 어렵게 한다.
   */
  private TravelPlan requireOwnedPlan(Long planId, Long userId) {
    return travelPlanRepository.findByPlanIdAndUser_IdAndDelYn(planId, userId, "N")
        .orElseThrow(() -> new TravelPlanNotFoundException(
            "여행 계획을 찾을 수 없거나 접근 권한이 없습니다."));
  }
}
