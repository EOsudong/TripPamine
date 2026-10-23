package com.example.trippaminebe.domain.quest.dto.response;

// 관리자 선행 관계 목록: 선택한 선행 퀘스트의 현재 표시 정보
public record QuestPrerequisiteResponse(
        Long prerequisiteQuestId,
        String prerequisiteQuestName,
        Boolean isActive
) {
}
