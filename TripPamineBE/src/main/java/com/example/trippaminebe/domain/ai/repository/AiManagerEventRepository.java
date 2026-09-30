package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiManagerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AI Manager Event 저장소.
 *
 * AI_MANAGER_EVENTS는 AI Manager가 관찰하는
 * 사용자 활동을 시간 순서대로 저장하는 append-only Event Log 역할을 한다.
 *
 * 예:
 *
 * - 감정 분석 완료
 * - 퀘스트 완료
 * - 여행 시작
 * - 다이어리 생성
 * - 향후 금융 Context 변경
 */
public interface AiManagerEventRepository
		extends JpaRepository<AiManagerEvent, Long> {

	/**
	 * 기존 조회 메서드.
	 *
	 * 다른 코드가 이미 사용할 가능성이 있으므로 삭제하지 않는다.
	 */
	List<AiManagerEvent>
	findTop50ByUser_IdOrderByCreatedAtDesc(
			Long userId
	);


	/**
	 * AI Manager Home Activity Feed용.
	 *
	 * Home에서는 너무 많은 Event를 가져올 필요가 없으므로
	 * 최근 20개까지만 읽는다.
	 *
	 * 이후 Service에서 실제 화면에는 최대 10개 정도만 노출한다.
	 */
	List<AiManagerEvent>
	findTop20ByUser_IdOrderByCreatedAtDesc(
			Long userId
	);


	/**
	 * 같은 도메인 사건이 중복 기록되는 것을 막기 위한 조회.
	 *
	 * 예:
	 *
	 * QUEST_COMPLETED:22
	 *
	 * 같은 Quest Log #22가 여러 번 Event로 저장되는 것을 방지한다.
	 *
	 * 주의:
	 * 이것은 Application 수준의 1차 방어다.
	 * 장기적으로 DEDUPE_KEY에 UNIQUE INDEX를 두면 DB 수준에서도
	 * race condition을 방어할 수 있다.
	 */
	boolean existsByDedupeKey(
			String dedupeKey
	);
}