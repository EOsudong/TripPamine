package com.example.trippaminebe.domain.ai.priority;

import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerPriorityResponse;
import com.example.trippaminebe.domain.ai.dto.LatestEmotionResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;


/**
 * Emotion Domain Priority Rule.
 *
 * =========================================================
 * 역할
 * =========================================================
 *
 * 최신 감정 관찰값이
 * AI Manager가 현재 답변을 만들 때
 * 고려할 만한 신호인지 판단한다.
 *
 *
 * 이 Rule은:
 *
 * - 사용자를 진단하지 않는다.
 * - 위험 상태를 판정하지 않는다.
 * - 사용자에게 보여줄 문장을 만들지 않는다.
 *
 *
 * 오직:
 *
 * "최근의 신뢰 가능한 감정 신호를
 * 현재 Context에서 고려할 필요가 있는가?"
 *
 * 만 판단한다.
 *
 *
 * 사용자에게 보여줄 실제 자연어는
 * 이후 Manager Briefing / Manager SLM 단계에서 담당한다.
 */
@Component
public class EmotionPriorityRule
		implements AiManagerPriorityRule {


	/**
	 * 현재 Attention 후보로 사용하는
	 * coarse emotion.
	 *
	 * "기쁨"은 감정 Context에는 유지하지만
	 * 지금 확인해야 할 Attention 항목으로는 만들지 않는다.
	 */
	private static final Set<String>
			ATTENTION_COARSE_LABELS =
			Set.of(
					"분노",
					"슬픔",
					"불안",
					"상처",
					"당황"
			);


	@Override
	public String domain() {

		return "emotion";
	}


	@Override
	public List<AiManagerPriorityResponse.PriorityItem>
	evaluate(
			AiManagerContextResponse context
	) {

		/* =====================================================
		 * 1. Emotion 존재 여부
		 * ===================================================== */

		if (
				context == null
						||
						context.emotion() == null
		) {

			return List.of();
		}


		LatestEmotionResponse emotion =
				context.emotion();


		/*
		 * Priority가 실제 관찰 결과를 가리키려면
		 * observationId가 반드시 필요하다.
		 */
		if (
				emotion.observationId() == null
		) {

			return List.of();
		}


		/* =====================================================
		 * 2. 관찰 시각 검증
		 * =====================================================
		 *
		 * 오래된 감정을 현재 상태처럼 사용하지 않는다.
		 */

		if (
				emotion.observedAt() == null
						||
						context.generatedAt() == null
		) {

			return List.of();
		}


		Duration age =
				Duration.between(
						emotion.observedAt(),
						context.generatedAt()
				);


		/*
		 * 미래 시각으로 기록된 비정상 데이터는
		 * Priority 판단에서 제외한다.
		 */
		if (
				age.isNegative()
		) {

			return List.of();
		}


		// 정확히 24시간 이상 경과한 감정은 제외한다.
		if (
				age.compareTo(
						Duration.ofHours(
								AiManagerPriorityPolicy.EMOTION_RECENT_HOURS
						)
				) >= 0
		) {
			return List.of();
		}


		/* =====================================================
		 * 3. Confidence 검증
		 * ===================================================== */

		BigDecimal confidence =
				emotion.confidence();


		if (
				confidence == null
						||
						confidence.doubleValue()
								< AiManagerPriorityPolicy
								.EMOTION_MIN_CONFIDENCE
		) {

			return List.of();
		}


		/* =====================================================
		 * 4. coarse emotion 정책
		 * =====================================================
		 *
		 * 현재는:
		 *
		 * 분노 / 슬픔 / 불안 / 상처 / 당황만 Attention 후보로 사용한다.
		 *
		 * 기쁨은 Context에는 남아 있으므로
		 * 이후 Manager SLM이 자연스러운 브리핑을 만들 때
		 * 활용할 수 있다.
		 */

		if (
				emotion.coarseLabel() == null
						||
						!ATTENTION_COARSE_LABELS.contains(
								emotion.coarseLabel()
						)
		) {

			return List.of();
		}


		/* =====================================================
		 * 5. Priority 생성
		 * ===================================================== */

		int score =
				AiManagerPriorityPolicy
						.clampScore(
								AiManagerPriorityPolicy
										.EMOTION_ATTENTION_SCORE
						);


		/*
		 * reasonCode는 내부 판단 근거다.
		 *
		 * 사용자 화면에 이 문자열을 직접 보여주지 않는다.
		 */
		List<String> reasonCodes =
				List.of(
						"EMOTION_RECENT",
						"EMOTION_CONFIDENT_ENOUGH",
						"EMOTION_ATTENTION_SIGNAL"
				);


		AiManagerPriorityResponse.PriorityItem item =
				new AiManagerPriorityResponse.PriorityItem(

						"emotion.latest."
								+ emotion.observationId(),

						domain(),

						"EMOTION_OBSERVATION",

						String.valueOf(
								emotion.observationId()
						),

						score,

						AiManagerPriorityPolicy
								.toLevel(
										score
								),

						reasonCodes
				);


		return List.of(
				item
		);
	}
}