package com.example.trippaminebe.domain.user.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "USERS")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USERS_GEN")
  @SequenceGenerator(name = "SEQ_USERS_GEN", sequenceName = "SEQ_USERS", allocationSize = 1)
  @Column(name = "USER_ID")
  private Long id;

  @Column(name = "EMAIL", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "PASSWORD", nullable = false, length = 255)
  private String password;

  @Column(name = "NAME", length = 20)
  private String name;

  @Column(name = "USER_NAME", nullable = false, unique = true, length = 50)
  private String userName;

  @Column(name = "PHONE_NUMBER", length = 20)
  private String phoneNumber;

  @Column(name = "TOTAL_POINTS")
  @Builder.Default
  private Long totalPoints = 0L;

  @Enumerated(EnumType.STRING)
  @Column(name = "USER_GRADE", length = 20)
  @Builder.Default
  private UserGrade grade = UserGrade.BRONZE;

  @Column(name = "SUBSCRIBE_YN", length = 1)
  @Builder.Default
  private String subscribeYn = "N";

  @Column(name = "PROFILE_IMAGE_URL", length = 500)
  private String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", length = 20)
  @Builder.Default
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "CREATE_DATE", insertable = false, updatable = false)
  private LocalDateTime createDate;

  @Column(name = "UPDATE_DATE")
  private LocalDateTime updateDate;

  @Column(name = "LAST_LOGIN_DATE")
  private LocalDateTime lastLoginDate;

  @Column(name = "WITHDRAW_DATE")
  private LocalDateTime withdrawDate;

  // 관리자 강제 정지 기능 추가 (2026.08.12)
  @Column(name = "SUSPEND_REASON", length = 200)
  private String suspendReason; // 정지사유: 관리자가 입력하는 정지 사유

  @Column(name = "SUSPENDED_BY")
  private Long suspendedBy; // 정지처리자: 정지를 처리한 관리자 ID (Admin 엔티티를 직접 참조(@ManyToOne)하지 않고 ID만 저장 - User 도메인이 Admin 도메인을 몰라도 되게 하기 위함)

  @Column(name = "SUSPENDED_AT")
  private LocalDateTime suspendedAt; // 정지일시: 회원이 정지 처리된 시각
  // 여기까지

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<UserSocialAccount> socialAccounts = new ArrayList<>();


  // JPA 연관관계 편의 메서드(1:N 양방향 관계 설정)
  public void addSocialAccount(UserSocialAccount socialAccount) {
    this.socialAccounts.add(socialAccount); // 1. 부모(User)의 리스트에 자식(SocialAccount) 추가
    socialAccount.assignUser(this); // 2. 자식(SocialAccount) 객체에도 부모(User)를 참조로 저장
  }

  // 사용자 프로필 정보 변경 비즈니스 메서드
  public void updateProfile(String userName, String phoneNumber, String profileImageUrl) {
    this.userName = userName;
    this.phoneNumber = phoneNumber;
    this.profileImageUrl = profileImageUrl;
    this.updateDate = LocalDateTime.now(); // 변경 시점의 수정날짜 자동 갱신
  }

  // 최종 로그인 시각 기록 메서드
  // lastLoginDate가 현재 시점으로부터 1년 이상 지났으면 배치 작업으로 휴면 처리
  public void updateLastLoginDate() {
    this.lastLoginDate = LocalDateTime.now();
  }

  // 회원 탈퇴 처리 도메인 로직 메서드
  public void withdraw() {
    this.status = UserStatus.WITHDRAW; // 계정 상태를 '탈퇴'로 변경
    this.withdrawDate = LocalDateTime.now(); // 탈퇴 처리 날짜 기록
    this.updateDate = LocalDateTime.now(); // 수정 날짜 갱신
  }

  // 관리자 강제 정지 처리 도메인 메서드.
  // suspendedBy는 Admin 엔티티를 직접 참조하지 않고 ID(Long)만 저장 - User 도메인이
  // Admin 도메인을 몰라도 되게 하기 위함(도메인 간 결합도를 낮춤)
  public void suspend(String reason, Long suspendedBy) {
    this.status = UserStatus.SUSPENDED; // 계정 상태를 '정지(SUSPENDED)'로 변경
    this.suspendReason = reason; // 정지 사유 기록
    this.suspendedBy = suspendedBy; // 정지 처리한 관리자 ID 기록
    this.suspendedAt = LocalDateTime.now(); // 정지 처리 시각 기록
    this.updateDate = LocalDateTime.now(); // 수정 날짜 갱신
  }

  // 관리자 정지 해제(재활성화) 처리 도메인 메서드
  public void unsuspend() {
    this.status = UserStatus.ACTIVE; // 계정 상태를 다시 '활동중(ACTIVE)'으로 변경
    this.suspendReason = null; // 정지 사유 기록 초기화
    this.suspendedBy = null;   // 정지 처리한 관리자 ID 기록 초기화
    this.suspendedAt = null;   // 정지일시 초기화
    this.updateDate = LocalDateTime.now();
  }

}//class
