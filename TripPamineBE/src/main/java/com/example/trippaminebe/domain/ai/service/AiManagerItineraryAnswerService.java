package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse.TravelItem;
import com.example.trippaminebe.domain.ai.dto.TravelItinerarySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STEP 7: 여행 날짜·방문 순서·방문 시간에 관한 질문은 실제 추천 JSON으로 직접 답한다.
 * LLM의 추측을 통하지 않으며, 날씨/대체 POI 대화는 기존 경로로 남겨둔다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerItineraryAnswerService {

    private static final Pattern DAY_PATTERN = Pattern.compile("([1-9]\\d?)일차");
    private static final Pattern ORDER_PATTERN = Pattern.compile("([1-9]\\d?)번째");
    private final AiTravelOverviewService travelOverviewService;
    private final TravelItineraryContextService itineraryContextService;

    public String findAnswer(Long userId, String message, List<String> previousUserMessages) {
        String q = normalize(message);
        // 이전 사용자 질문의 일차·순서를 따로 복원한다.
        // 예: "1일차 3번째" → "그 장소는 몇 시?" → "2번째 장소는?"에서
        // 일차(1)는 유지하고 마지막으로 명시한 순서(2)만 교체한다.
        ItineraryContext context = latestItineraryContext(previousUserMessages);
        boolean direct = isItineraryQuestion(q);
        boolean followup = context != null && (
                isReferenceFollowup(q) || isBareDay(q) || isBareOrder(q));
        if (!direct && !followup) {
            return null;
        }

        List<TravelItem> plans = travelOverviewService.getOverview(userId).plans();
        if (plans == null || plans.isEmpty()) {
            return "현재 등록된 여행계획이 없습니다.";
        }

        // 현재 질문에 새 여행지/여행명이 명시되면 과거 여행 문맥보다 반드시 우선한다.
        List<TravelItem> matching = choosePlans(plans, q);
        if (matching.isEmpty()) {
            if (TravelPoiResolverService.knownRegion(q) != null) {
                return "요청한 지역과 일치하는 저장 여행을 찾지 못했어요. 여행 이름을 확인해 주세요.";
            }
            if (context != null && context.planQuestion() != null) {
                matching = choosePlans(plans, context.planQuestion());
            }
        }
        if (matching.isEmpty()) {
            // 이전 여행을 특정할 수 없는 경우에만 단일 여행을 선택한다.
            matching = plans.size() == 1 ? plans : List.of();
        }
        if (matching.size() != 1) {
            return "확인할 여행이 여러 건이거나 특정되지 않았어요. "
                    + "여행 이름과 일차를 함께 알려주세요. 예: 서울 여행 1일차 3번째 장소";
        }

        TravelItinerarySnapshot snapshot;
        try {
            snapshot = itineraryContextService.load(userId, matching.get(0).planId());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    && "이 여행에 저장된 AI 추천 일정이 없습니다.".equals(exception.getReason())) {
                return "이 여행에는 아직 저장된 AI 추천 일정이 없습니다. "
                        + "추천 페이지에서 일정을 먼저 생성해 주세요.";
            }
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                return "저장된 추천 일정의 날짜나 시간 형식이 올바르지 않아 "
                        + "현재 방문 순서를 안전하게 안내할 수 없습니다.";
            }
            throw exception;
        }

        Integer dayNo = parseDay(q);
        Integer sequence = parseOrder(q);
        if (context != null) {
            // 사용자 발화에 지정된 값은 절대 이전 값으로 덮어쓰지 않는다.
            if (dayNo == null && (followup || (direct && sequence != null))) {
                dayNo = context.day();
            }
            // "1일차"라는 짧은 답변은 직전의 "2번째 장소" 요청을 완성한다.
            // "2일차 전체 일정"처럼 새로운 전체 조회는 이전 순서를 이어받지 않는다.
            if (sequence == null && (isReferenceFollowup(q) || isBareDay(q))) {
                sequence = context.sequence();
            }
        }

        boolean locationQuestion = contains(q, "좌표", "위치", "지도");
        if (locationQuestion) {
            snapshot = itineraryContextService.withCoordinates(snapshot);
        }

        // 명시적인 장소명은 과거 방문 순서보다 우선한다.
        // 예: 3번째 장소를 본 다음 "삼청동 카페거리 몇 시야?"라고 물으면 2번째를 조회한다.
        List<MatchedPlace> named = findNamedPlaces(snapshot, q);
        if (named.isEmpty() && context != null && isReferenceFollowup(q)
                && context.namedQuestion() != null) {
            named = findNamedPlaces(snapshot, context.namedQuestion());
        }
        if (named.size() == 1) {
            MatchedPlace target = named.get(0);
            return formatPlace(snapshot, target.day(), target.index(), q, locationQuestion);
        }
        if (named.size() > 1) {
            // 같은 이름의 방문지가 여러 날짜에 있다면 일차로만 좁힌다.
            Integer selectedDay = dayNo;
            if (selectedDay != null) {
                List<MatchedPlace> inDay = named.stream()
                        .filter(item -> item.day().day() == selectedDay)
                        .toList();
                if (inDay.size() == 1) {
                    MatchedPlace target = inDay.get(0);
                    return formatPlace(snapshot, target.day(), target.index(), q, locationQuestion);
                }
            }
            return "같은 이름의 방문지가 여러 날짜에 있습니다. "
                    + "몇 일차인지 함께 알려주세요.";
        }

        // 이름이 명시되지 않았다면 일차와 방문 순서로 조회한다.
        if (dayNo != null && sequence != null) {
            return answerAtIndex(snapshot, dayNo, sequence, q, locationQuestion);
        }

        if (sequence != null) {
            return "몇 일차의 " + sequence + "번째 장소를 확인할까요?";
        }
        if (dayNo != null) {
            return formatDay(snapshot, dayNo);
        }
        if (contains(q, "방문지", "장소목록", "전체일정")) {
            StringBuilder result = new StringBuilder(snapshot.planName()).append(" 저장된 방문지 목록입니다.");
            for (TravelItinerarySnapshot.Day day : snapshot.days()) {
                result.append("\n\n").append(formatDay(snapshot, day.day()));
            }
            return result.toString();
        }
        return "확인할 일차 또는 장소를 알려주세요. 예: 1일차 3번째 장소는 몇 시야?";
    }

    private static String answerAtIndex(
            TravelItinerarySnapshot snapshot, int dayNo, int sequence,
            String question, boolean locationQuestion
    ) {
        for (TravelItinerarySnapshot.Day day : snapshot.days()) {
            if (day.day() == dayNo) {
                if (sequence > day.places().size()) {
                    return dayNo + "일차에는 " + sequence + "번째 방문지가 없습니다. "
                            + "저장된 방문지는 " + day.places().size() + "곳입니다.";
                }
                return formatPlace(snapshot, day, sequence - 1, question, locationQuestion);
            }
        }
        return dayNo + "일차에 저장된 추천 일정이 없습니다.";
    }

    private static String formatDay(TravelItinerarySnapshot snapshot, int dayNo) {
        for (TravelItinerarySnapshot.Day day : snapshot.days()) {
            if (day.day() != dayNo) continue;
            StringBuilder text = new StringBuilder(snapshot.planName())
                    .append(" · ").append(dayNo).append("일차 (")
                    .append(day.date()).append(") 방문 순서입니다.");
            for (TravelItinerarySnapshot.Place place : day.places()) {
                text.append("\n").append(place.sequence()).append(". ")
                        .append(place.name()).append(" · ").append(time(place));
            }
            return text.toString();
        }
        return dayNo + "일차에 저장된 추천 일정이 없습니다.";
    }

    private static String formatPlace(
            TravelItinerarySnapshot snapshot, TravelItinerarySnapshot.Day day,
            int index, String question, boolean locationQuestion
    ) {
        TravelItinerarySnapshot.Place place = day.places().get(index);
        // 후속 시간 질문에는 장소 개요를 반복하지 않고 실제 저장된 시간만 답한다.
        if (isTimeOnlyQuestion(question)) {
            return place.name() + "의 " + day.day() + "일차 방문 예정 시간은 "
                    + time(place) + "입니다. 실제 이동 시간·영업 여부는 아직 검증되지 않았습니다.";
        }
        StringBuilder text = new StringBuilder(snapshot.planName())
                .append(" · ").append(day.day()).append("일차 ")
                .append(place.sequence()).append("번째 방문지는 '")
                .append(place.name()).append("'입니다.")
                .append("\n날짜: ").append(day.date())
                .append("\n방문 예정: ").append(time(place))
                .append("\n설정 예상 비용: ")
                .append(place.estimatedCost() == null ? "미등록" : place.estimatedCost().toPlainString() + "원");

        if (contains(question, "앞뒤", "전후", "이전", "다음", "전장소", "다음장소")) {
            if (index > 0) {
                TravelItinerarySnapshot.Place before = day.places().get(index - 1);
                text.append("\n이전 방문지: ").append(before.name()).append(" (").append(time(before)).append(")");
            } else {
                text.append("\n이전 방문지: 없음 (해당 일차 첫 장소)");
            }
            if (index + 1 < day.places().size()) {
                TravelItinerarySnapshot.Place after = day.places().get(index + 1);
                text.append("\n다음 방문지: ").append(after.name()).append(" (").append(time(after)).append(")");
            } else {
                text.append("\n다음 방문지: 없음 (해당 일차 마지막 장소)");
            }
        }
        if (locationQuestion) {
            if ("VERIFIED".equals(place.locationStatus())) {
                text.append("\n카카오 확인 주소: ").append(place.address())
                        .append("\n확인 좌표: ").append(place.latitude()).append(", ").append(place.longitude());
            } else {
                text.append("\n정확한 위치는 검증되지 않았습니다. 상태: ")
                        .append(place.locationStatus());
            }
        }
        text.append("\n방문 시간은 저장된 추천 일정이며, 실제 이동 시간·영업 여부는 아직 검증되지 않았습니다.");
        return text.toString();
    }

    private static String time(TravelItinerarySnapshot.Place place) {
        return place.startTime() == null || place.endTime() == null
                ? "시간 미등록" : place.startTime() + " ~ " + place.endTime();
    }

    private static List<MatchedPlace> findNamedPlaces(TravelItinerarySnapshot snapshot, String q) {
        List<MatchedPlace> matched = new ArrayList<>();
        for (TravelItinerarySnapshot.Day day : snapshot.days()) {
            for (int i = 0; i < day.places().size(); i++) {
                String name = normalize(day.places().get(i).name());
                if (name.length() >= 2 && q.contains(name)) {
                    matched.add(new MatchedPlace(day, i));
                }
            }
        }
        return matched;
    }

    private static List<TravelItem> choosePlans(List<TravelItem> plans, String q) {
        // 정확한 여행명을 지정하면 같은 지역의 다른 여행보다 우선한다.
        List<TravelItem> exact = new ArrayList<>();
        for (TravelItem plan : plans) {
            String name = normalize(plan.planName());
            if (!name.isBlank() && q.contains(name)) {
                exact.add(plan);
            }
        }
        if (!exact.isEmpty()) {
            return exact;
        }

        List<TravelItem> regional = new ArrayList<>();
        for (TravelItem plan : plans) {
            String region = TravelPoiResolverService.knownRegion(plan.planName());
            if (region != null && q.contains(normalize(region))) {
                regional.add(plan);
            }
        }
        return regional;
    }

    /**
     * 최근 사용자 발화에서 여행·일차·방문 순서를 각각 복원한다.
     * 여러 발화를 하나의 문장처럼 합치지 않으므로 명시적으로 바뀐 순서가 유지된다.
     * 관련 없는 질문이 나오면 그 이전 대화를 참조하지 않는다.
     */
    private static ItineraryContext latestItineraryContext(List<String> history) {
        if (history == null || history.isEmpty()) return null;
        Integer day = null;
        Integer sequence = null;
        String planQuestion = null;
        String namedQuestion = null;
        boolean seen = false;

        // 채팅 요청에 담긴 최근 사용자 발화만 사용한다. 장기 기억이나 DB 변경은 없다.
        for (int i = history.size() - 1, checked = 0; i >= 0 && checked < 5; i--, checked++) {
            String previous = normalize(history.get(i));
            boolean related = isItineraryQuestion(previous)
                    || isReferenceFollowup(previous)
                    || isBareDay(previous)
                    || isBareOrder(previous);
            if (!related) break;

            seen = true;
            if (day == null) day = parseDay(previous);
            if (sequence == null) sequence = parseOrder(previous);
            if (planQuestion == null && (previous.contains("여행")
                    || TravelPoiResolverService.knownRegion(previous) != null)) {
                planQuestion = previous;
            }
            if (namedQuestion == null && isItineraryQuestion(previous)) {
                namedQuestion = previous;
            }
        }
        return seen ? new ItineraryContext(day, sequence, planQuestion, namedQuestion) : null;
    }

    /** 사용자가 짧은 "1일차"로 이전 질문의 누락 조건을 채우는 경우를 허용한다. */
    private static boolean isBareDay(String q) {
        return q.matches("[1-9]\\d?일차[?？]?")
                || q.matches("(첫날|둘째날)[?？]?");
    }

    /** "그럼 2번째는?"처럼 순서만 교체하는 발화를 허용한다. */
    private static boolean isBareOrder(String q) {
        return q.length() <= 35 && parseOrder(q) != null
                && contains(q, "번째", "순서")
                && !contains(q, "퀘스트", "포인트", "마스코트", "날씨");
    }

    private static boolean isTimeOnlyQuestion(String q) {
        return contains(q, "몇시", "방문시간", "시간은", "언제가")
                && !contains(q, "앞뒤", "전후", "이전", "다음");
    }

    private static boolean isItineraryQuestion(String q) {
        if (q.isBlank() || q.length() > 160) return false;
        return ((parseDay(q) != null || contains(q, "첫날", "둘째날"))
                    && contains(q, "여행", "일정", "장소", "방문", "어디", "번째", "몇시", "순서"))
                || (parseOrder(q) != null && contains(q, "장소", "방문지", "몇시", "여행"))
                || contains(q, "앞뒤", "전후", "전체방문지", "방문지목록", "장소목록")
                || (contains(q, "몇시에가", "몇시에방문") && contains(q, "여행", "장소"));
    }

    private static boolean isReferenceFollowup(String q) {
        if (q.length() > 100 || contains(q, "지금몇시", "현재시간", "오늘날씨")) {
            return false;
        }
        return contains(q,
                "그장소", "그곳", "거기는", "거기", "몇시", "앞뒤", "그전", "그다음");
    }

    private static Integer parseDay(String q) {
        Matcher matcher = DAY_PATTERN.matcher(q);
        if (matcher.find()) return Integer.valueOf(matcher.group(1));
        if (q.contains("첫날")) return 1;
        if (q.contains("둘째날")) return 2;
        return null;
    }

    private static Integer parseOrder(String q) {
        Matcher matcher = ORDER_PATTERN.matcher(q);
        if (matcher.find()) return Integer.valueOf(matcher.group(1));
        if (q.contains("첫번째")) return 1;
        if (q.contains("두번째")) return 2;
        if (q.contains("세번째")) return 3;
        return null;
    }

    private static boolean contains(String source, String... keywords) {
        for (String keyword : keywords) if (source.contains(keyword)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** 응답마다 최근 발화에서 재구성하는 읽기 전용 문맥. */
    private record ItineraryContext(
            Integer day, Integer sequence, String planQuestion, String namedQuestion
    ) {
    }

    private record MatchedPlace(TravelItinerarySnapshot.Day day, int index) {
    }
}
