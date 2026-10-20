
package com.example.trippaminebe.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 복합 질문은 기존 여행·게임 조회 결과를 합쳐 응답한다.
 * 대상이 명확하지 않은 후속 질문에는 수치를 추측하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerCompositeAnswerService {

	private final AiManagerVerifiedAnswerService travelAnswerService;
	private final AiManagerGameAnswerService gameAnswerService;

	public String findAnswer(
			Long userId,
			String message,
			List<String> previousUserMessages
	) {
		String question = normalize(message);
		String previous = lastUserQuestion(previousUserMessages);

		// 대상이 없는 "그건 얼마야?"를 먼저 차단한다.
		if (isAmbiguousAmount(question)) {
			if (isTravelRequest(previous) && isGameRequest(previous)) {
				return "여행 예산, 보유 포인트, 퀘스트 보상 중 어느 금액을 "
						+ "확인할까요? 여행 예산이라면 여행 이름도 알려주세요.";
			}

			if (isTravelRequest(previous)) {
				return "어느 여행의 예산을 확인할까요? "
						+ "여행 이름을 알려주세요.";
			}

			return "어떤 항목의 금액인지 알려주세요. "
					+ "여행 예산, 보유 포인트, 퀘스트 보상 중 선택할 수 있어요.";
		}

		if (isAmbiguousNext(question)) {
			return "무엇의 다음 내용을 확인하고 싶으신가요? "
					+ "여행 일정이나 퀘스트 진행 상황 등 대상을 알려주세요.";
		}

		// 단일 질문은 이미 검증된 기존 서비스가 처리한다.
		if (!isPersonal(question)
				|| !isTravelRequest(question)
				|| !isGameRequest(question)) {
			return null;
		}

		boolean points = question.contains("포인트");
		boolean quests = contains(question, "퀘스트", "미션");
		boolean rpg = contains(
				question, "rpg", "레벨", "경험치", "exp", "칭호"
		);
		boolean mascots = question.contains("마스코트")
				&& contains(
				question, "수집", "모은", "잡은", "포획"
		);

		// 1. 여행정보: 기존 검증 서비스에서 직접 조회
		String travelAnswer = travelAnswerService.findAnswer(
				userId,
				"내 여행 일정 알려줘",
				List.of()
		);

		// 2. 게임정보: 실제 요청한 항목만 조회
		StringBuilder gameQuestion = new StringBuilder("내 ");

		if (points) {
			gameQuestion.append("포인트 ");
		}
		if (quests) {
			gameQuestion.append("퀘스트 ");
		}
		if (rpg) {
			gameQuestion.append("RPG 레벨 경험치 ");
		}
		if (mascots) {
			gameQuestion.append("마스코트 수집 ");
		}

		gameQuestion.append("알려줘");

		String gameAnswer = gameAnswerService.findAnswer(
				userId,
				gameQuestion.toString()
		);

		// 부분 결과를 정상적인 전체 조회로 표시하지 않는다.
		if (travelAnswer == null || travelAnswer.isBlank()
				|| gameAnswer == null || gameAnswer.isBlank()) {
			throw new IllegalStateException(
					"복합 조회 결과가 완전하지 않습니다."
			);
		}

		return "여행 정보\n"
				+ travelAnswer
				+ "\n\n게임 활동\n"
				+ gameAnswer;
	}

	private static boolean isTravelRequest(String q) {
		return q.contains("여행")
				&& contains(
				q, "일정", "계획", "예산", "출발", "기간"
		);
	}

	private static boolean isGameRequest(String q) {
		return q.contains("포인트")
				|| contains(
				q, "퀘스트", "미션", "rpg",
				"레벨", "경험치", "exp", "칭호"
		)
				|| (
				q.contains("마스코트")
						&& contains(
						q, "수집", "모은", "잡은", "포획"
				)
		);
	}

	private static boolean isPersonal(String q) {
		return q.startsWith("내")
				|| q.startsWith("나의")
				|| q.startsWith("제")
				|| q.startsWith("저의")
				|| q.contains("내가");
	}

	private static boolean isAmbiguousAmount(String q) {
		return contains(q, "그건", "그거", "그것")
				&& contains(q, "얼마", "금액", "가격")
				&& !contains(
				q, "포인트", "퀘스트", "미션", "예산"
		);
	}

	private static boolean isAmbiguousNext(String q) {
		return q.replaceAll(
				"[\\p{Punct}“”‘’？！]", ""
		).equals("다음은");
	}

	private static String lastUserQuestion(List<String> history) {
		if (history == null || history.isEmpty()) {
			return "";
		}

		return normalize(history.get(history.size() - 1));
	}

	private static boolean contains(
			String q,
			String... keywords
	) {
		for (String keyword : keywords) {
			if (q.contains(keyword)) {
				return true;
			}
		}

		return false;
	}

	private static String normalize(String text) {
		return text == null
				? ""
				: text.toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", "");
	}
}