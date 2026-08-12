package com.example.trippaminebe.domain.account.service;

import com.example.trippaminebe.domain.account.dto.request.AccountAliasUpdateRequest;
import com.example.trippaminebe.domain.account.dto.request.AccountLinkRequest;
import com.example.trippaminebe.domain.account.dto.response.AccountHistoryResponse;
import com.example.trippaminebe.domain.account.dto.response.AccountResponse;
import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.account.entity.LinkStatus;
import com.example.trippaminebe.domain.account.repository.AccountHistoryRepository;
import com.example.trippaminebe.domain.account.repository.AccountRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository; // TODO: 실제 UserRepository 패키지 경로 확인
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

  private final AccountRepository accountRepository;
  private final AccountHistoryRepository accountHistoryRepository;
  private final UserRepository userRepository;

  // 계좌 연동 등록
  @Transactional
  public AccountResponse linkAccount(Long userId, AccountLinkRequest request) {
    // ⚠ UserRepository.findById(Long)으로 호출하고 있는데, 현재 UserRepository가
    // JpaRepository<User, String>으로 선언되어 있다면 타입이 안 맞아 컴파일 에러가 남.
    // User 담당자와 UserRepository의 ID 타입(Long/String)을 먼저 맞춰야 함.
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. userId=" + userId));

    Account account = Account.builder()
        .user(user)
        .bankCode(request.getBankCode())
        .bankName(request.getBankName())
        .accountNumber(request.getAccountNumber())
        .fintechUseNum(request.getFintechUseNum())
        .accountAlias(request.getAccountAlias())
        .build();

    Account saved = accountRepository.save(account);
    return AccountResponse.from(saved);
  }

  // 내 계좌 목록 조회 (연동 해지된 계좌 포함 여부는 필요에 따라 조정)
  public List<AccountResponse> getMyAccounts(Long userId) {
    return accountRepository.findByUser_Id(userId).stream()
        .map(AccountResponse::from)
        .toList();
  }

  // 계좌 별칭 수정
  @Transactional
  public AccountResponse updateAlias(Long userId, Long accountId, AccountAliasUpdateRequest request) {
    Account account = getOwnedAccountOrThrow(userId, accountId);
    account.updateAlias(request.getAccountAlias());
    return AccountResponse.from(account);
  }

  // 계좌 연동 해지 (soft delete: LINK_STATUS = 'N')
  @Transactional
  public void unlinkAccount(Long userId, Long accountId) {
    Account account = getOwnedAccountOrThrow(userId, accountId);
    account.unlink();
  }

  // 계좌 거래내역 조회 (페이징)
  public Page<AccountHistoryResponse> getHistory(Long userId, Long accountId, Pageable pageable) {
    // 소유권 검증 먼저 수행 (남의 계좌 내역 조회 방지)
    getOwnedAccountOrThrow(userId, accountId);

    return accountHistoryRepository
        .findByAccount_AccountIdOrderByTransactionDateDesc(accountId, pageable)
        .map(AccountHistoryResponse::from);
  }

  // 본인 소유 계좌인지 검증하고 반환 (아니면 예외) - 다른 회원의 계좌 정보를 조회/수정하지 못하게 막는 공통 로직
  private Account getOwnedAccountOrThrow(Long userId, Long accountId) {
    return accountRepository.findByAccountIdAndUser_Id(accountId, userId)
        .orElseThrow(() -> new IllegalArgumentException(
            "본인 소유의 계좌가 아니거나 존재하지 않습니다. accountId=" + accountId));
  }
}
