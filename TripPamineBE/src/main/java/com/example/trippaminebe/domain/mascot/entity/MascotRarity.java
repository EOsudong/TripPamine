package com.example.trippaminebe.domain.mascot.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 마스코트 희귀도. 포획 확률과 보상 포인트를 차등 적용하는 데 사용한다.
// (quest.entity.QuestStatus 처럼 단순 enum으로 관리)
//
// [포획 판정 서버 이전] baseCatchRate = 미니게임 점수가 0점일 때의 기본 포획 확률.
// 실제 확률은 MascotCatchJudge가 점수 보정을 더해 계산한다. 밸런스를 바꾸고 싶으면 이 값만 수정.
@Getter
@RequiredArgsConstructor
public enum MascotRarity {
    COMMON("일반", 0.90),
    RARE("희귀", 0.60),
    EPIC("에픽", 0.35);

    private final String label;
    private final double baseCatchRate;
}
