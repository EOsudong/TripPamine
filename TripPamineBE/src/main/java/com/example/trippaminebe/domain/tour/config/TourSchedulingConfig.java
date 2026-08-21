package com.example.trippaminebe.domain.tour.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TourCacheRefreshScheduler의 @Scheduled가 동작하려면 스케줄링 기능을 켜야 한다.
 * (프로젝트 어디에도 @EnableScheduling이 없어서 tour 도메인 전용으로 여기에 추가)
 */
@Configuration
@EnableScheduling
public class TourSchedulingConfig {
}
