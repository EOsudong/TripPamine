package com.example.trippaminebe.domain.diary.dto.response;

import com.example.trippaminebe.domain.diary.entity.DiaryKeyword;
import com.example.trippaminebe.domain.diary.entity.KeywordType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "AI 다이어리 키워드 응답 DTO")
public class DiaryKeywordResponse {

    private Long keywordId;
    private KeywordType keywordType;
    private String keywordName;

    public static DiaryKeywordResponse from(DiaryKeyword keyword) {
        return DiaryKeywordResponse.builder()
            .keywordId(keyword.getKeywordId())
            .keywordType(keyword.getKeywordType())
            .keywordName(keyword.getKeywordName())
            .build();
    }
}
