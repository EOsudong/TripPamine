package com.example.trippaminebe.domain.ai.priority;

import com.example.trippaminebe.domain.ai.dto.AiManagerPriorityResponse;


/**
 * AI Manager Priority의 공통 점수 정책.
 *
 * =========================================================
 * 목적
 * =========================================================
 *
 * Priority Rule마다 점수 기준을 제각각 정의하지 않고
 * 하나의 정책 클래스에서 관리한다.
 *
 * 이후 정책을 조정할 때:
 *
 * - Travel
 * - Quest
 * - Emotion
 * - Finance
 *
 * Rule 코드를 전부 찾아다니지 않아도 된다.
 */
public final class AiManagerPriorityPolicy {


	private AiManagerPriorityPolicy() {
	}


	/* =====================================================
	 * 공통 Score 범위
	 * ===================================================== */

	public static final int MIN_SCORE =
			0;

	public static final int MAX_SCORE =
			100;


	/* =====================================================
	 * Priority Level 기준
	 * ===================================================== */

	public static final int HIGH_MIN_SCORE =
			80;

	public static final int NORMAL_MIN_SCORE =
			50;


	/* =====================================================
	 * Travel 정책
	 * ===================================================== */

	/**
	 * 현재 진행 중인 여행.
	 */
	public static final int TRAVEL_ACTIVE_BASE_SCORE =
			85;


	/**
	 * 여행 종료까지 24시간 이하일 때 추가되는 점수.
	 */
	public static final int TRAVEL_ENDING_WITHIN_24H_BONUS =
			10;


	/**
	 * 여행 시작까지 1일 이하.
	 */
	public static final int TRAVEL_STARTING_WITHIN_1_DAY_SCORE =
			80;


	/**
	 * 여행 시작까지 3일 이하.
	 */
	public static final int TRAVEL_STARTING_WITHIN_3_DAYS_SCORE =
			70;


	/**
	 * 여행 시작까지 7일 이하.
	 */
	public static final int TRAVEL_STARTING_WITHIN_7_DAYS_SCORE =
			55;


	/**
	 * 그보다 먼 예정 여행.
	 */
	public static final int TRAVEL_UPCOMING_DEFAULT_SCORE =
			40;

	/* =====================================================
	 * Quest 정책
	 * ===================================================== */

	/**
	 * 현재 진행 중인 Quest.
	 *
	 * Quest가 진행 중이라는 사실만으로는
	 * 긴급 상황이라고 볼 수 없기 때문에
	 * NORMAL의 시작점인 50점을 사용한다.
	 *
	 * 향후 실제 deadline / expiresAt 등의
	 * 검증 가능한 시간 정보가 생기면
	 * 별도 가산 정책을 추가한다.
	 */
	public static final int QUEST_IN_PROGRESS_SCORE =
			50;

	/* =====================================================
	 * Emotion 정책
	 * ===================================================== */

	/**
	 * 최근 감정 중 AI Manager가 응답 Context에서
	 * 고려할 가치가 있는 신호의 기본 Priority.
	 *
	 * 감정 결과 하나만으로 긴급 상황을 의미하지 않으므로
	 * HIGH가 아니라 NORMAL 범위에서 시작한다.
	 */
	public static final int EMOTION_ATTENTION_SCORE =
			55;


	/**
	 * 감정 결과를 Priority 판단에 사용할
	 * 최소 confidence.
	 *
	 * 너무 불확실한 결과를 기반으로
	 * 사용자에게 상태를 추정하지 않도록
	 * 보수적인 하한선을 둔다.
	 */
	public static final double EMOTION_MIN_CONFIDENCE =
			0.65;


	/**
	 * 최신 감정으로 인정하는 최대 시간.
	 *
	 * 현재는 24시간.
	 *
	 * 오래된 감정 결과를
	 * "현재 상태"처럼 다루지 않도록 한다.
	 */
	public static final long EMOTION_RECENT_HOURS =
			24L;


	/**
	 * 계산 결과가 항상 0 ~ 100 범위 안에 있도록 보정한다.
	 */
	public static int clampScore(
			int score
	) {

		return Math.max(
				MIN_SCORE,
				Math.min(
						MAX_SCORE,
						score
				)
		);
	}


	/**
	 * 숫자 Score를 Priority Level로 변환한다.
	 */
	public static AiManagerPriorityResponse.PriorityLevel
	toLevel(
			int score
	) {

		int normalizedScore =
				clampScore(
						score
				);


		if (
				normalizedScore
						>= HIGH_MIN_SCORE
		) {

			return AiManagerPriorityResponse
					.PriorityLevel
					.HIGH;
		}


		if (
				normalizedScore
						>= NORMAL_MIN_SCORE
		) {

			return AiManagerPriorityResponse
					.PriorityLevel
					.NORMAL;
		}


		return AiManagerPriorityResponse
				.PriorityLevel
				.LOW;
	}
}