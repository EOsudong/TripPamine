package com.example.trippaminebe.domain.travel.event;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;


/**
 * 여행 계획에서 발생한 변화를 다른 Domain에 전달하는 Application Event.
 *
 *
 * =========================================================
 * 학습 포인트
 * =========================================================
 *
 * TravelPlanService가 AI_MANAGER_EVENTS를 직접 저장하게 만들면:
 *
 * Travel Domain
 *      ↓
 * AI Manager Repository
 *
 * 로 강하게 결합된다.
 *
 *
 * 대신:
 *
 * Travel Domain
 *      ↓
 * Application Event
 *      ↓
 * AI Manager Listener
 *
 * 구조를 사용한다.
 *
 *
 * Travel Domain은:
 *
 * "여행 계획이 생성됐다"
 * "여행 계획이 수정됐다"
 * "여행 계획이 삭제됐다"
 *
 * 라는 사실만 알린다.
 *
 * 그 사실을 AI Manager가 어떻게 사용하는지는
 * AI Manager Domain이 결정한다.
 */
public record TravelPlanActivityApplicationEvent(

		Type type,

		Long planId,

		Long userId,

		String planName,

		BigDecimal totalBudget,

		String companionType,

		String locationCd,

		String blindYn,

		LocalDateTime startDate,

		LocalDateTime endDate,

		/**
		 * 실제 Event가 발생한 시간.
		 *
		 * AI_MANAGER_EVENTS.OCCURRED_AT으로 들어간다.
		 */
		LocalDateTime occurredAt

) {

	private static final ZoneId SERVICE_ZONE =
			ZoneId.of("Asia/Seoul");

	/**
	 * 여행 계획에서 현재 기록할 사건 종류.
	 */
	public enum Type {

		CREATED,

		UPDATED,

		DELETED
	}


	/**
	 * TravelPlan Entity의 현재 상태를
	 * Event Snapshot으로 변환한다.
	 *
	 * 중요한 점:
	 *
	 * Entity 자체를 Event에 담지 않고
	 * 필요한 값만 복사한다.
	 *
	 * 이유:
	 *
	 * AFTER_COMMIT Listener가 실행될 때는
	 * 기존 JPA Persistence Context가 끝난 상태일 수 있기 때문이다.
	 */
	public static TravelPlanActivityApplicationEvent from(
			Type type,
			TravelPlan plan
	) {

		return new TravelPlanActivityApplicationEvent(

				type,

				plan.getPlanId(),

				plan.getUser().getId(),

				plan.getPlanName(),

				plan.getTotalBudget(),

				plan.getCompanionType(),

				plan.getLocationCd(),

				plan.getBlindYn(),

				plan.getStartDate(),

				plan.getEndDate(),

				LocalDateTime.now(
						SERVICE_ZONE
				)
		);
	}
}