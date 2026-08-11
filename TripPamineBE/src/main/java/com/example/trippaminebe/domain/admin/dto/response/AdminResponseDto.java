package com.example.trippaminebe.domain.admin.dto.response;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.admin.entity.AdminRole;
import com.example.trippaminebe.domain.admin.entity.AdminStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 관리자 프로필 조회(GET /admin/{adminId}) 응답 DTO. Entity를 그대로 반환하지 않고
// 이 DTO를 거치는 이유: PASSWORD 필드가 실수로 응답에 노출되는 걸 차단하기 위함
@Getter
@Builder
@Schema(description = "관리자 프로필 응답 DTO")
public class AdminResponseDto {
  private Long adminId;               // 관리자일련번호: 내부 고유 식별자 PK
  private String adminLoginId;        // 관리자로그인아이디: 어드민 콘솔 로그인 ID
  private String adminName;           // 관리자명: 담당자 실명
  private AdminRole role;             // 관리자권한: SUPER(총괄)/STAFF(일반 운영자)
  private AdminStatus status;         // 계정상태: ACTIVE(사용중)/SUSPENDED(정지)
  private String email;               // 이메일: 비밀번호 재설정 및 알림 발송용
  private LocalDateTime lastLoginDate; // 최종로그인일시: 휴면 계정 관리용
  private LocalDateTime createDate;    // 생성일시: 관리자 계정 등록일

  // Entity -> Response 변환
  public static AdminResponseDto adminResponseDto(Admin admin) {
    return AdminResponseDto.builder()
        .adminId(admin.getId())
        .adminLoginId(admin.getAdminLoginId())
        .adminName(admin.getAdminName())
        .role(admin.getRole())
        .status(admin.getStatus())
        .email(admin.getEmail())
        .lastLoginDate(admin.getLastLoginDate())
        .createDate(admin.getCreateDate())
        .build();
  }
}