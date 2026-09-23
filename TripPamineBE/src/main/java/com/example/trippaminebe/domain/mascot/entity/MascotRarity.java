package com.example.trippaminebe.domain.mascot.entity;

// 마스코트 희귀도. 포획 확률과 보상 포인트를 차등 적용하는 데 사용한다.
// (quest.entity.QuestStatus 처럼 단순 enum으로 관리)
public enum MascotRarity {
    COMMON,
    RARE,
    EPIC
}
