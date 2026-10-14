package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiManagerMemoryResponse;
import com.example.trippaminebe.domain.ai.entity.AiUserFact;
import com.example.trippaminebe.domain.ai.entity.AiUserFactStatus;
import com.example.trippaminebe.domain.ai.repository.AiUserFactRepository;
import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * AI Manager Memory 조회 Service.
 *
 * =========================================================
 * 핵심 역할
 * =========================================================
 *
 * AI_USER_FACTS
 *      ↓
 * ACTIVE Fact 조회
 *      ↓
 * FACT_VALUE_JSON 해석
 *      ↓
 * Observation / Pattern으로 분류
 *      ↓
 * AiManagerMemoryResponse
 *
 *
 * 중요한 원칙:
 *
 * DB의 내부 JSON 구조를
 * Controller나 LLM이 직접 해석하지 않는다.
 *
 * 이 Service가 Memory의 의미를 해석하는
 * 단일 경계가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerMemoryService {


	private static final String
			KIND_OBSERVED_ATTRIBUTE =
			"OBSERVED_ATTRIBUTE";


	private static final String
			KIND_PATTERN_CANDIDATE =
			"PATTERN_CANDIDATE";


	private final AiUserFactRepository
			aiUserFactRepository;


	private final JsonMapper
			jsonMapper;


	/**
	 * 사용자의 현재 ACTIVE Memory를 조회한다.
	 *
	 * 현재 단계에서는:
	 *
	 * - Observation
	 * - Pattern Candidate
	 *
	 * 두 종류만 외부 조회 모델로 제공한다.
	 *
	 * Preference는 아직 만들지 않는다.
	 */
	public AiManagerMemoryResponse getMemory(
			Long userId
	) {

		if (
				userId == null
		) {

			throw new IllegalArgumentException(
					"AI Memory 조회에는 userId가 필요합니다."
			);
		}


		/*
		 * =================================================
		 * 1. 현재 ACTIVE Fact 전체 조회
		 * =================================================
		 */

		List<AiUserFact> facts =
				aiUserFactRepository
						.findAllByUser_IdAndStatusOrderByUpdatedAtDesc(

								userId,

								AiUserFactStatus.ACTIVE
						);


		/*
		 * =================================================
		 * 2. 응답용 Collection 준비
		 * =================================================
		 */

		List<AiManagerMemoryResponse.Observation>
				observations =
				new ArrayList<>();


		List<AiManagerMemoryResponse.Pattern>
				patterns =
				new ArrayList<>();


		/*
		 * =================================================
		 * 3. Fact 종류별 변환
		 * =================================================
		 */

		for (
				AiUserFact fact : facts
		) {

			JsonNode payload =
					readPayload(
							fact
					);


			if (
					payload == null
			) {

				continue;
			}


			String kind =
					readText(
							payload,
							"kind"
					);


			if (
					kind == null
			) {

				continue;
			}


			/*
			 * 직접 관찰 Fact
			 */
			if (
					KIND_OBSERVED_ATTRIBUTE
							.equalsIgnoreCase(
									kind
							)
			) {

				AiManagerMemoryResponse.Observation
						observation =
						toObservation(
								fact,
								payload
						);


				if (
						observation != null
				) {

					observations.add(
							observation
					);
				}


				continue;
			}


			/*
			 * 반복 관찰에서 계산된 Pattern Candidate
			 */
			if (
					KIND_PATTERN_CANDIDATE
							.equalsIgnoreCase(
									kind
							)
			) {

				AiManagerMemoryResponse.Pattern
						pattern =
						toPattern(
								fact,
								payload
						);


				if (
						pattern != null
				) {

					patterns.add(
							pattern
					);
				}
			}
		}


		/*
		 * =================================================
		 * 4. 최종 Memory Snapshot
		 * =================================================
		 */

		return new AiManagerMemoryResponse(

				userId,

				AiTimeProvider
						.nowInServiceZone(),

				List.copyOf(
						observations
				),

				List.copyOf(
						patterns
				)
		);
	}


	/**
	 * OBSERVED_ATTRIBUTE Fact를
	 * 외부 응답 모델로 변환한다.
	 */
	private AiManagerMemoryResponse.Observation
	toObservation(

			AiUserFact fact,

			JsonNode payload

	) {

		String domain =
				readText(
						payload,
						"domain"
				);


		String attribute =
				readText(
						payload,
						"attribute"
				);


		String value =
				readText(
						payload,
						"value"
				);


		if (
				domain == null
						||
						attribute == null
						||
						value == null
		) {

			log.warn(
					"[AI-MEMORY-READ] "
							+ "Observed Fact 필수 필드 누락. factId={}",
					fact.getFactId()
			);


			return null;
		}


		return new AiManagerMemoryResponse.Observation(

				fact.getFactId(),

				fact.getFactKey(),

				domain,

				attribute,

				value,

				fact.getConfidence(),

				safeEvidenceCount(
						fact
				),

				fact.getFirstObservedAt(),

				fact.getLastObservedAt()
		);
	}


	/**
	 * PATTERN_CANDIDATE Fact를
	 * 외부 응답 모델로 변환한다.
	 */
	private AiManagerMemoryResponse.Pattern
	toPattern(

			AiUserFact fact,

			JsonNode payload

	) {

		String domain =
				readText(
						payload,
						"domain"
				);


		String attribute =
				readText(
						payload,
						"attribute"
				);


		String value =
				readText(
						payload,
						"value"
				);


		Integer supportCount =
				readInteger(
						payload,
						"supportCount"
				);


		Integer totalObservedCount =
				readInteger(
						payload,
						"totalObservedCount"
				);


		BigDecimal share =
				readBigDecimal(
						payload,
						"share"
				);


		BigDecimal maturity =
				readBigDecimal(
						payload,
						"maturity"
				);


		/*
		 * Confidence는 JSON에도 있지만
		 * AI_USER_FACTS.CONFIDENCE가
		 * 현재 Fact의 canonical 값이다.
		 *
		 * 따라서 외부 응답에서는 DB Column 값을 사용한다.
		 */
		BigDecimal confidence =
				fact.getConfidence();


		if (
				domain == null
						||
						attribute == null
						||
						value == null
						||
						supportCount == null
						||
						totalObservedCount == null
						||
						share == null
						||
						maturity == null
		) {

			log.warn(
					"[AI-MEMORY-READ] "
							+ "Pattern Fact 필수 필드 누락. factId={}",
					fact.getFactId()
			);


			return null;
		}


		return new AiManagerMemoryResponse.Pattern(

				fact.getFactId(),

				fact.getFactKey(),

				domain,

				attribute,

				value,

				supportCount,

				totalObservedCount,

				share,

				maturity,

				confidence,

				safeEvidenceCount(
						fact
				),

				fact.getFirstObservedAt(),

				fact.getLastObservedAt()
		);
	}


	/**
	 * Fact의 JSON을 안전하게 읽는다.
	 *
	 * Memory 한 건이 깨졌다고 해서
	 * 전체 AI Manager Memory 조회를 실패시키지 않는다.
	 */
	private JsonNode readPayload(
			AiUserFact fact
	) {

		String factValueJson =
				fact.getFactValueJson();


		if (
				factValueJson == null
						||
						factValueJson.isBlank()
		) {

			return null;
		}


		try {

			return jsonMapper
					.readTree(
							factValueJson
					);

		} catch (
				RuntimeException e
		) {

			log.warn(
					"[AI-MEMORY-READ] "
							+ "FACT_VALUE_JSON 파싱 실패. factId={}",
					fact.getFactId(),
					e
			);


			return null;
		}
	}


	/**
	 * JSON 문자열 필드를 안전하게 읽는다.
	 */
	private String readText(

			JsonNode payload,

			String field

	) {

		JsonNode node =
				payload.get(
						field
				);


		if (
				node == null
						||
						node.isNull()
		) {

			return null;
		}


		try {

			String value =
					node.asString();


			if (
					value == null
							||
							value.isBlank()
			) {

				return null;
			}


			return value.trim();

		} catch (
				RuntimeException ignored
		) {

			return null;
		}
	}


	/**
	 * JSON 정수 필드를 안전하게 읽는다.
	 */
	private Integer readInteger(

			JsonNode payload,

			String field

	) {

		String value =
				readScalarAsString(
						payload,
						field
				);


		if (
				value == null
		) {

			return null;
		}


		try {

			return Integer.valueOf(
					value
			);

		} catch (
				NumberFormatException ignored
		) {

			return null;
		}
	}


	/**
	 * JSON Decimal 필드를 안전하게 읽는다.
	 */
	private BigDecimal readBigDecimal(

			JsonNode payload,

			String field

	) {

		String value =
				readScalarAsString(
						payload,
						field
				);


		if (
				value == null
		) {

			return null;
		}


		try {

			return new BigDecimal(
					value
			);

		} catch (
				NumberFormatException ignored
		) {

			return null;
		}
	}


	/**
	 * 숫자 / 문자열 Scalar 값을
	 * String 형태로 안전하게 읽는다.
	 */
	private String readScalarAsString(

			JsonNode payload,

			String field

	) {

		JsonNode node =
				payload.get(
						field
				);


		if (
				node == null
						||
						node.isNull()
		) {

			return null;
		}


		try {

			String value =
					node.asString();


			if (
					value == null
							||
							value.isBlank()
			) {

				return null;
			}


			return value.trim();

		} catch (
				RuntimeException ignored
		) {

			return null;
		}
	}


	/**
	 * DB 값이 혹시 null이더라도
	 * API에는 음수/Null Evidence Count를 내보내지 않는다.
	 */
	private int safeEvidenceCount(
			AiUserFact fact
	) {

		Integer evidenceCount =
				fact.getEvidenceCount();


		if (
				evidenceCount == null
						||
						evidenceCount < 0
		) {

			return 0;
		}


		return evidenceCount;
	}
}