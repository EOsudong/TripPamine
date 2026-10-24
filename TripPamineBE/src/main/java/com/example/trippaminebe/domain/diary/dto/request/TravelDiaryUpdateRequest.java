package com.example.trippaminebe.domain.diary.dto.request;

import com.example.trippaminebe.domain.diary.entity.KeywordType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "AI 다이어리 수정 요청 DTO")
public class TravelDiaryUpdateRequest {

  @NotBlank(message = "제목을 입력해 주세요.")
  @Size(max = 150, message = "제목은 150자 이하로 입력해 주세요.")
  private String aiTitle;

  @NotBlank(message = "본문을 입력해 주세요.")
  @Size(max = 5000, message = "본문은 5000자 이하로 입력해 주세요.")
  private String aiContent;

  // null이면 해시태그는 그대로 두고, 값이 있으면(빈 목록 포함) 그 목록으로 통째로 교체한다
  @Valid
  @Size(max = 20, message = "해시태그는 최대 20개까지 등록할 수 있어요.")
  private List<KeywordItem> keywords;

  @Getter
  @Setter
  @NoArgsConstructor
  public static class KeywordItem {

    @NotNull(message = "해시태그 종류를 선택해 주세요.")
    private KeywordType keywordType;

    @NotBlank(message = "해시태그를 입력해 주세요.")
    @Size(max = 50, message = "해시태그는 50자 이하로 입력해 주세요.")
    private String keywordName;
  }
}