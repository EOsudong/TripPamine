package com.example.trippaminebe.domain.replanning.service;

import com.example.trippaminebe.domain.replanning.entity.TravelReplanningHistory;
import com.example.trippaminebe.domain.replanning.repository.TravelReplanningHistoryRepository;
import com.example.trippaminebe.domain.recommendation.entity.AiTravelRecommendation;
import com.example.trippaminebe.domain.recommendation.repository.AiTravelRecommendationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TravelReplanningRestoreService {

    private final TravelReplanningHistoryRepository historyRepository;
    private final AiTravelRecommendationRepository recommendationRepository;
    private final TravelPlanRepository travelPlanRepository;

    /**
     * AI 여행 재계획 이전 일정 복구
     */
    @Transactional
    public void restore(Long planId, Long historyId, Long userId) {

        // 0. 로그인 사용자의 여행 계획 소유권 검증
        travelPlanRepository.findByPlanIdAndUser_IdAndDelYn(
                planId,
                userId,
                "N"
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "여행 계획을 찾을 수 없거나 접근 권한이 없습니다."
        ));

        // 1. 복구할 변경 이력 조회
        TravelReplanningHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "변경 이력을 찾을 수 없습니다."
                ));

        // 2. 해당 사용자와 여행 계획의 이력인지 확인
        if (!Objects.equals(history.getPlanId(), planId)
                || !Objects.equals(history.getUserId(), userId)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 변경 이력에 접근할 권한이 없습니다."
            );
        }

        // 3. 현재 AI 추천 일정 조회 및 DB 행 잠금
        AiTravelRecommendation recommendation =
                recommendationRepository.findByPlanIdForUpdate(planId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "AI 추천 일정을 찾을 수 없습니다."
                        ));

        // 4. 현재 일정과 복구 대상 이력의 변경 후 일정 비교
        String currentJson = recommendation.getRecommendJson();

        if (!Objects.equals(currentJson, history.getReplannedJson())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이후 일정이 변경되어 복구할 수 없습니다."
            );
        }

        // 5. 복구 작업 자체도 변경 이력으로 저장
        TravelReplanningHistory restoreHistory = new TravelReplanningHistory();

        restoreHistory.setPlanId(planId);
        restoreHistory.setUserId(userId);
        restoreHistory.setTargetDay(history.getTargetDay());
        restoreHistory.setOriginalJson(currentJson);
        restoreHistory.setReplannedJson(history.getOriginalJson());
        restoreHistory.setReason("AI 재계획 이력 복구 #" + historyId);

        historyRepository.save(restoreHistory);

        // 6. 기존 일정으로 복구
        recommendation.setRecommendJson(history.getOriginalJson());

        recommendationRepository.save(recommendation);
    }
}