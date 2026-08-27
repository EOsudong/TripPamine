package com.example.trippaminebe.domain.mysterytour.service;

import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import com.example.trippaminebe.domain.mysterytour.dto.MysteryQuestCompleteRequest;
import com.example.trippaminebe.domain.mysterytour.dto.MysteryQuestResponse;
import com.example.trippaminebe.domain.mysterytour.dto.MysteryTourCreateRequest;
import com.example.trippaminebe.domain.mysterytour.dto.MysteryTourCreateResponse;
import com.example.trippaminebe.domain.mysterytour.entity.MysteryQuest;
import com.example.trippaminebe.domain.mysterytour.entity.MysteryProgressStatus;
import com.example.trippaminebe.domain.mysterytour.entity.MysteryTour;
import com.example.trippaminebe.domain.mysterytour.exception.KakaoLocalApiException;
import com.example.trippaminebe.domain.mysterytour.exception.MysteryTourLocationException;
import com.example.trippaminebe.domain.mysterytour.exception.MysteryTourStateConflictException;
import com.example.trippaminebe.domain.mysterytour.repository.MysteryQuestRepository;
import com.example.trippaminebe.domain.mysterytour.repository.MysteryTourRepository;
import com.example.trippaminebe.domain.quest.service.GeoUtils;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MysteryTourService {

	// 여행 퀘스트(QuestService)와 동일한 기본 반경 - 특정 장소를 찾아가는 GPS 퀘스트 기준
	private static final int DEFAULT_GPS_CLEAR_RADIUS_METERS = 100;

	// 1번 퀘스트는 MysteryTourAiService 프롬프트 규칙(7번)상 "목적지로 이동하는 미션"이라
	// 특정 랜드마크가 아니라 도시/군 단위 목적지를 가리킨다. 좌표가 채워져 있어도 100m는
	// 사실상 도달 불가능한 기준이라 더 넉넉한 반경을 별도로 둔다.
	// TODO: 임시 기본값 - 실측 데이터를 보면서 조정 필요.
	private static final int DESTINATION_QUEST_CLEAR_RADIUS_METERS = 3000;

	// 여행 퀘스트(QuestService)와 동일한 기준 - 오차가 이보다 크면 "약한 신호"로 보고 거리 검증 자체를 건너뛴다.
	private static final double MAX_ACCEPTABLE_ACCURACY_METERS = 100.0;
	private static final Set<String> ALLOWED_VERIFY_TYPES = Set.of("GPS", "PHOTO", "SIMPLE");
	private static final int REQUIRED_QUEST_COUNT = 4;

	private final MysteryTourRepository mysteryTourRepository;
	private final MysteryQuestRepository mysteryQuestRepository;
	private final UserRepository userRepository;
	private final MysteryTourAiService mysteryTourAiService;
	private final MysteryQuestLocationResolver mysteryQuestLocationResolver;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Transactional
	public MysteryTourCreateResponse createMysteryTour(

			Long userId,
			MysteryTourCreateRequest request
	) {
		boolean alreadyExists =
				mysteryTourRepository
						.findFirstByUser_IdAndStatusInOrderByCreatedAtDesc(
								userId,
								List.of(
										MysteryProgressStatus.READY,
										MysteryProgressStatus.PROGRESS
								)
						)
						.isPresent();

		if (alreadyExists) {
			throw new MysteryTourStateConflictException(
					"이미 진행 중인 미스터리 투어가 있습니다."
			);
		}
		// 1. 로그인 사용자 조회
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new IllegalArgumentException(
								"사용자를 찾을 수 없습니다. userId=" + userId
						)
				);

		try {

			// 2. OpenAI 호출
			String aiJson =
					mysteryTourAiService.generateMysteryTour(request);

			JsonNode root =
					objectMapper.readTree(aiJson);

			String destination =
					root.path("destination").asText();

			// 3. 미스터리 투어 저장
			MysteryTour mysteryTour =
					MysteryTour.builder()
							.user(user)
							.travelDate(request.getTravelDate())
							.travelDays(request.getTravelDays())
							.peopleCount(request.getPeopleCount())
							.budget(request.getBudget())
							.radiusKm(request.getRadiusKm())
							.departure(request.getDeparture())
							.travelStyle(request.getTravelStyle())
							.destination(destination)
							.aiPlanJson(aiJson)
							.status(MysteryProgressStatus.READY)
							.build();

			MysteryTour savedTour =
					mysteryTourRepository.save(mysteryTour);

			// 4. AI 퀘스트 저장
			JsonNode quests =
					root.path("quests");

			validateGeneratedPlan(destination, quests);

			int questCount = 0;

			for (JsonNode questNode : quests) {

				int order =
						questNode.path("order").asInt();

				String verifyType = questNode.path("verifyType")
						.asText("")
						.strip()
						.toUpperCase(Locale.ROOT);

				KakaoPlace resolvedPlace = "GPS".equals(verifyType)
						? mysteryQuestLocationResolver.resolve(
								destination,
								questNode.path("placeKeyword").asText(null)
						)
						: null;

				MysteryQuest quest =
						MysteryQuest.builder()
								.mysteryTour(savedTour)
								.questOrder(order)
								.questName(
										questNode.path("name").asText()
								)
								.questDesc(
										questNode.path("description").asText()
								)
								.verifyType(verifyType)
								.targetLat(resolvedPlace == null ? null : resolvedPlace.latitude())
								.targetLng(resolvedPlace == null ? null : resolvedPlace.longitude())
								.timeLimitMin(
										getInteger(
												questNode,
												"timeLimitMin"
										)
								)
								.rewardPoint(
										questNode
												.path("rewardPoint")
												.asInt(0)
								)
								.status(MysteryProgressStatus.LOCKED)
								.build();

				mysteryQuestRepository.save(quest);

				questCount++;
			}

			// 5. 목적지는 일부러 응답에 안 보냄
			return MysteryTourCreateResponse.builder()
					.mysteryTourId(
							savedTour.getMysteryTourId()
					)
					.travelDate(
							savedTour.getTravelDate()
					)
					.travelDays(
							savedTour.getTravelDays()
					)
					.peopleCount(
							savedTour.getPeopleCount()
					)
					.budget(
							savedTour.getBudget()
					)
					.questCount(questCount)
					.status(
							savedTour.getStatus().name()
					)
					.destinationLocked(true)
					.build();

		} catch (MysteryTourLocationException | KakaoLocalApiException e) {
			throw e;
		} catch (Exception e) {

			throw new RuntimeException(
					"미스터리 투어 생성 중 오류가 발생했습니다.",
					e
			);
		}
	}

	private void validateGeneratedPlan(String destination, JsonNode quests) {
		if (destination == null || destination.isBlank()) {
			throw new MysteryTourLocationException("AI가 여행 목적지를 생성하지 못했습니다.");
		}

		if (!quests.isArray() || quests.size() != REQUIRED_QUEST_COUNT) {
			throw new MysteryTourLocationException("미스터리 퀘스트는 정확히 4개가 필요합니다.");
		}

		for (JsonNode questNode : quests) {
			String verifyType = questNode.path("verifyType")
					.asText("")
					.strip()
					.toUpperCase(Locale.ROOT);

			if (!ALLOWED_VERIFY_TYPES.contains(verifyType)) {
				throw new MysteryTourLocationException("지원하지 않는 퀘스트 인증 방식입니다: " + verifyType);
			}

			if ("GPS".equals(verifyType)
					&& questNode.path("placeKeyword").asText("").isBlank()) {
				throw new MysteryTourLocationException("GPS 퀘스트의 장소 검색어가 없습니다.");
			}
		}

		JsonNode firstQuest = quests.get(0);
		if (firstQuest.path("order").asInt() != 1
				|| !"GPS".equalsIgnoreCase(firstQuest.path("verifyType").asText())) {
			throw new MysteryTourLocationException("첫 번째 퀘스트는 목적지 이동 GPS 퀘스트여야 합니다.");
		}
	}

	private Integer getInteger(
			JsonNode node,
			String field
	) {

		JsonNode value =
				node.get(field);

		if (
				value == null ||
						value.isNull()
		) {
			return null;
		}

		return value.asInt();
	}

	@Transactional(readOnly = true)
	public MysteryTourCreateResponse getActiveMysteryTour(Long userId) {

		MysteryTour tour = mysteryTourRepository
				.findFirstByUser_IdAndStatusInOrderByCreatedAtDesc(
						userId,
						List.of(
								MysteryProgressStatus.READY,
								MysteryProgressStatus.PROGRESS
						)
				)
				.orElse(null);

		if (tour == null) {
			return null;
		}

		long questCount =
				mysteryQuestRepository
						.countByMysteryTour_MysteryTourId(
								tour.getMysteryTourId()
						);

		return MysteryTourCreateResponse.builder()
				.mysteryTourId(tour.getMysteryTourId())
				.travelDate(tour.getTravelDate())
				.travelDays(tour.getTravelDays())
				.peopleCount(tour.getPeopleCount())
				.budget(tour.getBudget())
				.questCount((int) questCount)
				.status(tour.getStatus().name())
				.destinationLocked(true)
				.build();
	}

	@Transactional(readOnly = true)
	public MysteryQuestResponse getCurrentQuest(Long mysteryTourId) {

		MysteryQuest quest =
				mysteryQuestRepository
						.findFirstByMysteryTour_MysteryTourIdAndStatusOrderByQuestOrderAsc(
								mysteryTourId,
								MysteryProgressStatus.PROGRESS
						)
						.orElse(null);

		if (quest == null) {
			return null;
		}

		return toResponse(quest);
	}

	@Transactional
	public MysteryQuestResponse completeQuest(
			Long mysteryTourId,
			Long mysteryQuestId,
			MysteryQuestCompleteRequest request
	) {
		MysteryTour tour = findTour(mysteryTourId);

		if (tour.getStatus() != MysteryProgressStatus.PROGRESS) {
			throw new MysteryTourStateConflictException("진행 중인 미스터리 투어가 아닙니다.");
		}

		MysteryQuest quest = findQuestForTour(mysteryTourId, mysteryQuestId);

		if (quest.getStatus() != MysteryProgressStatus.PROGRESS) {
			throw new MysteryTourStateConflictException("현재 진행할 수 없는 퀘스트입니다.");
		}

		// GPS 타입만 실제 위치를 검증한다. PHOTO/SIMPLE은 지금처럼 그대로 통과(이번 계획 범위 밖).
		// 실패 시 예외만 던지고 퀘스트 상태는 그대로 PROGRESS로 남기 때문에, 사용자는 위치를 옮긴 뒤
		// 이 API를 다시 호출하는 것만으로 재시도할 수 있다.
		if ("GPS".equals(quest.getVerifyType())) {
			verifyGpsOrThrow(quest, request);
		}

		// 현재 퀘스트 완료
		quest.complete();

		// 다음 퀘스트 찾기
		MysteryQuest nextQuest = mysteryQuestRepository
				.findFirstByMysteryTour_MysteryTourIdAndQuestOrderGreaterThanOrderByQuestOrderAsc(
						mysteryTourId,
						quest.getQuestOrder()
				)
				.orElse(null);

		// 다음 퀘스트가 있으면 PROGRESS
		if (nextQuest != null) {

			nextQuest.start();

			return toResponse(nextQuest);
		}

		// 다음 퀘스트가 없으면 투어 종료
		tour.complete();

		return null;
	}

	@Transactional
	public MysteryQuestResponse skipQuest(
			Long userId,
			Long mysteryTourId,
			Long mysteryQuestId
	) {
		MysteryTour tour = findOwnedTour(userId, mysteryTourId);

		if (tour.getStatus() != MysteryProgressStatus.PROGRESS) {
			throw new MysteryTourStateConflictException("진행 중인 미스터리 투어가 아닙니다.");
		}

		MysteryQuest quest = findQuestForTour(mysteryTourId, mysteryQuestId);
		quest.skip();

		MysteryQuest nextQuest = findNextQuest(mysteryTourId, quest.getQuestOrder());

		if (nextQuest != null) {
			nextQuest.start();
			return toResponse(nextQuest);
		}

		// 마지막 퀘스트를 스킵해도 투어 동선은 끝까지 진행한 것이므로 종료 처리한다.
		// 성공/스킵 비율은 각 MYSTERY_QUESTS.STATUS로 따로 집계할 수 있다.
		tour.complete();
		return null;
	}

	@Transactional
	public void abandonMysteryTour(Long userId, Long mysteryTourId) {
		MysteryTour tour = findOwnedTour(userId, mysteryTourId);
		tour.abandon();

		mysteryQuestRepository
				.findByMysteryTour_MysteryTourIdOrderByQuestOrderAsc(mysteryTourId)
				.forEach(MysteryQuest::abandon);
	}

	// GPS 타입 퀘스트의 현재 위치를 검증한다. verifyType이 GPS인 경우에만 completeQuest()에서 호출된다.
	private void verifyGpsOrThrow(MysteryQuest quest, MysteryQuestCompleteRequest request) {
		if (quest.getTargetLat() == null || quest.getTargetLng() == null) {
			throw new MysteryTourStateConflictException(
					"퀘스트 목표 위치가 설정되지 않았습니다. 현재 퀘스트를 스킵하거나 투어를 다시 생성해주세요."
			);
		}

		if (request == null || request.getCurrentLat() == null || request.getCurrentLng() == null) {
			throw new IllegalArgumentException("현재 위치 정보가 필요합니다.");
		}

		if (request.getAccuracyMeters() != null && request.getAccuracyMeters() > MAX_ACCEPTABLE_ACCURACY_METERS) {
			throw new IllegalArgumentException(
					"GPS 신호가 약합니다. 실외의 개방된 장소에서 다시 시도해주세요. (오차 반경: "
							+ request.getAccuracyMeters() + "m)"
			);
		}

		int clearRadius = quest.getQuestOrder() != null && quest.getQuestOrder() == 1
				? DESTINATION_QUEST_CLEAR_RADIUS_METERS
				: DEFAULT_GPS_CLEAR_RADIUS_METERS;

		double distance = GeoUtils.distanceMeters(
				quest.getTargetLat(), quest.getTargetLng(),
				request.getCurrentLat(), request.getCurrentLng()
		);

		if (distance > clearRadius) {
			throw new IllegalArgumentException(
					"목표 지점에서 너무 멀리 있습니다. (약 " + Math.round(distance) + "m, 인증 반경 " + clearRadius + "m)"
			);
		}
	}

	@Transactional
	public void startMysteryTour(Long mysteryTourId) {

		MysteryTour tour = findTour(mysteryTourId);
		tour.start();

		MysteryQuest firstQuest =
				mysteryQuestRepository
						.findFirstByMysteryTour_MysteryTourIdOrderByQuestOrderAsc(
								mysteryTourId
						)
						.orElseThrow(() ->
								new IllegalArgumentException("퀘스트가 존재하지 않습니다.")
						);

		firstQuest.start();
	}

	@Transactional
	public void cancelMysteryTour(Long mysteryTourId) {

		MysteryTour tour = findTour(mysteryTourId);
		tour.cancel();
	}

	private MysteryTour findTour(Long mysteryTourId) {
		return mysteryTourRepository
				.findById(mysteryTourId)
				.orElseThrow(() ->
						new IllegalArgumentException("미스터리 투어를 찾을 수 없습니다.")
				);
	}

	private MysteryTour findOwnedTour(Long userId, Long mysteryTourId) {
		MysteryTour tour = findTour(mysteryTourId);

		if (!tour.getUser().getId().equals(userId)) {
			// 다른 사용자의 투어 존재 여부를 노출하지 않는다.
			throw new IllegalArgumentException("미스터리 투어를 찾을 수 없습니다.");
		}

		return tour;
	}

	private MysteryQuest findQuestForTour(Long mysteryTourId, Long mysteryQuestId) {
		MysteryQuest quest = mysteryQuestRepository
				.findById(mysteryQuestId)
				.orElseThrow(() ->
						new IllegalArgumentException("퀘스트를 찾을 수 없습니다.")
				);

		if (!quest.getMysteryTour().getMysteryTourId().equals(mysteryTourId)) {
			throw new IllegalArgumentException("해당 투어의 퀘스트가 아닙니다.");
		}

		return quest;
	}

	private MysteryQuest findNextQuest(Long mysteryTourId, Integer questOrder) {
		return mysteryQuestRepository
				.findFirstByMysteryTour_MysteryTourIdAndQuestOrderGreaterThanOrderByQuestOrderAsc(
						mysteryTourId,
						questOrder
				)
				.orElse(null);
	}

	private MysteryQuestResponse toResponse(MysteryQuest quest) {
		return MysteryQuestResponse.builder()
				.mysteryQuestId(quest.getMysteryQuestId())
				.questOrder(quest.getQuestOrder())
				.questName(quest.getQuestName())
				.questDesc(quest.getQuestDesc())
				.verifyType(quest.getVerifyType())
				.targetLat(quest.getTargetLat())
				.targetLng(quest.getTargetLng())
				.clearRadiusMeters(getClearRadiusMeters(quest))
				.rewardPoint(quest.getRewardPoint())
				.status(quest.getStatus().name())
				.build();
	}

	private Integer getClearRadiusMeters(MysteryQuest quest) {
		if (!"GPS".equals(quest.getVerifyType())) {
			return null;
		}

		return quest.getQuestOrder() != null && quest.getQuestOrder() == 1
				? DESTINATION_QUEST_CLEAR_RADIUS_METERS
				: DEFAULT_GPS_CLEAR_RADIUS_METERS;
	}
}
