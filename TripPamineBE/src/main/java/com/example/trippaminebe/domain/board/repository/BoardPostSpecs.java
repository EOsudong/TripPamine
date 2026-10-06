package com.example.trippaminebe.domain.board.repository;

import com.example.trippaminebe.domain.board.dto.request.BoardSearchType;
import com.example.trippaminebe.domain.board.entity.BoardCategory;
import com.example.trippaminebe.domain.board.entity.BoardPost;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 게시글 목록 검색 조건 조립기. null/빈 값인 조건은 자동으로 빠진다.
public final class BoardPostSpecs {

  private BoardPostSpecs() {
  }

  public static Specification<BoardPost> search(
      BoardCategory category,
      BoardStatus status,
      BoardSearchType searchType,
      String keyword
  ) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (category != null) {
        predicates.add(cb.equal(root.get("category"), category));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      if (StringUtils.hasText(keyword)) {
        // LIKE 와일드카드 문자(% _)는 이스케이프해서 사용자가 입력한 글자 그대로 검색되게 한다.
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        BoardSearchType type = searchType != null ? searchType : BoardSearchType.TITLE_CONTENT;

        Predicate title = cb.like(cb.lower(root.<String>get("title")), pattern, '\\');
        Predicate content = cb.like(cb.lower(root.<String>get("content")), pattern, '\\');

        switch (type) {
          case TITLE -> predicates.add(title);
          case CONTENT -> predicates.add(content);
          case TITLE_CONTENT -> predicates.add(cb.or(title, content));
          case WRITER -> {
            var user = root.join("user", JoinType.LEFT);
            var admin = root.join("admin", JoinType.LEFT);
            predicates.add(cb.or(
                cb.like(cb.lower(user.<String>get("userName")), pattern, '\\'),
                cb.like(cb.lower(admin.<String>get("adminName")), pattern, '\\')
            ));
          }
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
