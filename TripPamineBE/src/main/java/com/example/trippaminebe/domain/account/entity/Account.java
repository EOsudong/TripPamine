package com.example.trippaminebe.domain.account.entity;

import com.example.trippaminebe.domain.user.entity.User; // TODO: 실제 User 엔티티 패키지 경로에 맞춰 수정
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * USER_ACCOUNTS 테이블 매핑
 * 사용자별 금융 계좌 및 간편결제 연동 정보
 *
 * [Mock 은행 연동 추가] balance 컬럼이 새로 추가됨
 * 계좌 연동 시점에 Mock 은행 서버(MockBankService)가 발급한 최초 잔액으로 채워지고,
 * 이후로는 가계부(TRANSACTIONS)에 이 계좌를 지정해서 수입/지출을 입력할 때마다
 * AccountBalanceService가 그 금액만큼 이 필드를 더하거나 뺀다
 */
@Entity
@Table(name = "USER_ACCOUNTS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

  @Id
  @Column(name = "ACCOUNT_ID")
  @SequenceGenerator(
      name = "seqUserAccounts",
      sequenceName = "SEQ_USER_ACCOUNTS",
      allocationSize = 1
  )
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqUserAccounts")
  private Long accountId; // 계좌연동일련번호: 내부 고유 식별자 (Sequence 적용) PK

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "USER_ID", nullable = false)
  private User user; // 사용자일련번호: USERS(USER_ID) 참조 FK - 이 계좌를 연동한 회원

  @Column(name = "BANK_CODE", length = 10)
  private String bankCode; // 은행/기관 코드 (금융결제원 표준코드 등, 원본 SQL엔 없던 추가 컬럼)

  @Column(name = "BANK_NAME", length = 50, nullable = false)
  private String bankName; // 은행/기관명 - 신한은행, 카카오페이, 토스 등

  // 암호화 저장 대상 (계좌번호)
  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "ACCOUNT_NUMBER", length = 100, nullable = false)
  private String accountNumber; // 계좌번호 - 암호화 처리하여 저장 (EncryptedStringConverter가 DB 저장/조회 시 자동 암복호화)

  // 암호화 저장 대상 (핀테크 이용번호)
  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "FINTECH_USE_NUM", length = 32)
  private String fintechUseNum; // 오픈뱅킹 핀테크 이용번호 - Mock 은행 서버(MockBankService)가 계좌 실명확인 시 발급한 값

  @Column(name = "ACCOUNT_ALIAS", length = 50)
  private String accountAlias; // 계좌별칭 - 사용자가 직접 지정하는 이름(예: "여행용 비상금")

  @Convert(converter = LinkStatusConverter.class)
  @Column(name = "LINK_STATUS", columnDefinition = "CHAR(1)", length = 1, nullable = false)
  private LinkStatus linkStatus; // 연동상태 - 현재 실시간 연동 활성화 여부 (DB엔 Y/N 한 글자로 저장, LinkStatusConverter가 Enum과 상호 변환)

  @Column(name = "LINK_DATE")
  private LocalDateTime linkDate; // 최초연동일시 - 계좌를 최초로 연동한 날짜

  // [Mock 은행 연동 추가] 이 계좌의 현재 잔액. 연동 시점엔 Mock 은행이 발급한 값으로
  // 시작하고, 이후로는 가계부에서 이 계좌로 지정한 수입/지출만큼 계속 가감된다.
  @Column(name = "BALANCE", precision = 20, nullable = false)
  private BigDecimal balance;

  @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<AccountHistory> histories = new ArrayList<>(); // 이 계좌에 딸린 입출금 내역 목록 (1:N)

  @Builder
  private Account(User user, String bankCode, String bankName,
                  String accountNumber, String fintechUseNum, String accountAlias,
                  BigDecimal balance) {
    this.user = user;
    this.bankCode = bankCode;
    this.bankName = bankName;
    this.accountNumber = accountNumber;
    this.fintechUseNum = fintechUseNum;
    this.accountAlias = accountAlias;
    this.balance = balance != null ? balance : BigDecimal.ZERO;
    this.linkStatus = LinkStatus.ACTIVE; // DEFAULT 'Y'
  }

  // 저장 직전 자동 실행: 연동일시/연동상태를 안 넣고 만들어도 기본값을 채워줌
  @PrePersist
  private void prePersist() {
    if (this.linkDate == null) {
      this.linkDate = LocalDateTime.now();
    }
    if (this.linkStatus == null) {
      this.linkStatus = LinkStatus.ACTIVE;
    }
    if (this.balance == null) {
      this.balance = BigDecimal.ZERO;
    }
  }

  // ---- 도메인 로직 ----

  // 계좌 별칭 변경
  public void updateAlias(String newAlias) {
    this.accountAlias = newAlias;
  }

  // 계좌 연동 해지 (soft delete - 실제 삭제 대신 상태만 INACTIVE로 변경)
  public void unlink() {
    this.linkStatus = LinkStatus.INACTIVE;
  }

  // 현재 연동이 살아있는 계좌인지 확인
  public boolean isActive() {
    return this.linkStatus == LinkStatus.ACTIVE;
  }

  // [Mock 은행 연동 추가] 가계부에서 이 계좌로 수입/지출을 입력하거나 취소할 때 호출.
  // delta가 양수면 잔액 증가(입금), 음수면 잔액 감소(출금) - AccountBalanceService 전용
  public void adjustBalance(BigDecimal delta) {
    this.balance = this.balance.add(delta);
  }
}
