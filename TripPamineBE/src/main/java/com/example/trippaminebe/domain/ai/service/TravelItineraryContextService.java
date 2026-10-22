package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.TravelItinerarySnapshot;
import com.example.trippaminebe.domain.recommendation.entity.AiTravelRecommendation;
import com.example.trippaminebe.domain.recommendation.repository.AiTravelRecommendationRepository;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 실제 저장된 AI 여행 추천 JSON을 검증하고 일정별 방문 순서/시간을 조회한다.
 * 없는 추천을 자동 생성하면 LLM 비용이 발생하므로 getOrCreate()는 절대 호출하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TravelItineraryContextService {

    private static final Pattern TIME = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d");
    private static final int MAX_DAYS = 31;
    private static final int MAX_PLACES_PER_DAY = 25;
    private static final int MAX_GEOCODES_PER_REQUEST = 20;

    private final TravelPlanRepository travelPlanRepository;
    private final AiTravelRecommendationRepository recommendationRepository;
    private final TravelPoiResolverService poiResolverService;
    // JSON 파서 인스턴스를 직접 생성한다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 인증 사용자의 삭제되지 않은 여행만 접근한다.
     * 저장된 추천이 없어도 AI를 호출하여 새 일정을 생성하지 않는다.
     */
    @Transactional(readOnly = true)
    public TravelItinerarySnapshot load(Long userId, Long planId) {
        if (userId == null || planId == null || userId <= 0 || planId <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행 계획을 찾을 수 없습니다.");
        }
        TravelPlan plan = travelPlanRepository
                .findByPlanIdAndUser_IdAndDelYn(planId, userId, "N")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "여행 계획을 찾을 수 없습니다."));

        // 모든 조회는 소유권 확인 후 진행한다. 신규 추천을 생성하지 않는다.
        AiTravelRecommendation recommendation = recommendationRepository
                .findByTravelPlan_PlanId(planId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "이 여행에 저장된 AI 추천 일정이 없습니다."));

        if (plan.getStartDate() == null) {
            throw invalidSchedule();
        }
        String json = recommendation.getRecommendJson();
        if (json == null || json.isBlank() || json.length() > 500_000) {
            throw invalidSchedule();
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw invalidSchedule();
        }
        JsonNode daysNode = root == null ? null : root.path("days");
        if (daysNode == null || !daysNode.isArray()
                || daysNode.isEmpty() || daysNode.size() > MAX_DAYS) {
            throw invalidSchedule();
        }

        List<TravelItinerarySnapshot.Day> days = new ArrayList<>();
        Set<Integer> usedDays = new HashSet<>();
        LocalDate startDate = plan.getStartDate().toLocalDate();
        LocalDate endDate = plan.getEndDate() == null ? null : plan.getEndDate().toLocalDate();

        for (JsonNode dayNode : daysNode) {
            JsonNode dayNumber = dayNode.path("day");
            JsonNode placesNode = dayNode.path("places");
            if (!dayNumber.isIntegralNumber() || !dayNumber.canConvertToInt()
                    || !placesNode.isArray() || placesNode.size() > MAX_PLACES_PER_DAY) {
                throw invalidSchedule();
            }
            int day = dayNumber.intValue();
            if (day < 1 || day > MAX_DAYS || !usedDays.add(day)) {
                throw invalidSchedule();
            }
            LocalDate date = startDate.plusDays(day - 1L);
            if (endDate != null && date.isAfter(endDate)) {
                throw invalidSchedule();
            }

            List<TravelItinerarySnapshot.Place> places = new ArrayList<>();
            int sequence = 0;
            for (JsonNode placeNode : placesNode) {
                sequence++;
                if (!placeNode.isObject() || !placeNode.path("name").isTextual()) {
                    throw invalidSchedule();
                }
                String name = clean(placeNode.path("name").asText(), 100);
                if (name.isBlank()) {
                    throw invalidSchedule();
                }
                String description = placeNode.path("description").isTextual()
                        ? clean(placeNode.path("description").asText(), 300) : "";

                BigDecimal cost = null;
                JsonNode costNode = placeNode.path("estimatedCost");
                if (costNode.isNumber()) {
                    cost = costNode.decimalValue();
                    if (cost.signum() < 0) {
                        throw invalidSchedule();
                    }
                }
                String begin = timeOrNull(placeNode.path("startTime"));
                String end = timeOrNull(placeNode.path("endTime"));
                if (begin != null && end != null && begin.compareTo(end) >= 0) {
                    throw invalidSchedule();
                }

                places.add(new TravelItinerarySnapshot.Place(
                        "d" + day + "-p" + sequence,
                        sequence, name, description, cost,
                        begin, end, null, null, null, null, "NOT_REQUESTED"));
            }
            days.add(new TravelItinerarySnapshot.Day(day, date, List.copyOf(places)));
        }
        days.sort(java.util.Comparator.comparingInt(TravelItinerarySnapshot.Day::day));
        return new TravelItinerarySnapshot(plan.getPlanId(), clean(plan.getPlanName(), 100),
                List.copyOf(days));
    }

    /**
     * DB 트랜잭션이 끝난 뒤 별도 호출한다. 최대 20개 장소를 검색해 외부 API 쿼터를 제한한다.
     * 이름/주소가 일치하지 않으면 좌표를 반환하지 않으며 결과도 DB에 저장하지 않는다.
     */
    public TravelItinerarySnapshot withCoordinates(TravelItinerarySnapshot snapshot) {
        String region = TravelPoiResolverService.knownRegion(snapshot.planName());
        Map<String, TravelPoiResolverService.Resolution> cache = new HashMap<>();
        List<TravelItinerarySnapshot.Day> enrichedDays = new ArrayList<>();
        int searches = 0;

        for (TravelItinerarySnapshot.Day day : snapshot.days()) {
            List<TravelItinerarySnapshot.Place> enrichedPlaces = new ArrayList<>();
            for (TravelItinerarySnapshot.Place place : day.places()) {
                TravelPoiResolverService.Resolution resolved;
                String key = (region == null ? "" : region) + "|" + place.name();
                if (cache.containsKey(key)) {
                    resolved = cache.get(key);
                } else if (searches >= MAX_GEOCODES_PER_REQUEST) {
                    resolved = TravelPoiResolverService.Resolution.unresolved("NOT_REQUESTED");
                } else {
                    resolved = poiResolverService.resolve(place.name(), region);
                    cache.put(key, resolved);
                    searches++;
                }
                enrichedPlaces.add(new TravelItinerarySnapshot.Place(
                        place.scheduleKey(), place.sequence(), place.name(),
                        place.description(), place.estimatedCost(),
                        place.startTime(), place.endTime(),
                        resolved.placeId(), resolved.latitude(), resolved.longitude(),
                        resolved.address(), resolved.status()));
            }
            enrichedDays.add(new TravelItinerarySnapshot.Day(
                    day.day(), day.date(), List.copyOf(enrichedPlaces)));
        }
        return new TravelItinerarySnapshot(snapshot.planId(), snapshot.planName(),
                List.copyOf(enrichedDays));
    }

    private static String timeOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || !TIME.matcher(node.asText()).matches()) {
            throw invalidSchedule();
        }
        return node.asText();
    }

    private static String clean(String value, int length) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        return clean.length() <= length ? clean : clean.substring(0, length);
    }

    private static ResponseStatusException invalidSchedule() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "저장된 AI 추천 일정의 형식이나 날짜가 올바르지 않습니다.");
    }
}
