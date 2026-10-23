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
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
	private final TravelPlanRepository travelPlanRepository;
	private final AccountRepository accountRepository;
	private final AccountBookService accountBookService;

	public List<AiNegoLogResponse> findActiveOffers(Long userId) {
		// 동일 PLAN_ID가 결제됐거나 여행이 삭제되면 숨긴다.
		// PLAN_ID가 없는 이전 미수락 오퍼도 숨기며, 사용자별 격리는 Repository에서 확인한다.
		return aiNegoLogRepository.findDisplayableOffers(userId, LocalDateTime.now())
				.stream()
				.map(AiNegoLogResponse::from)
				.toList();
	}

	// 시간 내 결제 완료 처리 (전환)
	public AiNegoLogResponse accept(Long userId, Long negoId, Long accountId) {
		// 서로 다른 NEGO_ID가 동일 PLAN_ID로 동시에 수락되는 상황을 막는다.
		// 사용자 → 오퍼 → 계좌 순서로 잠금을 획득하며, 생성 작업도 동일한 사용자 잠금을 사용한다.
		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));

		// 동일 오퍼에 대한 중복 요청도 기존처럼 개별 오퍼 행을 잠가 직렬화한다.
		AiNegoLog offer = aiNegoLogRepository.findByIdForUpdate(negoId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 네고 제안입니다. id=" + negoId));

		if (!offer.getUser().getId().equals(userId)) {
			throw new IllegalArgumentException("본인에게 발송된 제안만 수락할 수 있습니다.");
		}
		// 이미 수락된 오퍼를 재요청하더라도 가계부 등록과 잔액 차감을 반복하지 않는다.
		if (offer.isConverted()) {
			return AiNegoLogResponse.from(offer);
		}
		if (offer.isExpired()) {
			throw new IllegalArgumentException("이미 만료된 제안입니다.");
		}

		// PLAN_ID 없는 과거 미결제 오퍼는 어떤 여행의 것인지 확인할 수 없다.
		// 이미 결제한 같은 오퍼는 위에서 멱등 반환하고, 미결제 레거시는 차감 전에 409로 차단한다.
		if (offer.getPlanId() == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"기존 네고 제안은 여행 연결 정보가 없어 수락할 수 없습니다. 새로운 제안을 이용해주세요.");
		}

		// 결제 직전에 사용자 소유·삭제 여부를 다시 확인한다. 이름은 여행 식별자로 사용하지 않는다.
		TravelPlan plan = travelPlanRepository.findByPlanIdAndUser_IdAndDelYn(
				offer.getPlanId(), userId, "N")
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
						"삭제되었거나 접근할 수 없는 여행의 네고 제안입니다."));

		// 사용자 행 잠금 *이후* PLAN_ID 기준으로 다른 완료 오퍼가 있는지 다시 검사한다.
		if (aiNegoLogRepository.existsByUser_IdAndPlanIdAndConversionYn(userId, plan.getPlanId(), "Y")) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"해당 여행에 이미 결제 완료된 네고 오퍼가 있습니다.");
		}

		// [전환 보호] 아직 PLAN_ID가 없는 과거 완료 이력을 이름으로 찾는다.
		// 같은 이름의 다른 여행을 일시적으로 막을 수 있으나, 레거시 정리 전까지 이중 결제를 우선 방지한다.
		if (aiNegoLogRepository.existsByUser_IdAndPlanIdIsNullAndItemNameAndConversionYn(
				userId, offer.getItemName(), "Y")) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"이름이 일치하는 과거 결제 내역이 있어 중복 결제를 확인해야 합니다.");
		}

		// 동일 오퍼 잠금 후 실제 결제 계좌도 행 잠금으로 보호한다.
		// 다른 오퍼 또는 일반 가계부 출금이 이 계좌의 잔액을 바꾸는 동안 기다렸다가
		// 최신 잔액으로 다시 검증한다. 잠금은 현재 @Transactional 커밋까지 유지된다.
		Account account = accountRepository.findOwnedForUpdate(accountId, userId)
				.orElseThrow(() -> new IllegalArgumentException("본인 소유의 계좌가 아니거나 존재하지 않습니다."));
		if (!account.isActive()) {
			throw new IllegalArgumentException("연동이 해지된 계좌입니다.");
		}
		// 0원·음수 오퍼 및 Long 범위를 넘는 금액은 결제로 처리하지 않는다.
		// TRANSACTIONS.AMOUNT는 Long, AI_NEGO_LOGS.OFFERED_PRICE는 BigDecimal이다.
		BigDecimal offeredPrice = offer.getOfferedPrice();
		if (offeredPrice == null || offeredPrice.signum() <= 0) {
			throw new IllegalArgumentException("결제할 수 없는 네고 제안 금액입니다.");
		}
		long paymentAmount;
		try {
			paymentAmount = offeredPrice.longValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("지원하지 않는 네고 제안 금액입니다.", e);
		}
		if (account.getBalance().compareTo(offeredPrice) < 0) {
			throw new IllegalArgumentException("계좌 잔액이 부족합니다.");
		}

		TransactionEntity transaction = new TransactionEntity();
		// AccountBookService가 인증된 userId로 소유권과 레거시 USERNAME을 지정한다.
		transaction.setTransactionDate(LocalDateTime.now());
		// AI 타임 네고 결제로 생긴 가계부 항목임을 카테고리로 명시한다(프론트 CATEGORY_MAP의 NEGO와 대응).
		transaction.setCategory("NEGO");
		transaction.setDescription(offer.getItemName());
		transaction.setAmount(paymentAmount);
		transaction.setType("expense");
		transaction.setAccountId(accountId);
		accountBookService.saveNegoTransaction(userId, transaction);

		offer.setPaymentAccountId(accountId);
		offer.setPaidAt(LocalDateTime.now());
		offer.convert();

		return AiNegoLogResponse.from(offer);
	}

	// 출발 임박 여행 한 건에 대해 PLAN_ID를 포함한 신규 오퍼 발급.
	// 스케줄러가 전달한 여행 객체는 오래된 값일 수 있으므로 사용자 잠금 뒤 DB에서 다시 조회한다.
	public void generateOfferForPlan(TravelPlan plan) {
		if (plan == null || plan.getPlanId() == null || plan.getUser() == null) {
			return;
		}

		Long userId = plan.getUser().getId();
		if (userId == null) {
			return;
		}

		// 생성과 수락이 동일한 사용자 잠금을 공유해 중복 발급·결제 검사 구간을 직렬화한다.
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));

		// 삭제되거나 소유자가 변경된 여행은 오퍼를 발행하지 않는다.
		TravelPlan currentPlan = travelPlanRepository
				.findByPlanIdAndUser_IdAndDelYn(plan.getPlanId(), userId, "N")
				.orElse(null);
		if (currentPlan == null) {
			return;
		}

		// 발급 전 최신 출발일이 스케줄러의 24시간 대상 범위에 여전히 속하는지 확인한다.
		LocalDateTime now = LocalDateTime.now();
		if (currentPlan.getStartDate() == null
				|| !currentPlan.getStartDate().isAfter(now)
				|| currentPlan.getStartDate().isAfter(now.plusHours(24))) {
			return;
		}

		// 예산 0원·음수 여행에서는 계산 불가 또는 0원 오퍼가 발생하므로 발급하지 않는다.
		if (currentPlan.getTotalBudget() == null || currentPlan.getTotalBudget().signum() <= 0) {
			log.debug("네고 오퍼 미생성: 여행 예산이 0원 이하입니다. planId={}", currentPlan.getPlanId());
			return;
		}

		Long planId = currentPlan.getPlanId();
		String itemName = currentPlan.getPlanName() + " 막판 특가 패키지";

		// [신규] 여행명이 같더라도 PLAN_ID가 다르면 서로 다른 여행이다.
		if (aiNegoLogRepository.existsByUser_IdAndPlanIdAndConversionYn(userId, planId, "Y")) {
			return;
		}

		// [레거시 보호] PLAN_ID=NULL인 과거 완료 내역이 있으면 자동 추가 발급을 막는다.
		// 정식 이관 전에는 같은 이름의 다른 여행까지 제한할 수 있다.
		if (aiNegoLogRepository.existsByUser_IdAndPlanIdIsNullAndItemNameAndConversionYn(
				userId, itemName, "Y")) {
			return;
		}

		if (aiNegoLogRepository.existsByUser_IdAndPlanIdAndConversionYnAndExpiredAtAfter(
				userId, planId, "N", now)) {
			return;
		}

		DiscountDecision decision = decideDiscount(currentPlan);
		BigDecimal offeredPrice = currentPlan.getTotalBudget()
				.multiply(BigDecimal.ONE.subtract(decision.rate()))
				.setScale(0, RoundingMode.HALF_UP);
		if (offeredPrice.signum() <= 0) {
			log.debug("네고 오퍼 미생성: 계산된 가격이 0원입니다. planId={}", planId);
			return;
		}

		AiNegoLog offer = AiNegoLog.builder()
				.user(user)
				.planId(planId)
				.itemName(itemName)
				.offeredPrice(offeredPrice)
				.expiredAt(now.plusSeconds(OFFER_TTL_SECONDS))
				.conversionYn("N")
				.discountRate(decision.rate())
				.triggerReason(decision.reason())
				.build();

		aiNegoLogRepository.save(offer);
		log.info("AI 타임 네고 발송: userId={}, planId={}, offeredPrice={}, rate={}, reason={}",
				userId, planId, offeredPrice, decision.rate(), decision.reason());

		// 기존 WebSocket 메시지 계약을 유지하고 응답에 planId만 추가한다.
		// 주의: 전송이 DB 커밋보다 앞서는 기존 구조는 별도의 outbox/커밋 후 전송 단계에서 개선한다.
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
