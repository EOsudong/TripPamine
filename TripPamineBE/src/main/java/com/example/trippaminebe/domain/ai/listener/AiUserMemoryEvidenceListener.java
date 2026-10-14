package com.example.trippaminebe.domain.ai.listener;

import com.example.trippaminebe.domain.ai.event.AiManagerEventRecordedApplicationEvent;
import com.example.trippaminebe.domain.ai.service.AiUserMemoryEvidenceService;
import com.example.trippaminebe.domain.ai.service.AiUserPatternCandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * AI_MANAGER_EVENTS 저장 완료 후
 * AI Memory 파이프라인을 시작하는 Listener.
 *
 *
 * =========================================================
 * Transaction 구조
 * =========================================================
 *
 * AI_MANAGER_EVENTS
 *      ↓ COMMIT
 *
 * AFTER_COMMIT
 *      ↓
 *
 * Observation Evidence Transaction
 *      ↓ COMMIT
 *
 * Pattern Candidate Transaction
 *      ↓ COMMIT / ROLLBACK
 *
 *
 * 중요한 원칙:
 *
 * Pattern 추론이 실패하더라도
 * 이미 관찰된 사실과 Evidence는 절대로
 * 함께 Rollback되면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUserMemoryEvidenceListener {

	private final AiUserMemoryEvidenceService
			aiUserMemoryEvidenceService;

	private final AiUserPatternCandidateService
			aiUserPatternCandidateService;


	/**
	 * AI_MANAGER_EVENTS 행이 정상 Commit된 뒤 실행한다.
	 */
	@TransactionalEventListener(
			phase = TransactionPhase.AFTER_COMMIT
	)
	public void onAiManagerEventRecorded(
			AiManagerEventRecordedApplicationEvent event
	) {

		/*
		 * =================================================
		 * STEP 1
		 * Observation / Evidence 저장
		 * =================================================
		 *
		 * captureEvidence() 자체가 REQUIRES_NEW이므로
		 * 메서드가 정상 반환되는 시점에는
		 * Evidence Transaction이 이미 Commit된 상태다.
		 */
		int createdEvidenceCount;

		try {

			createdEvidenceCount =
					aiUserMemoryEvidenceService
							.captureEvidence(
									event.eventId()
							);

		} catch (
				RuntimeException e
		) {

			/*
			 * Observation 자체가 실패했다면
			 * Pattern을 계산해서는 안 된다.
			 */
			log.error(
					"[AI-MEMORY] Memory Evidence 생성 실패. "
							+ "eventId={}, userId={}, eventType={}",
					event.eventId(),
					event.userId(),
					event.eventType(),
					e
			);


			return;
		}


		log.info(
				"[AI-MEMORY] Memory Evidence 생성 완료. "
						+ "eventId={}, userId={}, eventType={}, "
						+ "createdEvidence={}",
				event.eventId(),
				event.userId(),
				event.eventType(),
				createdEvidenceCount
		);


		/*
		 * 새 Evidence가 없으면
		 * Pattern을 다시 계산할 이유도 없다.
		 */
		if (
				createdEvidenceCount <= 0
		) {

			return;
		}


		/*
		 * =================================================
		 * STEP 2
		 * Pattern Candidate 계산
		 * =================================================
		 *
		 * 여기부터는 Observation과 완전히 별개다.
		 *
		 * Pattern 계산 실패:
		 *
		 *      Pattern Transaction만 Rollback
		 *
		 * 이미 Commit된 Observation Evidence:
		 *
		 *      그대로 유지
		 */
		try {

			aiUserPatternCandidateService
					.refreshTravelPatternCandidates(
							event.userId()
					);


			log.info(
					"[AI-MEMORY-PATTERN] Pattern Candidate 처리 완료. "
							+ "eventId={}, userId={}",
					event.eventId(),
					event.userId()
			);

		} catch (
				RuntimeException e
		) {

			/*
			 * Pattern 추론 실패를
			 * Observation 실패로 취급하지 않는다.
			 *
			 * 이 로그를 통해 다음 단계에서
			 * 실제 Pattern Service 예외 원인을 추적한다.
			 */
			log.error(
					"[AI-MEMORY-PATTERN] Pattern Candidate 처리 실패. "
							+ "eventId={}, userId={}",
					event.eventId(),
					event.userId(),
					e
			);
		}
	}
}