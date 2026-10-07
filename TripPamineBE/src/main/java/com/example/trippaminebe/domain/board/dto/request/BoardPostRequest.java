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

// 게시글 작성/수정 요청 DTO (회원용).
// multipart/form-data 요청의 "request" 파트(JSON)로 받고, 이미지는 "images" 파트로 따로 받는다.
@Getter
@Setter
@Schema(description = "게시글 작성/수정 요청 DTO (회원용)")
public class BoardPostRequest {

  @NotNull(message = "카테고리를 선택해주세요.")
  @Schema(description = "카테고리 (REVIEW/QNA/FREE) - NOTICE는 관리자 전용", example = "REVIEW")
  private BoardCategory category;

  @NotBlank(message = "제목을 입력해주세요.")
  @Size(max = 200, message = "제목은 200자 이하로 입력해주세요.")
  @Schema(description = "제목", example = "제주 3박 4일 후기")
  private String title;

  @NotBlank(message = "내용을 입력해주세요.")
  @Size(max = 20000, message = "내용은 20,000자 이하로 입력해주세요.")
  @Schema(description = "본문")
  private String content;

  // 수정 시에만 사용: 기존 이미지 중 남겨둘 이미지 ID 목록 (여기 없는 기존 이미지는 삭제됨)
  @Schema(description = "[수정 전용] 유지할 기존 이미지 ID 목록")
  private List<Long> keepImageIds = new ArrayList<>();
}
