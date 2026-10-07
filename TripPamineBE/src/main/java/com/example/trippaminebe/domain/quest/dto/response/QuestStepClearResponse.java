package com.example.trippaminebe.domain.quest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "연쇄 퀘스트 단계 클리어(GPS 인증) 응답 DTO")
public class QuestStepClearResponse {

    // 이번 요청으로 이 단계를 실제로 클리어했는지(반경 밖이면 false - 그냥 다시 시도)
    private boolean stepCleared;

    // 이번 클리어로 연쇄 퀘스트 전체가 완료되었는지(= 마지막 단계였는지)
    private boolean questCompleted;

    private Integer clearedStepCount;
    private Integer totalStepCount;

    // 다음에 클리어해야 할 단계 ID. 전체 완료되었으면 null.
    private Long nextStepId;

    // 전체 퀘스트 로그 현황(상태/보상/진행률). questCompleted=true일 때 보상 정보가 채워진다.
    private UserQuestLogResponse questLog;
}
