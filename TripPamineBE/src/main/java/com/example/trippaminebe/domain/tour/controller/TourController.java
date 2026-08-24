package com.example.trippaminebe.domain.tour.controller;

import com.example.trippaminebe.domain.tour.dto.TourDetailResponse;
import com.example.trippaminebe.domain.tour.dto.TourItemResponse;
import com.example.trippaminebe.domain.tour.service.TourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 한국관광공사 오픈API(한국관광콘텐츠랩) 데이터를 프론트 사이드바의 3개 대분류 페이지에 내려주는 컨트롤러.
 * 로그인 여부와 무관한 공개 관광정보라서 SecurityConfig에서 "/tour/**"를 permitAll 처리함
 */
@RestController
@RequestMapping("/tour")
@RequiredArgsConstructor
public class TourController {

  private final TourService tourService;

  @GetMapping("/festivals")
  @Operation(summary = "국내 축제 및 행사 목록 조회")
  public ResponseEntity<List<TourItemResponse>> getFestivals(
      @RequestParam(defaultValue = "전체")
      @Parameter(description = "전체 | 진행중 | 문화 예술 | 체험 | 음식 음료") String filter
  ) {
    return ResponseEntity.ok(tourService.getFestivals(filter));
  }

  @GetMapping("/destinations")
  @Operation(summary = "국내 관광 여행지 목록 조회")
  public ResponseEntity<List<TourItemResponse>> getDestinations(
      @RequestParam(defaultValue = "전체")
      @Parameter(description = "전체 | 체험 관광 | 역사 관광 | 자연 관광 | 문화 관광") String filter
  ) {
    return ResponseEntity.ok(tourService.getDestinations(filter));
  }

  @GetMapping("/industry")
  @Operation(summary = "국내 관광 산업 목록 조회")
  public ResponseEntity<List<TourItemResponse>> getIndustry(
      @RequestParam(defaultValue = "전체")
      @Parameter(description = "전체 | 숙박 | 음식 | 레저 스포츠 | 쇼핑") String filter
  ) {
    return ResponseEntity.ok(tourService.getIndustry(filter));
  }

  // 축제/여행지/관광산업 카드를 클릭했을 때 보여줄 상세 정보.
  // 목록 3개(festivals/destinations/industry)와 마찬가지로 로그인 여부와 무관하게 공개된
  // 관광정보라서 SecurityConfig의 "/tour/**" permitAll 규칙을 그대로 탄다.
  @GetMapping("/detail/{contentId}")
  @Operation(summary = "관광 상세 정보 조회 (축제/여행지/관광산업 공통)")
  public ResponseEntity<TourDetailResponse> getDetail(
      @PathVariable String contentId,
      @RequestParam(required = false)
      @Parameter(description = "TourAPI contentTypeId. 캐시에서 항목을 못 찾았을 때만 보조로 사용됨 (선택)") String contentTypeId
  ) {
    return ResponseEntity.ok(tourService.getDetail(contentId, contentTypeId));
  }
}



