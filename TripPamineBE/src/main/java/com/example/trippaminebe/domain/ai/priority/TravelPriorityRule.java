package com.example.trippaminebe.domain.ai.priority;

import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerHomeResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerPriorityResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


/**
 * Travel Domain Priority Rule.
 *
 * =========================================================
 * 현재 정책
 * =========================================================
 *
 * ACTIVE
 *
 * - 현재 여행 중
 * - 종료 24시간 이내 여부
 *
 *
 * UPCOMING
 *
 * - 1일 이내
 * - 3일 이내
 * - 7일 이내
 * - 그 이후
 *
 *
 * 이 Rule은 자연어 메시지를 생성하지 않는다.
 *
 * 무엇이 중요한지와
 * 왜 중요한지만 구조화한다.
 */
@Component
public class TravelPriorityRule
		implements AiManagerPriorityRule {


	@Override
	public String domain() {

		return "travel";
	}


	@Override
	public List<AiManagerPriorityResponse.PriorityItem>
	evaluate(
			AiManagerContextResponse context
	) {

		if (
				context == null
						||
						context.travel() == null
						||
						context.travel().focusPlan() == null
		) {

			return List.of();
		}


		AiManagerHomeResponse.FocusTravel focusPlan =
				context
						.travel()
						.focusPlan();


		/*
		 * Priority가 실제 Domain 객체를 가리키려면
		 * 식별자가 반드시 필요하다.
		 */
		if (
				focusPlan.planId() == null
		) {

			return List.of();
		}


		List<String> reasonCodes =
				new ArrayList<>();


		int score;


		/* =====================================================
		 * ACTIVE
		 * ===================================================== */

		if (
				"ACTIVE".equalsIgnoreCase(
						focusPlan.state()
				)
		) {

			score =
					AiManagerPriorityPolicy
							.TRAVEL_ACTIVE_BASE_SCORE;


			reasonCodes.add(
					"TRAVEL_ACTIVE_NOW"
			);


			/*
			 * 전체 Context가 생성된 기준 시각을 사용한다.
			 *
			 * 서로 다른 LocalDateTime.now()를 호출하면
			 * 같은 요청 안에서도 시간 기준이 달라질 수 있으므로
			 * Context Snapshot의 generatedAt을 기준으로 계산한다.
			 */
			if (
					focusPlan.endDate() != null
							&&
							context.generatedAt() != null
			) {

				long minutesUntilEnd =
						Duration
								.between(
										context.generatedAt(),
										focusPlan.endDate()
								)
								.toMinutes();


				if (
						minutesUntilEnd >= 0
								&&
								minutesUntilEnd <= 24L * 60L
				) {

					score +=
							AiManagerPriorityPolicy
									.TRAVEL_ENDING_WITHIN_24H_BONUS;


					reasonCodes.add(
							"TRAVEL_ENDING_WITHIN_24H"
					);
				}
			}


			/* =====================================================
			 * UPCOMING
			 * ===================================================== */

		} else if (
				"UPCOMING".equalsIgnoreCase(
						focusPlan.state()
				)
		) {

			Long daysUntilStart =
					focusPlan.daysUntilStart();


			if (
					daysUntilStart == null
			) {

				score =
						AiManagerPriorityPolicy
								.TRAVEL_UPCOMING_DEFAULT_SCORE;


				reasonCodes.add(
						"TRAVEL_UPCOMING"
				);


			} else if (
					daysUntilStart <= 1
			) {

				score =
						AiManagerPriorityPolicy
								.TRAVEL_STARTING_WITHIN_1_DAY_SCORE;


				reasonCodes.add(
						"TRAVEL_STARTING_WITHIN_1_DAY"
				);


			} else if (
					daysUntilStart <= 3
			) {

				score =
						AiManagerPriorityPolicy
								.TRAVEL_STARTING_WITHIN_3_DAYS_SCORE;


				reasonCodes.add(
						"TRAVEL_STARTING_WITHIN_3_DAYS"
				);


			} else if (
					daysUntilStart <= 7
			) {

				score =
						AiManagerPriorityPolicy
								.TRAVEL_STARTING_WITHIN_7_DAYS_SCORE;


				reasonCodes.add(
						"TRAVEL_STARTING_WITHIN_7_DAYS"
				);


			} else {

				score =
						AiManagerPriorityPolicy
								.TRAVEL_UPCOMING_DEFAULT_SCORE;


				reasonCodes.add(
						"TRAVEL_UPCOMING"
				);
			}


		} else {

			/*
			 * 아직 정의하지 않은 Travel State를
			 * 임의로 해석하지 않는다.
			 */
			return List.of();
		}


		score =
				AiManagerPriorityPolicy
						.clampScore(
								score
						);


		AiManagerPriorityResponse.PriorityItem item =
				new AiManagerPriorityResponse.PriorityItem(

						"travel.focus."
								+ focusPlan.planId(),

						domain(),

						"TRAVEL_PLAN",

						String.valueOf(
								focusPlan.planId()
						),

						score,

						AiManagerPriorityPolicy
								.toLevel(
										score
								),

						List.copyOf(
								reasonCodes
						)
				);


		return List.of(
				item
		);
	}
}