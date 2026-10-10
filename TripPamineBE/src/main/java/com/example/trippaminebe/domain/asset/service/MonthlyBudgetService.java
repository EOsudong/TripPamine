package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.asset.dto.MonthlyBudgetRequestDto;
import com.example.trippaminebe.domain.asset.dto.MonthlyBudgetResponseDto;
import com.example.trippaminebe.domain.asset.entity.MonthlyBudget;
import com.example.trippaminebe.domain.asset.repository.MonthlyBudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


/**
 * 월 예산
 *
 * [보안 수정] 로그인 사용자 본인의 예산만 조회 · 수정 · 삭제한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyBudgetService {

    private final MonthlyBudgetRepository monthlyBudgetRepository;


    @Transactional
    public Long register(
            Long userId,
            MonthlyBudgetRequestDto dto
    ) {
        MonthlyBudget monthlyBudget =
                MonthlyBudget.builder()
                        // Body 의 userId 는 무시
                        .userId(userId)
                        .budgetYear(dto.getBudgetYear())
                        .budgetMonth(dto.getBudgetMonth())
                        .categoryId(dto.getCategoryId())
                        .categoryName(dto.getCategoryName())
                        .budgetAmount(dto.getBudgetAmount())
                        .build();

        return monthlyBudgetRepository.save(monthlyBudget).getBudgetId();
    }


    public List<MonthlyBudgetResponseDto> getAll(
            Long userId
    ) {
        return monthlyBudgetRepository
                .findByUserIdOrderByBudgetYearDescBudgetMonthDesc(userId)
                .stream()
                .map(MonthlyBudgetResponseDto::new)
                .toList();
    }


    public MonthlyBudgetResponseDto getOne(
            Long userId,
            Long budgetId
    ) {
        return new MonthlyBudgetResponseDto(
                findOwned(userId, budgetId)
        );
    }


    @Transactional
    public void modify(
            Long userId,
            Long budgetId,
            MonthlyBudgetRequestDto dto
    ) {
        MonthlyBudget monthlyBudget =
                findOwned(userId, budgetId);

        monthlyBudget.setBudgetYear(dto.getBudgetYear());
        monthlyBudget.setBudgetMonth(dto.getBudgetMonth());
        monthlyBudget.setCategoryId(dto.getCategoryId());
        monthlyBudget.setCategoryName(dto.getCategoryName());
        monthlyBudget.setBudgetAmount(dto.getBudgetAmount());
    }


    @Transactional
    public void remove(
            Long userId,
            Long budgetId
    ) {
        monthlyBudgetRepository.delete(
                findOwned(userId, budgetId)
        );
    }


    private MonthlyBudget findOwned(
            Long userId,
            Long budgetId
    ) {
        return monthlyBudgetRepository
                .findByBudgetIdAndUserId(budgetId, userId)
                .orElseThrow(() -> notFound("월 예산", budgetId));
    }


    private ResponseStatusException notFound(
            String what,
            Long id
    ) {
        // 남의 데이터인지 여부를 노출하지 않도록 "없음" 으로 통일
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                what + "을(를) 찾을 수 없습니다. id=" + id
        );
    }

}
