package com.example.trippaminebe.domain.board.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// Spring Data Page를 그대로 직렬화하면 버전에 따라 JSON 구조가 바뀔 수 있어(PageImpl 직렬화 경고)
// 프론트가 쓰는 필드만 고정된 형태로 내려준다. 필드명은 기존 Page 응답(content/totalPages/number/size)과 동일.
@Getter
@AllArgsConstructor
public class BoardPageResponse<T> {

  private List<T> content;
  private long totalElements;
  private int totalPages;
  private int number; // 현재 페이지 (0부터)
  private int size;
  private boolean first;
  private boolean last;

  public static <T> BoardPageResponse<T> from(Page<T> page) {
    return new BoardPageResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize(),
        page.isFirst(),
        page.isLast()
    );
  }
}
