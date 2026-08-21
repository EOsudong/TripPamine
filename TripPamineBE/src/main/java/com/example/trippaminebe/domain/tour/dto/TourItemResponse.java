package com.example.trippaminebe.domain.tour.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TourAPI 원본 응답을 화면에서 바로 쓰기 좋은 형태로 정리한 DTO.
 * 관광지/관광산업 항목은 eventStartDate/eventEndDate/status가 항상 null이고,
 * 축제 항목만 두 날짜와 status("ongoing" | "upcoming")가 채워진다.
 *
 * TourCacheService가 이 객체 목록을 JSON 문자열로 직렬화해서 DB(TOUR_CACHE_ENTRY)에 저장했다가
 * 다시 역직렬화해서 읽기 때문에(Jackson ObjectMapper), 기본 생성자 + Setter도 함께 열어둔다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourItemResponse {
  private String contentId;
  private String contentTypeId;
  private String title;
  private String category;       // 화면 표시용 카테고리 라벨 (예: "숙박", "체험 관광", "음식 음료")
  private String address;
  private String imageUrl;       // 대표이미지가 없으면 null
  private Double mapX;
  private Double mapY;
  private String eventStartDate; // yyyyMMdd, 축제 전용
  private String eventEndDate;   // yyyyMMdd, 축제 전용
  private String status;         // "ongoing" | "upcoming", 축제 전용
}
