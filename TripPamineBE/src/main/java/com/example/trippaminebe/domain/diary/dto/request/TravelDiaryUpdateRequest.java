package com.example.trippaminebe.domain.diary.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}