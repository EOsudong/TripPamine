package com.example.trippaminebe.domain.admin.dto.response;

import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserGrade;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 관리자가 회원 목록/상세를 볼 때 쓰는 응답 DTO. User 도메인의 UserResponseDto와 별개로
// 관리자 화면에 필요한 정보(가입일, 정지 사유 등)를 따로 구성함
@Getter
@Builder
@Schema(description = "관리자용 회원 조회 응답 DTO")
public class AdminUserResponseDto {
  private Long userId;
  private String email;
  private String name;
  private String userName;
  private String phoneNumber;
  private UserGrade grade;
  private UserStatus status;
  private String suspendReason;      // 정지 사유 (정지 상태가 아니면 null)
  private LocalDateTime createDate;

  public static AdminUserResponseDto adminUserResponseDto(User user) {
    return AdminUserResponseDto.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .name(user.getName())
        .userName(user.getUserName())
        .phoneNumber(user.getPhoneNumber())
        .grade(user.getGrade())
        .status(user.getStatus())
        .suspendReason(user.getSuspendReason())
        .createDate(user.getCreateDate())
        .build();
  }
}