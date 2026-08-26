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
 *   1) 앱이 완전히 뜬 직후 1번 (ApplicationReadyEvent)
 *   2) 매일 오전 9시 10분 (cron)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinlifeCacheRefreshScheduler {

  private final FinlifeCacheService finlifeCacheService;

  @EventListener(ApplicationReadyEvent.class)
  public void warmUpOnStartup() {
    if (!finlifeCacheService.isWarmupNeeded()) {
      log.info("FINLIFE 캐시가 이미 최신 상태라 기동 시 웜업을 건너뜁니다.");
      return;
    }
    log.info("앱 기동 완료 - FINLIFE(금융상품 한눈에) 캐시 최초 적재를 시작합니다.");
    finlifeCacheService.refreshAll();
  }

  // 서버 실행 환경의 기본 타임존과 무관하게 항상 한국 시간 오전 9시 10분에 돌도록 zone을 명시
  @Scheduled(cron = "0 10 9 * * *", zone = "Asia/Seoul")
  public void refreshDaily() {
    log.info("정기 스케줄 - FINLIFE(금융상품 한눈에) 캐시를 새로고침합니다.");
    finlifeCacheService.refreshAll();
  }
}
