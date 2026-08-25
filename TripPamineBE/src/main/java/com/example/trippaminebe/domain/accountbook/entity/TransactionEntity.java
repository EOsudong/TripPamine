package com.example.trippaminebe.domain.accountbook.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTIONS")
@Data
@NoArgsConstructor
public class TransactionEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false)
   private String username; // 작성자 식별자 (TripPamine 유저)

   @Column(nullable = false)
   private LocalDateTime transactionDate; // 거래 일시

   @Column(nullable = false)
   private String category = "ETC"; // 카테고리 (FOOD, TRANSPORT, MEDICAL 등)

   @Column(nullable = false)
   private String description; // 내역 이름

   @Column(nullable = false)
   private Long amount; // 금액

   @Column(nullable = false)
   private String type; // "income" 또는 "expense"

   // [Mock 은행 연동 추가]
   // 이 가계부 항목을 어느 연동 계좌에 반영할지 선택한 값 (USER_ACCOUNTS.ACCOUNT_ID).
   // null이면 예전처럼 계좌와 무관한 순수 가계부 항목 - 잔액에 아무 영향 없음.
   // null이 아니면 AccountBookService가 AccountBalanceService를 통해 해당 계좌의
   // BALANCE에 즉시 반영하고, ACCOUNT_HISTORY에도 이 항목(LEDGER_TXN_ID=id)로 기록을 남긴다.
   @Column(name = "ACCOUNT_ID")
   private Long accountId;
}
