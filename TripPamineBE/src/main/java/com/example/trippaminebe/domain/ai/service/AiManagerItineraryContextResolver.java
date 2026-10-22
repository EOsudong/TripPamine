package com.example.trippaminebe.domain.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STEP 7.2: 최근 발화를 오래된 순서부터 재생하여 각 조건을 갱신한다.
 * DB 조회·외부 API·전역 캐시 없이 순수하게 현재 요청 이력에서 상태를 계산한다.
 */
final class AiManagerItineraryContextResolver {

    private static final Pattern DAY = Pattern.compile("([1-9]\\d?)일차");
    private static final Pattern ORDER = Pattern.compile("([1-9]\\d?)번째");
    private static final int MAX_HISTORY = 12;

    private AiManagerItineraryContextResolver() {
    }

    static AiManagerItineraryContextState resolve(String message, List<String> history) {
        return apply(message, resolvePrevious(history));
    }

    static AiManagerItineraryContextState resolvePrevious(List<String> history) {
        AiManagerItineraryContextState state = null;
        if (history != null) {
            int start = Math.max(0, history.size() - MAX_HISTORY);
            for (int index = start; index < history.size(); index++) {
                // 관련 없는 주제는 경계로 처리한다. 오래된 여행을 다른 주제에 끌어오지 않는다.
                state = apply(history.get(index), state);
            }
        }
        return state;
    }

    private static AiManagerItineraryContextState apply(
            String message, AiManagerItineraryContextState previous
    ) {
        String q = normalize(message);
        if (q.isBlank() || q.length() > 160 || isOtherDomain(q)) {
            return null;
        }

        Integer newDay = parseDay(q);
        Integer newOrder = parseOrder(q);
        boolean bareDay = q.matches("(?:[1-9]\\d?일차|첫날|둘째날)[?？]?");
        boolean wantsNext = contains(q, "그다음", "그다음장소", "다음장소", "다음방문지", "다음곳");
        boolean wantsPrevious = contains(q, "그전", "그이전", "이전장소", "앞장소");
        boolean ref = isReference(q);
        boolean direct = isDirect(q, newDay, newOrder);
        boolean followup = previous != null && (bareDay || ref || newOrder != null
                || (newDay != null && q.length() <= 45));
        if (!direct && !followup) {
            return null;
        }

        boolean currentPlan = q.contains("여행")
                && (TravelPoiResolverService.knownRegion(q) != null
                        || contains(q, "일차", "장소", "방문", "일정"));
        String oldRegion = previous == null || previous.planQuestion() == null
                ? null : TravelPoiResolverService.knownRegion(previous.planQuestion());
        String newRegion = TravelPoiResolverService.knownRegion(q);
        boolean changedTrip = previous != null && currentPlan && newRegion != null
                && oldRegion != null && !newRegion.equals(oldRegion);

        // 명시적인 다른 여행지로 변경되면 이전 여행의 일차·순서를 상속하지 않는다.
        AiManagerItineraryContextState base = changedTrip ? null : previous;
        boolean wholeDay = contains(q, "전체일정", "전체방문지", "모든장소", "일정전체", "일정목록");
        String planQuestion = currentPlan ? q : base == null ? null : base.planQuestion();
        Integer day = newDay != null ? newDay : base == null ? null : base.day();
        Integer sequence = newOrder != null ? newOrder : base == null ? null : base.sequence();
        String namedQuestion = base == null ? null : base.namedQuestion();
        int relativeOffset = base == null ? 0 : base.relativeOffset();

        // 새 일차 전체 조회는 기존의 특정 장소 선택을 해제한다.
        if (wholeDay) {
            sequence = null;
            namedQuestion = null;
            relativeOffset = 0;
        }
        // '3번째' 등 방문 순서를 명시하면 과거 특정 장소 이름을 제거한다.
        if (newOrder != null || newDay != null) {
            namedQuestion = null;
            relativeOffset = 0;
        }
        // 이전에 명시한 장소명을 후속 대명사 질문에서도 사용할 수 있게 기록한다.
        // "그 장소" 같은 대명사는 새로운 장소명으로 취급하지 않는다.
        if (newDay == null && newOrder == null && !wholeDay
                && !wantsNext && !wantsPrevious
                && !contains(q, "그장소", "그곳", "거기", "그다음", "그전")
                && contains(q, "몇시", "방문시간", "앞뒤", "전후")) {
            namedQuestion = q;
            relativeOffset = 0;
        }
        if (wantsNext || wantsPrevious) {
            // 기준 장소가 없으면 이동시킬 수 없으므로 null을 유지한다.
            if (sequence != null) {
                sequence = wantsNext ? sequence + 1 : Math.max(0, sequence - 1);
            }
            if (namedQuestion != null) {
                relativeOffset += wantsNext ? 1 : -1;
            }
        }

        AiManagerItineraryContextState.RequestedField field =
                AiManagerItineraryContextState.RequestedField.PLACE_DETAIL;
        if (contains(q, "얼마나걸", "몇분걸", "이동시간", "소요시간")) {
            field = AiManagerItineraryContextState.RequestedField.MOVEMENT_TIME;
        } else if (contains(q, "좌표", "위치", "지도")) {
            field = AiManagerItineraryContextState.RequestedField.LOCATION;
        } else if (wantsNext) {
            field = AiManagerItineraryContextState.RequestedField.NEXT;
        } else if (wantsPrevious) {
            field = AiManagerItineraryContextState.RequestedField.PREVIOUS;
        } else if (contains(q, "앞뒤", "전후")) {
            field = AiManagerItineraryContextState.RequestedField.NEIGHBORS;
        } else if (wholeDay) {
            field = AiManagerItineraryContextState.RequestedField.DAY_OVERVIEW;
        } else if (isTimeQuestion(q)) {
            field = AiManagerItineraryContextState.RequestedField.VISIT_TIME;
        }
        return new AiManagerItineraryContextState(null, planQuestion, day, sequence,
                namedQuestion, relativeOffset, field, newDay != null, newOrder != null, currentPlan);
    }

    /** 저장된 장소명 직접 언급은 DB 로딩 후 검사하므로 후보 문장을 별도로 보존한다. */
    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    static boolean isTimeQuestion(String q) {
        return contains(q, "몇시", "방문시간", "시간은", "언제가")
                && !contains(q, "앞뒤", "전후", "그다음", "이전", "다음");
    }

    private static boolean isOtherDomain(String q) {
        return contains(q, "날씨", "강수", "비가", "비오", "우천", "퀘스트", "마스코트",
                "포인트", "예산", "가계부", "금융", "예약해줘", "삭제해줘", "취소해줘")
                || contains(q, "지금몇시", "현재시간");
    }

    private static boolean isReference(String q) {
        return contains(q, "그장소", "그곳", "거기는", "거기", "몇시", "앞뒤", "그전", "그다음",
                "다음장소", "이전장소", "그럼언제", "소요시간", "이동시간");
    }

    private static boolean isDirect(String q, Integer day, Integer order) {
        return (day != null && contains(q, "여행", "일정", "장소", "방문", "어디", "번째", "순서"))
                || (order != null && contains(q, "장소", "방문", "몇시", "여행"))
                || contains(q, "전체일정", "일정전체", "전체방문지", "방문지목록", "장소목록", "앞뒤", "전후")
                || (q.contains("여행") && contains(q, "몇시에가", "몇시에방문"));
    }

    private static Integer parseDay(String q) {
        Matcher matcher = DAY.matcher(q);
        if (matcher.find()) return Integer.valueOf(matcher.group(1));
        if (q.contains("첫날")) return 1;
        if (q.contains("둘째날")) return 2;
        return null;
    }

    private static Integer parseOrder(String q) {
        Matcher matcher = ORDER.matcher(q);
        if (matcher.find()) return Integer.valueOf(matcher.group(1));
        if (q.contains("첫번째")) return 1;
        if (q.contains("두번째")) return 2;
        if (q.contains("세번째")) return 3;
        return null;
    }

    private static boolean contains(String q, String... words) {
        for (String word : words) if (q.contains(word)) return true;
        return false;
    }
}
