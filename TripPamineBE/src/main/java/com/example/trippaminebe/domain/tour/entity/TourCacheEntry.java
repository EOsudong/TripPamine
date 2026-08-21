package com.example.trippaminebe.domain.tour.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * TourAPI(한국관광공사 오픈API) 응답을 캐시해두는 테이블.
 * cacheKey 하나가 TourCacheService가 실제로 외부 API에 던지는 "물리적인 조회 단위" 하나에
 * 대응한다 (예: "festivals:ALL", "destinations:체험 관광", "industry:숙박" 등).
 * 사용자 요청은 이 테이블만 읽고, 실제 외부 API 호출은 TourCacheRefreshScheduler가
 * 24시간 주기(+ 앱 기동 시 1번)로만 수행한다.
 */
@Entity
@Table(name = "TOUR_CACHE_ENTRY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourCacheEntry {

  @Id
  @Column(name = "CACHE_ENTRY_ID")
  @SequenceGenerator(name = "seqTourCacheEntry", sequenceName = "SEQ_TOUR_CACHE_ENTRY", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqTourCacheEntry")
  private Long id;

  @Column(name = "CACHE_KEY", nullable = false, unique = true, length = 50)
  private String cacheKey;

  // TourItemResponse 목록을 JSON 문자열로 직렬화해서 저장 (Oracle CLOB)
  @Lob
  @Column(name = "PAYLOAD_JSON", nullable = false)
  private String payloadJson;

  @Column(name = "FETCHED_AT", nullable = false)
  private LocalDateTime fetchedAt;

  @Builder
  private TourCacheEntry(String cacheKey, String payloadJson, LocalDateTime fetchedAt) {
    this.cacheKey = cacheKey;
    this.payloadJson = payloadJson;
    this.fetchedAt = fetchedAt;
  }

  /** 스케줄러가 새로 받아온 데이터로 캐시 내용을 통째로 교체할 때 사용 */
  public void refresh(String payloadJson, LocalDateTime fetchedAt) {
    this.payloadJson = payloadJson;
    this.fetchedAt = fetchedAt;
  }
}
