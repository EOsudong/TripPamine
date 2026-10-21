package com.example.trippaminebe.domain.asset.scheduler;

import com.example.trippaminebe.domain.asset.service.AssetDashboardService;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * =========================================================
 * 매일 자산 Snapshot 기록
 * =========================================================
 *
 * [추가 배경]
 * 자산 Snapshot 은 원래 사용자가 자산관리 화면을 연 날에만 저장됐다.
 * 그래서 화면을 안 연 날은 자산 내역 그래프에 점이 비었다.
 * (1주일 보기에 점이 1~2개뿐인 경우)
 *
 * 매일 밤 23:50(한국 시간)에 모든 활성 사용자의 오늘 Snapshot 을 갱신한다.
 * 연동 계좌도 없고 이번 달 가계부 기록도 없는 사용자는 건너뛴다.
 *
 * (@EnableScheduling 은 tour 도메인 TourSchedulingConfig 에 이미 선언되어 있다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSnapshotScheduler {

    private final UserRepository userRepository;
    private final AssetDashboardService assetDashboardService;


    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void recordDailySnapshots() {

        int saved = 0;
        int failed = 0;

        for (User user : userRepository.findAll()) {

            if (user.getStatus() == UserStatus.WITHDRAW) {
                continue;
            }

            try {
                if (assetDashboardService.refreshTodaySnapshot(user.getId())) {
                    saved++;
                }
            } catch (RuntimeException e) {
                // 한 사용자의 실패가 전체 기록을 멈추지 않도록 계속 진행
                failed++;
                log.warn("[AssetSnapshotScheduler] userId={} 실패: {}", user.getId(), e.getMessage());
            }
        }

        log.info("[AssetSnapshotScheduler] 일일 자산 Snapshot 기록 완료 - 저장 {}명, 실패 {}명", saved, failed);
    }
}
