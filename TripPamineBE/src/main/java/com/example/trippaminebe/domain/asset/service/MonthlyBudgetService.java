package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.asset.dto.MonthlyBudgetRequestDto;
import com.example.trippaminebe.domain.asset.dto.MonthlyBudgetResponseDto;
import com.example.trippaminebe.domain.asset.entity.MonthlyBudget;
import com.example.trippaminebe.domain.asset.repository.MonthlyBudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyBudgetService {

    private final MonthlyBudgetRepository monthlyBudgetRepository;


    // ========================================
    // 월별 예산 등록
    // ========================================
    @Transactional
    public Long register(MonthlyBudgetRequestDto dto) {

        MonthlyBudget monthlyBudget =
                MonthlyBudget.builder()
                        .userId(dto.getUserId())
                        .budgetYear(dto.getBudgetYear())
                        .budgetMonth(dto.getBudgetMonth())
                        .categoryId(dto.getCategoryId())
                        .categoryName(dto.getCategoryName())
                        .budgetAmount(dto.getBudgetAmount())
                        .build();

        MonthlyBudget savedBudget =
                monthlyBudgetRepository.save(monthlyBudget);

        return savedBudget.getBudgetId();
    }


    // ========================================
    // 전체 예산 조회
    // ========================================
    public List<MonthlyBudgetResponseDto> getAll() {

        return monthlyBudgetRepository
                .findAll()
                .stream()
                .map(MonthlyBudgetResponseDto::new)
                .toList();
    }


    // ========================================
    // 특정 예산 조회
    // ========================================
    public MonthlyBudgetResponseDto getOne(Long budgetId) {

        MonthlyBudget monthlyBudget =
                monthlyBudgetRepository
                        .findById(budgetId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "월별 예산이 존재하지 않습니다. id="
                                                + budgetId
                                )
                        );

        return new MonthlyBudgetResponseDto(monthlyBudget);
    }


    // ========================================
    // 예산 수정
    // ========================================
    @Transactional
    public void modify(
            Long budgetId,
            MonthlyBudgetRequestDto dto
    ) {

        MonthlyBudget monthlyBudget =
                monthlyBudgetRepository
                        .findById(budgetId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "월별 예산이 존재하지 않습니다. id="
                                                + budgetId
                                )
                        );

        monthlyBudget.setBudgetYear(
                dto.getBudgetYear()
        );

        monthlyBudget.setBudgetMonth(
                dto.getBudgetMonth()
        );

        monthlyBudget.setCategoryId(
                dto.getCategoryId()
        );

        monthlyBudget.setCategoryName(
                dto.getCategoryName()
        );

        monthlyBudget.setBudgetAmount(
                dto.getBudgetAmount()
        );
    }


    // ========================================
    // 예산 삭제
    // ========================================
    @Transactional
    public void remove(Long budgetId) {

        MonthlyBudget monthlyBudget =
                monthlyBudgetRepository
                        .findById(budgetId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "월별 예산이 존재하지 않습니다. id="
                                                + budgetId
                                )
                        );

        monthlyBudgetRepository.delete(monthlyBudget);
    }
}