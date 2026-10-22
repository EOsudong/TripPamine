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

/**
 * STEP 7: 여행 날짜·방문 순서·방문 시간에 관한 질문은 실제 추천 JSON으로 직접 답한다.
 * LLM의 추측을 통하지 않으며, 날씨/대체 POI 대화는 기존 경로로 남겨둔다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerItineraryAnswerService {

    private final AiTravelOverviewService travelOverviewService;
    private final TravelItineraryContextService itineraryContextService;

    public String findAnswer(Long userId, String message, List<String> previousUserMessages) {
        String q = normalize(message);
        // STEP 7.2: 대화 이력을 순서대로 재생해 구조화된 상태를 생성한다.
        // 서버 저장 대화는 서버 이력, 비저장 대화는 요청 이력만 참조하며 별도 공유 캐시는 없다.
        AiManagerItineraryContextState context =
                AiManagerItineraryContextResolver.resolve(message, previousUserMessages);
        if (context == null) {
            return null;
        }

        List<TravelItem> plans = travelOverviewService.getOverview(userId).plans();
        if (plans == null || plans.isEmpty()) {
            return "현재 등록된 여행계획이 없습니다.";
        }

        // 현재 발화에서 명시적으로 지정된 여행은 이전 대화보다 우선한다.
        List<TravelItem> matching = choosePlans(plans, q);
        if (matching.isEmpty() && context.explicitPlan()) {
            return "요청한 여행 이름이나 지역과 일치하는 저장 여행을 찾지 못했어요.";
        }
        if (matching.isEmpty() && context.planQuestion() != null) {
            matching = choosePlans(plans, context.planQuestion());
        }
        if (matching.isEmpty()) {
            // 문맥이 확실하지 않을 때 여러 여행 중 하나를 임의로 선택하지 않는다.
            matching = plans.size() == 1 ? plans : List.of();
        }
        if (matching.size() != 1) {
            return "확인할 여행이 여러 건이거나 특정되지 않았어요. "
                    + "여행 이름과 일차를 함께 알려주세요. 예: 서울 여행 1일차 3번째 장소";
        }

        // 같은 지역에서도 다른 여행을 명시한 경우 이전 여행의 장소 순서는 승계하지 않는다.
        // 정확히 하나의 이전 여행이 확인된 경우에만 ID를 비교한다.
        if (context.explicitPlan()) {
            AiManagerItineraryContextState before =
                    AiManagerItineraryContextResolver.resolvePrevious(previousUserMessages);
            if (before != null && before.planQuestion() != null) {
                List<TravelItem> previousPlans = choosePlans(plans, before.planQuestion());
                if (previousPlans.size() == 1
                        && !matching.get(0).planId().equals(previousPlans.get(0).planId())) {
                    context = context.withoutInheritedSelection();
                }
            }
        }
        // 권한이 확인된 여행 ID만 상태에 넣는다. 이 ID를 클라이언트 이력에서 받지 않는다.
        context = context.withPlanId(matching.get(0).planId());
        TravelItinerarySnapshot snapshot;
        try {
            snapshot = itineraryContextService.load(userId, context.planId());
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

        Integer dayNo = context.day();
        Integer sequence = context.sequence();
        boolean locationQuestion = context.requestedField()
                == AiManagerItineraryContextState.RequestedField.LOCATION;

        // 이전 장소에서 다음/이전 장소로 이동할 때 경계 밖의 순서를 만들어내지 않는다.
        if (context.requestedField() == AiManagerItineraryContextState.RequestedField.MOVEMENT_TIME) {
            return "현재 저장된 추천 일정에는 실제 이동 소요 시간이 검증되어 있지 않습니다. "
                    + "이동 경로 API 연결 후 해당 구간의 시간을 안내할 수 있어요.";
        }
        if (locationQuestion) {
            snapshot = itineraryContextService.withCoordinates(snapshot);
        }

        // 명시적인 장소명은 과거 방문 순서보다 우선한다.
        // 예: 3번째 장소를 본 다음 "삼청동 카페거리 몇 시야?"라고 물으면 2번째를 조회한다.
        // 실제로 명시된 장소명은 이전 숫자 순서보다 우선한다.
        if (context.namedQuestion() != null && !context.explicitDay() && !context.explicitSequence()) {
            List<MatchedPlace> anchor = findNamedPlaces(snapshot, context.namedQuestion());
            if (anchor.size() == 1) {
                dayNo = anchor.get(0).day().day();
                sequence = anchor.get(0).index() + 1 + context.relativeOffset();
            }
        }
        List<MatchedPlace> named = findNamedPlaces(snapshot, q);
        if (named.isEmpty() && context.namedQuestion() != null
                && context.requestedField() != AiManagerItineraryContextState.RequestedField.NEXT
                && context.requestedField() != AiManagerItineraryContextState.RequestedField.PREVIOUS
                && !context.explicitDay() && !context.explicitSequence()) {
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

        // "그다음" / "그전"은 현재 선택한 위치에서 이동한 결과를 반환한다.
        if (context.requestedField() == AiManagerItineraryContextState.RequestedField.NEXT
                || context.requestedField() == AiManagerItineraryContextState.RequestedField.PREVIOUS) {
            if (dayNo == null || sequence == null) {
                return "기준이 되는 방문지를 먼저 알려주세요. 예: 서울 여행 1일차 2번째 장소";
            }
            return answerAtIndex(snapshot, dayNo, sequence, q, locationQuestion);
        }

        // 여행·일차·방문 순서별로 DB 저장값을 직접 조회한다.
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
                if (sequence < 1) {
                    return dayNo + "일차에는 더 이전 방문지가 없습니다.";
                }
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
        if (AiManagerItineraryContextResolver.isTimeQuestion(question)) {
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

        if (contains(question, "앞뒤", "전후")) {
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

    private static boolean contains(String source, String... keywords) {
        for (String keyword : keywords) if (source.contains(keyword)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record MatchedPlace(TravelItinerarySnapshot.Day day, int index) {
    }
}
