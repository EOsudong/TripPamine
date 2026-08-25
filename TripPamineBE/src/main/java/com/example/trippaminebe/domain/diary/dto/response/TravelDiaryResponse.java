package com.example.trippaminebe.domain.diary.dto.response;

import com.example.trippaminebe.domain.diary.entity.TravelDiary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "AI 여행 다이어리 응답 DTO")
public class TravelDiaryResponse {

    private Long diaryId;
    private Long planId;
    private String aiTitle;
    private String aiContent;
    private Long dopamineScore;
    private String aiImageUrl;
    private String shareYn;
    private LocalDateTime createDate;
    private List<DiaryKeywordResponse> keywords;

    public static TravelDiaryResponse from(TravelDiary diary) {
        return TravelDiaryResponse.builder()
            .diaryId(diary.getDiaryId())
            .planId(diary.getTravelPlan().getPlanId())
            .aiTitle(diary.getAiTitle())
            .aiContent(diary.getAiContent())
            .dopamineScore(diary.getDopamineScore())
            .aiImageUrl(diary.getAiImageUrl())
            .shareYn(diary.getShareYn())
            .createDate(diary.getCreateDate())
            .keywords(diary.getKeywords().stream()
                .map(DiaryKeywordResponse::from)
                .toList())
            .build();
    }
}
