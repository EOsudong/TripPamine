package com.example.trippaminebe.domain.ai.priority;

import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerPriorityResponse;

import java.util.List;


/**
 * AI Manager Priority Rule 공통 계약.
 *
 * =========================================================
 * 목적
 * =========================================================
 *
 * 각 Domain은 자신의 Context만 해석해서
 * Priority 후보를 생성한다.
 *
 *
 * 예:
 *
 * TravelPriorityRule
 * QuestPriorityRule
 * EmotionPriorityRule
 * FinancePriorityRule
 *
 *
 * PriorityService는 각 Domain의 세부 정책을
 * 직접 알 필요가 없다.
 */
public interface AiManagerPriorityRule {


	/**
	 * 이 Rule이 담당하는 Domain.
	 *
	 * 예:
	 *
	 * travel
	 * quest
	 * emotion
	 */
	String domain();


	/**
	 * 현재 Manager Context를 읽고
	 * 이 Domain에서 발생하는 Priority를 반환한다.
	 *
	 * 후보가 없다면 null이 아니라
	 * 빈 List를 반환한다.
	 */
	List<AiManagerPriorityResponse.PriorityItem> evaluate(
			AiManagerContextResponse context
	);
}