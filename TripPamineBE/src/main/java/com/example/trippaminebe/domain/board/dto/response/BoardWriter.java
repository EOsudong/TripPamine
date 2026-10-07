package com.example.trippaminebe.domain.board.dto.response;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.board.entity.BoardWriterType;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserStatus;

// 응답 DTO들이 공통으로 쓰는 작성자 표시 정보 계산기 (DTO 필드로 직접 노출되지는 않음)
record BoardWriter(Long writerId, String writerName, String profileImageUrl, BoardWriterType writerType) {

  static BoardWriter of(User user, Admin admin) {
    if (admin != null) {
      return new BoardWriter(admin.getId(), admin.getAdminName() + " (운영자)", null, BoardWriterType.ADMIN);
    }
    if (user == null) {
      return new BoardWriter(null, "알 수 없음", null, BoardWriterType.USER);
    }
    if (user.getStatus() == UserStatus.WITHDRAW) {
      return new BoardWriter(user.getId(), "탈퇴한 회원", null, BoardWriterType.USER);
    }
    return new BoardWriter(user.getId(), user.getUserName(), user.getProfileImageUrl(), BoardWriterType.USER);
  }
}
