package com.example.trippaminebe.domain.ai.priority;

import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerHomeResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerPriorityResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


/**
 * Quest Domain Priority Rule.
 *
 * =========================================================
 * 현재 정책
 * =========================================================
 *
 * 현재 실제로 진행 중인 Quest를
 * 각각 하나의 Priority 후보로 만든다.
 *
 *
 * PROGRESS Quest
 *
 * score:
 * 50
 *
 * level:
 * NORMAL
 *
 * reason:
 * QUEST_IN_PROGRESS
 *
 *
 * 중요한 점:
 *
 * 현재 Quest Context에는
 *
 * - deadline
 * - expiresAt
 * - 실제 마감 시각
 *
 * 이 존재하지 않는다.
 *
 * 따라서 startedAt이 오래되었다는 이유만으로
 * "마감 임박"이나 "긴급"으로 해석하지 않는다.
 *
 *
 * Chain 진행률:
 *
 * clearedStepCount / totalStepCount
 *
 * 역시 현재는 Context 정보로만 사용하고
 * Priority 점수에는 반영하지 않는다.
 */
@Component
public class QuestPriorityRule
		implements AiManagerPriorityRule {


	/**
	 * 이 Rule이 담당하는 Domain.
	 */
	@Override
	public String domain() {

		return "quest";
	}


	/**
	 * 현재 진행 중인 Quest 각각을
	 * Priority 후보로 변환한다.
	 */
	@Override
	public List<AiManagerPriorityResponse.PriorityItem>
	evaluate(
			AiManagerContextResponse context
	) {

		/*
		 * =================================================
		 * 1. Context 방어
		 * =================================================
		 */

		if (
				context == null
						||
						context.quests() == null
						||
						context.quests().activeQuests() == null
						||
						context.quests().activeQuests().isEmpty()
		) {

			return List.of();
		}


		/*
		 * =================================================
		 * 2. Active Quest → Priority
		 * =================================================
		 */

		List<AiManagerPriorityResponse.PriorityItem> priorities =
				new ArrayList<>();


		for (
				AiManagerHomeResponse.ActiveQuest activeQuest
				: context.quests().activeQuests()
		) {

			/*
			 * 하나의 Quest 수행 기록을 안정적으로 식별하려면
			 * logId가 필요하다.
			 *
			 * 같은 questId의 Quest를 사용자가
			 * 여러 번 수행할 가능성이 있기 때문에
			 * priorityKey와 subjectId는 questId가 아니라
			 * UserQuestLog의 logId를 사용한다.
			 */
			if (
					activeQuest == null
							||
							activeQuest.logId() == null
			) {

				continue;
			}


			int score =
					AiManagerPriorityPolicy
							.clampScore(
									AiManagerPriorityPolicy
											.QUEST_IN_PROGRESS_SCORE
							);


			List<String> reasonCodes =
					List.of(
							"QUEST_IN_PROGRESS"
					);


			AiManagerPriorityResponse.PriorityItem item =
					new AiManagerPriorityResponse.PriorityItem(

							/*
							 * 예:
							 * quest.active.12
							 */
							"quest.active."
									+ activeQuest.logId(),

							domain(),

							/*
							 * subjectId가 Quest 정의 자체가 아니라
							 * 사용자의 현재 Quest 수행 기록(logId)을
							 * 가리킨다는 점을 명시한다.
							 */
							"USER_QUEST_LOG",

							String.valueOf(
									activeQuest.logId()
							),

							score,

							AiManagerPriorityPolicy
									.toLevel(
											score
									),

							reasonCodes
					);


			priorities.add(
					item
			);
		}


		/*
		 * PriorityService에서 전체 Domain 결과를
		 * 다시 Score 기준으로 정렬하므로,
		 * 이 Rule에서는 별도 정렬이 필요 없다.
		 */
		return List.copyOf(
				priorities
		);
	}
}