package com.example.trippaminebe.domain.ai.listener;

import com.example.trippaminebe.domain.ai.service.AiManagerEventRecorder;
import com.example.trippaminebe.domain.quest.event.QuestCompletedApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * Quest Domain에서 발생한 "퀘스트 완료" 사건을
 * AI Manager Event Log로 연결하는 Listener.
 *
 *
 * =========================================================
 * 학습 포인트
 * =========================================================
 *
 * QuestService가 AI Manager Repository를 직접 호출하지 않고:
 *
 * QuestService
 *      ↓
 * Application Event
 *      ↓
 * 이 Listener
 *      ↓
 * AI Manager Event Recorder
 *
 * 구조를 사용한다.
 *
 * 이렇게 하면 Quest Domain과 AI Manager Domain의
 * 결합도를 낮출 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiManagerQuestEventListener {

	private final AiManagerEventRecorder
			aiManagerEventRecorder;


	/**
	 * AFTER_COMMIT:
	 *
	 * 원래 Quest Transaction이 실제 COMMIT된 뒤에만 실행한다.
	 *
	 * 따라서 Quest가 rollback됐는데도
	 * QUEST_COMPLETED Event가 남는 문제를 방지한다.
	 */
	@TransactionalEventListener(
			phase = TransactionPhase.AFTER_COMMIT
	)
	public void onQuestCompleted(
			QuestCompletedApplicationEvent event
	) {

		/*
		 * Listener가 실제 호출되는지 확인하기 위한 운영 로그.
		 *
		 * DEBUG 대신 INFO를 사용하는 이유는
		 * 현재 Event Pipeline 도입 단계에서
		 * 실제 연결 상태를 확인하기 위해서다.
		 */
		log.info(
				"[AI-MANAGER-EVENT] Quest 완료 Event 수신. "
						+ "logId={}, userId={}, questId={}, questName={}",
				event.logId(),
				event.userId(),
				event.questId(),
				event.questName()
		);


		try {

			Long managerEventId =
					aiManagerEventRecorder
							.recordQuestCompleted(
									event
							);


			/*
			 * 실제 AI_MANAGER_EVENTS 저장까지 성공했음을 확인한다.
			 */
			log.info(
					"[AI-MANAGER-EVENT] Quest Event 저장 완료. "
							+ "logId={}, managerEventId={}",
					event.logId(),
					managerEventId
			);

		} catch (Exception e) {

			/*
			 * 이미 Quest Transaction은 COMMIT된 상태다.
			 *
			 * Event Tracking 실패 때문에
			 * 사용자에게 성공한 Quest를 실패처럼 보이게 하면 안 된다.
			 */
			log.error(
					"[AI-MANAGER-EVENT] Quest Event 저장 실패. "
							+ "logId={}, userId={}, questId={}",
					event.logId(),
					event.userId(),
					event.questId(),
					e
			);
		}
	}
}