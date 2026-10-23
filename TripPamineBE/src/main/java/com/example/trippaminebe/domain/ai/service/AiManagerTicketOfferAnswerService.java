package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.nego.dto.response.MyRealTripCatalogResponse;
import com.example.trippaminebe.domain.nego.service.MyRealTripCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일정·좌표가 없는 독립적인 투어/티켓 질문을 실제 상품 카탈로그로 연결한다.
 * 검색 결과가 없거나 외부 API가 실패해도 언어모델이 가격·판매 여부를 만들어내지 않도록 직접 답변한다.
 */
@Service
public class AiManagerTicketOfferAnswerService {

    private static final Logger log = LoggerFactory.getLogger(AiManagerTicketOfferAnswerService.class);
    private static final int MAX_PRODUCTS = 3;
    // 다음 상품 요청에서만 최대 두 페이지를 조회한다. 카탈로그 내부 캐시는 기존 3분 TTL을 사용한다.
    private static final int MAX_FOLLOWUP_PAGES = 2;
    private static final int MAX_RECENT_TICKET_REPLIES = 3;
    private static final Pattern OFFER_HEADER = Pattern.compile(
            "^\\[([\\p{IsHangul}A-Za-z0-9]{2,30}) 관련 투어·티켓\\](?:\\r?\\n|$)");
    private static final Pattern OFFER_LINK = Pattern.compile(
            "\\[상품 보기\\]\\((https://experiences\\.myrealtrip\\.com/products/[0-9]{1,20})\\)");
    private static final Pattern TARGET_AND_TYPE = Pattern.compile(
            "^(.{1,60}?)(?:관련|에관한|에서할수있는|에서하는|에서|의)?"
                    + "(?:투어|티켓|입장권|체험|액티비티|관광패스|패스)(?:.*)$");
    private static final Set<String> REGIONS = Set.of(
            "제주", "서울", "부산", "인천", "대구", "대전", "광주", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남");

    private final MyRealTripCatalogService catalogService;

    public AiManagerTicketOfferAnswerService(MyRealTripCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * '경복궁 관련 티켓', '제주관련 티켓'처럼 장소를 명시한 상품 질문만 처리한다.
     * 기존 '근처/주변' 일정 기반 POI 검색은 가로채지 않는다.
     * 취소·환불 문의는 절차만 안내하며 실제 주문 변경이나 취소는 수행하지 않는다.
     */
    public String findAnswer(String message) {
        return findAnswer(message, List.of());
    }

    /**
     * 티켓 검색 후의 환불·취소 질문은 상품 검색/LLM 답변과 분리한다.
     * 직전 사용자 발화만 참고해 무관한 과거 티켓 문맥으로 잘못 연결하지 않는다.
     */
    public String findAnswer(String message, List<String> previousUserMessages) {
        return findAnswer(message, previousUserMessages, List.of());
    }

    /**
     * 직전 상품 추천에서만 장소·검색어를 재사용한다.
     * 서버/클라이언트 대화 이력을 그대로 신뢰하지 않고, 실제 응답 헤더와 공식 URL 형식을 확인한다.
     */
    public String findAnswer(String message, List<String> previousUserMessages,
                             List<String> previousAssistantMessages) {
        String compact = normalizeQuestion(message);
        if (isPurchaseSupportRequest(compact, previousUserMessages)) {
            // 구매 내역/상품별 환불 정책을 조회하지 않았으므로 처리 완료나 환불 가능을 확정하지 않는다.
            return "티켓·투어의 취소·환불·예약 변경은 구매하신 곳의 예약·결제 내역에서 "
                    + "해당 상품의 규정과 가능 여부를 확인해야 해요. "
                    + "현재 AI 매니저는 실제 구매 내역을 확인하거나 취소·환불을 접수하지 않았습니다. "
                    + "어느 사이트에서 구매하셨는지 알려주시면 확인 방법을 안내할게요.";
        }
        // 다른 도메인의 새 질문으로 주제가 바뀌면 이전 티켓 추천을 절대 승계하지 않는다.
        boolean askingMore = isMoreOfferRequest(compact);
        PreviousTicketContext previous = askingMore && !isOtherDomainRequest(compact)
                ? previousTicketContext(previousUserMessages, previousAssistantMessages) : null;
        if (!containsAny(compact, "티켓", "입장권", "투어", "체험", "액티비티", "관광패스")
                && previous == null) {
            return null;
        }
        if (containsAny(compact, "근처", "주변", "가까운", "가까이", "일차", "번째", "첫날",
                "환불", "취소", "변경", "결제", "항공", "비행기", "기차", "ktx", "열차", "고속버스")) {
            return null;
        }
        // 과거 여행 경험을 말하는 일반 대화에는 제휴 상품 API를 호출하지 않는다.
        if (!containsAny(compact, "있어", "있나", "보여", "추천", "찾아", "알려", "가격", "얼마",
                "검색", "목록", "소개", "구매", "예약", "주세요", "궁금", "뭐가", "어떤")
                && !containsAnySuffix(compact, "티켓", "입장권", "투어", "체험", "액티비티")
                && !(askingMore && previous != null)) {
            return null;
        }

        String keyword = extractKeyword(compact);
        if (keyword == null && previous != null) {
            keyword = previous.keyword();
        }
        if (keyword == null) {
            return "어느 지역이나 관광지의 투어·티켓을 찾으시나요? 예: 경복궁 관련 티켓, 제주 체험 추천";
        }
        // 앞서 실제로 보여준 공식 상품 링크만 제외한다. 검색어가 달라지면 새 검색으로 시작한다.
        Set<String> excludedUrls = previous != null && keyword.equals(previous.keyword())
                ? previous.productUrls() : Set.of();
        // 검색어 한 개당 1회 호출한다. 기존 카탈로그 서비스의 3분 공통 캐시를 재사용한다.
        try {
            MyRealTripCatalogResponse catalog = catalogService.searchTours(keyword);
            boolean ticketRequested = containsAny(compact, "입장권", "티켓", "관광패스");
            String key = normalizeName(keyword);
            List<MyRealTripCatalogResponse.Product> candidates = new ArrayList<>(catalog.items());
            boolean additionalPageFailed = false;
            // '다른 상품 더'인 경우에만 2페이지를 추가로 살핀다. 새 검색은 기존 한 페이지 정책을 유지한다.
            if (!excludedUrls.isEmpty() && catalog.hasNextPage()
                    && eligibleProducts(candidates, key, REGIONS.contains(keyword),
                            ticketRequested, excludedUrls).size() < MAX_PRODUCTS) {
                try {
                    MyRealTripCatalogResponse next = catalogService.searchTours(keyword, MAX_FOLLOWUP_PAGES);
                    candidates.addAll(next.items());
                } catch (RuntimeException exception) {
                    additionalPageFailed = true;
                    // 추가 페이지 오류가 첫 페이지에서 이미 확인된 정상 상품을 숨기지 않게 한다.
                    log.warn("AI 매니저 추가 투어 상품 페이지 조회 실패: {}",
                            exception.getClass().getSimpleName());
                }
            }
            List<MyRealTripCatalogResponse.Product> products = eligibleProducts(
                    candidates, key, REGIONS.contains(keyword), ticketRequested, excludedUrls);

            if (products.isEmpty()) {
                if (additionalPageFailed) {
                    return "추가 투어·티켓 상품을 조회하는 중 일부 결과를 불러오지 못했어요. "
                            + "잠시 후 다시 확인해 주세요.";
                }
                if (!excludedUrls.isEmpty()) {
                    return "앞서 소개한 상품 외에 현재 확인된 '" + keyword
                            + "' 관련 추가 상품이 없어요. 다른 지역이나 체험 유형으로 검색해 주세요.";
                }
                return "현재 조회된 상품 중 '" + keyword + "' 관련 투어·티켓 상품을 확인하지 못했어요. "
                        + "다른 장소명이나 체험 유형으로 다시 검색해 주세요.";
            }
            StringBuilder answer = new StringBuilder("[").append(keyword).append(" 관련 투어·티켓]");
            NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
            for (int index = 0; index < products.size(); index++) {
                MyRealTripCatalogResponse.Product product = products.get(index);
                answer.append("\n").append(index + 1).append(". ")
                        .append(safeName(product.itemName()))
                        .append(" · 조회 가격 ").append(formatter.format(product.salePrice()))
                        .append("원 · [상품 보기](").append(product.productUrl()).append(")");
            }
            answer.append("\n투어·가이드·체험 상품은 해당 관광지의 입장권이 아닐 수 있습니다. ")
                    .append("최종 가격과 이용 가능 여부는 상품 상세에서 확인해 주세요.");
            return answer.toString();
        } catch (RuntimeException exception) {
            // 외부 오류를 확인되지 않은 상품 안내나 모델의 추측으로 대체하지 않는다.
            log.warn("AI 매니저 투어·티켓 카탈로그 조회 실패: {}", exception.getClass().getSimpleName());
            return "현재 '" + keyword + "' 관련 상품 정보를 조회하지 못했어요. 잠시 후 다시 검색해 주세요.";
        }
    }

    /**
     * 실제로 구매한 상품의 사후 처리 의도만 포착한다.
     * 항공·열차 예약은 별도 업무이므로 투어·티켓 환불 안내로 잘못 라우팅하지 않는다.
     */
    private static boolean isPurchaseSupportRequest(String compact, List<String> previousUserMessages) {
        if (containsAny(compact, "항공", "비행기", "기차", "ktx", "열차", "고속버스")) {
            return false;
        }
        if (!containsAny(compact, "환불", "취소", "예약변경", "날짜변경", "시간변경", "일정변경", "예약바꾸")) {
            return false;
        }
        // '티켓 취소됐어'와 같은 과거 사실 진술에는 구매처 안내를 강제하지 않는다.
        boolean askingForHelp = containsAny(compact, "하고싶", "해줘", "해주세요", "하려", "해야", "가능",
                "방법", "어떻게", "규정", "조건", "수수료", "접수", "신청", "원해", "원합니다",
                "될까", "되나요", "되는지", "알려", "바꾸고")
                || compact.endsWith("환불") || compact.endsWith("취소") || compact.endsWith("예약변경");
        if (!askingForHelp) {
            return false;
        }
        if (containsAny(compact, "티켓", "입장권", "투어", "체험", "액티비티", "관광패스")) {
            return true;
        }
        if (previousUserMessages == null || previousUserMessages.isEmpty()) {
            return false;
        }
        String previous = normalizeQuestion(previousUserMessages.get(previousUserMessages.size() - 1));
        return containsAny(previous, "티켓", "입장권", "투어", "체험", "액티비티", "관광패스")
                && !containsAny(previous, "항공", "비행기", "기차", "ktx", "열차", "고속버스");
    }

    /** 앞선 대화에서 계속 상품을 더 요청하는 경우에도 중복 상품을 다시 보여주지 않는다. */
    private static List<MyRealTripCatalogResponse.Product> eligibleProducts(
            List<MyRealTripCatalogResponse.Product> candidates,
            String normalizedKeyword,
            boolean region,
            boolean ticketRequested,
            Set<String> excludedUrls
    ) {
        Set<String> seenGids = new LinkedHashSet<>();
        return candidates.stream()
                .filter(AiManagerTicketOfferAnswerService::validProduct)
                .filter(product -> relevant(product, normalizedKeyword, region))
                .filter(product -> !excludedUrls.contains(product.productUrl()))
                .filter(product -> seenGids.add(product.gid()))
                .sorted(Comparator.comparingInt(
                                (MyRealTripCatalogResponse.Product product) ->
                                        rank(product, normalizedKeyword, ticketRequested))
                        .reversed())
                .limit(MAX_PRODUCTS)
                .toList();
    }

    private record PreviousTicketContext(String keyword, Set<String> productUrls) {
    }

    /**
     * 한 번 이상 관련 상품을 실제로 보여준 직후의 추가 추천 요청만 인식한다.
     * 인과관계가 불명확한 과거 추천 내용이나 숙소·날씨 등 새 주제를 가져오지 않는다.
     */
    private static PreviousTicketContext previousTicketContext(
            List<String> previousUserMessages, List<String> previousAssistantMessages) {
        if (previousUserMessages == null || previousUserMessages.isEmpty()
                || previousAssistantMessages == null || previousAssistantMessages.isEmpty()) {
            return null;
        }
        String lastUser = normalizeQuestion(previousUserMessages.get(previousUserMessages.size() - 1));
        if ((!containsAny(lastUser, "티켓", "입장권", "투어", "체험", "액티비티", "관광패스")
                && !isMoreOfferRequest(lastUser))
                || isOtherDomainRequest(lastUser)
                || containsAny(lastUser, "근처", "주변", "가까운", "일차", "번째", "첫날")) {
            return null;
        }
        int index = previousAssistantMessages.size() - 1;
        String lastAnswer = previousAssistantMessages.get(index);
        Matcher header = OFFER_HEADER.matcher(lastAnswer == null ? "" : lastAnswer);
        if (!header.find()) return null;
        String keyword = header.group(1);
        Set<String> used = new LinkedHashSet<>();
        int scanned = 0;
        for (int i = index; i >= 0 && scanned < MAX_RECENT_TICKET_REPLIES; i--, scanned++) {
            String answer = previousAssistantMessages.get(i);
            Matcher candidateHeader = OFFER_HEADER.matcher(answer == null ? "" : answer);
            if (!candidateHeader.find() || !keyword.equals(candidateHeader.group(1))) break;
            Matcher link = OFFER_LINK.matcher(answer);
            while (link.find()) used.add(link.group(1));
        }
        return used.isEmpty() ? null : new PreviousTicketContext(keyword, Set.copyOf(used));
    }

    private static boolean isMoreOfferRequest(String compact) {
        return containsAny(compact, "다른거", "다른것", "다른상품", "다른티켓", "다른투어",
                "다른체험", "더보여", "더알려", "더추천", "추가로", "또보여", "또추천", "다음상품");
    }

    private static boolean isOtherDomainRequest(String compact) {
        return containsAny(compact, "날씨", "기온", "습도", "숙소", "숙박", "호텔", "리조트", "비행기",
                "항공", "열차", "가계부", "예산", "포인트", "퀘스트", "마스코트", "환불", "취소",
                "예약변경", "예약바꾸");
    }

    static String extractKeyword(String compact) {
        Matcher matcher = TARGET_AND_TYPE.matcher(compact);
        if (!matcher.matches()) return null;
        String value = matcher.group(1).replaceFirst("^(?:혹시|그럼|저기|이번에)", "")
                .replaceFirst("(?:관련|에관한|에서할수있는|에서하는|에서|의)$", "")
                .replaceFirst("(?:여행|관광)$", "");
        if (value.equals("제주도")) value = "제주";
        if (value.equals("서울특별시")) value = "서울";
        if (value.isBlank() || value.length() > 30 || containsAny(value,
                "어떤", "무슨", "다른", "그것", "그곳", "어디", "없어", "있어", "보여줘")) {
            return null;
        }
        // 짧은 검색어라도 한글·영문·숫자만 허용해 공급사에 임의 문장/기호를 전달하지 않는다.
        return value.matches("[\\p{IsHangul}A-Za-z0-9]{2,30}") ? value : null;
    }

    private static boolean validProduct(MyRealTripCatalogResponse.Product product) {
        if (product == null || product.gid() == null || !product.gid().matches("[0-9]{1,20}")
                || product.itemName() == null || product.itemName().isBlank()
                || product.salePrice() == null || product.salePrice().compareTo(BigDecimal.ZERO) <= 0
                || product.salePrice().stripTrailingZeros().scale() > 0
                || product.productUrl() == null) return false;
        try {
            URI uri = URI.create(product.productUrl());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "experiences.myrealtrip.com".equalsIgnoreCase(uri.getHost())
                    && ("/products/" + product.gid()).equals(uri.getRawPath())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getUserInfo() == null && uri.getPort() == -1;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean relevant(MyRealTripCatalogResponse.Product product, String keyword, boolean region) {
        if (normalizeName(product.itemName()).contains(keyword)) return true;
        if (!region) return false;
        if (normalizeName(product.description()).contains(keyword)) return true;
        return product.tags() != null && product.tags().stream()
                .anyMatch(tag -> normalizeName(tag).contains(keyword));
    }

    private static int rank(MyRealTripCatalogResponse.Product product, String keyword, boolean ticketRequested) {
        String title = normalizeName(product.itemName());
        int score = title.contains(keyword) ? 20 : 0;
        if (ticketRequested && containsAny(title, "티켓", "입장권", "패스")) score += 12;
        if (containsAny(title, "투어", "해설", "가이드", "체험")) score += 5;
        if (product.reviewCount() != null && product.reviewCount() > 0) {
            score += (int) Math.min(5L, product.reviewCount() / 20);
        }
        return score;
    }

    private static String safeName(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").replaceAll("\\]\\(", " ").trim();
        return clean.length() > 160 ? clean.substring(0, 160) : clean;
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private static boolean containsAnySuffix(String value, String... suffixes) {
        for (String suffix : suffixes) if (value.endsWith(suffix)) return true;
        return false;
    }

    private static String normalizeQuestion(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHangul}A-Za-z0-9]", "");
    }
}
