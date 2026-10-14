package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerPriorityResponse;
import com.example.trippaminebe.domain.ai.priority.AiManagerPriorityRule;
import com.example.trippaminebe.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * AI Manager Priority Engine.
 *
 * =========================================================
 * 역할
 * =========================================================
 *
 * Manager Context
 *      ↓
 * Domain Priority Rules
 *      ↓
 * Priority 후보 통합
 *      ↓
 * Score 정렬
 *
 *
 * 이 Service는 이제 Travel / Quest / Emotion 등의
 * 구체적인 판단 규칙을 직접 갖지 않는다.
 *
 * 각 Domain Rule의 결과를 모으고
 * 정렬하는 Orchestrator 역할만 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerPriorityService {


	private final AiManagerContextService
			aiManagerContextService;


	/**
	 * Spring이 AiManagerPriorityRule 구현체를
	 * 자동으로 주입한다.
	 *
	 * 현재:
	 *
	 * - TravelPriorityRule
	 *
	 * 이후:
	 *
	 * - QuestPriorityRule
	 * - EmotionPriorityRule
	 * - FinancePriorityRule
	 *
	 * 등을 추가할 수 있다.
	 */
	private final List<AiManagerPriorityRule>
			priorityRules;


	/**
	 * 현재 인증 사용자의
	 * 전체 Priority를 평가한다.
	 */
	public AiManagerPriorityResponse evaluate(
			User user
	) {

		if (
				user == null
						||
						user.getId() == null
		) {

			throw new IllegalArgumentException(
					"AI Manager Priority를 계산하려면 인증된 사용자가 필요합니다."
			);
		}


		/* =====================================================
		 * 1. Manager Context
		 * ===================================================== */

		AiManagerContextResponse context =
				aiManagerContextService
						.getContext(
								user
						);


		/* =====================================================
		 * 2. Domain Rule 실행
		 * ===================================================== */

		List<AiManagerPriorityResponse.PriorityItem> priorities =
				new ArrayList<>();


		for (
				AiManagerPriorityRule priorityRule
				: priorityRules
		) {

			List<AiManagerPriorityResponse.PriorityItem> ruleResults =
					priorityRule.evaluate(
							context
					);


			if (
					ruleResults == null
							||
							ruleResults.isEmpty()
			) {

				continue;
			}


			priorities.addAll(
					ruleResults
			);
		}


		/* =====================================================
		 * 3. Priority 정렬
		 * =====================================================
		 *
		 * 1순위: 높은 Score
		 * 2순위: 동일 Score라면 priorityKey
		 *
		 * Spring Bean 주입 순서와 관계없이
		 * 외부 응답 순서를 항상 동일하게 유지한다.
		 */

		priorities.sort(

				Comparator
						.comparingInt(
								AiManagerPriorityResponse
										.PriorityItem
										::score
						)
						.reversed()
						.thenComparing(
								AiManagerPriorityResponse
										.PriorityItem
										::priorityKey
						)
		);


		/* =====================================================
		 * Final
		 * ===================================================== */

		return new AiManagerPriorityResponse(

				context.generatedAt(),

				List.copyOf(
						priorities
				)
		);
	}
}