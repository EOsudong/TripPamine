package com.example.trippaminebe.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * AI 매니저의 검증된 답변 기능을 하나의 우선순위 파이프라인으로 연결한다.
 *
 * <p>STEP 8.7-A에서는 기존 도메인 서비스의 판정·소유권·동의 검사를 그대로 사용한다.
 * 서비스가 null을 반환하면 다음 기능으로 넘기고, 하나라도 답하면 LLM 호출을 막는다.
 * 이 클래스에서는 외부 예약·결제·환불 등 쓰기 작업을 수행하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerUnifiedIntentRouter {

    /** 실행된 실제 검증 서비스의 종류. 미처리 질문은 GENERAL_CONVERSATION이다. */
    public enum Intent {
        ASSET_SAVINGS_PLAN,
        ASSET_OVERVIEW,
        FINANCE_SUMMARY,
        BOOKING_SUPPORT,
        TOUR_TICKET_SEARCH,
        ACCOMMODATION_SEARCH,
        NEARBY_PLACE_SEARCH,
        TRAVEL_WEATHER,
        CURRENT_WEATHER,
        ITINERARY_LOOKUP,
        COMPOSITE_QUERY,
        MASCOT_GUIDE,
        VERIFIED_INFORMATION,
        GAME_READ_INTENT,
        GAME_INFORMATION,
        GENERAL_CONVERSATION
    }

    /** 답변 본문은 기존 서비스가 작성한 원문을 보존한다. null이면 Qwen3로 넘긴다. */
    public record RoutingResult(Intent intent, String answer) {
        public boolean handled() {
            return answer != null;
        }
    }

    private final AiManagerTravelSavingsPlanAnswerService travelSavingsPlanAnswerService;
    private final AiManagerAssetOverviewAnswerService assetOverviewAnswerService;
    private final AiManagerFinanceAnswerService financeAnswerService;
    private final AiManagerTicketOfferAnswerService ticketOfferAnswerService;
    private final AiManagerNearbyIndoorAnswerService nearbyIndoorAnswerService;
    private final AiManagerTravelWeatherAnswerService travelWeatherAnswerService;
    private final AiManagerCurrentWeatherAnswerService currentWeatherAnswerService;
    private final AiManagerItineraryAnswerService itineraryAnswerService;
    private final AiManagerCompositeAnswerService compositeAnswerService;
    private final AiManagerVerifiedAnswerService verifiedAnswerService;
    private final AiManagerReadIntentRouter readIntentRouter;
    private final AiManagerGameAnswerService gameAnswerService;

    /**
     * 기존 서비스들의 실행 순서를 유지한다.
     * 금융 정보는 동의 검사를 우선하고, 티켓 사후 문의는 카탈로그보다 먼저 기존 서비스에서 분리한다.
     * 도메인별 세부 조건을 이 라우터에 복제하지 않아 판정 규칙이 어긋나는 것을 막는다.
     */
    public RoutingResult route(
            Long userId,
            String message,
            List<String> previousUserMessages,
            List<String> previousAssistantMessages,
            Double latitude,
            Double longitude
    ) {
        // 명시적인 자산·여행 목표 질문은 일반 월별 가계부 조회보다 먼저 처리한다.
        // 두 서비스 모두 별도의 금융 활용 동의 확인을 수행한다.
        // 목표 달성 시뮬레이션은 단순 자산 조회보다 먼저 판정해 질문을 가로채지 않도록 한다.
        // 금융 활용 동의와 본인 데이터 확인은 시뮬레이션 서비스가 직접 수행한다.
        String answer = travelSavingsPlanAnswerService.findAnswer(userId, message);
        if (answer != null) return result(Intent.ASSET_SAVINGS_PLAN, answer);

        answer = assetOverviewAnswerService.findAnswer(userId, message);
        if (answer != null) return result(Intent.ASSET_OVERVIEW, answer);

        answer = financeAnswerService.findAnswer(userId, message, previousUserMessages);
        if (answer != null) return result(Intent.FINANCE_SUMMARY, answer);

        // 독립 상품 검색과 환불·취소 안내 모두 기존 티켓 서비스가 안전하게 구분한다.
        // 새 상품 요청/환불과 더불어 직전 상품 중복 제외를 위해 현재 대화의 응답 이력도 전달한다.
        answer = ticketOfferAnswerService.findAnswer(message, previousUserMessages, previousAssistantMessages);
        if (answer != null) {
            return result(isPurchaseSupportText(message)
                    ? Intent.BOOKING_SUPPORT : Intent.TOUR_TICKET_SEARCH, answer);
        }

        // 일정 문맥과 실제 좌표 검증이 필요한 숙박/주변 검색은 동일한 기존 서비스에 위임한다.
        answer = nearbyIndoorAnswerService.findAnswer(
                userId, message, previousUserMessages, previousAssistantMessages);
        if (answer != null) {
            return result(isAccommodationText(message, previousUserMessages)
                    ? Intent.ACCOMMODATION_SEARCH : Intent.NEARBY_PLACE_SEARCH, answer);
        }

        answer = travelWeatherAnswerService.findAnswer(userId, message, previousUserMessages);
        if (answer != null) return result(Intent.TRAVEL_WEATHER, answer);

        // 좌표는 이 요청의 실시간 날씨 조회에만 전달하고 대화 기록/로그에는 넣지 않는다.
        answer = currentWeatherAnswerService.findAnswer(message, latitude, longitude);
        if (answer != null) return result(Intent.CURRENT_WEATHER, answer);

        answer = itineraryAnswerService.findAnswer(userId, message, previousUserMessages);
        if (answer != null) return result(Intent.ITINERARY_LOOKUP, answer);

        answer = compositeAnswerService.findAnswer(userId, message, previousUserMessages);
        if (answer != null) return result(Intent.COMPOSITE_QUERY, answer);

        answer = AiManagerFeatureCatalog.getVerifiedMascotDexGuide(message);
        if (answer != null) return result(Intent.MASCOT_GUIDE, answer);

        answer = verifiedAnswerService.findAnswer(userId, message, previousUserMessages);
        if (answer != null) return result(Intent.VERIFIED_INFORMATION, answer);

        answer = readIntentRouter.findAnswer(userId, message);
        if (answer != null) return result(Intent.GAME_READ_INTENT, answer);

        answer = gameAnswerService.findAnswer(userId, message);
        if (answer != null) return result(Intent.GAME_INFORMATION, answer);

        // 모든 검증 경로가 미처리한 경우에만 기존 자유 대화 경로를 사용한다.
        return new RoutingResult(Intent.GENERAL_CONVERSATION, null);
    }

    private static RoutingResult result(Intent intent, String answer) {
        return new RoutingResult(intent, answer);
    }

    /** 유형 표시는 메타데이터이며, 상품/환불을 처리할지 결정하는 원본 서비스 판정은 변경하지 않는다. */
    private static boolean isPurchaseSupportText(String message) {
        String text = normalize(message);
        return contains(text, "환불", "취소", "예약변경", "날짜변경", "시간변경", "일정변경", "예약바꾸");
    }

    /** 날짜·인원만 입력한 직후에도 숙박 안내 유형이 유지되도록 마지막 사용자 질문만 참고한다. */
    private static boolean isAccommodationText(String message, List<String> previousUserMessages) {
        String text = normalize(message);
        if (contains(text, "숙소", "숙박", "호텔", "리조트")) return true;
        if (!text.matches(".*\\d{4}-\\d{2}-\\d{2}.*") || previousUserMessages == null
                || previousUserMessages.isEmpty()) return false;
        String previous = normalize(previousUserMessages.get(previousUserMessages.size() - 1));
        return contains(previous, "숙소", "숙박", "호텔", "리조트");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static boolean contains(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) return true;
        }
        return false;
    }
}
