package com.example.trippaminebe.domain.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ADMIN_LOGS")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class AdminLogs {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_ADMIN_LOGS_GEN")
  @SequenceGenerator(name = "SEQ_ADMIN_LOGS_GEN", sequenceName = "SEQ_ADMIN_LOGS", allocationSize = 1)
  @Column(name = "ADMIN_LOG_ID")
  private Long id; // 로그일련번호: 내부 고유 식별자(Sequence 적용) PK

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ADMIN_ID", nullable = false)
  private Admin admin; // 관리자일련번호: ADMINS(ADMIN_ID) 참조 FK

  @Column(name = "ACTION_TYPE", nullable = false, length = 30)
  private String actionType; // 액션유형: 예) LOGIN, USER_SUSPEND, QUEST_CREATE, LEDGER_VIEW 등

  @Column(name = "TARGET_TABLE", length = 50)
  private String targetTable; // 대상테이블: 변경이 발생한 테이블명(예: USERS, QUESTS)

  @Column(name = "TARGET_ID")
  private Long targetId; // 대상PK: 변경 대상 레코드의 기본키 값

  @Column(name = "ACTION_DATE", insertable = false, updatable = false)
  private LocalDateTime actionDate; // 액션일시: 활동이 발생한 시각(DB가 자동으로 채움)

  @Column(name = "IP_ADDRESS", length = 45)
  private String ipAddress; // 접속IP: 관리자 콘솔 접속 IP(IPv6 포함 가능한 길이)

  @Lob
  @Column(name = "DETAIL")
  private String detail; // 변경상세: 변경 전/후 값 등을 JSON 텍스트로 저장(선택 입력)


  // JPA 연관관계 편의 메서드 - Admin.addLog()에서 함께 호출됨
  public void assignAdmin(Admin admin) {
    this.admin = admin;
  }

}//class