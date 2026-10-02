package com.example.trippaminebe.domain.ai.listener;

import com.example.trippaminebe.domain.ai.service.AiManagerEventRecorder;
import com.example.trippaminebe.domain.travel.event.TravelPlanActivityApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * Travel Domain Event를
 * AI_MANAGER_EVENTS로 연결하는 Listener.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiManagerTravelEventListener {

	private final AiManagerEventRecorder
			aiManagerEventRecorder;


	/**
	 * 원래 Travel Transaction이 실제 성공한 뒤 실행한다.
	 *
	 * CREATE / UPDATE / DELETE가 rollback되면
	 * AI Manager Event도 생성하지 않는다.
	 */
	@TransactionalEventListener(
			phase = TransactionPhase.AFTER_COMMIT
	)
	public void onTravelPlanActivity(
			TravelPlanActivityApplicationEvent event
	) {

		log.info(
				"[AI-MANAGER-EVENT] Travel Event 수신. "
						+ "type={}, planId={}, userId={}, planName={}",
				event.type(),
				event.planId(),
				event.userId(),
				event.planName()
		);


		try {

			Long managerEventId =
					aiManagerEventRecorder
							.recordTravelPlanActivity(
									event
							);


			log.info(
					"[AI-MANAGER-EVENT] Travel Event 저장 완료. "
							+ "type={}, planId={}, managerEventId={}",
					event.type(),
					event.planId(),
					managerEventId
			);

		} catch (
				Exception e
		) {

			/*
			 * 원래 여행 변경 Transaction은 이미 성공했다.
			 *
			 * AI Event 기록 실패 때문에
			 * 여행 저장 성공 자체를 실패처럼 보이면 안 된다.
			 */
			log.error(
					"[AI-MANAGER-EVENT] Travel Event 저장 실패. "
							+ "type={}, planId={}, userId={}",
					event.type(),
					event.planId(),
					event.userId(),
					e
			);
		}
	}
}