
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiManagerTravelWeatherAnswerService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MAX_DAYS = 7;

	// 한글 단어 사이의 일부인 '비가'는 강수 표현이 아니다.
	// 예: '여행 준비가 궁금해'에 들어 있는 '비가'를 비 소식으로 오해하지 않는다.
	private static final Pattern RAIN_SUBJECT = Pattern.compile("(?<![가-힣])비가");

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
	// 기존 호출부 호환성 유지. 후속 질문을 해석할 수 있는 호출부에서는 이력을 전달한다.
	public String findAnswer(Long userId, String message) {
		return findAnswer(userId, message, List.of());
	}

	/**
	 * 사용자 후속 발화의 여행지 문맥은 최근 사용자 질문에서만 복원한다.
	 * Qwen3가 이전에 만든 답변이나 다른 사용자의 대화는 참조하지 않는다.
	 */
	public String findAnswer(
			Long userId, String message, List<String> previousUserMessages
	) {
		String question = normalize(message);

		// 날씨 의도 판정에는 공백이 남아 있는 사용자 원문을 사용한다.
		// 공백을 먼저 제거하면 '준비가'와 '비가'를 구분할 수 없다.
		boolean directWeatherQuestion = isTravelWeatherQuestion(message);
		String previousWeatherQuestion = directWeatherQuestion
				? null : recentTravelWeatherQuestion(message, previousUserMessages);
		boolean rainFollowup = previousWeatherQuestion != null;
		// 같은 우천 질문이라도 '어떻게 대비?'와 '어디로 갈까?'는 다른 의도다.
		boolean alternativePlaceRequested = isAlternativePlaceRequest(question);

		if (!directWeatherQuestion && !rainFollowup) {
			return null;
		}

		// "혹시 비가 오면?"에는 현재 예보를 다시 확인하되 실제 우천 시 대응을 별도로 제공한다.
		String destinationQuestion = rainFollowup ? previousWeatherQuestion : question;
		boolean adviceRequested = !rainFollowup && contains(question,
				"조정", "변경", "바꿔", "바꾸", "대안",
				"실내", "우천", "비오면", "비가오면",
				"강풍", "폭우", "취소", "추천", "위험");

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
						matchedDestination(plan.planName(), destinationQuestion)
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
								destinationQuestion
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

		// 실제 대체 장소를 원하는 질문에는 같은 예보와 주의사항을 반복하지 않는다.
		// 로그인한 사용자의 여행지 권역에서 Kakao Local의 실제 검색 결과만 제시한다.
		if (alternativePlaceRequested) {
			return suggestIndoorPlaces(selected.destination(), plan.planName());
		}

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
				.append(rainFollowup
						? " 여행의 우천 대비안을 안내합니다.\n"
						: " 여행의 날씨 예보입니다.\n")
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

			// 예보 일부 값이 누락된 날을 0℃ 등으로 잘못 출력하지 않는다.
			if (summary == null || !summary.isAvailable()
					|| summary.getTempMin() == null
					|| summary.getTempMax() == null
					|| summary.getRainProbability() == null
					|| summary.getWindSpeed() == null) {
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

			// 사용자가 요청한 경우에만 해당 날짜의 보수적인 조정 제안을 표시한다.
			if (adviceRequested) {
				answer.append("\n  일정 조정 제안: ")
						.append(adviceForDay(summary));
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

		if (rainFollowup) {
			// 강수확률 0%를 '비가 절대 오지 않음'으로 잘못 단정하지 않는다.
			answer.append("\n예보상 강수확률이 낮더라도 당일 날씨는 바뀔 수 있습니다. "
					+ "만약 실제로 비가 내린다면 다음 순서로 대응하세요.")
					.append("\n1. 출발 전 예보와 현장 강수 상황을 다시 확인하세요.")
					.append("\n2. 야외 방문 시간을 줄이고 실내 관광·전시·식사 등으로 "
						+ "순서를 바꾸는 대안을 준비하세요.")
					.append("\n3. 대체 장소의 실제 운영시간·예약 가능 여부·이동 시간을 "
						+ "확인한 뒤 변경 여부를 결정하세요.");
			answer.append("\n아직 저장된 일정이나 예약은 변경하지 않았습니다.");
		}

		if (adviceRequested) {
			answer.append("\n이 내용은 여행 목적지의 날짜별 예보에 따른 제안입니다. "
					+ "저장된 방문지·예약·이동 경로는 변경하지 않았어요. "
					+ "실제 대체 장소의 영업시간과 이동 가능 여부는 별도로 확인해야 합니다.");
		}

		answer.append("\n이 결과는 검색된 대표 지점의 육상 예보입니다. "
				+ "해상 교통을 이용하는 여행이라면 "
				+ "해상 기상과 여객선 운항 여부를 별도로 확인해야 합니다.");

		return answer.toString();
	}

	/**
	 * 여행과 날씨가 함께 언급되어도, 실제 예보를 '요청'한 경우에만 DB/API를 조회한다.
	 * '비가 올까 봐 걱정돼'처럼 마음을 표현한 경우는 일반 대화로 넘긴다.
	 * 단, 날씨를 명시적으로 알려 달라거나 질문 형태로 물으면 기존 예보 기능을 유지한다.
	 */
	static boolean isTravelWeatherQuestion(String message) {
		String question = normalize(message);
		boolean mentionsWeather = contains(question,
				"날씨", "예보", "강수", "기온", "비올", "비오",
				"바람", "풍랑", "우천", "폭우")
				|| RAIN_SUBJECT.matcher(message == null ? "" : message).find();
		if (!question.contains("여행") || !mentionsWeather) {
			return false;
		}

		// '걱정돼'라는 말만으로 긴 날짜별 예보를 실행하면 공감 대화가 끊긴다.
		// 질문/조회/조정 의도가 명확하거나 짧은 '여행 날씨' 질의일 때만 도구를 사용한다.
		return contains(question,
				"알려", "조회", "확인", "어때", "어떨", "어떻게",
				"몇도", "강수확률", "추천", "대비", "대안",
				"조정", "변경", "바꿔", "바꾸")
				|| message.contains("?") || message.contains("？")
				|| question.endsWith("날씨") || question.endsWith("예보")
				|| question.endsWith("비올까") || question.endsWith("비가올까");
	}

	/**
	 * "비가 오면 다른 장소는?" 등 장소 후보를 구하는 질문을 구분한다.
	 * 단순 우천 대비 설명과 다르게 실제 장소 조회가 필요하다.
	 */
	private static boolean isAlternativePlaceRequest(String question) {
		return contains(question,
				"다른장소", "대체장소", "실내장소", "실내관광",
				"실내여행", "실내가볼", "장소추천", "갈만한곳")
				|| (contains(question, "장소", "어디", "가볼만한곳")
						&& contains(question, "대안", "대체", "실내", "추천", "다른", "비오"));
	}

	/** 우천에 대한 대비·대체 장소를 묻는 짧은 후속 질문만 허용한다. */
	static boolean isRainContingency(String message) {
		String question = normalize(message);
		if (question.isBlank() || question.length() > 120) {
			return false;
		}
		if (isAlternativePlaceRequest(question)) {
			return true;
		}
		// '준비가'가 들어 있다는 이유만으로 우천 후속 질문이라고 판단하지 않는다.
		boolean rainMention = RAIN_SUBJECT.matcher(message == null ? "" : message).find()
				|| contains(question, "비오", "비올", "비내", "우천", "소나기", "강수", "실내");
		return rainMention && contains(question,
				"혹시", "만약", "오면", "온다면", "내리면", "어떡",
				"대비", "대안", "조정", "바꿔", "바꾸", "준비", "그럼");
	}

	/**
	 * Kakao 검색 결과가 지역·시설 유형과 일치할 때만 실제 대체 장소로 표시한다.
	 * 권역 전체 검색이므로 여행 동선과의 거리·영업시간은 확정하지 않는다.
	 * 읽기 전용 검색이며 여행 일정이나 예약을 변경하지 않는다.
	 */
	private String suggestIndoorPlaces(String destination, String planName) {
		if (destination == null || destination.isBlank() || destination.length() > 30) {
			return "여행 목적지의 권역을 정확하게 확인하지 못했어요. "
					+ "실내 장소를 찾을 지역명을 알려주세요.";
		}

		String region = destination.endsWith("도") && destination.length() > 2
				? destination.substring(0, destination.length() - 1)
				: destination;

		// 한 번의 질문에 최대 4회만 외부 검색을 호출한다.
		// 특정 유형의 시설명까지 검색 결과에서 확인한 경우에만 후보로 채택한다.
		List<String> categories = List.of("박물관", "미술관", "과학관", "아쿠아리움");
		List<String> alternatives = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (String category : categories) {
			if (alternatives.size() >= 3) {
				break;
			}

			List<KakaoPlace> found;
			try {
				found = kakaoLocalClient.searchPlaces(region + " " + category, 5);
			} catch (RuntimeException exception) {
				// 하나의 검색 카테고리에서 장애가 발생해도 다른 유형의 검색은 계속한다.
				log.warn("AI 우천 대체 장소 검색 실패. category={}", category);
				continue;
			}

			if (found == null) {
				continue;
			}

			for (KakaoPlace candidate : found) {
				if (candidate == null) {
					continue;
				}
				String name = singleLine(candidate.placeName());
				String address = singleLine(candidate.addressName());
				if (!name.contains(category) || !address.contains(region)) {
					continue;
				}
				if (!seen.add(name + "|" + address)) {
					continue;
				}
				alternatives.add(name + " (주소: " + address + ")");
				break;
			}
		}

		if (alternatives.isEmpty()) {
			return planName + " 여행의 우천 대체 장소를 검색했지만, "
					+ "현재 검색 결과에서 위치가 확인된 실내 시설을 찾지 못했어요. "
					+ "구·동 등 더 좁은 지역명을 알려주시면 범위를 구체화할 수 있어요.";
		}

		StringBuilder answer = new StringBuilder(planName)
				.append(" 여행에서 비가 올 경우 검토할 실내 장소 후보입니다.")
				.append("\n카카오 장소 검색에서 확인한 ")
				.append(region)
				.append(" 권역의 시설입니다.");

		for (int index = 0; index < alternatives.size(); index++) {
			answer.append("\n")
					.append(index + 1)
					.append(". ")
					.append(alternatives.get(index));
		}

		return answer.append("\n실제 방문 전 운영시간·예약 가능 여부·"
				+ "현지 날씨·이동 경로를 확인해 주세요. "
				+ "가까운 순서로 정렬한 결과는 아닙니다. "
				+ "저장된 여행 일정은 변경하지 않았습니다.").toString();
	}

	/** 외부 장소명과 주소는 한 줄로 정리해 대화 응답에 표시한다. */
	private static String singleLine(String text) {
		if (text == null) {
			return "";
		}
		String clean = text.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
		return clean.length() > 100 ? clean.substring(0, 100) : clean;
	}

	/**
	 * 최근 사용자 대화에서 마지막 여행 날씨 질문만 복원한다.
	 * 중간에 무관한 발화가 있으면 이전 여행으로 돌아가지 않는다.
	 */
	static String recentTravelWeatherQuestion(
			String message, List<String> previousUserMessages
	) {
		if (!isRainContingency(message) || previousUserMessages == null) {
			return null;
		}
		int checked = 0;
		for (int i = previousUserMessages.size() - 1;
				i >= 0 && checked < 3; i--, checked++) {
			// 과거 발화도 원문 그대로 확인해야 '준비가' 같은 오탐을 방지한다.
			String previous = previousUserMessages.get(i);
			if (isTravelWeatherQuestion(previous)) {
				return normalize(previous);
			}
			if (!isRainContingency(previous)) {
				return null;
			}
		}
		return null;
	}

	/**
	 * 날짜별 강수확률·풍속으로 실내 대안 검토 여부를 제시한다.
	 * 아래 값은 서비스의 편의상 기준이며 공식 기상특보를 의미하지 않는다.
	 * 실제 장소/예약/동선을 변경하거나 존재하지 않는 POI를 제안하지 않는다.
	 */
	private static String adviceForDay(DailyWeatherSummary summary) {
		int rainProbability = summary.getRainProbability();
		double windSpeed = summary.getWindSpeed();

		if (windSpeed >= 14.0) {
			return "바람이 강할 수 있어 해안·고지대 등 노출된 야외 일정은 재검토하고 "
					+ "실내 활동으로 바꾸는 방안을 검토하세요.";
		}
		if (rainProbability >= 60 && windSpeed >= 9.0) {
			return "비와 바람 가능성이 함께 있어 야외 체험 대신 "
					+ "실내 관광·전시·식사 일정을 우선 검토하세요.";
		}
		if (rainProbability >= 60) {
			return "비가 올 가능성이 있어 야외 방문에 앞서 "
					+ "실내 관광·체험 대안을 준비하세요.";
		}
		if (windSpeed >= 9.0) {
			return "바람이 강할 수 있으므로 노출된 야외 활동 시간을 줄이고 "
					+ "실내 또는 바람을 피할 수 있는 일정을 검토하세요.";
		}

		return "현재 조회된 강수확률과 풍속만으로는 큰 변경 필요성이 "
				+ "두드러지지 않습니다. 출발 전에 예보를 다시 확인하세요.";
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