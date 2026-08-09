package com.example.trippaminebe.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ADMINS")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Admin {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_ADMINS_GEN")
  @SequenceGenerator(name = "SEQ_ADMINS_GEN", sequenceName = "SEQ_ADMINS", allocationSize = 1)
  @Column(name = "ADMIN_ID")
  private Long id; // 관리자일련번호: 내부 고유 식별자(Sequence 적용) PK

  @Column(name = "ADMIN_LOGIN_ID", nullable = false, unique = true, length = 50)
  private String adminLoginId; // 관리자로그인아이디: 어드민 콘솔 로그인 ID(고객 EMAIL과 별개 체계)

  @Column(name = "PASSWORD", nullable = false, length = 255)
  private String password; // 관리자비밀번호: 로그인 비밀번호(암호화 해시 저장)

  @Column(name = "ADMIN_NAME", nullable = false, length = 50)
  private String adminName; // 관리자명: 담당자 실명

  @Enumerated(EnumType.STRING)
  @Column(name = "ADMIN_ROLE", length = 20)
  @Builder.Default
  private AdminRole role = AdminRole.STAFF; // 관리자권한: SUPER(총괄)/STAFF(일반 운영자)

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", length = 20)
  @Builder.Default
  private AdminStatus status = AdminStatus.ACTIVE; // 계정상태: ACTIVE(사용중)/SUSPENDED(정지)

  @Column(name = "EMAIL", length = 255)
  private String email; // 이메일: 비밀번호 재설정 및 알림 발송용

  @Column(name = "LAST_LOGIN_DATE")
  private LocalDateTime lastLoginDate; // 최종로그인일시: 휴면 계정 관리용

  @Column(name = "CREATE_DATE", insertable = false, updatable = false)
  private LocalDateTime createDate; // 생성일시: 관리자 계정 등록일(DB가 자동으로 채움)

  @Column(name = "UPDATE_DATE")
  private LocalDateTime updateDate; // 정보수정일시: 관리자 정보가 마지막으로 변경된 시각

  @OneToMany(mappedBy = "admin", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<AdminLogs> logs = new ArrayList<>(); // 이 관리자가 남긴 활동 로그 목록(1:N)


  // JPA 연관관계 편의 메서드(1:N 양방향 관계 설정)
  public void addLog(AdminLogs log) {
    this.logs.add(log); // 1. 부모(Admin)의 리스트에 자식(AdminLogs) 추가
    log.assignAdmin(this); // 2. 자식(AdminLogs) 객체에도 부모(Admin)를 참조로 저장
  }

  // 관리자 프로필 정보 변경 비즈니스 메서드
  public void updateProfile(String adminName, String email) {
    this.adminName = adminName;
    this.email = email;
    this.updateDate = LocalDateTime.now();
  }

  // 최종 로그인 시각 기록 메서드
  public void updateLastLoginDate() {
    this.lastLoginDate = LocalDateTime.now();
  }

  // 관리자 정지 처리 도메인 로직 메서드
  public void suspend() {
    this.status = AdminStatus.SUSPENDED; // 계정 상태를 '정지'로 변경
    this.updateDate = LocalDateTime.now(); // 수정 날짜 갱신
  }

  // 관리자 정지 해제(활성화) 도메인 로직 메서드
  public void activate() {
    this.status = AdminStatus.ACTIVE; // 계정 상태를 '활동중'으로 변경
    this.updateDate = LocalDateTime.now(); // 수정 날짜 갱신
  }

}//class