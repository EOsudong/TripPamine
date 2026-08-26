package com.example.trippaminebe.domain.finlife.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 금융감독원 오픈API(금융상품 한눈에, FINLIFE) 응답을 캐시해두는 테이블.
 * cacheKey 하나가 FinlifeCacheService가 실제로 외부 API에 던지는 "물리적인 조회 단위" 하나에
 * 대응한다
 */
@Entity
@Table(name = "FINLIFE_CACHE_ENTRY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinlifeCacheEntry {

  @Id
  @Column(name = "CACHE_ENTRY_ID")
  @SequenceGenerator(name = "seqFinlifeCacheEntry", sequenceName = "SEQ_FINLIFE_CACHE_ENTRY", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqFinlifeCacheEntry")
  private Long id;

  @Column(name = "CACHE_KEY", nullable = false, unique = true, length = 50)
  private String cacheKey;

  // FinancialProductResponse 목록을 JSON 문자열로 직렬화해서 저장 (Oracle CLOB)
  @Lob
  @Column(name = "PAYLOAD_JSON", nullable = false)
  private String payloadJson;

  @Column(name = "FETCHED_AT", nullable = false)
  private LocalDateTime fetchedAt;

  @Builder
  private FinlifeCacheEntry(String cacheKey, String payloadJson, LocalDateTime fetchedAt) {
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
