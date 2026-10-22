package com.example.trippaminebe.domain.ai.service;

/**
 * STEP 7.2: 한 번의 AI 매니저 요청에서 사용하는 구조화된 여행 대화 상태.
 * 사용자 메시지로 planId를 신뢰하지 않으며, 소유권 검증을 마친 뒤에만 할당한다.
 * 영구 저장이나 사용자 간 공유를 하지 않는다.
 */
record AiManagerItineraryContextState(
        Long planId,
        String planQuestion,
        Integer day,
        Integer sequence,
        String namedQuestion,
        int relativeOffset,
        RequestedField requestedField,
        boolean explicitDay,
        boolean explicitSequence,
        boolean explicitPlan
) {
    enum RequestedField {
        PLACE_DETAIL,
        VISIT_TIME,
        NEIGHBORS,
        DAY_OVERVIEW,
        LOCATION,
        NEXT,
        PREVIOUS,
        MOVEMENT_TIME
    }

    /** 명시적으로 다른 여행으로 전환했으면 이전 장소 선택은 초기화한다. */
    AiManagerItineraryContextState withoutInheritedSelection() {
        return new AiManagerItineraryContextState(null, planQuestion,
                explicitDay ? day : null, explicitSequence ? sequence : null,
                null, 0, requestedField, explicitDay, explicitSequence, explicitPlan);
    }

    AiManagerItineraryContextState withPlanId(Long ownedPlanId) {
        return new AiManagerItineraryContextState(ownedPlanId, planQuestion, day,
                sequence, namedQuestion, relativeOffset, requestedField, explicitDay,
                explicitSequence, explicitPlan);
    }
}
