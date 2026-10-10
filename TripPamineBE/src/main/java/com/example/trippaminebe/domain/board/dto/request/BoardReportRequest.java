package com.example.trippaminebe.domain.board.dto.request;

import com.example.trippaminebe.domain.board.entity.BoardReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "게시글 신고 요청 DTO")
public class BoardReportRequest {

  @NotNull(message = "신고 사유를 선택해주세요.")
  @Schema(description = "신고 사유", example = "SPAM")
  private BoardReportReason reason;

  // 사유가 ETC(기타)면 필수
  @Size(max = 500, message = "상세 내용은 500자 이하로 입력해주세요.")
  @Schema(description = "상세 내용 (기타 사유일 때 필수)", example = "같은 광고 글을 반복해서 올립니다.")
  private String detail;
}
