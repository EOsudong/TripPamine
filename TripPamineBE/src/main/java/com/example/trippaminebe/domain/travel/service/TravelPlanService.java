package com.example.trippaminebe.domain.travel.service;

import com.example.trippaminebe.domain.travel.dto.TravelPlanRequest;
import com.example.trippaminebe.domain.travel.dto.TravelPlanResponse;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.event.TravelPlanActivityApplicationEvent;
import com.example.trippaminebe.domain.travel.exception.TravelPlanNotFoundException;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 여행 계획 핵심 Business Service.
 *
 *
 * =========================================================
 * 이번 변경의 핵심
 * =========================================================
 *
 * 여행 계획 자체의 저장/수정/삭제 책임은 그대로 유지한다.
 *
 * 추가로 Business Transaction 내부에서:
 *
 * ApplicationEventPublisher
 *
 * 를 이용해 여행 변화 사실만 외부에 알린다.
 *
 *
 * 실제 AI_MANAGER_EVENTS 저장은
 * 이 Service가 담당하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TravelPlanService {

    private final TravelPlanRepository
        travelPlanRepository;

    private final UserRepository
        userRepository;


    /**
     * Spring의 Domain 간 Event 전달 도구.
     */
    private final ApplicationEventPublisher
        applicationEventPublisher;


    /* =====================================================
     * CREATE
     * ===================================================== */

    public TravelPlanResponse create(
        Long userId,
        TravelPlanRequest request
    ) {

        validateDates(
            request
        );


        User user =
            userRepository
                .findById(
                    userId
                )
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "존재하지 않는 회원입니다. id="
                                + userId
                        )
                );


        TravelPlan travelPlan =
            new TravelPlan();


        travelPlan.setUser(
            user
        );

        travelPlan.setPlanName(
            request.getPlanName()
        );

        travelPlan.setTotalBudget(
            request.getTotalBudget()
        );

        travelPlan.setCompanionType(
            request.getCompanionType()
        );

        travelPlan.setCompanionCount(
            request.getCompanionCount()
        );

        travelPlan.setBlindYn(
            request.getBlindYn() != null
                ? request.getBlindYn()
                : "N"
        );

        travelPlan.setStartDate(
            request.getStartDate()
        );

        travelPlan.setEndDate(
            request.getEndDate()
        );

        travelPlan.setLocationCd(
            request.getLocationCd() != null
                ? request.getLocationCd()
                : "ETC"
        );

        travelPlan.setDelYn(
            "N"
        );


        TravelPlan saved =
            travelPlanRepository
                .save(
                    travelPlan
                );


        /*
         * =================================================
         * Travel Created Event
         * =================================================
         *
         * 이 시점에서는 Event를 DB에 직접 넣지 않는다.
         *
         * Spring Transaction이 실제 COMMIT된 뒤
         * AI Manager Listener가 처리한다.
         */
        applicationEventPublisher.publishEvent(

            TravelPlanActivityApplicationEvent.from(

                TravelPlanActivityApplicationEvent
                    .Type
                    .CREATED,

                saved
            )
        );


        return TravelPlanResponse.from(
            saved
        );
    }


    /* =====================================================
     * READ
     * ===================================================== */

    @Transactional(readOnly = true)
    public List<TravelPlanResponse> findAllByUserId(
        Long userId
    ) {

        return travelPlanRepository
            .findByUser_IdAndDelYn(
                userId,
                "N"
            )
            .stream()
            .map(
                TravelPlanResponse::from
            )
            .toList();
    }


    @Transactional(readOnly = true)
    public TravelPlanResponse findById(
        Long planId,
        Long userId
    ) {

        TravelPlan travelPlan =
            travelPlanRepository
                .findByPlanIdAndUser_IdAndDelYn(
                    planId,
                    userId,
                    "N"
                )
                .orElseThrow(
                    () ->
                        new TravelPlanNotFoundException(
                            "여행 계획을 찾을 수 없거나 접근 권한이 없습니다."
                        )
                );


        return TravelPlanResponse.from(
            travelPlan
        );
    }


    /* =====================================================
     * UPDATE
     * ===================================================== */

    public TravelPlanResponse update(
        Long planId,
        Long userId,
        TravelPlanRequest request
    ) {

        validateDates(
            request
        );


        TravelPlan travelPlan =
            travelPlanRepository
                .findByPlanIdAndUser_IdAndDelYn(
                    planId,
                    userId,
                    "N"
                )
                .orElseThrow(
                    () ->
                        new TravelPlanNotFoundException(
                            "여행 계획을 찾을 수 없거나 수정 권한이 없습니다."
                        )
                );


        travelPlan.setPlanName(
            request.getPlanName()
        );

        travelPlan.setTotalBudget(
            request.getTotalBudget()
        );

        travelPlan.setCompanionType(
            request.getCompanionType()
        );

        travelPlan.setCompanionCount(
            request.getCompanionCount()
        );

        /*
         * 기존 TravelPlanService 동작을 유지한다.
         *
         * 이번 STEP은 Event 연결이 목적이므로
         * 기존 Business Rule은 임의로 변경하지 않는다.
         */
        travelPlan.setBlindYn(
            request.getBlindYn()
        );

        travelPlan.setStartDate(
            request.getStartDate()
        );

        travelPlan.setEndDate(
            request.getEndDate()
        );

        travelPlan.setLocationCd(
            request.getLocationCd() != null
                ? request.getLocationCd()
                : "ETC"
        );


        /*
         * =================================================
         * Travel Updated Event
         * =================================================
         *
         * JPA Dirty Checking이 Transaction COMMIT 시
         * TravelPlan 변경사항을 저장한다.
         *
         * Listener 역시 AFTER_COMMIT이므로
         * 실제 Update가 성공한 이후에만 Event가 저장된다.
         */
        applicationEventPublisher.publishEvent(

            TravelPlanActivityApplicationEvent.from(

                TravelPlanActivityApplicationEvent
                    .Type
                    .UPDATED,

                travelPlan
            )
        );


        return TravelPlanResponse.from(
            travelPlan
        );
    }


    /* =====================================================
     * DELETE
     * ===================================================== */

    /**
     * 실제 DELETE SQL을 사용하는 것이 아니라
     * DEL_YN = 'Y'로 변경하는 Soft Delete.
     */
    public void delete(
        Long planId,
        Long userId
    ) {

        TravelPlan travelPlan =
            travelPlanRepository
                .findByPlanIdAndUser_IdAndDelYn(
                    planId,
                    userId,
                    "N"
                )
                .orElseThrow(
                    () ->
                        new TravelPlanNotFoundException(
                            "여행 계획을 찾을 수 없거나 삭제 권한이 없습니다."
                        )
                );


        travelPlan.setDelYn(
            "Y"
        );


        /*
         * 삭제 Event에서도 여행 이름/기간을 남겨두면
         * 나중에 사용자가 삭제한 여행을 AI Memory Evidence에서
         * 설명할 수 있다.
         */
        applicationEventPublisher.publishEvent(

            TravelPlanActivityApplicationEvent.from(

                TravelPlanActivityApplicationEvent
                    .Type
                    .DELETED,

                travelPlan
            )
        );
    }


    /* =====================================================
     * Validation
     * ===================================================== */

    /**
     * create / update에서 중복되던 날짜 검증을
     * 하나의 내부 메서드로 정리한다.
     *
     * Business Rule 자체는 기존과 동일하다.
     */
    private void validateDates(
        TravelPlanRequest request
    ) {

        if (
            request.getStartDate() != null
                &&
                request.getStartDate()
                    .isBefore(
                        LocalDateTime.now()
                    )
        ) {

            throw new IllegalArgumentException(
                "출발일시는 현재 이후여야 합니다."
            );
        }


        if (
            request.getStartDate() != null
                &&
                request.getEndDate() != null
                &&
                request.getEndDate()
                    .isBefore(
                        request.getStartDate()
                    )
        ) {

            throw new IllegalArgumentException(
                "종료일시는 출발일시 이후여야 합니다."
            );
        }
    }
}