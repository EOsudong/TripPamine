
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.client.ManagerSlmClient;
import com.example.trippaminebe.domain.ai.dto.AiManagerChatResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerRoutingResult;
import com.example.trippaminebe.domain.user.entity.User;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * TripPamine AI Manager Chat Service V2.
 *
 * 신규 처리 방식:
 *
 * 1. 인증 사용자 확인
 * 2. 메시지 검증
 * 3. Context 필요 여부 사전 판단
 * 4. 필요한 경우에만 Context 조회
 * 5. Router 결과에 따라 응답
 *
 * 중요:
 * - 현재 Quest 개수 질문은 기존 Context 조회 유지
 * - 일반 SLM 대화는 전체 Context 조회 생략
 * - DB 저장 및 Action 실행 없음
 * - SLM 외부 요청 중 장시간 DB 트랜잭션 유지 방지
 */
@Service
@RequiredArgsConstructor
public class AiManagerChatService {

	private static final String DIRECT_ANSWER =
			"DIRECT_ANSWER";

	private static final String SLM_GENERATION =
			"SLM_GENERATION";

	private final AiManagerContextService
			aiManagerContextService;

	private final AiManagerContextRouter
			aiManagerContextRouter;

	private final AiManagerPromptBuilder
			aiManagerPromptBuilder;

	private final ManagerSlmClient
			managerSlmClient;

	/**
	 * 현재 로그인 사용자의 Chat 요청 처리.
	 */
	public AiManagerChatResponse chat(
			User user,
			String message
	) {

		/*
		 * 1. 사용자 인증 검증.
		 */
		if (
				user == null
						|| user.getId() == null
		) {
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED,
					"로그인이 필요합니다."
			);
		}

		/*
		 * 2. 메시지 검증.
		 */
		if (
				message == null
						|| message.isBlank()
		) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"메시지를 입력해주세요."
			);
		}

		String normalizedMessage = message.trim();

		if (normalizedMessage.length() > 2000) {

			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"메시지는 최대 2000자까지 허용됩니다."
			);
		}

		/*
		 * =================================================
		 * 3. STEP F-2-5A
		 * 필요한 Context만 조회하도록 경로 분리.
		 * =================================================
		 */
		AiManagerRoutingResult routing;

		boolean requiresQuestContext =
				aiManagerContextRouter
						.requiresVerifiedQuestContext(
								normalizedMessage
						);

		if (requiresQuestContext) {

			/*
			 * Quest 개수는 DB 검증 데이터에 의존한다.
			 *
			 * 이번 단계에서는 기존 검증된
			 * ContextService를 그대로 사용한다.
			 */
			AiManagerContextResponse context =
					aiManagerContextService
							.getContext(user);

			routing = aiManagerContextRouter.route(
					normalizedMessage,
					context
			);

		} else {

			/*
			 * DB Context가 필요하지 않은 질문.
			 *
			 * 여행 출발지 확인
			 * 공감 체크인
			 * 일반 SLM 대화
			 *
			 * 위 질문에서는 getContext()를
			 * 호출하지 않는다.
			 */
			routing = aiManagerContextRouter
					.routeWithoutContext(
							normalizedMessage
					);
		}

		/*
		 * 4. Router 응답 검증.
		 */
		if (
				routing == null
						|| routing.decision() == null
		) {
			throw new IllegalStateException(
					"AI Manager Routing 결과가 올바르지 않습니다."
			);
		}

		String responseMode =
				routing.decision().responseMode();

		/*
		 * 5-A. 직접 응답.
		 *
		 * SLM 호출 없이 확정된 문장을 반환한다.
		 */
		if (DIRECT_ANSWER.equals(responseMode)) {

			String directAnswer =
					routing.decision().directAnswer();

			if (
					directAnswer == null
							|| directAnswer.isBlank()
			) {
				throw new IllegalStateException(
						"직접 응답 내용이 비어 있습니다."
				);
			}

			return new AiManagerChatResponse(
					routing.intent(),
					DIRECT_ANSWER,
					directAnswer
			);
		}

		/*
		 * 5-B. SLM 생성 응답.
		 *
		 * 현재 Prompt Builder는 구조화된
		 * 사용자별 Context를 지원하지 않는다.
		 *
		 * 따라서 일반 대화는 Context를
		 * 조회하지 않고 바로 생성한다.
		 */
		if (SLM_GENERATION.equals(responseMode)) {

			String systemPrompt =
					aiManagerPromptBuilder
							.buildSystemPrompt(
									routing
							);

			String answer =
					managerSlmClient.generate(
							systemPrompt,
							normalizedMessage
					);

			return new AiManagerChatResponse(
					routing.intent(),
					SLM_GENERATION,
					answer
			);
		}

		/*
		 * 6. 정의되지 않은 처리 방식 차단.
		 */
		throw new IllegalStateException(
				"지원하지 않는 AI Manager 응답 방식입니다."
		);
	}
}