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
 *   1) 서버 기동: 비어있는 캐시 또는 놓친 정기 갱신만 보완한다.
 *   2) 한국시간 매일 09:00: 기존과 동일하게 전체 캐시 갱신.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TourCacheRefreshScheduler {

  private final TourCacheService tourCacheService;

  @EventListener(ApplicationReadyEvent.class)
  public void warmUpOnStartup() {
    // 30분 경과만으로 전체 API를 다시 호출하지 않고 정기 주기 누락 여부를 검사한다.
    tourCacheService.refreshOnStartup();
  }

  // 서버 실행 환경의 기본 타임존과 무관하게 항상 한국 시간 오전 9시에 돌도록 zone을 명시
  @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
  public void refreshDaily() {
    log.info("정기 스케줄 - TourAPI 캐시를 새로고침합니다.");
    tourCacheService.refreshAll();
  }
}
