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
}