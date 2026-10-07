package com.example.trippaminebe.domain.quest.event;

import java.time.LocalDateTime;

/**
 * Quest Domain에서 "퀘스트 성공"이라는 사실이 발생했음을
 * 다른 Domain에 알리는 Application Event.
 *
 * 중요한 설계 포인트:
 *
 * QuestService가 AiManagerEventRepository를 직접 호출하지 않는다.
 *
 * 잘못된 구조:
 *
 * QuestService
 *      ↓
 * AiManagerEventRepository
 *
 * 이렇게 하면 Quest Domain이 AI Manager 구현에 강하게 의존한다.
 *
 *
 * 현재 구조:
 *
 * QuestService
 *      ↓
 * QuestCompletedApplicationEvent 발행
 *      ↓
 * AI Manager Listener가 필요할 때 소비
 *
 *
 * 이렇게 하면 Quest Domain은
 * "퀘스트가 완료됐다"는 사실만 알리고,
 *
 * 그 사실을:
 *
 * - AI Manager가 기록하거나
 * - Notification이 알림으로 보내거나
 * - 통계 시스템이 집계하는 것
 *
 * 은 각 Domain이 독립적으로 결정할 수 있다.
 */
public record QuestCompletedApplicationEvent(

		Long logId,

		Long userId,

		Long questId,

		String questName,

		Long rewardPoint,

		//AI Manager 등 이벤트 소비자가 필요 시 활용.
		Long rewardExp,

		LocalDateTime clearDate
) {
}
