
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse.TravelItem;
import com.example.trippaminebe.domain.mysterytour.client.KakaoLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import com.example.trippaminebe.domain.weather.dto.DailyWeatherSummary;
import com.example.trippaminebe.domain.weather.service.WeatherSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiManagerTravelWeatherAnswerService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MAX_DAYS = 7;

	private static final Set<String> GENERIC_WORDS = Set.of(
			"여행", "일정", "계획", "가족", "휴가",
			"주말", "투어", "이번", "다음"
	);

	private final AiTravelOverviewService travelOverviewService;
	private final KakaoLocalClient kakaoLocalClient;
	private final WeatherSummaryService weatherSummaryService;

	/**
	 * 저장된 여행의 날씨 질문에만 직접 응답한다.
	 * 일반 대화는 기존 라우터가 처리하도록 null을 반환한다.
	 */
	public String findAnswer(Long userId, String message) {
		String question = normalize(message);

		if (!question.contains("여행")
				|| !contains(question,
				"날씨", "예보", "강수", "기온",
				"비가", "비올", "바람", "풍랑")) {
			return null;
		}

		// 반드시 로그인한 사용자의 여행계획만 조회한다.
		List<TravelItem> plans = travelOverviewService
				.getOverview(userId)
				.plans();

		if (plans == null || plans.isEmpty()) {
			return "저장된 여행계획이 없습니다. "
					+ "여행 플래너에서 여행을 먼저 등록해 주세요.";
		}

		List<PlanMatch> matches = plans.stream()
				.filter(plan ->
						"PLANNED".equals(plan.phase())
								|| "ACTIVE".equals(plan.phase()))
				.map(plan -> new PlanMatch(
						plan,
						matchedDestination(plan.planName(), question)
				))
				.filter(match -> match.destination() != null)
				.toList();


		long upcomingCount = plans.stream()
				.filter(plan ->
						"PLANNED".equals(plan.phase())
								|| "ACTIVE".equals(plan.phase()))
				.count();

		long keywordMatchCount = plans.stream()
				.filter(plan ->
						matchedDestination(
								plan.planName(),
								question
						) != null)
				.count();

		log.info(
				"AI_WEATHER_MATCH total={}, upcoming={}, keywordMatches={}, selected={}",
				plans.size(),
				upcomingCount,
				keywordMatchCount,
				matches.size()
		);

		if (matches.isEmpty()) {
			return "질문에서 언급한 여행지와 일치하는 "
					+ "진행 중이거나 예정된 여행계획을 찾지 못했습니다. "
					+ "여행 플래너에 저장된 여행 이름과 상태를 확인해 주세요.";
		}

		if (matches.size() > 1) {
			return "해당 목적지를 포함한 예정 여행이 여러 건입니다. "
					+ "어느 여행을 확인할지 여행 이름을 구체적으로 알려주세요.";
		}

		PlanMatch selected = matches.get(0);
		TravelItem plan = selected.plan();

		if (plan.startDate() == null || plan.endDate() == null) {
			return "선택한 여행의 출발일 또는 종료일이 등록되지 않아 "
					+ "날짜별 예보를 확인할 수 없습니다.";
		}

		LocalDate start = plan.startDate().toLocalDate();
		LocalDate end = plan.endDate().toLocalDate();

		if (end.isBefore(start)) {
			return "여행 종료일이 출발일보다 빠릅니다. "
					+ "여행 플래너에서 일정을 확인해 주세요.";
		}

		LocalDate today = LocalDate.now(KST);

		if (end.isBefore(today)) {
			return "해당 여행은 이미 종료되었습니다. "
					+ "현재 예보로 지난 날짜의 실제 날씨를 확인할 수는 없습니다.";
		}

		if (start.isAfter(today.plusDays(4))) {
			return plan.planName() + " 여행은 "
					+ start + "부터 시작합니다. "
					+ "현재 연결된 예보 서비스의 조회 범위를 벗어나 "
					+ "아직 여행 날짜의 날씨를 안내할 수 없습니다.";
		}

		// 목적지 검색 결과를 실제 지역 주소와 대조한다.
		// 검색 첫 결과가 다른 지역의 동명 업체일 수 있으므로
		// 무조건 첫 좌표를 사용하는 것은 피한다.
		KakaoPlace place;

		try {
			List<KakaoPlace> candidates = kakaoLocalClient
					.searchPlaces(selected.destination(), 5);

			String region = selected.destination();
			if (region.endsWith("도") && region.length() > 2) {
				region = region.substring(0, region.length() - 1);
			}

			final String regionKey = region;

			place = candidates.stream()
					.filter(candidate ->
							candidate.addressName() != null
									&& candidate.addressName().contains(regionKey))
					.findFirst()
					.orElse(null);

		} catch (RuntimeException exception) {
			log.warn("AI 여행 날씨 장소 검색 실패", exception);
			return "여행계획은 확인했지만 목적지 위치를 "
					+ "조회하지 못했습니다. 잠시 후 다시 확인해 주세요.";
		}

		if (place == null) {
			return "여행계획은 확인했지만 목적지의 대표 위치를 "
					+ "정확히 확인할 수 없어 날씨를 조회하지 않았습니다. "
					+ "여행지 이름을 더 구체적으로 알려주세요.";
		}

		StringBuilder answer = new StringBuilder()
				.append(plan.planName())
				.append(" 여행의 날씨 예보입니다.\n")
				.append("여행 기간: ")
				.append(start)
				.append(" ~ ")
				.append(end)
				.append("\n조회 지점: ")
				.append(place.placeName())
				.append(" (카카오 장소 검색 기준 대표 지점)\n");

		int availableDays = 0;
		int unavailableDays = 0;
		boolean caution = false;

		LocalDate date = start;

		for (int i = 0; i < MAX_DAYS && !date.isAfter(end); i++) {
			DailyWeatherSummary summary;

			try {
				summary = weatherSummaryService.getDailySummary(
						place.latitude().doubleValue(),
						place.longitude().doubleValue(),
						date
				);
			} catch (RuntimeException exception) {
				log.warn("AI 여행 날씨 예보 조회 실패", exception);
				return "여행 일정은 확인했지만 날씨 제공 서비스에서 "
						+ "예보를 가져오지 못했습니다. "
						+ "잠시 후 다시 확인해 주세요.";
			}

			answer.append("\n").append(date).append(": ");

			if (summary == null || !summary.isAvailable()) {
				answer.append("예보 미제공");
				unavailableDays++;
				date = date.plusDays(1);
				continue;
			}

			availableDays++;

			answer.append(String.format(
					Locale.KOREA,
					"기온 %.1f~%.1f℃, 강수확률 %d%%, 최대 풍속 %.1fm/s",
					summary.getTempMin(),
					summary.getTempMax(),
					summary.getRainProbability(),
					summary.getWindSpeed()
			));

			if (summary.getRainProbability() >= 60
					|| summary.getWindSpeed() >= 9.0) {
				caution = true;
			}

			date = date.plusDays(1);
		}

		if (!date.isAfter(end)) {
			answer.append("\n나머지 일정은 표시하지 않았습니다.");
		}

		if (availableDays == 0) {
			answer.append("\n아직 확인 가능한 여행 날짜의 예보가 없습니다.");
		} else if (caution) {
			answer.append("\n일부 날짜에 비 또는 강한 바람의 "
					+ "가능성이 있어 야외 일정에 주의가 필요합니다.");
		} else {
			answer.append("\n현재 조회된 날짜의 수치만으로는 "
					+ "큰 강수·강풍 위험이 두드러지지 않지만, "
					+ "예보는 변할 수 있습니다.");
		}

		if (unavailableDays > 0) {
			answer.append("\n예보가 없는 날짜가 있으므로 "
					+ "여행 전체 기간의 날씨가 괜찮다고 "
					+ "확정할 수는 없습니다.");
		}

		answer.append("\n이 결과는 검색된 대표 지점의 육상 예보입니다. "
				+ "해상 교통을 이용하는 여행이라면 "
				+ "해상 기상과 여객선 운항 여부를 별도로 확인해야 합니다.");

		return answer.toString();
	}

	/**
	 * 사용자의 질문과 저장된 여행 이름이 공유하는
	 * 목적지 후보 단어를 찾는다.
	 */
	private static String matchedDestination(
			String planName, String question
	) {
		if (planName == null || planName.isBlank()) {
			return null;
		}

		String best = null;

		for (String token : planName.split(
				"[^\\p{IsHangul}A-Za-z0-9]+")) {

			String word = normalize(token);

			if (word.length() < 2
					|| word.matches(".*\\d.*")
					|| GENERIC_WORDS.contains(word)
					|| !question.contains(word)) {
				continue;
			}

			if (best == null || word.length() > best.length()) {
				best = word;
			}
		}

		return best;
	}

	private static boolean contains(
			String text, String... keywords
	) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private static String normalize(String text) {
		return text == null ? ""
				: text.toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", "");
	}

	private record PlanMatch(
			TravelItem plan,
			String destination
	) {}
}