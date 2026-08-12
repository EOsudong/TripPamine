package com.example.trippaminebe.domain.account.repository;

import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.account.entity.LinkStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

  // 특정 유저의 연동 계좌 전체 조회
  // 주의: User 엔티티의 PK 필드명이 "id"이므로 findByUser_Id로 작성 (userId 아님)
  List<Account> findByUser_Id(Long userId);

  // 특정 유저의 활성(ACTIVE) 계좌만 조회
  List<Account> findByUser_IdAndLinkStatus(Long userId, LinkStatus linkStatus);

  // 소유권 검증용: accountId가 실제로 해당 userId 소유인지 함께 조회
  // (Controller/Service에서 "남의 계좌 접근" 방지용으로 이 메서드를 통해서만 조회하도록 사용)
  Optional<Account> findByAccountIdAndUser_Id(Long accountId, Long userId);
}
