package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerHomeResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerMemoryResponse;
import com.example.trippaminebe.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * AI Manager 전체 Context 조립 Service.
 *
 * =========================================================
 * 역할
 * =========================================================
 *
 * AiManagerHomeService
 *      +
 * AiManagerMemoryService
 *
 * 를 조합해서
 *
 * AiManagerContextResponse
 *
 * 하나를 만든다.
 *
 *
 * 이 Service에서 중요한 점은
 * 각 도메인의 데이터를 다시 계산하지 않는 것이다.
 *
 *
 * 잘못된 구조:
 *
 * ContextService
 *   ├ EmotionRepository 직접 조회
 *   ├ TravelRepository 직접 조회
 *   ├ QuestRepository 직접 조회
 *   └ MemoryRepository 직접 조회
 *
 *
 * 현재 구조:
 *
 * 이미 검증된 Domain Context Service
 *              ↓
 *      Context Assembly
 *
 *
 * 따라서 이후 도메인 로직이 변경되어도
 * Manager Context 계층이 DB 상세 구현에 직접 종속되지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerContextService {


	private final AiManagerHomeService
			aiManagerHomeService;


	private final AiManagerMemoryService
			aiManagerMemoryService;


	/**
	 * 현재 인증 사용자의
	 * AI Manager 전체 Context Snapshot을 만든다.
	 *
	 * 이 값이 이후:
	 *
	 * Priority Engine
	 *      ↓
	 * Manager SLM
	 *      ↓
	 * Context Router / Chat
	 *
	 * 의 기본 입력이 된다.
	 */
	public AiManagerContextResponse getContext(
			User user
	) {

		/*
		 * =================================================
		 * 1. 인증 사용자 방어
		 * =================================================
		 */

		if (
				user == null
						||
						user.getId() == null
		) {

			throw new IllegalArgumentException(
					"AI Manager Context를 조회하려면 인증된 사용자가 필요합니다."
			);
		}


		/*
		 * =================================================
		 * 2. Home Context 조회
		 * =================================================
		 *
		 * 여기서 이미:
		 *
		 * - User
		 * - Emotion
		 * - Travel
		 * - Quest
		 * - Activity
		 *
		 * 가 검증된 방식으로 조립된다.
		 *
		 * 따라서 이 Service에서 동일 로직을
		 * 다시 구현하지 않는다.
		 */

		AiManagerHomeResponse home =
				aiManagerHomeService
						.getHome(
								user
						);


		/*
		 * =================================================
		 * 3. 장기 Memory 조회
		 * =================================================
		 */

		AiManagerMemoryResponse memory =
				aiManagerMemoryService
						.getMemory(
								user.getId()
						);


		/*
		 * =================================================
		 * 4. Manager Context 조립
		 * =================================================
		 *
		 * generatedAt은 Home Context 생성 시각을 사용한다.
		 *
		 * 이유:
		 *
		 * Context 전체의 기준 시각을 하나로 고정하기 위해서다.
		 *
		 * Memory 내부에는 자체 generatedAt이 존재하지만,
		 * 전체 Snapshot의 기준은 Home generatedAt으로 본다.
		 */

		return new AiManagerContextResponse(

				home.generatedAt(),

				home.user(),

				home.emotion(),

				home.travel(),

				home.quests(),

				home.activities(),

				memory
		);
	}
}