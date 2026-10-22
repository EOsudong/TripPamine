package com.example.trippaminebe.domain.finlife.scheduler;

import com.example.trippaminebe.domain.finlife.service.FinlifeCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FINLIFE(금융상품 한눈에) 캐시(FINLIFE_CACHE_ENTRY)를 채우는 트리거 2가지.
 *   1) 기동 시: 비어있는 키와 놓친 정기 갱신을 보완
 *   2) 한국시간 매일 오전 9시 10분: 기존과 동일하게 전체 갱신
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinlifeCacheRefreshScheduler {

  private final FinlifeCacheService finlifeCacheService;

  @EventListener(ApplicationReadyEvent.class)
  public void warmUpOnStartup() {
    // 기존 30분 TTL 기반의 불필요한 기동 재호출을 없앤다.
    finlifeCacheService.refreshOnStartup();
  }

  // 서버 실행 환경의 기본 타임존과 무관하게 항상 한국 시간 오전 9시 10분에 돌도록 zone을 명시
  @Scheduled(cron = "0 10 9 * * *", zone = "Asia/Seoul")
  public void refreshDaily() {
    log.info("정기 스케줄 - FINLIFE(금융상품 한눈에) 캐시를 새로고침합니다.");
    finlifeCacheService.refreshAll();
  }
}
