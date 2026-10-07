package com.example.trippaminebe.domain.quest.dto.response;

import com.example.trippaminebe.domain.quest.entity.Title;
import com.example.trippaminebe.domain.quest.entity.TitleConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "칭호 응답 DTO")
public class TitleResponse {

    private Long titleId;
    private String titleName;
    private String description;
    private String iconUrl;
    private TitleConditionType requiredConditionType;
    private Integer requiredConditionValue;
    private boolean earned;
    private LocalDateTime earnedAt;
    private boolean equipped;

    // 사용자 화면(내 칭호 목록)용 - 보유/장착 여부를 함께 내려준다.
    public static TitleResponse of(Title title, boolean earned, LocalDateTime earnedAt, boolean equipped) {
        return TitleResponse.builder()
            .titleId(title.getTitleId())
            .titleName(title.getTitleName())
            .description(title.getDescription())
            .iconUrl(title.getIconUrl())
            .requiredConditionType(title.getRequiredConditionType())
            .requiredConditionValue(title.getRequiredConditionValue())
            .earned(earned)
            .earnedAt(earnedAt)
            .equipped(equipped)
            .build();
    }

    // 관리자 화면(칭호 마스터 관리)용 - 특정 유저 기준 보유/장착 개념이 없으므로 전부 기본값(false/null).
    // AdminQuest.locked가 관리자 조회에서 항상 false인 것과 동일한 패턴.
    public static TitleResponse from(Title title) {
        return of(title, false, null, false);
    }
}
