package com.example.trippaminebe.domain.quest.entity;

// 칭호 자동 획득 조건 유형
public enum TitleConditionType {

    // 퀘스트 1회 이상 완료 시 (requiredConditionValue는 사용하지 않음)
    FIRST_QUEST_CLEAR,

    // 누적 완료 퀘스트 수 >= requiredConditionValue
    QUEST_COUNT,

    // RPG 레벨 >= requiredConditionValue
    LEVEL_REACHED
}
