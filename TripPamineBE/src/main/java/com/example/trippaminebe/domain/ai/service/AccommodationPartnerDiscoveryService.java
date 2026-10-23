package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.TravelItinerarySnapshot;
import com.example.trippaminebe.domain.nego.dto.request.MyRealTripAccommodationSearchRequest;
import com.example.trippaminebe.domain.nego.service.MyRealTripAccommodationSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 숙박 상품을 우선 조회하고, 확인된 가격과 공식 상품 링크만 사용자에게 표시한다.
 * 외부 숙소 응답에서 좌표·주소가 검증되지 않았으므로 방문지와의 실제 거리는 추정하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationPartnerDiscoveryService {

    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})(?!\\d)");
    private static final Pattern ADULTS = Pattern.compile("성인\\s*(\\d{1,2})(?!\\d)\\s*명?");
    private static final int MAX_RESULTS = 3;
    private static final int MAX_SEARCH_REQUESTS = 2;

    private final TravelItineraryContextService itineraryContextService;
    private final MyRealTripAccommodationSearchService accommodationService;
    private final TravelPoiResolverService poiResolverService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String findAnswer(Long userId, Long planId, int day, int sequence, String question) {
        // 여행 소유권은 공통 일정 서비스에서 검증한다. 검색 과정에서 일정을 수정하지 않는다.
        TravelItinerarySnapshot snapshot = itineraryContextService.load(userId, planId);
        TravelItinerarySnapshot.Day selectedDay = snapshot.days().stream()
                .filter(d -> d.day() == day).findFirst().orElse(null);
        if (selectedDay == null || sequence < 1 || sequence > selectedDay.places().size()) {
            return "해당 방문지의 일정을 찾지 못했어요. 여행 이름과 일차를 확인해 주세요.";
        }
        String referenceName = selectedDay.places().get(sequence - 1).name();
        if (TravelNearbyCategoryPoiService.genericName(referenceName)) {
            return "'" + referenceName + "'은 여러 지점을 뜻할 수 있어요. 정확한 장소를 지정해 주세요.";
        }
        String region = TravelPoiResolverService.knownRegion(snapshot.planName());
        if (region == null) return "여행 지역을 확인하지 못했어요. 여행 계획의 지역을 확인해 주세요.";

        // 체크인과 인원을 임의 추측하면 실제 판매가와 달라지므로 재질문한다.
        Matcher dates = DATE.matcher(question);
        String checkInText = dates.find() ? dates.group(1) : null;
        String checkOutText = dates.find() ? dates.group(1) : null;
        Matcher adults = ADULTS.matcher(question);
        Integer adultCount = adults.find() ? Integer.valueOf(adults.group(1)) : null;
        if (checkInText == null || checkOutText == null || adultCount == null) {
            return "'" + referenceName + "' 주변 여행 숙소 상품을 검색하려면 체크인·체크아웃 날짜와 "
                    + "성인 인원을 알려주세요. 예: "
                    + selectedDay.date() + " ~ " + selectedDay.date().plusDays(1)
                    + " 성인 2명";
        }
        final LocalDate checkIn;
        final LocalDate checkOut;
        try {
            checkIn = LocalDate.parse(checkInText);
            checkOut = LocalDate.parse(checkOutText);
        } catch (DateTimeParseException exception) {
            return "날짜 형식을 확인해 주세요. 예: 2026-10-28 ~ 2026-10-29 성인 2명";
        }
        if (!checkOut.isAfter(checkIn) || checkIn.isBefore(LocalDate.now())
                || adultCount < 1 || adultCount > 20) {
            return "체크아웃은 체크인 이후여야 하고 지난 날짜는 조회할 수 없어요. 성인은 1~20명으로 지정해 주세요.";
        }

        // 기준 방문지의 행정구역만 검색 우선순위에 사용한다.
        // 외부 숙소 검색 결과에는 확인된 좌표가 없어 '가까운 숙소'로 확정하지 않는다.
        TravelPoiResolverService.Resolution origin = poiResolverService.resolve(referenceName, region);
        if (!"VERIFIED".equals(origin.status())) {
            return "'" + referenceName + "'의 정확한 위치를 확인하지 못했어요. 방문지 이름을 확인해 주세요.";
        }
        Set<String> keywords = new LinkedHashSet<>();
        String district = districtKeyword(origin.address(), region);
        if (district != null) keywords.add(district);
        keywords.add(region);

        Map<String, StayOffer> found = new LinkedHashMap<>();
        boolean apiFailed = false;
        int searches = 0;
        for (String keyword : keywords) {
            if (searches++ >= MAX_SEARCH_REQUESTS || !found.isEmpty()) break;
            try {
                String raw = accommodationService.search(new MyRealTripAccommodationSearchRequest(
                        keyword, checkIn.toString(), checkOut.toString(), adultCount, 0));
                JsonNode root = objectMapper.readTree(raw);
                // 실제 제휴 응답에서 확인된 구조 외에는 값을 추정하여 노출하지 않는다.
                if (root.path("result").path("status").asInt(-1) != 200
                        || !root.path("data").path("items").isArray()) {
                    apiFailed = true;
                    continue;
                }
                for (JsonNode item : root.path("data").path("items")) {
                    StayOffer offer = validatedOffer(item, checkInText, checkOutText, adultCount, keyword);
                    if (offer != null) found.putIfAbsent(offer.id(), offer);
                }
            } catch (JsonProcessingException exception) {
                log.warn("AI 매니저 숙소 상품 JSON 검증 실패");
                apiFailed = true;
            } catch (RuntimeException exception) {
                log.warn("AI 매니저 숙소 상품 조회 실패");
                apiFailed = true;
            }
        }

        // 상품명에 실제 지역/방문지 관련 표현이 있다면 앞에 두되, 지리적 거리로 해석하지 않는다.
        List<StayOffer> ranked = new ArrayList<>(found.values());
        ranked.sort(Comparator.comparingInt(
                (StayOffer offer) -> relevanceScore(offer, referenceName, district)).reversed());
        StringBuilder answer = new StringBuilder("[숙소 상품 추천]\n")
                .append(checkIn).append(" 체크인 · ").append(checkOut)
                .append(" 체크아웃 · 성인 ").append(adultCount).append("명");
        if (ranked.isEmpty()) {
            answer.append(apiFailed
                    ? "\n현재 상품 조회가 원활하지 않거나 조건에 맞는 상품이 없어요. 잠시 후 다시 확인해 주세요."
                    : "\n선택한 날짜와 인원에 맞는 확인 가능한 숙소 상품을 찾지 못했어요.");
        }
        for (int index = 0; index < Math.min(MAX_RESULTS, ranked.size()); index++) {
            StayOffer offer = ranked.get(index);
            answer.append("\n").append(index + 1).append(". ").append(offer.name())
                    .append(" · 조회 가격 ")
                    .append(java.text.NumberFormat.getNumberInstance(Locale.KOREA).format(offer.price()))
                    .append("원 · [상품 보기](").append(offer.url()).append(")");
        }
        answer.append("\n'").append(referenceName).append("' 방문 여행 기준으로 ")
                .append(district == null ? region : district).append(" 지역을 우선 검색했습니다. ")
                .append("상품의 실제 위치·방문지까지의 거리는 확인되지 않았으므로 상세 정보에서 확인해 주세요. ")
                .append("가격·객실 옵션·예약 가능 여부는 최종 예약 단계에서 달라질 수 있습니다.");
        return answer.toString();
    }

    /** 사용자 여행의 검증된 주소에서 행정구역 검색어만 추출한다. 지역명을 새로 추정하지 않는다. */
    private static String districtKeyword(String address, String region) {
        if (address == null || !address.contains(region)) return null;
        for (String part : address.split("\\s+")) {
            if (part.length() >= 2 && (part.endsWith("구") || part.endsWith("군"))) {
                // 상품 검색은 행정구역 접미어가 없는 지명도 시도한다. 예: 종로구 → 종로.
                return part.length() > 2 ? part.substring(0, part.length() - 1) : part;
            }
        }
        return null;
    }

    /** 제휴 상품의 ID, 이름, 실제 양수 판매가, 공식 상품 URL을 모두 검증한다. */
    private static StayOffer validatedOffer(JsonNode item, String checkIn, String checkOut,
                                           int adultCount, String keyword) {
        JsonNode idNode = item.path("itemId");
        String id = idNode.isIntegralNumber() && idNode.canConvertToLong()
                ? idNode.asText() : "";
        String name = item.path("itemName").isTextual() ? safe(item.path("itemName").asText()) : "";
        JsonNode price = item.path("salePrice");
        String url = item.path("productUrl").isTextual() ? item.path("productUrl").asText() : "";
        if (!id.matches("[1-9][0-9]{0,19}") || name.isBlank() || !price.isNumber()
                || price.decimalValue().signum() <= 0
                || !validStayUrl(id, url, checkIn, checkOut, adultCount)) return null;
        return new StayOffer(id, name, price.decimalValue(), url, keyword);
    }

    private static int relevanceScore(StayOffer offer, String referenceName, String district) {
        String name = normalizeName(offer.name());
        int score = normalizeName(referenceName).isBlank() ? 0
                : name.contains(normalizeName(referenceName)) ? 15 : 0;
        if (district != null && name.contains(normalizeName(district))) score += 10;
        if (district != null && district.equals(offer.keyword())) score += 5;
        return score;
    }

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHangul}A-Za-z0-9]", "");
    }

    private record StayOffer(String id, String name, BigDecimal price, String url, String keyword) { }

    /** 숙박 URL은 이미 프론트와 MyLink 서버가 허용하는 실제 응답 경로만 인정한다. */
    static boolean validStayUrl(String id, String url, String checkIn, String checkOut, int adultCount) {
        try {
            URI parsed = URI.create(url);
            String query = "checkIn=" + checkIn + "&checkOut=" + checkOut
                    + "&adultCount=" + adultCount + "&childCount=0&childAges=";
            return "https".equalsIgnoreCase(parsed.getScheme())
                    && "accommodation.myrealtrip.com".equalsIgnoreCase(parsed.getHost())
                    && ("/union/products/" + id).equals(parsed.getRawPath())
                    && query.equals(parsed.getRawQuery()) && parsed.getPort() == -1
                    && parsed.getUserInfo() == null && parsed.getRawFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String safe(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        // 채팅 마크다운 링크 문법을 이름에 섞어 실행 가능한 링크를 위장하지 못하게 한다.
        clean = clean.replace('[', ' ').replace(']', ' ').replace('(', ' ').replace(')', ' ');
        return clean.length() > 120 ? clean.substring(0, 120) : clean;
    }
}
