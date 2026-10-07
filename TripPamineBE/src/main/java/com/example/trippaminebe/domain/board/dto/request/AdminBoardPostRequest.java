package com.example.trippaminebe.domain.board.dto.request;

import com.example.trippaminebe.domain.board.entity.BoardCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// 게시글 작성/수정 요청 DTO (관리자용) - 공지(NOTICE) 포함 모든 카테고리 + 상단 고정 지정 가능
@Getter
@Setter
@Schema(description = "게시글 작성/수정 요청 DTO (관리자용)")
public class AdminBoardPostRequest {

  @NotNull(message = "카테고리를 선택해주세요.")
  @Schema(description = "카테고리", example = "NOTICE")
  private BoardCategory category;

  @NotBlank(message = "제목을 입력해주세요.")
  @Size(max = 200, message = "제목은 200자 이하로 입력해주세요.")
  private String title;

  @NotBlank(message = "내용을 입력해주세요.")
  @Size(max = 20000, message = "내용은 20,000자 이하로 입력해주세요.")
  private String content;

  @Schema(description = "상단 고정 여부", example = "true")
  private boolean pinned;

  @Schema(description = "[수정 전용] 유지할 기존 이미지 ID 목록")
  private List<Long> keepImageIds = new ArrayList<>();
}
