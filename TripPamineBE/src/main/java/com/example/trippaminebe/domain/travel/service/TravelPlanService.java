package com.example.trippaminebe.domain.travel.service;

import com.example.trippaminebe.domain.travel.dto.TravelPlanRequest;
import com.example.trippaminebe.domain.travel.dto.TravelPlanResponse;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.exception.TravelPlanNotFoundException;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TravelPlanService {

    private final TravelPlanRepository travelPlanRepository;
    private final UserRepository userRepository;

    public TravelPlanResponse create(Long userId, TravelPlanRequest request) {
        if (request.getStartDate() != null &&
            request.getStartDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("출발일시는 현재 이후여야 합니다.");
        }

        if (request.getStartDate() != null &&
            request.getEndDate() != null &&
            request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("종료일시는 출발일시 이후여야 합니다.");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));

        TravelPlan travelPlan = new TravelPlan();
        travelPlan.setUser(user);
        travelPlan.setPlanName(request.getPlanName());
        travelPlan.setTotalBudget(request.getTotalBudget());
        travelPlan.setCompanionType(request.getCompanionType());
        travelPlan.setBlindYn(request.getBlindYn() != null ? request.getBlindYn() : "N");
        travelPlan.setStartDate(request.getStartDate());
        travelPlan.setEndDate(request.getEndDate());
        travelPlan.setLocationCd(request.getLocationCd() != null ? request.getLocationCd() : "ETC");
        travelPlan.setDelYn("N");

        TravelPlan saved = travelPlanRepository.save(travelPlan);

        return TravelPlanResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TravelPlanResponse> findAllByUserId(Long userId) {
        return travelPlanRepository.findByUser_IdAndDelYn(userId, "N")
            .stream()
            .map(TravelPlanResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public TravelPlanResponse findById(Long planId, Long userId) {
        TravelPlan travelPlan = travelPlanRepository.findByPlanIdAndUser_IdAndDelYn(planId, userId, "N")
            .orElseThrow(() -> new TravelPlanNotFoundException("여행 계획을 찾을 수 없거나 접근 권한이 없습니다."));
        return TravelPlanResponse.from(travelPlan);
    }

    public TravelPlanResponse update(Long planId, Long userId, TravelPlanRequest request) {
        if (request.getStartDate() != null &&
            request.getStartDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("출발일시는 현재 이후여야 합니다.");
        }

        if (request.getStartDate() != null &&
            request.getEndDate() != null &&
            request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("종료일시는 출발일시 이후여야 합니다.");
        }

        TravelPlan travelPlan = travelPlanRepository.findByPlanIdAndUser_IdAndDelYn(planId, userId, "N")
            .orElseThrow(() -> new TravelPlanNotFoundException("여행 계획을 찾을 수 없거나 수정 권한이 없습니다."));

        travelPlan.setPlanName(request.getPlanName());
        travelPlan.setTotalBudget(request.getTotalBudget());
        travelPlan.setCompanionType(request.getCompanionType());
        travelPlan.setBlindYn(request.getBlindYn());
        travelPlan.setStartDate(request.getStartDate());
        travelPlan.setEndDate(request.getEndDate());
        travelPlan.setLocationCd(request.getLocationCd() != null ? request.getLocationCd() : "ETC");

        return TravelPlanResponse.from(travelPlan);
    }

    public void delete(Long planId, Long userId) {
        TravelPlan travelPlan = travelPlanRepository.findByPlanIdAndUser_IdAndDelYn(planId, userId, "N")
            .orElseThrow(() -> new TravelPlanNotFoundException("여행 계획을 찾을 수 없거나 삭제 권한이 없습니다."));

        travelPlan.setDelYn("Y");
    }
}