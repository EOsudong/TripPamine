package com.example.trippaminebe.domain.ai.dto;

import java.time.LocalDateTime;
import java.util.List;


/**
 * AI Manager Priority Engine 결과.
 *
 * =========================================================
 * 역할
 * =========================================================
 *
 * Manager Context에서
 *
 * "현재 사용자에게 무엇이 더 중요한가?"
 *
 * 를 결정한 구조화 결과이다.
 *
 *
 * 중요한 원칙:
 *
 * - 자연어 답변을 생성하지 않는다.
 * - 실제 Action을 실행하지 않는다.
 * - 사용자의 의도를 임의로 추측하지 않는다.
 *
 *
 * 즉:
 *
 * Context
 *      ↓
 * Priority Engine
 *      ↓
 * PriorityItem
 *      ↓
 * Manager SLM
 *
 * 구조에서 중간 판단 계층 역할을 한다.
 */
public record AiManagerPriorityResponse(

		/**
		 * Priority 계산에 사용된
		 * Context Snapshot 기준 시각.
		 */
		LocalDateTime generatedAt,


		/**
		 * 높은 점수 순으로 정렬된
		 * 현재 Priority 목록.
		 */
		List<PriorityItem> priorities

) {


	/**
	 * Priority 단계.
	 *
	 * 현재 초기 정책에서는:
	 *
	 * HIGH   : 80 ~ 100
	 * NORMAL : 50 ~ 79
	 * LOW    : 0 ~ 49
	 *
	 * 로 사용한다.
	 */
	public enum PriorityLevel {

		HIGH,

		NORMAL,

		LOW
	}


	/**
	 * 하나의 Priority 후보.
	 *
	 * 예:
	 *
	 * domain       = travel
	 * subjectType  = TRAVEL_PLAN
	 * subjectId    = 3
	 * score        = 95
	 * level        = HIGH
	 *
	 * reasonCodes:
	 *
	 * - TRAVEL_ACTIVE_NOW
	 * - TRAVEL_ENDING_WITHIN_24H
	 *
	 *
	 * 자연어 문장이 아니라
	 * reasonCode를 저장하는 이유는
	 * 이후 Manager SLM이 표현을 담당하도록 하기 위해서다.
	 */
	public record PriorityItem(

			/**
			 * Priority 후보를 식별하는 안정적인 Key.
			 */
			String priorityKey,


			/**
			 * travel / quest / emotion / finance / diary 등.
			 */
			String domain,


			/**
			 * Priority가 가리키는 대상 종류.
			 *
			 * 현재:
			 * TRAVEL_PLAN
			 */
			String subjectType,


			/**
			 * 원본 Domain 객체 ID.
			 *
			 * Long 대신 String으로 둬서
			 * 이후 여러 Domain ID 형식을 수용할 수 있게 한다.
			 */
			String subjectId,


			/**
			 * 0 ~ 100 Priority 점수.
			 */
			int score,


			/**
			 * 사람이 이해하기 쉬운 Priority 단계.
			 */
			PriorityLevel level,


			/**
			 * 왜 이 점수가 계산됐는지를 설명하는
			 * 구조화 Reason Code.
			 *
			 * SLM 또는 Debug API에서 활용한다.
			 */
			List<String> reasonCodes

	) {
	}
}