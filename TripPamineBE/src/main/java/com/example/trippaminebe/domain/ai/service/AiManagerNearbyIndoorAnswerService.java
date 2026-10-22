package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse.TravelItem;
import com.example.trippaminebe.domain.ai.dto.NearbyIndoorAlternativesResponse;
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
 * STEP 8: 사용자의 '그 장소 근처에 비를 피할 곳은?' 요청을
 * 최근 여행 대화 문맥과 연결한 뒤 실제 일정의 좌표 기반 검색으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerNearbyIndoorAnswerService {

    private static final Pattern DAY = Pattern.compile("([1-9]\\d?)일차");
    private static final Pattern SEQUENCE = Pattern.compile("([1-9]\\d?)번째");
    private static final int DEFAULT_RADIUS_METERS = 1500;

    private final AiTravelOverviewService travelOverviewService;
    private final TravelItineraryContextService itineraryContextService;
    private final TravelNearbyIndoorPoiService nearbyPoiService;

    public String findAnswer(Long userId, String message, List<String> previousUserMessages) {
        String q = normalize(message);
        if (!isNearbyIndoorRequest(q)) {
            return null;
        }
        List<TravelItem> plans = travelOverviewService.getOverview(userId).plans();
        if (plans == null || plans.isEmpty()) {
            return "저장된 여행 계획이 없습니다. 여행 플래너에서 일정을 먼저 등록해 주세요.";
        }
        // 현재 질문의 명시적 여행을 우선한다. 다른 여행을 임의 선택하지 않는다.
        List<TravelItem> matches = matchPlans(plans, q);
        AiManagerItineraryContextState previous = AiManagerItineraryContextResolver.resolvePrevious(previousUserMessages);
        if (matches.isEmpty() && q.contains("여행") && TravelPoiResolverService.knownRegion(q) != null) {
            return "질문에서 지정한 여행을 찾지 못했어요. 저장된 여행 이름을 확인해 주세요.";
        }
        if (matches.isEmpty() && previous != null && previous.planQuestion() != null) {
            matches = matchPlans(plans, previous.planQuestion());
        }
        if (matches.isEmpty() && plans.size() == 1) {
            matches = plans;
        }
        if (matches.size() != 1) {
            return "어느 여행의 방문지 주변을 찾을까요? 여행 이름과 일차를 알려주세요.";
        }
        Long planId = matches.get(0).planId();
        TravelItinerarySnapshot snapshot;
        try {
            snapshot = itineraryContextService.load(userId, planId);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return "해당 여행에는 저장된 AI 추천 일정이 없습니다.";
            }
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                return "저장된 여행 일정의 형식이 올바르지 않아 주변 장소를 조회할 수 없습니다.";
            }
            throw exception;
        }
        Integer day = parseNumber(DAY, q);
        Integer sequence = parseNumber(SEQUENCE, q);
        Integer previousDay = previous == null ? null : previous.day();
        Integer previousSequence = previous == null ? null : previous.sequence();
        if (day == null) {
            day = previousDay;
        }
        if (sequence == null) {
            sequence = previousSequence;
        }

        // 현재 발화에서 정확한 장소를 지정한 경우 과거 순서보다 우선한다.
        List<Target> namedPlaces = new ArrayList<>();
        for (TravelItinerarySnapshot.Day oneDay : snapshot.days()) {
            for (TravelItinerarySnapshot.Place place : oneDay.places()) {
                if (place.name().length() >= 2 && q.contains(normalize(place.name()))) {
                    namedPlaces.add(new Target(oneDay.day(), place.sequence()));
                }
            }
        }
        if (!namedPlaces.isEmpty()) {
            Integer selectedDay = day;
            if (selectedDay != null) {
                namedPlaces = namedPlaces.stream().filter(target -> target.day() == selectedDay).toList();
            }
            if (namedPlaces.size() != 1) {
                return "같은 이름의 방문지가 여러 곳이거나 요청한 일차에서 찾지 못했어요. "
                        + "일차와 방문 순서를 함께 알려주세요.";
            }
            day = namedPlaces.get(0).day();
            sequence = namedPlaces.get(0).sequence();
        }
        if (day == null || sequence == null) {
            return "주변 장소를 검색할 기준 방문지가 필요해요. "
                    + "예: 서울 여행 1일차 3번째 장소 근처에 비를 피할 곳은?";
        }
        NearbyIndoorAlternativesResponse result;
        try {
            result = nearbyPoiService.suggest(userId, planId, day, sequence, DEFAULT_RADIUS_METERS);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                return "지정한 일차나 방문 순서에 해당하는 장소가 없습니다. 저장된 일정을 확인해 주세요.";
            }
            throw exception;
        }
        return format(result);
    }

    private static String format(NearbyIndoorAlternativesResponse result) {
        if ("REFERENCE_AMBIGUOUS".equals(result.status())) {
            return "'" + result.referenceName() + "'은(는) 여러 지점을 가리킬 수 있어 "
                    + "기준 좌표를 확정하지 않았습니다. "
                    + "예: 여의도한강공원, 뚝섬한강공원처럼 실제 지점을 확인해 주세요. "
                    + "저장된 여행 일정은 변경하지 않았습니다.";
        }
        if (result.status().startsWith("REFERENCE_")) {
            return "'" + result.referenceName() + "'의 정확한 좌표를 확인하지 못해 "
                    + "잘못된 장소 주변을 추천하지 않았습니다. "
                    + "여행 플래너에서 정확한 장소 이름을 확인해 주세요.";
        }
        if (result.candidates().isEmpty()) {
            return "'" + result.referenceName() + "' 주변 반경 " + result.radiusMeters() + "m에서 "
                    + ("API_UNAVAILABLE".equals(result.status())
                            ? "장소 검색 서비스를 일시적으로 이용할 수 없습니다."
                            : "조건에 맞는 실내 시설 후보를 검증하지 못했습니다.")
                    + " 다른 지역이나 방문지를 지정해 주세요.";
        }
        StringBuilder text = new StringBuilder()
                .append(result.day()).append("일차 ")
                .append(result.sequence()).append("번째 '")
                .append(result.referenceName()).append("' 기준 반경 ")
                .append(result.radiusMeters()).append("m 안의 실내 시설 후보입니다.");
        for (int i = 0; i < result.candidates().size(); i++) {
            NearbyIndoorAlternativesResponse.Candidate candidate = result.candidates().get(i);
            text.append("\n").append(i + 1).append(". ").append(candidate.name())
                    .append(" · 직선거리 약 ").append(candidate.straightLineMeters()).append("m")
                    .append(" · ").append(candidate.address());
        }
        if ("PARTIAL".equals(result.status())) {
            text.append("\n일부 장소 검색이 실패하여 후보가 누락됐을 수 있습니다.");
        }
        text.append("\n직선거리 순서이며 실제 도보·차량 이동 시간, 운영시간, 예약 가능 여부는 검증되지 않았습니다. ")
                .append("저장된 여행 일정은 변경하지 않았습니다.");
        return text.toString();
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

    private static boolean isNearbyIndoorRequest(String q) {
        return contains(q, "근처", "주변", "가까운", "가까이")
                && contains(q, "실내", "비", "우천", "대체", "장소", "곳", "박물관", "미술관");
    }

    private static Integer parseNumber(Pattern pattern, String q) {
        Matcher matcher = pattern.matcher(q);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
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
