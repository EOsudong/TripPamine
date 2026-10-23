package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse.TravelItem;
import com.example.trippaminebe.domain.ai.dto.NearbyCategoryAlternativesResponse;
import com.example.trippaminebe.domain.ai.dto.NearbyTourPartnerResponse;
import com.example.trippaminebe.domain.ai.dto.TravelItinerarySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사용자의 '그 장소 근처에 비를 피할 곳은?' 요청을
 * 최근 여행 대화 문맥과 연결한 뒤 실제 일정의 좌표 기반 검색으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerNearbyIndoorAnswerService {

    private static final int DEFAULT_RADIUS_METERS = 1500;
    // 요청당 광범위한 DB 순회(N+1 조회)를 방지한다. 초과 시 사용자에게 여행 선택 요청.
    private static final int MAX_PLAN_LOOKUPS = 20;
    private static final Pattern PREVIOUS_CANDIDATE = Pattern.compile(
            "(?m)^\\d+\\.\\s+(.+?)\\s+·\\s+직선거리\\s+약\\s+\\d+m");

    private final AiTravelOverviewService travelOverviewService;
    private final TravelItineraryContextService itineraryContextService;
    private final TravelNearbyCategoryPoiService nearbyCategoryService;
    private final TourPartnerDiscoveryService tourPartnerDiscoveryService;
    private final AccommodationPartnerDiscoveryService accommodationPartnerDiscoveryService;

    public String findAnswer(Long userId, String message, List<String> previousUserMessages,
                             List<String> previousAssistantMessages) {
        String q = normalize(message);
        // 근처/주변 질문 또는 직전 근처 추천을 이어받은 "다른 곳 더"만 처리한다.
        boolean followup = contains(q, "다른곳더", "다른곳은", "다른곳없", "더추천", "추가추천")
                && previousUserMessages != null
                && previousUserMessages.stream().anyMatch(previousMessage ->
                        isNearbyRequest(normalize(previousMessage)));
        // 숙소 조건을 되묻고 사용자가 날짜·성인 수만 답한 경우 직전 숙소 검색을 승계한다.
        // 과거의 무관한 숙소 질문까지 잡지 않도록 마지막 사용자 메시지만 확인한다.
        String lastUserQuestion = previousUserMessages == null || previousUserMessages.isEmpty()
                ? "" : normalize(previousUserMessages.get(previousUserMessages.size() - 1));
        boolean lodgingConditionFollowup = q.matches(".*\\d{4}-\\d{2}-\\d{2}.*")
                && isNearbyRequest(lastUserQuestion)
                && contains(lastUserQuestion, "숙소", "숙박", "호텔", "리조트");
        if (!isNearbyRequest(q) && !followup && !lodgingConditionFollowup) {
            return null;
        }
        List<TravelItem> plans = travelOverviewService.getOverview(userId).plans();
        if (plans == null || plans.isEmpty()) {
            return "저장된 여행 계획이 없습니다. 여행 플래너에서 일정을 먼저 등록해 주세요.";
        }
        // STEP 8.5-A: 여행 제목을 말하지 않아도 실제 저장된 방문지로 여행을 찾는다.
        // 먼저 로그인 사용자의 여행 목록만 사용하고, 일정 자체도 소유권 검증 후 읽는다.
        String explicitPlaceName = extractExplicitPlaceName(q);
        Integer explicitDay = KoreanVisitOrderParser.day(q);
        Map<Long, TravelItinerarySnapshot> loaded = new HashMap<>();
        List<TravelItem> matches = matchPlans(plans, q);
        AiManagerItineraryContextState previous =
                AiManagerItineraryContextResolver.resolvePrevious(previousUserMessages);

        if (explicitPlaceName != null) {
            // 여행명을 지정했다면 그 여행 안에서만 찾는다. 다른 여행으로 몰래 이동하지 않는다.
            List<TravelItem> searchScope = matches.isEmpty() ? plans : matches;
            if (matches.isEmpty() && q.contains("여행")
                    && TravelPoiResolverService.knownRegion(q) != null) {
                return "질문에서 지정한 여행을 찾지 못했어요. 저장된 여행 이름을 확인해 주세요.";
            }
            if (searchScope.size() > MAX_PLAN_LOOKUPS) {
                return "저장된 여행이 많아 방문지 이름만으로 확정하기 어렵습니다. "
                        + "여행 이름을 함께 알려주세요.";
            }
            matches = plansContainingPlace(userId, searchScope, explicitPlaceName, explicitDay, loaded);
            if (matches.isEmpty()) {
                return "질문에서 지정한 '" + explicitPlaceName + "' 장소를 저장된 여행 일정에서 "
                        + "찾지 못했어요. 여행 이름·일차·방문지를 확인해 주세요. "
                        + "다른 장소를 임의로 기준점으로 삼지 않았습니다.";
            }
            if (matches.size() > 1) {
                TravelItem recent = findRecentPlan(userId, plans, previousUserMessages, loaded);
                if (recent != null && matches.stream().anyMatch(p -> p.planId().equals(recent.planId()))) {
                    matches = List.of(recent);
                }
            }
        } else if (matches.isEmpty()) {
            // "그 장소 근처", "다른 곳 더"처럼 대명사만 있을 때 최근 명시 장소를 사용한다.
            TravelItem recent = findRecentPlan(userId, plans, previousUserMessages, loaded);
            if (recent != null) {
                matches = List.of(recent);
            } else if (previous != null && previous.planQuestion() != null) {
                matches = matchPlans(plans, previous.planQuestion());
            }
        }
        if (matches.isEmpty() && explicitPlaceName == null && plans.size() == 1) {
            matches = plans;
        }
        if (matches.size() != 1) {
            return "해당 방문지가 여러 여행에 있거나 기준 여행을 특정하지 못했어요. "
                    + "여행 이름과 일차를 함께 알려주세요.";
        }
        Long planId = matches.get(0).planId();
        TravelItinerarySnapshot snapshot;
        try {
            snapshot = loaded.containsKey(planId)
                    ? loaded.get(planId) : itineraryContextService.load(userId, planId);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return "해당 여행에는 저장된 AI 추천 일정이 없습니다.";
            }
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                return "저장된 여행 일정의 형식이 올바르지 않아 주변 장소를 조회할 수 없습니다.";
            }
            throw exception;
        }
        Integer day = explicitDay;
        Integer sequence = KoreanVisitOrderParser.sequence(q);
        Integer previousDay = previous == null ? null : previous.day();
        Integer previousSequence = previous == null ? null : previous.sequence();
        if (day == null) {
            day = previousDay;
        }
        if (sequence == null) {
            sequence = previousSequence;
        }

        // 질문에 장소명이 직접 있는 경우 이전에 선택된 방문 순서보다 반드시 우선한다.
        // 이름이 일정에 없다면 과거 순서로 되돌아가지 않고 재확인을 요청한다.
        // 직전 질문에서 장소명을 직접 언급한 경우 "그 장소"를 동일 방문지로 복원한다.
        // 새로운 장소가 명시되면 항상 현재 질문을 우선한다.
        String referencePlaceName = explicitPlaceName != null ? explicitPlaceName
                : sequence == null ? recentPlaceName(previousUserMessages, snapshot) : null;
        if (referencePlaceName != null) {
            List<Target> namedPlaces = new ArrayList<>();
            for (TravelItinerarySnapshot.Day oneDay : snapshot.days()) {
                for (TravelItinerarySnapshot.Place place : oneDay.places()) {
                    if (normalize(place.name()).equals(referencePlaceName)) {
                        namedPlaces.add(new Target(oneDay.day(), place.sequence()));
                    }
                }
            }
            if (explicitDay != null) {
                namedPlaces = namedPlaces.stream()
                        .filter(target -> target.day() == explicitDay).toList();
            } else if (day != null) {
                final int preferredDay = day;
                List<Target> sameDay = namedPlaces.stream()
                        .filter(target -> target.day() == preferredDay).toList();
                if (!sameDay.isEmpty()) namedPlaces = sameDay;
            }
            if (namedPlaces.isEmpty()) {
                return "질문에서 지정한 '" + referencePlaceName + "' 장소를 저장된 여행 일정에서 "
                        + "찾지 못했어요. 일차와 방문지 이름을 확인해 주세요. "
                        + "다른 장소를 임의로 기준점으로 삼지 않았습니다.";
            }
            if (namedPlaces.size() != 1) {
                return "같은 이름의 방문지가 여러 곳입니다. 일차와 방문 순서를 함께 알려주세요.";
            }
            day = namedPlaces.get(0).day();
            sequence = namedPlaces.get(0).sequence();
        }
        if (day == null || sequence == null) {
            return "주변 장소를 검색할 기준 방문지가 필요해요. "
                    + "예: 서울 여행 1일차 3번째 장소 근처에 비를 피할 곳은?";
        }
        // 이전 AI 답변에서 동일한 기준 방문지에 제공했던 장소명만 추출한다.
        // "다른 장소"가 아니라 단순 재조회라면 기존 순위대로 보여준다.
        TravelItinerarySnapshot.Place selectedPlace = findSelectedPlace(snapshot, day, sequence);
        boolean wantsOther = contains(q, "다른", "추가로", "또다른", "그외", "더추천", "더없");
        Set<String> excluded = wantsOther && selectedPlace != null
                ? previouslyShownNames(previousAssistantMessages, day, sequence, selectedPlace.name())
                : Set.of();
        // 이번 질문에 특정 분류가 없을 때에만 직전 주변 검색 목적을 승계한다.
        TravelNearbyCategoryPoiService.Intent intent = TravelNearbyCategoryPoiService.intentFor(q);
        boolean lodgingRequest = lodgingConditionFollowup
                || contains(q, "숙소", "숙박", "호텔", "리조트");
        // 숙소는 체크인·체크아웃과 성인 인원이 필요하므로 일반 카테고리 검색과 분리한다.
        // 상품 자체의 좌표가 제공되지 않아 '경복궁 도보권 숙소'로 오인시키지 않는다.
        if (lodgingRequest) {
            return accommodationPartnerDiscoveryService.findAnswer(
                    userId, planId, day, sequence, message);
        }
        boolean tourRequest = contains(q, "투어", "티켓", "입장권", "관광공사", "제휴");
        // 상품을 직접 물어보는 경우에는 지도 후보와 관광정보를 길게 나열하지 않는다.
        // 방문지 검증/여행 소유권 검사는 기존 탐색 및 상품 서비스에서 유지한다.
        boolean productOnly = contains(q, "투어", "티켓", "입장권", "제휴")
                && !contains(q, "관광지", "명소", "볼거리", "문화시설", "관광공사");
        if (productOnly) {
            NearbyTourPartnerResponse offers = tourPartnerDiscoveryService.suggest(
                    userId, planId, day, sequence, DEFAULT_RADIUS_METERS, true, Set.of());
            return formatProductOffers(offers);
        }
        if (intent == TravelNearbyCategoryPoiService.Intent.GENERAL && tourRequest) {
            intent = TravelNearbyCategoryPoiService.Intent.TOURISM;
        }
        if (intent == TravelNearbyCategoryPoiService.Intent.GENERAL && previousUserMessages != null) {
            for (int index = previousUserMessages.size() - 1; index >= 0; index--) {
                String earlier = normalize(previousUserMessages.get(index));
                if (isNearbyRequest(earlier)) {
                    intent = TravelNearbyCategoryPoiService.intentFor(earlier);
                    break;
                }
            }
        }
        NearbyCategoryAlternativesResponse result;
        try {
            result = nearbyCategoryService.suggest(
                    userId, planId, day, sequence, DEFAULT_RADIUS_METERS, intent, excluded);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                return "지정한 일차나 방문 순서에 해당하는 장소가 없습니다. 저장된 일정을 확인해 주세요.";
            }
            throw exception;
        }
        String answer = format(result, !excluded.isEmpty());
        // 관광 의도 또는 투어/티켓 요청일 때만 관광공사 캐시를 함께 보여준다.
        // 제휴 검색은 상품 의도를 명시했을 때만 실행하고 제휴 링크를 자동 발급하지 않는다.
        if (!result.status().startsWith("REFERENCE_")) {
            if (intent == TravelNearbyCategoryPoiService.Intent.TOURISM || tourRequest) {
                boolean includePartner = contains(q, "마이리얼트립", "제휴", "투어", "티켓", "입장권");
                Set<String> suppress = new HashSet<>(excluded);
                for (NearbyCategoryAlternativesResponse.Candidate candidate : result.candidates()) {
                    suppress.add(candidate.name());
                }
                NearbyTourPartnerResponse extra = tourPartnerDiscoveryService.suggest(
                        userId, planId, day, sequence, DEFAULT_RADIUS_METERS,
                        includePartner, suppress);
                answer += formatTourPartner(extra);
            }
        }
        return answer;
    }

    /**
     * 관광정보와 판매 상품은 분리한다. 사용자가 필요하지 않은 외부 식별자는 숨긴다.
     * 상품 링크는 제휴사에서 받은 공식 URL만 사용하고 클릭 시 FE가 MyLink를 요청한다.
     */
    static String formatTourPartner(NearbyTourPartnerResponse extra) {
        if (!"VERIFIED".equals(extra.referenceStatus())) return "";
        StringBuilder text = new StringBuilder("\n\n[한국관광공사 추천 관광지]");
        if (extra.tourPlaces().isEmpty()) {
            text.append("\n현재 확보된 관광정보에서 가까운 장소를 찾지 못했어요.");
        } else {
            for (int i = 0; i < extra.tourPlaces().size(); i++) {
                NearbyTourPartnerResponse.TourPlace p = extra.tourPlaces().get(i);
                text.append("\n").append(i + 1).append(". ").append(p.title())
                        .append(" · 직선거리 약 ").append(p.straightLineMeters()).append("m")
                        .append(" · ").append(p.address() == null ? "주소 미제공" : p.address());
            }
        }
        text.append("\n관광정보는 위치 참고용이며 운영시간과 입장 가능 여부는 확인되지 않았습니다.");
        if (!"NOT_REQUESTED".equals(extra.partnerStatus())) {
            text.append("\n\n[관련 투어·티켓 상품]");
            if (extra.partnerProducts().isEmpty()) {
                text.append("\n현재 연결 가능한 관련 상품이 없어요.");
            } else {
                for (int i = 0; i < extra.partnerProducts().size(); i++) {
                    NearbyTourPartnerResponse.PartnerProduct p = extra.partnerProducts().get(i);
                    text.append("\n").append(i + 1).append(". ").append(p.itemName())
                            .append(" · 조회 판매가 ")
                            .append(java.text.NumberFormat.getNumberInstance(Locale.KOREA).format(p.salePrice()))
                            .append("원 · [상품 보기](").append(p.productUrl()).append(")");
                }
            }
            text.append("\n관련 투어 상품은 해당 장소 입장권이 아닐 수도 있습니다. ")
                    .append("표시가는 조회 시점 기준이며 옵션·재고·결제 금액은 예약 단계에서 달라질 수 있습니다. ")
                    .append("제휴 링크를 통해 확인하고 예약이 가능합니다.");
        }
        return text.toString();
    }

    /** 투어·티켓 가격 질문에는 상품명, 조회 가격, 클릭 가능한 상품 링크만 보여준다. */
    static String formatProductOffers(NearbyTourPartnerResponse offers) {
        if (!"VERIFIED".equals(offers.referenceStatus())) {
            return "방문지의 정확한 위치를 확인하지 못해 관련 상품을 검색할 수 없어요.";
        }
        StringBuilder text = new StringBuilder("[").append(offers.referenceName())
                .append(" 관련 투어·티켓]");
        if (offers.partnerProducts().isEmpty()) {
            text.append("\n현재 연결 가능한 투어·티켓 상품을 찾지 못했어요.");
        } else {
            for (int i = 0; i < offers.partnerProducts().size(); i++) {
                NearbyTourPartnerResponse.PartnerProduct product = offers.partnerProducts().get(i);
                text.append("\n").append(i + 1).append(". ").append(product.itemName())
                        .append(" · 조회 가격 ")
                        .append(java.text.NumberFormat.getNumberInstance(Locale.KOREA)
                                .format(product.salePrice()))
                        .append("원 · [상품 보기](").append(product.productUrl()).append(")");
            }
        }
        text.append("\n관련 체험·가이드 상품은 해당 시설의 입장권이 아닐 수 있으며, ")
                .append("최종 금액과 이용 가능 여부는 상품 상세에서 확인해 주세요.");
        return text.toString();
    }

    private static String format(NearbyCategoryAlternativesResponse result, boolean excludesPrevious) {
        if ("REFERENCE_AMBIGUOUS".equals(result.status())) {
            return "'" + result.referenceName() + "'은(는) 여러 지점을 가리켜 기준 좌표를 "
                    + "확정하지 않았습니다. 여의도한강공원·뚝섬한강공원처럼 방문 지점을 지정해 주세요. "
                    + "저장된 여행 일정은 변경하지 않았습니다.";
        }
        if (result.status().startsWith("REFERENCE_")) {
            return "'" + result.referenceName() + "'의 정확한 좌표를 확인하지 못했습니다. "
                    + "다른 장소를 임의로 기준으로 삼지 않았습니다.";
        }
        if (result.candidates().isEmpty()) {
            return "'" + result.referenceName() + "' 기준 반경 " + result.radiusMeters() + "m에서 "
                    + ("API_UNAVAILABLE".equals(result.status())
                        ? "카테고리 검색 서비스를 이용할 수 없습니다."
                        : excludesPrevious ? "앞서 보여드린 후보 외의 장소를 찾지 못했습니다."
                        : "해당 조건의 장소 후보를 찾지 못했습니다.")
                    + " 다른 카테고리 또는 검색 반경 확장을 요청해 주세요.";
        }
        StringBuilder answer = new StringBuilder()
                .append(result.day()).append("일차 ").append(result.sequence())
                .append("번째 '").append(result.referenceName())
                .append("' 기준 반경 ").append(result.radiusMeters())
                .append(excludesPrevious ? "m 안의 이전 추천을 제외한 추가 장소 후보입니다."
                        : "m 안의 주변 장소 후보입니다.");
        for (int index = 0; index < result.candidates().size(); index++) {
            NearbyCategoryAlternativesResponse.Candidate place = result.candidates().get(index);
            // 장소명을 직선거리 앞에 유지해 STEP 8.1의 이전 추천 제외 추출기를 재사용한다.
            answer.append("\n").append(index + 1).append(". ").append(place.name())
                    .append(" · 직선거리 약 ").append(place.straightLineMeters()).append("m")
                    .append(" · [").append(place.categoryLabel()).append("] ").append(place.address());
        }
        if ("PARTIAL".equals(result.status())) {
            answer.append("\n일부 카테고리 검색에 실패해 후보가 누락됐을 수 있습니다.");
        }
        answer.append("\n실내 이용 가능 여부·영업시간·일반 방문 가능성은 확인되지 않았습니다. ")
                .append("거리는 직선거리이며 도보/차량 소요 시간은 아닙니다. ")
                .append("저장된 여행 일정은 변경하지 않았습니다.");
        return answer.toString();
    }

    /**
     * '서울 여행 2일차 한강 공원 주변'에서 명시적인 장소명 부분만 추출한다.
     * '그 장소', '1번째 장소'는 실제 이름이 아니므로 null로 처리한다.
     */
    static String extractExplicitPlaceName(String q) {
        int end = -1;
        for (String separator : List.of("근처", "주변", "가까운", "가까이")) {
            int index = q.indexOf(separator);
            if (index >= 0 && (end < 0 || index < end)) end = index;
        }
        if (end < 0) return null;
        String prefix = q.substring(0, end);
        // 여행 제목·일차·순서의 구간을 제거하여 방문지 명칭만 남긴다.
        if (prefix.contains("여행")) prefix = prefix.substring(prefix.lastIndexOf("여행") + 2);
        // "일일차 첫번째", "1일차 1번째"와 같은 표기를 공통 규칙으로 제거한다.
        prefix = KoreanVisitOrderParser.removeLeadingDayAndOrder(prefix);
        String region = TravelPoiResolverService.knownRegion(prefix);
        if (region != null && prefix.startsWith(region)) prefix = prefix.substring(region.length());
        prefix = prefix.replaceFirst("^(?:그장소|그곳|해당장소|이장소)", "");
        prefix = prefix.replaceFirst("(?:장소|방문지|에서|의)$", "");
        if (prefix.isBlank() || contains(prefix, "어떤", "어느", "다른", "그거", "그럼")
                || List.of("그", "이", "여기", "거기", "그장소").contains(prefix)) {
            return null;
        }
        return prefix.length() <= 60 ? prefix : null;
    }

    private static TravelItinerarySnapshot.Place findSelectedPlace(
            TravelItinerarySnapshot snapshot, int day, int sequence) {
        return snapshot.days().stream().filter(d -> d.day() == day)
                .flatMap(d -> d.places().stream()).filter(p -> p.sequence() == sequence)
                .findFirst().orElse(null);
    }

    /** 사용자별 현재 대화에 남아 있는 실제 이전 AI 추천 응답만 제외 목록으로 사용한다. */
    private static Set<String> previouslyShownNames(
            List<String> replies, int day, int sequence, String referenceName) {
        if (replies == null || replies.isEmpty()) return Set.of();
        String heading = day + "일차 " + sequence + "번째 '" + referenceName + "' 기준 반경 ";
        Set<String> names = new HashSet<>();
        for (String reply : replies) {
            if (reply == null || !reply.startsWith(heading)) continue;
            Matcher matcher = PREVIOUS_CANDIDATE.matcher(reply);
            while (matcher.find()) names.add(normalize(matcher.group(1)));
        }
        return names;
    }

    /**
     * 실제 DB에 저장된 추천 방문지 이름을 기준으로 소유 여행 후보를 걸러낸다.
     * 외부 Kakao API를 사용하지 않고, 다른 사용자의 여행은 조회하지 않는다.
     */
    private List<TravelItem> plansContainingPlace(
            Long userId, List<TravelItem> plans, String placeName, Integer day,
            Map<Long, TravelItinerarySnapshot> loaded
    ) {
        List<TravelItem> matched = new ArrayList<>();
        for (TravelItem plan : plans) {
            TravelItinerarySnapshot snapshot = loadOwnedSnapshot(userId, plan.planId(), loaded);
            if (snapshot != null && containsPlace(snapshot, placeName, day)) {
                matched.add(plan);
            }
        }
        return matched;
    }

    /** 명시한 일차가 있다면 해당 날짜에서만 같은 장소 이름을 찾는다. */
    private static boolean containsPlace(
            TravelItinerarySnapshot snapshot, String placeName, Integer day
    ) {
        return snapshot.days().stream()
                .filter(d -> day == null || d.day() == day)
                .flatMap(d -> d.places().stream())
                .anyMatch(place -> normalize(place.name()).equals(placeName));
    }

    /** 추천 일정이 없는 여행은 후보에서만 제외한다. 인증 오류 등은 숨기지 않는다. */
    private TravelItinerarySnapshot loadOwnedSnapshot(
            Long userId, Long planId, Map<Long, TravelItinerarySnapshot> loaded
    ) {
        if (loaded.containsKey(planId)) return loaded.get(planId);
        try {
            TravelItinerarySnapshot snapshot = itineraryContextService.load(userId, planId);
            loaded.put(planId, snapshot);
            return snapshot;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    || exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                loaded.put(planId, null);
                return null;
            }
            throw exception;
        }
    }

    /**
     * 최근 대화에서 명시했던 여행 또는 실제 방문지를 역으로 탐색한다.
     * 애매하면 오래된 다른 여행을 임의 선택하지 않고 null을 반환한다.
     */
    private TravelItem findRecentPlan(
            Long userId, List<TravelItem> plans, List<String> history,
            Map<Long, TravelItinerarySnapshot> loaded
    ) {
        if (history == null || history.isEmpty()) return null;
        int start = Math.max(0, history.size() - 8);
        for (int i = history.size() - 1; i >= start; i--) {
            String oldQuestion = normalize(history.get(i));
            List<TravelItem> byPlan = matchPlans(plans, oldQuestion);
            if (!byPlan.isEmpty()) {
                return byPlan.size() == 1 ? byPlan.get(0) : null;
            }
            String named = extractExplicitPlaceName(oldQuestion);
            if (named == null) continue;
            if (plans.size() > MAX_PLAN_LOOKUPS) return null;
            List<TravelItem> byPlace = plansContainingPlace(
                    userId, plans, named, KoreanVisitOrderParser.day(oldQuestion), loaded);
            return byPlace.size() == 1 ? byPlace.get(0) : null;
        }
        return null;
    }

    /** "그 장소"의 근거가 되는 최근 명시 방문지 이름을 실제 일정과 대조한다. */
    private static String recentPlaceName(
            List<String> history, TravelItinerarySnapshot snapshot
    ) {
        if (history == null) return null;
        int start = Math.max(0, history.size() - 8);
        for (int i = history.size() - 1; i >= start; i--) {
            String value = extractExplicitPlaceName(normalize(history.get(i)));
            if (value == null) continue;
            return containsPlace(snapshot, value, KoreanVisitOrderParser.day(normalize(history.get(i))))
                    ? value : null;
        }
        return null;
    }

    private static List<TravelItem> matchPlans(List<TravelItem> plans, String q) {
        List<TravelItem> exact = plans.stream()
                .filter(plan -> plan.planName() != null && !normalize(plan.planName()).isBlank()
                        && q.contains(normalize(plan.planName()))).toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return plans.stream().filter(plan -> {
            String region = TravelPoiResolverService.knownRegion(plan.planName());
            return region != null && q.contains(normalize(region));
        }).toList();
    }

    private static boolean isNearbyRequest(String q) {
        return contains(q, "근처", "주변", "가까운", "가까이")
                && contains(q, "실내", "비", "우천", "대체", "장소", "곳", "박물관", "미술관",
                        "음식", "식당", "맛집", "밥", "카페", "커피", "관광", "명소", "전시",
                        "쉴", "휴식", "더", "어디", "추천", "볼거리",
                        "투어", "티켓", "입장권", "관광공사", "제휴", "숙소", "숙박", "호텔", "리조트");
    }

    private static boolean contains(String q, String... words) {
        for (String word : words) {
            if (q.contains(word)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record Target(int day, int sequence) {
    }
}
