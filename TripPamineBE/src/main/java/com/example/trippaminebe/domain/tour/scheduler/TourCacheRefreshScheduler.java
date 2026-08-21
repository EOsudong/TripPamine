package com.example.trippaminebe.domain.tour.scheduler;

import com.example.trippaminebe.domain.tour.service.TourCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TourAPI 캐시(TOUR_CACHE_ENTRY)를 채우는 트리거 2가지.
 *   1) 앱이 완전히 뜬 직후 1번 (ApplicationReadyEvent) — 배포 직후에도 24시간을 기다리지 않고
 *      바로 캐시가 채워지도록 하는 웜업. 단, 개발 중 재시작을 자주 하는 경우까지 매번 TourAPI를
 *      다시 호출하지 않도록 TourCacheService.isWarmupNeeded()로 "정말 필요할 때만" 실행한다
 *      (기본값: 캐시가 30분 이내에 이미 갱신된 상태면 건너뜀 — TourCacheService 참고).
 *   2) 매일 오전 9시 (cron) — 이후로는 하루 1번만 TourAPI를 호출해서 갱신.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TourCacheRefreshScheduler {

  private final TourCacheService tourCacheService;

  @EventListener(ApplicationReadyEvent.class)
  public void warmUpOnStartup() {
    if (!tourCacheService.isWarmupNeeded()) {
      log.info("TourAPI 캐시가 이미 최신 상태라 기동 시 웜업을 건너뜁니다.");
      return;
    }
    log.info("앱 기동 완료 - TourAPI 캐시 최초 적재를 시작합니다.");
    tourCacheService.refreshAll();
  }

  // 서버 실행 환경의 기본 타임존과 무관하게 항상 한국 시간 오전 9시에 돌도록 zone을 명시
  @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
  public void refreshDaily() {
    log.info("정기 스케줄 - TourAPI 캐시를 새로고침합니다.");
    tourCacheService.refreshAll();
  }
}
