package com.example.trippaminebe.domain.tour.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 축제/여행지/관광산업 카드를 클릭했을 때 보여줄 상세 정보 DTO.
 * TourCacheService(DB 캐시)에 있는 기본 정보(카테고리 라벨, 축제 기간 등)와
 * TourApiClient가 실시간으로 호출하는 detailCommon2(개요/전화번호/홈페이지 등)를
 * TourService.getDetail()에서 합쳐서 채워준다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourDetailResponse {
  private String contentId;
  private String contentTypeId;
  private String title;
  private String category;       // 캐시에 있을 때만 채워짐 (예: "체험 관광", "숙박")
  private String address;
  private String imageUrl;
  private Double mapX;
  private Double mapY;
  private String tel;            // 문의 전화번호
  private String homepage;       // 홈페이지 URL (HTML 태그 제거된 순수 텍스트)
  private String overview;       // 상세 소개글
  private String eventStartDate; // yyyyMMdd, 축제 전용
  private String eventEndDate;   // yyyyMMdd, 축제 전용
  private String status;         // "ongoing" | "upcoming", 축제 전용
}
