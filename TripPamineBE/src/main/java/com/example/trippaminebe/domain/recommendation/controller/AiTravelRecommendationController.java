package com.example.trippaminebe.domain.recommendation.controller;

import com.example.trippaminebe.domain.mysterytour.client.KakaoLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import com.example.trippaminebe.domain.recommendation.dto.AiTravelRecommendationResponse;
import com.example.trippaminebe.domain.recommendation.service.AiTravelRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class AiTravelRecommendationController {

  private final AiTravelRecommendationService recommendationService;

  //mysterytour 도메인이 이미 갖고 있던 Kakao Local
  // 클라이언트를 그대로 재사용한다 - REST API 키를 새로 발급/보관할 필요 없이 이 컨트롤러에
  // 주입만 받으면 된다.
  private final KakaoLocalClient kakaoLocalClient;

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

  /**
   * 장소 이름으로 좌표 후보를 검색한다.
   * 웹(KakaoMapModal.tsx)은 카카오 지도 JS SDK의 services.Places(키워드 검색)를 브라우저에서
   * 직접 호출하는데, 안드로이드 카카오맵 SDK(네이티브 앱 키)는 지도 렌더링만 담당하고
   * 이런 검색 기능이 없다. 그래서 안드로이드 앱은 이 엔드포인트를 통해 서버가 대신
   * Kakao Local REST API를 호출하도록 한다 - 이 경로는 SecurityConfig에서
   * "/recommendations/**"가 이미 permitAll이라 별도 인증 설정이 필요 없다.
   */
  @GetMapping("/places/search")
  public ResponseEntity<List<KakaoPlace>> searchPlaces(
      @RequestParam String keyword,
      @RequestParam(defaultValue = "8") int size
  ) {
    return ResponseEntity.ok(
        kakaoLocalClient.searchPlaces(keyword, size)
    );
  }
}