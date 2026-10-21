package com.example.trippaminebe.domain.ai.event;

import java.time.LocalDateTime;


/**
 * AI_MANAGER_EVENTS에 Event 저장이 완료됐음을
 * AI Manager 내부 다른 기능에 알려주는 Application Event.
 *
 *
 * =========================================================
 * 별도의 Event가 필요한 이유
 * =========================================================
 *
 * Travel / Quest
 *      ↓
 * Domain Application Event
 *      ↓
 * AiManagerEventRecorder
 *      ↓
 * AI_MANAGER_EVENTS
 *
 *
 * 앞으로는 여기에:
 *
 * AI_MANAGER_EVENTS
 *      ↓
 * AI Memory
 *      ↓
 * AI_USER_FACTS
 *      ↓
 * AI_USER_FACT_EVIDENCE
 *
 * 를 연결해야 한다.
 *
 *
 * Memory Service가 Travel/Quest Listener를 직접 알게 만들지 않고,
 *
 * "AI Manager Event가 하나 정상 저장됐다"
 *
 * 라는 공통 Event를 전달한다.
 *
 * 이렇게 해두면 앞으로:
 *
 * - Diary
 * - Finance
 * - Emotion
 * - Activity
 *
 * Event가 추가되어도 Memory 구조를 재사용할 수 있다.
 */
public record AiManagerEventRecordedApplicationEvent(

		Long eventId,

		Long userId,

		String eventType,

		LocalDateTime occurredAt

) {
}