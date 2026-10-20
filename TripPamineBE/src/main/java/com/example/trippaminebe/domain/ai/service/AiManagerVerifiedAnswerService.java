
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse;
import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse.TravelItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * AI 매니저의 검증된 사실 응답.
 *
 * 여행 수, 일정, 예산은 Qwen3가 추측하지 않고
 * 로그인 사용자의 실제 DB 조회 결과로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerVerifiedAnswerService {

	private static final DateTimeFormatter DATE_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final AiTravelOverviewService travelOverviewService;

	/**
	 * 검증된 직접 응답이 가능하면 문자열을 반환한다.
	 * 일반적인 자유 대화라면 null을 반환하여 Qwen3로 전달한다.
	 */
	public String findAnswer(
			Long userId,
			String message,
			List<String> previousUserMessages
	) {
		String q = normalize(message);

		// 1. AI의 정체성 질문
		if (contains(
				q,
				"너는어디서왔",
				"넌어디서왔",
				"너는누구",
				"넌누구",
				"너누구"
		)) {
			return "저는 트립파민에서 여행 계획과 서비스 이용을 돕는 AI 매니저입니다. "
					+ "사람처럼 특정 장소에서 온 것은 아니고, 앱 안에서 대화하는 AI예요.";
		}

		// 2. 확인되지 않은 UI 메뉴를 만들어내지 않도록
		// 실제 구현된 여행 플래너만 안내한다.
		if (q.contains("여행")
				&& q.contains("휴식")
				&& contains(q, "중심", "위주", "편하게", "쉬고")) {

			return "휴식 중심이라면 이동 구간을 줄이고 한 장소에서 머무는 시간을 "
					+ "넉넉히 잡는 편이 좋아요. "
					+ "트립파민 여행 플래너에서 기존 일정을 확인하고 조정할 수 있습니다. "
					+ "어느 여행을 휴식 중심으로 구성할까요?";
		}

		boolean personal = contains(
				q,
				"내여행",
				"나의여행",
				"저장된여행",
				"저장한여행"
		);

		boolean countQuestion = personal && contains(
				q, "몇개", "몇건", "총몇", "몇번"
		);

		boolean detailQuestion = personal && contains(
				q, "일정", "예산", "계획", "보여", "알려", "언제"
		);

		boolean budgetFollowup =
				q.contains("예산")
						&& contains(q, "괜찮", "부족", "충분", "적당", "적절")
						&& previousIsAboutTravel(previousUserMessages);

		// 실제 사용자 정보가 필요하지 않은 질문은 DB를 조회하지 않는다.
		if (!countQuestion && !detailQuestion && !budgetFollowup) {
			return null;
		}

		// 3. 로그인 사용자 본인의 여행계획만 조회
		AiTravelOverviewResponse overview =
				travelOverviewService.getOverview(userId);

		// 4. 여행계획 개수는 실제 조회 결과를 직접 출력
		if (countQuestion) {
			return "현재 저장된 여행계획은 총 "
					+ overview.totalPlanCount() + "개입니다. "
					+ "여행 준비 중 " + overview.plannedCount() + "개, "
					+ "여행 중 " + overview.activeCount() + "개, "
					+ "종료된 여행 " + overview.completedCount() + "개입니다.";
		}

		List<TravelItem> plans =
				overview.plans() == null ? List.of() : overview.plans();

		if (plans.isEmpty()) {
			return "현재 저장된 여행계획이 없습니다. "
					+ "여행 플래너에서 새 여행을 만들 수 있어요.";
		}

		// 여행 중 → 여행 준비 중 → 종료된 여행 순서
		List<TravelItem> sorted = plans.stream()
				.sorted(
						Comparator
								.comparingInt(
										(TravelItem p) -> priority(p.phase())
								)
								.thenComparing(
										TravelItem::startDate,
										Comparator.nullsLast(
												Comparator.naturalOrder()
										)
								)
				)
				.toList();

		// 5. "그럼 예산은 괜찮을까?"에 대한 검증된 답변
		if (budgetFollowup && !detailQuestion) {

			List<TravelItem> upcoming = sorted.stream()
					.filter(p ->
							"ACTIVE".equals(p.phase())
									|| "PLANNED".equals(p.phase())
					)
					.toList();

			if (upcoming.size() != 1) {
				return upcoming.isEmpty()
						? "현재 진행 중이거나 예정된 여행이 없습니다. "
						  + "어느 여행의 예산을 확인할까요?"
						: "진행 중이거나 예정된 여행이 "
						  + upcoming.size()
						  + "개입니다. 어느 여행의 예산을 확인할까요?";
			}

			TravelItem plan = upcoming.get(0);

			return safeName(plan.planName())
					+ "의 설정 예산은 " + budget(plan) + "입니다. "
					+ "실제 지출과 남은 금액은 이번 여행계획 조회에 포함되지 않아 "
					+ "예산이 충분한지 단정할 수 없습니다.";
		}

		// 6. 실제 저장된 여행 이름, 기간, 예산 출력
		StringBuilder answer =
				new StringBuilder("저장된 여행계획은 총 ")
						.append(overview.totalPlanCount())
						.append("개입니다.\n");

		int shown = Math.min(3, sorted.size());

		for (int i = 0; i < shown; i++) {
			TravelItem plan = sorted.get(i);

			answer.append(i + 1)
					.append(". ")
					.append(safeName(plan.planName()))
					.append(" (상태: ")
					.append(safeName(plan.statusLabel()))
					.append(")\n")
					.append("   일정: ")
					.append(date(plan.startDate()))
					.append(" ~ ")
					.append(date(plan.endDate()))
					.append("\n")
					.append("   설정 예산: ")
					.append(budget(plan))
					.append("\n");
		}

		if (sorted.size() > shown) {
			answer.append("나머지 ")
					.append(sorted.size() - shown)
					.append("개 여행의 상세 정보는 생략했습니다.");
		}

		return answer.toString().trim();
	}

	private static boolean previousIsAboutTravel(List<String> messages) {
		if (messages == null || messages.isEmpty()) {
			return false;
		}

		for (
				int i = messages.size() - 1, checked = 0;
				i >= 0 && checked < 3;
				i--, checked++
		) {
			String q = normalize(messages.get(i));

			if (contains(
					q,
					"이번여행",
					"내여행",
					"여행계획",
					"여행일정"
			)) {
				return true;
			}
		}

		return false;
	}

	private static boolean contains(
			String text,
			String... words
	) {
		for (String word : words) {
			if (text.contains(word)) {
				return true;
			}
		}

		return false;
	}

	private static String normalize(String value) {
		return value == null
				? ""
				: value.toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", "");
	}

	private static int priority(String phase) {
		if ("ACTIVE".equals(phase)) return 0;
		if ("PLANNED".equals(phase)) return 1;
		return 2;
	}

	private static String safeName(String name) {
		if (name == null || name.isBlank()) {
			return "미등록";
		}

		String value = name
				.replaceAll("[\\p{Cntrl}]", " ")
				.trim();

		return value.length() > 60
				? value.substring(0, 60)
				: value;
	}

	private static String date(LocalDateTime value) {
		return value == null
				? "미등록"
				: value.format(DATE_FORMAT);
	}

	private static String budget(TravelItem plan) {
		return plan.totalBudget() == null
				? "미등록"
				: plan.totalBudget().toPlainString() + "원";
	}
}