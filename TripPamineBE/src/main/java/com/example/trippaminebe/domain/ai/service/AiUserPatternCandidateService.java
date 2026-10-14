package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.entity.AiUserFact;
import com.example.trippaminebe.domain.ai.entity.AiUserFactEvidence;
import com.example.trippaminebe.domain.ai.entity.AiUserFactSourceType;
import com.example.trippaminebe.domain.ai.entity.AiUserFactStatus;
import com.example.trippaminebe.domain.ai.repository.AiUserFactEvidenceRepository;
import com.example.trippaminebe.domain.ai.repository.AiUserFactRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;


/**
 * 반복된 AI Memory Evidence를 분석해서
 * "Pattern Candidate"를 만드는 Service.
 *
 *
 * =========================================================
 * 중요한 설계 원칙
 * =========================================================
 *
 * Pattern Candidate는 Preference가 아니다.
 *
 * 예:
 *
 * travel.observed.companion_type.alone
 *
 * Evidence가 반복됐다고 해서 바로:
 *
 * "사용자는 혼자 여행을 선호한다"
 *
 * 로 저장하지 않는다.
 *
 *
 * 대신:
 *
 * travel.pattern.companion_type.alone
 *
 * 이라는 Pattern Candidate를 만든다.
 *
 *
 * 이후 단계에서:
 *
 * - Evidence 수
 * - 다른 값과의 비율
 * - 최근성
 * - 사용자 직접 선언
 *
 * 등을 종합해서 Preference로 승격할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUserPatternCandidateService {

	/**
	 * Pattern Candidate가 만들어지기 위한
	 * 최소 관찰 횟수.
	 *
	 * 한 번의 행동만으로 Pattern을 만들지 않는다.
	 */
	private static final int
			MIN_PATTERN_SUPPORT = 2;


	/**
	 * maturity가 1.0에 도달하는 관찰 횟수.
	 *
	 * 5번 이상 같은 행동이 관찰되면
	 * 반복성 측면에서는 충분히 성숙했다고 본다.
	 *
	 * 이것은 Preference 확정 기준이 아니라
	 * Pattern Candidate Confidence 계산용 값이다.
	 */
	private static final int
			FULL_MATURITY_SUPPORT = 5;


	private final AiUserFactRepository
			aiUserFactRepository;

	private final AiUserFactEvidenceRepository
			aiUserFactEvidenceRepository;

	private final UserRepository
			userRepository;

	private final JsonMapper
			jsonMapper;


	/**
	 * 사용자의 Travel 관찰 Fact들을 다시 계산해서
	 * Pattern Candidate를 생성/갱신한다.
	 *
	 *
	 * 현재 입력:
	 *
	 * travel.observed.*
	 *
	 *
	 * 출력:
	 *
	 * travel.pattern.*
	 */
	@Transactional(
			propagation = Propagation.REQUIRES_NEW
	)
	public void refreshTravelPatternCandidates(
			Long userId
	) {

		if (
				userId == null
		) {

			return;
		}


		/*
		 * =================================================
		 * 1. 현재 ACTIVE Memory Fact 조회
		 * =================================================
		 */

		List<AiUserFact> facts =
				aiUserFactRepository
						.findAllByUser_IdAndStatusOrderByUpdatedAtDesc(

								userId,

								AiUserFactStatus.ACTIVE
						);


		if (
				facts.isEmpty()
		) {

			return;
		}


		/*
		 * =================================================
		 * 2. 관찰 Fact만 읽기
		 * =================================================
		 */

		List<ObservedFact>
				observedFacts =
				new ArrayList<>();


		for (
				AiUserFact fact : facts
		) {

			/*
			 * Pattern Candidate 자신이나
			 * 앞으로 추가될 다른 종류의 Fact는 제외한다.
			 */
			if (
					fact.getFactKey() == null
							||
							!fact.getFactKey()
									.startsWith(
											"travel.observed."
									)
			) {

				continue;
			}


			ObservedFact observedFact =
					readObservedFact(
							fact
					);


			if (
					observedFact != null
							&&
							observedFact.supportCount() > 0
			) {

				observedFacts.add(
						observedFact
				);
			}
		}


		if (
				observedFacts.isEmpty()
		) {

			return;
		}


		/*
		 * =================================================
		 * 3. Attribute별 전체 Evidence 수 계산
		 * =================================================
		 *
		 * 예:
		 *
		 * companion_type
		 *
		 * ALONE  = 4
		 * FAMILY = 2
		 *
		 * total = 6
		 *
		 *
		 * ALONE share = 4 / 6
		 */
		Map<String, Integer>
				totalByAttribute =
				new LinkedHashMap<>();


		for (
				ObservedFact observedFact : observedFacts
		) {

			totalByAttribute.merge(

					observedFact.attribute(),

					observedFact.supportCount(),

					Integer::sum
			);
		}


		/*
		 * =================================================
		 * 4. 최소 Support 이상인 값만 Candidate 생성
		 * =================================================
		 */

		for (
				ObservedFact observedFact : observedFacts
		) {

			if (
					observedFact.supportCount()
							< MIN_PATTERN_SUPPORT
			) {

				continue;
			}


			int totalObservedCount =
					totalByAttribute.getOrDefault(

							observedFact.attribute(),

							observedFact.supportCount()
					);


			upsertPatternCandidate(

					userId,

					observedFact,

					totalObservedCount
			);
		}
	}


	/**
	 * 하나의 관찰 Fact를 Pattern Candidate로 변환한다.
	 */
	private void upsertPatternCandidate(
			Long userId,
			ObservedFact observedFact,
			int totalObservedCount
	) {

		int supportCount =
				observedFact.supportCount();


		/*
		 * =================================================
		 * 1. Share 계산
		 * =================================================
		 *
		 * 같은 Attribute 안에서
		 * 이 값이 차지하는 비율.
		 */
		BigDecimal share =
				divide(

						supportCount,

						Math.max(
								totalObservedCount,
								1
						)
				);


		/*
		 * =================================================
		 * 2. Maturity 계산
		 * =================================================
		 *
		 * support 2 → 0.4
		 * support 3 → 0.6
		 * support 4 → 0.8
		 * support 5+ → 1.0
		 */
		BigDecimal maturity =
				divide(

						Math.min(
								supportCount,
								FULL_MATURITY_SUPPORT
						),

						FULL_MATURITY_SUPPORT
				);


		/*
		 * =================================================
		 * 3. Pattern Confidence
		 * =================================================
		 *
		 * Confidence =
		 *
		 * Attribute Share
		 *      ×
		 * Observation Maturity
		 *
		 *
		 * 현재 ALONE 2 / 2라면:
		 *
		 * share    = 1.0
		 * maturity = 0.4
		 *
		 * confidence = 0.4
		 */
		BigDecimal confidence =
				share
						.multiply(
								maturity
						)
						.setScale(
								8,
								RoundingMode.HALF_UP
						);


		/*
		 * =================================================
		 * 4. Pattern Fact Key
		 * =================================================
		 */

		String normalizedValue =
				normalizeKeyComponent(
						observedFact.value()
				);


		if (
				normalizedValue == null
		) {

			return;
		}


		String patternFactKey =
				observedFact.domain()
						+ ".pattern."
						+ observedFact.attribute()
						+ "."
						+ normalizedValue;


		/*
		 * =================================================
		 * 5. Candidate JSON
		 * =================================================
		 */

		Map<String, Object>
				valueJson =
				new LinkedHashMap<>();


		valueJson.put(
				"domain",
				observedFact.domain()
		);

		valueJson.put(
				"kind",
				"PATTERN_CANDIDATE"
		);

		valueJson.put(
				"attribute",
				observedFact.attribute()
		);

		valueJson.put(
				"value",
				observedFact.value()
		);

		valueJson.put(
				"supportCount",
				supportCount
		);

		valueJson.put(
				"totalObservedCount",
				totalObservedCount
		);

		valueJson.put(
				"share",
				share
		);

		valueJson.put(
				"maturity",
				maturity
		);

		valueJson.put(
				"confidence",
				confidence
		);


		/*
		 * =================================================
		 * 6. Pattern의 근거가 되는 원본 Evidence 조회
		 * =================================================
		 *
		 * Pattern Candidate의 EVIDENCE_COUNT는
		 * 실제 Observed Fact Evidence 개수를 기준으로 한다.
		 *
		 * 이 값을 Candidate INSERT 전에 이미 알고 있으므로
		 * 새 Candidate를 INSERT한 직후 다시 UPDATE할 필요가 없다.
		 */
		List<AiUserFactEvidence>
				sourceEvidences =
				aiUserFactEvidenceRepository
						.findAllByFact_FactIdOrderByCreatedAtAsc(

								observedFact
										.fact()
										.getFactId()
						);


		int sourceEvidenceCount =
				sourceEvidences.size();


		/*
		 * =================================================
		 * 7. 기존 Pattern Candidate 조회
		 * =================================================
		 */

		AiUserFact candidate =
				aiUserFactRepository
						.findByUser_IdAndFactKey(

								userId,

								patternFactKey
						)
						.orElse(null);


		/*
		 * =================================================
		 * 8. Pattern Candidate 생성 / 갱신
		 * =================================================
		 */

		if (
				candidate == null
		) {

			User user =
					userRepository
							.getReferenceById(
									userId
							);


			/*
			 * 중요:
			 *
			 * 새 Candidate는 처음 INSERT할 때부터
			 * 정확한 Evidence Count를 넣는다.
			 *
			 * 따라서 INSERT 직후
			 *
			 * LAST_OBSERVED_AT
			 * UPDATED_AT
			 *
			 * 을 Java 시간으로 다시 UPDATE하지 않는다.
			 *
			 * FIRST_OBSERVED_AT / LAST_OBSERVED_AT은
			 * 같은 Oracle DB DEFAULT 시계로 생성되므로
			 *
			 * CK_AUF_OBSERVED_TIME
			 *
			 * 제약조건도 안전하게 만족한다.
			 */
			candidate =
					AiUserFact
							.builder()

							.user(
									user
							)

							.factKey(
									patternFactKey
							)

							.factValueJson(
									toJson(
											valueJson
									)
							)

							.sourceType(
									AiUserFactSourceType
											.SYSTEM_INFERRED
							)

							.confidence(
									confidence
							)

							.evidenceCount(
									sourceEvidenceCount
							)

							.status(
									AiUserFactStatus.ACTIVE
							)

							.build();


			candidate =
					aiUserFactRepository
							.saveAndFlush(
									candidate
							);

		} else {

			/*
			 * 기존 Candidate는 이미 FIRST_OBSERVED_AT이
			 * 과거 시점으로 확정돼 있다.
			 *
			 * 따라서 일반적인 갱신 시점에는
			 * updateFact()의 현재 시각이 FIRST_OBSERVED_AT보다
			 * 이후가 된다.
			 */
			candidate.updateFact(

					toJson(
							valueJson
					),

					AiUserFactSourceType
							.SYSTEM_INFERRED,

					confidence
			);


			candidate.setEvidenceCount(
					sourceEvidenceCount
			);


			aiUserFactRepository
					.saveAndFlush(
							candidate
					);
		}


		/*
		 * =================================================
		 * 9. 원본 Observed Evidence를
		 *    Pattern Candidate Evidence로 연결
		 * =================================================
		 */

		for (
				AiUserFactEvidence sourceEvidence
				: sourceEvidences
		) {

			Long eventId =
					sourceEvidence
							.getEvent()
							.getEventId();


			boolean exists =
					aiUserFactEvidenceRepository
							.existsByFact_FactIdAndEvent_EventId(

									candidate.getFactId(),

									eventId
							);


			if (
					exists
			) {

				continue;
			}


			AiUserFactEvidence candidateEvidence =
					AiUserFactEvidence
							.builder()

							.fact(
									candidate
							)

							.event(
									sourceEvidence
											.getEvent()
							)

							.weight(
									sourceEvidence
											.getWeight()
							)

							.build();


			aiUserFactEvidenceRepository
					.saveAndFlush(
							candidateEvidence
					);
		}


		/*
		 * =================================================
		 * 10. Pattern Evidence 정합성 검증
		 * =================================================
		 *
		 * Candidate Fact의 EVIDENCE_COUNT와
		 * 실제 연결된 Evidence 행 수가 다르면
		 * 조용히 잘못된 Memory를 남기지 않고
		 * Pattern Transaction만 Rollback한다.
		 */
		int actualCandidateEvidenceCount =
				aiUserFactEvidenceRepository
						.findAllByFact_FactIdOrderByCreatedAtAsc(

								candidate.getFactId()
						)
						.size();


		if (
				actualCandidateEvidenceCount
						!= sourceEvidenceCount
		) {

			throw new IllegalStateException(
					"Pattern Candidate Evidence 개수가 일치하지 않습니다. "
							+ "factKey="
							+ patternFactKey
							+ ", expected="
							+ sourceEvidenceCount
							+ ", actual="
							+ actualCandidateEvidenceCount
			);
		}

		log.info(
				"[AI-MEMORY-PATTERN] Pattern Candidate 갱신. "
						+ "userId={}, factKey={}, support={}, total={}, "
						+ "share={}, maturity={}, confidence={}",
				userId,
				patternFactKey,
				supportCount,
				totalObservedCount,
				share,
				maturity,
				confidence
		);
	}


	/**
	 * OBSERVED_ATTRIBUTE JSON을
	 * 내부 분석용 Record로 변환한다.
	 */
	private ObservedFact readObservedFact(
			AiUserFact fact
	) {

		if (
				fact.getFactValueJson() == null
						||
						fact.getFactValueJson().isBlank()
		) {

			return null;
		}


		try {

			JsonNode payload =
					jsonMapper
							.readTree(
									fact.getFactValueJson()
							);


			String kind =
					readText(
							payload,
							"kind"
					);


			if (
					!"OBSERVED_ATTRIBUTE"
							.equalsIgnoreCase(
									kind
							)
			) {

				return null;
			}


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

				return null;
			}


			int supportCount =
					fact.getEvidenceCount() != null
							? fact.getEvidenceCount()
							: 0;


			return new ObservedFact(

					fact,

					domain,

					attribute,

					value,

					supportCount
			);

		} catch (
				RuntimeException e
		) {

			log.warn(
					"[AI-MEMORY-PATTERN] "
							+ "Observed Fact JSON 파싱 실패. factId={}",
					fact.getFactId(),
					e
			);


			return null;
		}
	}


	/**
	 * JsonNode String 안전 조회.
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
	 * BigDecimal 비율 계산.
	 */
	private BigDecimal divide(
			int numerator,
			int denominator
	) {

		if (
				denominator <= 0
		) {

			return BigDecimal.ZERO
					.setScale(
							8,
							RoundingMode.HALF_UP
					);
		}


		return BigDecimal
				.valueOf(
						numerator
				)
				.divide(

						BigDecimal.valueOf(
								denominator
						),

						8,

						RoundingMode.HALF_UP
				);
	}


	/**
	 * Fact Key용 문자열 정규화.
	 */
	private String normalizeKeyComponent(
			String value
	) {

		if (
				value == null
						||
						value.isBlank()
		) {

			return null;
		}


		String normalized =
				value
						.trim()
						.toLowerCase(
								Locale.ROOT
						)
						.replaceAll(
								"[^a-z0-9가-힣_-]+",
								"_"
						)
						.replaceAll(
								"^_+|_+$",
								""
						);


		if (
				normalized.isBlank()
		) {

			return null;
		}


		if (
				normalized.length() > 50
		) {

			normalized =
					normalized.substring(
							0,
							50
					);
		}


		return normalized;
	}


	/**
	 * JSON 직렬화.
	 */
	private String toJson(
			Object value
	) {

		try {

			return jsonMapper
					.writeValueAsString(
							value
					);

		} catch (
				RuntimeException e
		) {

			throw new IllegalStateException(
					"AI Pattern Candidate JSON 직렬화에 실패했습니다.",
					e
			);
		}
	}


	/**
	 * Pattern 계산 중에만 사용하는 내부 구조.
	 */
	private record ObservedFact(

			AiUserFact fact,

			String domain,

			String attribute,

			String value,

			int supportCount

	) {
	}
}