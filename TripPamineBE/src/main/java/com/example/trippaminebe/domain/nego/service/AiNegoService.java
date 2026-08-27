package com.example.trippaminebe.domain.nego.service;

import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.account.repository.AccountRepository;
import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.entity.TravelLedger;
import com.example.trippaminebe.domain.accountbook.repository.TravelLedgerRepository;
import com.example.trippaminebe.domain.accountbook.service.AccountBookService;
import com.example.trippaminebe.domain.nego.dto.response.AiNegoLogResponse;
import com.example.trippaminebe.domain.nego.entity.AiNegoLog;
import com.example.trippaminebe.domain.nego.repository.AiNegoLogRepository;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AiNegoService {

	private static final Logger log = LoggerFactory.getLogger(AiNegoService.class);

	// DDL 주석 기준: 발송 시점 + 90초 후 카운트다운 만료
	private static final long OFFER_TTL_SECONDS = 90;

	// 출발 임박 블라인드/일반 여행에 대한 임시 할인율 (실 서비스에서는 상품/재고 API 연동으로 대체)
	private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.30");

	// 지출 데이터가 없을 때(여행 시작 전) 쓰는 기존 폴백 할인율
	private static final BigDecimal FALLBACK_DISCOUNT_RATE = new BigDecimal("0.30");
	// 예산 소진 속도가 계획보다 빠를 때 적용하는 상향 할인율
	private static final BigDecimal BUDGET_RISK_DISCOUNT_RATE = new BigDecimal("0.40");
	// actualSpendRatio - idealSpendRatio 가 이 값 이상이면 "위험"으로 판단
	private static final BigDecimal BURN_RATE_GAP_THRESHOLD = new BigDecimal("0.15");

	private static final String REASON_BUDGET_RISK = "BUDGET_RISK";
	private static final String REASON_DEPARTURE_IMMINENT = "DEPARTURE_IMMINENT";

	private final AiNegoLogRepository aiNegoLogRepository;
	private final UserRepository userRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final TravelLedgerRepository travelLedgerRepository;
	private final AccountRepository accountRepository;
	private final AccountBookService accountBookService;

	public List<AiNegoLogResponse> findActiveOffers(Long userId) {
		return aiNegoLogRepository
				.findByUser_IdAndExpiredAtAfterOrderByNegoIdDesc(userId, LocalDateTime.now())
				.stream()
				.map(AiNegoLogResponse::from)
				.toList();
	}

	// 시간 내 결제 완료 처리 (전환)
	public AiNegoLogResponse accept(Long userId, Long negoId, Long accountId) {
		AiNegoLog offer = aiNegoLogRepository.findById(negoId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 네고 제안입니다. id=" + negoId));

		if (!offer.getUser().getId().equals(userId)) {
			throw new IllegalArgumentException("본인에게 발송된 제안만 수락할 수 있습니다.");
		}
		if (offer.isExpired()) {
			throw new IllegalArgumentException("이미 만료된 제안입니다.");
		}

		Account account = accountRepository.findByAccountIdAndUser_Id(accountId, userId)
				.orElseThrow(() -> new IllegalArgumentException("본인 소유의 계좌가 아니거나 존재하지 않습니다."));
		if (!account.isActive()) {
			throw new IllegalArgumentException("연동이 해지된 계좌입니다.");
		}
		if (account.getBalance().compareTo(offer.getOfferedPrice()) < 0) {
			throw new IllegalArgumentException("계좌 잔액이 부족합니다.");
		}

		TransactionEntity transaction = new TransactionEntity();
		transaction.setUsername(offer.getUser().getEmail());
		transaction.setTransactionDate(LocalDateTime.now());
		transaction.setCategory("TRAVEL");
		transaction.setDescription(offer.getItemName());
		transaction.setAmount(offer.getOfferedPrice().longValue());
		transaction.setType("expense");
		transaction.setAccountId(accountId);
		accountBookService.saveTransaction(userId, transaction);

		offer.setPaymentAccountId(accountId);
		offer.setPaidAt(LocalDateTime.now());
		offer.convert();

		return AiNegoLogResponse.from(offer);
	}

	// 출발 임박 여행 계획(TravelPlan) 하나에 대해 AI 핫딜 제안을 1건 생성
	// - 이미 유효한 제안이 있으면 중복 발송하지 않음
	public void generateOfferForPlan(TravelPlan plan) {
		User user = userRepository.findById(plan.getUser().getId())
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + plan.getUser().getId()));

		String itemName = plan.getPlanName() + " 막판 특가 패키지";

		boolean alreadyOffered = aiNegoLogRepository
				.findFirstByUser_IdAndItemNameAndExpiredAtAfter(user.getId(), itemName, LocalDateTime.now())
				.isPresent();
		if (alreadyOffered) {
			return;
		}

		// [알고리즘 고도화] 지출 패턴 기반으로 할인율/트리거 사유 결정
		DiscountDecision decision = decideDiscount(plan);

		BigDecimal offeredPrice = plan.getTotalBudget()
				.multiply(BigDecimal.ONE.subtract(decision.rate()))
				.setScale(0, RoundingMode.HALF_UP);

		AiNegoLog offer = AiNegoLog.builder()
				.user(user)
				.itemName(itemName)
				.offeredPrice(offeredPrice)
				.expiredAt(LocalDateTime.now().plusSeconds(OFFER_TTL_SECONDS))
				.conversionYn("N")
				.discountRate(decision.rate())
				.triggerReason(decision.reason())
				.build();

		aiNegoLogRepository.save(offer);
		log.info("AI 타임 네고 발송: userId={}, item={}, offeredPrice={}, rate={}, reason={}",
				user.getId(), itemName, offeredPrice, decision.rate(), decision.reason());

		// WebSocket으로 해당 유저에게 실시간 푸시.
		// NegoStompAuthInterceptor가 CONNECT 시 Principal.name을 이메일(CustomUserDetails.getUsername())로
		// 세팅하므로, convertAndSendToUser의 첫 인자도 이메일이어야 클라이언트의 /user/queue/nego 구독과 매칭된다.
		// 폴링(/nego/active)은 소켓이 끊긴 사이의 누락을 메우는 백업 경로로 유지한다.
		messagingTemplate.convertAndSendToUser(
				user.getEmail(),
				"/queue/nego",
				AiNegoLogResponse.from(offer)
		);
	}

	// 여행 지출 패턴을 분석해 할인율과 트리거 사유를 결정한다.
	// - 여행이 아직 시작 전이라 지출 내역이 없으면 기존 폴백(출발임박) 로직 유지
	// - 지출 속도가 경과 일수 대비 예산 소진 속도보다 뚜렷이 빠르면(burnRateGap 기준 초과) 위험으로 보고 할인율 상향
	private DiscountDecision decideDiscount(TravelPlan plan) {
		if (plan.getStartDate() == null || plan.getEndDate() == null) {
			return new DiscountDecision(FALLBACK_DISCOUNT_RATE, REASON_DEPARTURE_IMMINENT);
		}

		BigDecimal spentSoFar = travelLedgerRepository.findByTravelPlan_PlanId(plan.getPlanId())
				.stream()
				.map(TravelLedger::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		// 아직 지출이 하나도 없으면(여행 시작 전) 판단 근거가 없으니 기존 로직으로 폴백
		if (spentSoFar.compareTo(BigDecimal.ZERO) == 0) {
			return new DiscountDecision(FALLBACK_DISCOUNT_RATE, REASON_DEPARTURE_IMMINENT);
		}

		long tripDurationDays = Math.max(
				Duration.between(plan.getStartDate(), plan.getEndDate()).toDays(), 1);
		long elapsedDays = Math.max(
				Duration.between(plan.getStartDate(), LocalDateTime.now()).toDays(), 0);

		BigDecimal idealSpendRatio = BigDecimal.valueOf(elapsedDays)
				.divide(BigDecimal.valueOf(tripDurationDays), 4, RoundingMode.HALF_UP);
		BigDecimal actualSpendRatio = spentSoFar
				.divide(plan.getTotalBudget(), 4, RoundingMode.HALF_UP);

		BigDecimal burnRateGap = actualSpendRatio.subtract(idealSpendRatio);

		if (burnRateGap.compareTo(BURN_RATE_GAP_THRESHOLD) >= 0) {
			return new DiscountDecision(BUDGET_RISK_DISCOUNT_RATE, REASON_BUDGET_RISK);
		}
		return new DiscountDecision(FALLBACK_DISCOUNT_RATE, REASON_DEPARTURE_IMMINENT);
	}

	private record DiscountDecision(BigDecimal rate, String reason) {
	}

}
