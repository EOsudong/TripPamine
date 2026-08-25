package com.example.trippaminebe.domain.account.service;

import com.example.trippaminebe.domain.account.client.MockOpenBankingClient;
import com.example.trippaminebe.domain.account.dto.request.AccountAliasUpdateRequest;
import com.example.trippaminebe.domain.account.dto.request.AccountLinkRequest;
import com.example.trippaminebe.domain.account.dto.response.AccountHistoryResponse;
import com.example.trippaminebe.domain.account.dto.response.AccountResponse;
import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.mockbank.dto.MockAccountVerifyResponse;
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
  // [Mock 은행 연동 추가]
  private final MockOpenBankingClient mockOpenBankingClient;

  // 계좌 연동 등록
  // [수정] 사용자가 입력한 계좌정보를 그대로 저장하는 대신, Mock 은행 서버에 계좌
  // 실명확인을 요청해서 핀테크이용번호와 최초 잔액을 발급받은 뒤 계좌를 생성한다.
  @Transactional
  public AccountResponse linkAccount(Long userId, AccountLinkRequest request) {
    // ⚠ UserRepository.findById(Long)으로 호출하고 있는데, 현재 UserRepository가
    // JpaRepository<User, String>으로 선언되어 있다면 타입이 안 맞아 컴파일 에러가 남.
    // User 담당자와 UserRepository의 ID 타입(Long/String)을 먼저 맞춰야 함.
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. userId=" + userId));

    // 예금주명은 별도로 입력받지 않으므로 null로 전달 - Mock 은행 서버가 계좌번호 기반으로
    // 그럴듯한 예금주명을 자동 생성해준다 (accountAlias는 "여행 경비 통장" 같은 별칭일 뿐
    // 사람 이름이 아니라서 예금주명 자리에 넣지 않는다).
    MockAccountVerifyResponse verified = mockOpenBankingClient.verifyAccount(
        request.getBankCode(), request.getBankName(), request.getAccountNumber(), null
    );

    Account account = Account.builder()
        .user(user)
        .bankCode(request.getBankCode())
        .bankName(request.getBankName())
        .accountNumber(request.getAccountNumber())
        .fintechUseNum(verified.getFintechUseNum())
        .accountAlias(request.getAccountAlias())
        .balance(verified.getBalance())
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

  // 계좌 거래내역 조회 (페이징) - 가계부에서 이 계좌로 지정해 입력한 내역이 쌓이는 곳
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
