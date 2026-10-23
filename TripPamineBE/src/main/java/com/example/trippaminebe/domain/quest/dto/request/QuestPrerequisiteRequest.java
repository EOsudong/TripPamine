package com.example.trippaminebe.domain.quest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 관리자가 기존 퀘스트 ID를 후속 퀘스트의 선행 조건으로 연결한다.
@Getter
@NoArgsConstructor
public class QuestPrerequisiteRequest {

    @NotNull(message = "선행 퀘스트 ID는 필수입니다.")
    @Positive(message = "선행 퀘스트 ID는 양수여야 합니다.")
    private Long prerequisiteQuestId;
}
