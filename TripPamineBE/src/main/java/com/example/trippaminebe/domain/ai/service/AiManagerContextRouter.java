
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerHomeResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerRoutingResult;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Map;

/**
 * TripPamine AI Manager Context Router V4.
 *
 * 질문을 두 경로로 구분한다.
 *
 * 1. Context가 필요한 질문
 *    - 퀘스트 개수 조회
 *
 * 2. Context 없이 처리할 질문
 *    - 여행 출발지 확인
 *    - 공감 체크인
 *    - 일반 SLM 대화
 *
 * 주의:
 * - 기존 route(message, context)는 유지한다.
 * - DB 조회는 이 클래스에서 수행하지 않는다.
 * - 실제 Quest 숫자는 검증된 Context에서만 가져온다.
 */
@Service
public class AiManagerContextRouter {

	private static final String SCHEMA_VERSION =
			"manager-input-v1";

	private static final String DIRECT_ANSWER =
			"DIRECT_ANSWER";

	private static final String SLM_GENERATION =
			"SLM_GENERATION";

	/**
	 * STEP F-2-5A
	 *
	 * 이 질문에 Quest Context 조회가 필요한지
	 * 미리 판단한다.
	 *
	 * 초기에는 기존 Quest 개수 탐지 규칙을
	 * 그대로 사용한다.
	 */
	public boolean requiresVerifiedQuestContext(
			String message
	) {

		String normalized = normalizeQuestion(message);

		return isQuestCountQuestion(normalized);
	}

	/**
	 * Context 없이 처리하는 전용 경로.
	 *
	 * Quest 개수 질문은 검증된 DB 정보가
	 * 필요하므로 이 메서드에서 처리하지 않는다.
	 */
	public AiManagerRoutingResult routeWithoutContext(
			String message
	) {

		String normalized = normalizeQuestion(message);

		if (isQuestCountQuestion(normalized)) {

			throw new IllegalArgumentException(
					"퀘스트 개수 조회에는 검증된 Context가 필요합니다."
			);
		}

		return routeNonQuest(normalized);
	}

	/**
	 * 기존 Context 기반 Router.
	 *
	 * 기존 메서드를 유지하여 다른 호출자와의
	 * 호환성을 보존한다.
	 */
	public AiManagerRoutingResult route(
			String message,
			AiManagerContextResponse context
	) {

		String normalized = normalizeQuestion(message);

		if (context == null) {

			throw new IllegalArgumentException(
					"AI Manager Context가 없습니다."
			);
		}

		/*
		 * 1. Quest 개수 질문.
		 *
		 * 이전과 동일하게 DB 기반 Context를 사용한다.
		 */
		if (isQuestCountQuestion(normalized)) {

			AiManagerHomeResponse.QuestContext quests =
					context.quests();

			if (quests == null) {
				return generalRoute();
			}

			boolean active =
					isActiveQuestQuestion(normalized);

			boolean completed =
					isCompletedQuestQuestion(normalized);

			boolean failed =
					isFailedQuestQuestion(normalized);

			int matchCount = 0;

			if (active) {
				matchCount++;
			}

			if (completed) {
				matchCount++;
			}

			if (failed) {
				matchCount++;
			}

			/*
			 * 상태가 없거나 여러 상태가 동시에
			 * 감지되면 직접 숫자를 확정하지 않는다.
			 */
			if (matchCount != 1) {
				return generalRoute();
			}

			if (active) {

				return directCountRoute(
						"QUEST_ACTIVE_COUNT",
						"activeQuestCount",
						quests.activeCount(),
						"현재 진행 중인 퀘스트는 "
				);
			}

			if (completed) {

				return directCountRoute(
						"QUEST_COMPLETED_COUNT",
						"completedQuestCount",
						quests.completedCount(),
						"완료된 퀘스트는 "
				);
			}

			return directCountRoute(
					"QUEST_FAILED_COUNT",
					"failedQuestCount",
					quests.failedCount(),
					"실패한 퀘스트는 "
			);
		}

		/*
		 * Quest 외의 질문은 공통 규칙으로 처리한다.
		 */
		return routeNonQuest(normalized);
	}

	/**
	 * Context가 필요 없는 질문 처리.
	 *
	 * 기존 V3의 응답 문장과 Intent를 유지한다.
	 */
	private AiManagerRoutingResult routeNonQuest(
			String normalized
	) {

		/*
		 * 여행 예산 질문에 출발지 정보가
		 * 제공되지 않은 경우.
		 */
		if (
				isTravelBudgetPlanningQuestion(normalized)
						&& !hasPossibleDepartureInformation(
						normalized
				)
		) {
			return missingDepartureRoute();
		}

		/*
		 * 제한된 공감 체크인.
		 */
		if (isSupportiveCheckIn(normalized)) {
			return supportiveCheckInRoute();
		}

		/*
		 * 그 밖의 질문은 SLM으로 전달.
		 *
		 * 현재 SLM 생성에 사용자별 DB Context를
		 * 사용하지 않으므로 별도 조회하지 않는다.
		 */
		return generalRoute();
	}

	/**
	 * 메시지 공통 검증 및 정규화.
	 */
	private String normalizeQuestion(
			String message
	) {

		if (
				message == null
						|| message.isBlank()
		) {
			throw new IllegalArgumentException(
					"AI Manager 질문이 비어 있습니다."
			);
		}

		if (message.length() > 2000) {
			throw new IllegalArgumentException(
					"AI Manager 질문은 최대 2000자입니다."
			);
		}

		return Normalizer.normalize(
				message,
				Normalizer.Form.NFKC
		).replaceAll("\\s+", "");
	}

	/**
	 * Quest 개수 질문 감지.
	 */
	private boolean isQuestCountQuestion(
			String question
	) {

		boolean mentionsQuest =
				question.contains("퀘스트");

		boolean asksCount =
				question.contains("몇")
						|| question.contains("개수")
						|| question.contains("갯수")
						|| question.contains("수량");

		return mentionsQuest && asksCount;
	}

	private boolean isActiveQuestQuestion(
			String question
	) {

		return question.contains("진행")
				|| question.contains("수행중")
				|| question.contains("활성퀘스트");
	}

	private boolean isCompletedQuestQuestion(
			String question
	) {

		return (
				question.contains("완료")
						&& !question.contains("미완료")
		) || question.contains("성공한퀘스트");
	}

	private boolean isFailedQuestQuestion(
			String question
	) {

		return question.contains("실패한퀘스트")
				|| question.contains("실패한")
				|| question.contains("실패된");
	}

	/**
	 * 여행 예산 계획 질문 탐지.
	 *
	 * V3 문자열 정책을 유지한다.
	 */
	private boolean isTravelBudgetPlanningQuestion(
			String question
	) {

		boolean travel =
				question.contains("여행")
						|| question.contains("제주")
						|| question.contains("항공권")
						|| question.contains("비행기");

		boolean budget =
				question.contains("예산")
						|| question.contains("경비")
						|| question.contains("비용")
						|| question.contains("여행자금")
						|| question.contains("만원");

		boolean planning =
				question.contains("계획")
						|| question.contains("어떻게")
						|| question.contains("가능")
						|| question.contains("할까")
						|| question.contains("가고싶");

		return travel && budget && planning;
	}

	/**
	 * 기존 출발지 탐지 정책.
	 *
	 * 실제 출발 도시 추출 기능은
	 * 후속 Context Parser 단계에서 개발한다.
	 */
	private boolean hasPossibleDepartureInformation(
			String question
	) {

		return question.contains("출발")
				|| question.contains("에서")
				|| question.contains("부터");
	}

	/**
	 * 명확한 첫 공감 체크인.
	 */
	private boolean isSupportiveCheckIn(
			String question
	) {

		String normalized = question.replaceAll(
				"[.!?。！？]+$",
				""
		);

		return normalized.equals("오늘은너무힘든하루였어")
				|| normalized.equals("오늘너무힘들었어")
				|| normalized.equals("오늘은정말힘든하루였어")
				|| normalized.equals("오늘하루너무힘들었어")
				|| normalized.equals("오늘은힘든하루였어")
				|| normalized.equals("오늘정말힘들었어");
	}

	/**
	 * 여행 출발지 확인 응답.
	 */
	private AiManagerRoutingResult missingDepartureRoute() {

		return new AiManagerRoutingResult(
				SCHEMA_VERSION,
				"TRAVEL_BUDGET_MISSING_DEPARTURE",
				Map.of(),
				Map.of(),
				new AiManagerRoutingResult.Decision(
						DIRECT_ANSWER,
						"여행 예산을 계획하려면 출발지부터 확인하는 게 좋아요. "
								+ "어디에서 출발하시나요?",
						"어디에서 출발하시나요?"
				)
		);
	}

	/**
	 * 안정적인 첫 공감 응답.
	 */
	private AiManagerRoutingResult supportiveCheckInRoute() {

		return new AiManagerRoutingResult(
				SCHEMA_VERSION,
				"SUPPORTIVE_CHECK_IN",
				Map.of(),
				Map.of(),
				new AiManagerRoutingResult.Decision(
						DIRECT_ANSWER,
						"오늘 많이 힘드셨겠어요. "
								+ "괜찮으시면 어떤 일이 있었는지 "
								+ "이야기해 주실래요?",
						null
				)
		);
	}

	/**
	 * 검증된 Quest 개수 직접 응답.
	 */
	private AiManagerRoutingResult directCountRoute(
			String intent,
			String factKey,
			long count,
			String answerPrefix
	) {

		return new AiManagerRoutingResult(
				SCHEMA_VERSION,
				intent,
				Map.of(),
				Map.of(
						factKey,
						count
				),
				new AiManagerRoutingResult.Decision(
						DIRECT_ANSWER,
						answerPrefix + count + "개입니다.",
						null
				)
		);
	}

	/**
	 * 일반 SLM 생성 경로.
	 */
	private AiManagerRoutingResult generalRoute() {

		return new AiManagerRoutingResult(
				SCHEMA_VERSION,
				"GENERAL_CHAT",
				Map.of(),
				Map.of(),
				new AiManagerRoutingResult.Decision(
						SLM_GENERATION,
						null,
						null
				)
		);
	}
}
