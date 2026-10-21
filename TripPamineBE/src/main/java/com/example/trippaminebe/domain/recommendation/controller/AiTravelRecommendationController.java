package com.example.trippaminebe.domain.recommendation.controller;

import com.example.trippaminebe.domain.mysterytour.client.KakaoLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import com.example.trippaminebe.domain.recommendation.dto.AiTravelRecommendationResponse;
import com.example.trippaminebe.domain.recommendation.service.AiTravelRecommendationService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

  /** 인증이 우회되더라도 익명 사용자로 AI 생성이 진행되지 않도록 한 번 더 검증한다. */
  private Long requireUserId(CustomUserDetails userDetails) {
    if (userDetails == null || userDetails.getUser() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    return userDetails.getUser().getId();
  }

  /**
   * 추천 조회
   * 저장된 추천이 없으면 최초 AI 생성까지 수행
   */
  @GetMapping("/travel-plans/{planId}")
  public ResponseEntity<AiTravelRecommendationResponse> getRecommendation(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long planId
  ) {
    return ResponseEntity.ok(
        recommendationService.getOrCreate(planId, requireUserId(userDetails))
    );
  }

  /**
   * 추천 다시 받기
   */
  @PostMapping("/travel-plans/{planId}/regenerate")
  public ResponseEntity<AiTravelRecommendationResponse> regenerate(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long planId
  ) {
    return ResponseEntity.ok(
        recommendationService.regenerate(planId, requireUserId(userDetails))
    );
  }

  @PostMapping("/travel-plans/{planId}/custom")
  public ResponseEntity<AiTravelRecommendationResponse> saveCustomRecommendation(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long planId,
      @RequestBody Map<String, String> request
  ) {
    String modifiedRecommendJson = request.get("modifiedRecommendJson");
    return ResponseEntity.ok(
        recommendationService.createCustomPlan(planId, requireUserId(userDetails), modifiedRecommendJson)
    );
  }

  /**
   * 장소 이름으로 좌표 후보를 검색한다.
   * 웹(KakaoMapModal.tsx)은 카카오 지도 JS SDK의 services.Places(키워드 검색)를 브라우저에서
   * 직접 호출하는데, 안드로이드 카카오맵 SDK(네이티브 앱 키)는 지도 렌더링만 담당하고
   * 이런 검색 기능이 없다. 그래서 안드로이드 앱은 이 엔드포인트를 통해 서버가 대신
   * Kakao Local REST API를 호출하도록 한다 - 이 경로는 SecurityConfig에서
   * 카카오 장소 검색만 SecurityConfig에서 GET 단일 경로로 공개 유지한다.
   * 사용자 여행 데이터를 다루는 /recommendations/travel-plans/** 경로는 모두 인증 필수이다.
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

  @PostMapping("/travel-plans/{planId}/days/{dayNum}/regenerate")
  public ResponseEntity<String> regenerateDay(
      @PathVariable Long planId,
      @PathVariable int dayNum,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody Map<String, List<String>> request
  ) {
    List<String> currentPlaces = request.getOrDefault("places", List.of());
    String dayJson = recommendationService.regenerateDay(planId, requireUserId(userDetails), dayNum, currentPlaces);
    return ResponseEntity.ok(dayJson);
  }
}
