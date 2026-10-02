package com.example.trippaminebe.domain.nego.scheduler;

import com.example.trippaminebe.domain.nego.service.AiNegoService;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// TRAVEL_PLANS.START_DATE 주석("출발 3시간 전 오픈 체크용")대로,
// 출발이 하루 이내로 임박한 여행 계획을 주기적으로 스캔해서 AI 타임 네고(핫딜) 제안을 발송
@Component
@RequiredArgsConstructor
public class AiNegoScheduler {

    private static final int LEAD_TIME_HOURS = 24;

    private final TravelPlanRepository travelPlanRepository;
    private final AiNegoService aiNegoService;

    // 1분마다 스캔 (tour 도메인 TourCacheRefreshScheduler와 동일하게 @Scheduled 사용)
    @Scheduled(fixedRate = 60_000)
    public void scanUpcomingDepartures() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusHours(LEAD_TIME_HOURS);

        List<TravelPlan> upcomingPlans =
            travelPlanRepository.findByDelYnAndStartDateBetween("N", now, windowEnd);

        for (TravelPlan plan : upcomingPlans) {
            aiNegoService.generateOfferForPlan(plan);
        }
    }
}
