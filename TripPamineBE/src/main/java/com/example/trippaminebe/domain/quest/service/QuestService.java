package com.example.trippaminebe.domain.quest.service;

import com.example.trippaminebe.domain.quest.dto.request.QuestClearRequest;
import com.example.trippaminebe.domain.quest.dto.request.QuestRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestResponse;
import com.example.trippaminebe.domain.quest.dto.response.QuestStepClearResponse;
import com.example.trippaminebe.domain.quest.dto.response.QuestStepResponse;
import com.example.trippaminebe.domain.quest.dto.response.UserQuestLogResponse;
import com.example.trippaminebe.domain.quest.entity.Quest;
import com.example.trippaminebe.domain.quest.entity.QuestChapter;
import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.entity.QuestStep;
import com.example.trippaminebe.domain.quest.entity.QuestType;
import com.example.trippaminebe.domain.quest.entity.UserQuestLog;
import com.example.trippaminebe.domain.quest.entity.UserQuestStepLog;
import com.example.trippaminebe.domain.quest.event.QuestCompletedApplicationEvent;
import com.example.trippaminebe.domain.quest.exception.QuestChapterNotFoundException;
import com.example.trippaminebe.domain.quest.exception.QuestDeleteConflictException;
import com.example.trippaminebe.domain.quest.exception.QuestNotFoundException;
import com.example.trippaminebe.domain.quest.repository.QuestChapterRepository;
import com.example.trippaminebe.domain.quest.repository.QuestRepository;
import com.example.trippaminebe.domain.quest.repository.QuestStepRepository;
import com.example.trippaminebe.domain.quest.repository.UserQuestLogRepository;
import com.example.trippaminebe.domain.quest.repository.UserQuestStepLogRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


/**
 * Quest 핵심 Business Service.
 *
 * Quest 성공/실패 판정과 포인트 지급은
 * 이 Service가 계속 책임진다.
 *
 * RPG 확장에서 레벨/EXP/칭호 반영은 RpgStatsService에 위임한다
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QuestService {

	/**
	 * GPS Horizontal Accuracy가 이 값을 넘으면
	 * 신뢰할 수 없는 위치로 판단한다.
	 */
	private static final double
			MAX_ACCEPTABLE_ACCURACY_METERS =
			200.0;


	private final QuestRepository
			questRepository;

	private final QuestChapterRepository
			questChapterRepository;

	private final UserQuestLogRepository
			userQuestLogRepository;

	private final QuestStepRepository
			questStepRepository;

	private final UserQuestStepLogRepository
			userQuestStepLogRepository;

	private final UserRepository
			userRepository;

	private final RpgStatsService
			rpgStatsService;


	/**
	 * Spring Application Event 발행기.
	 *
	 * QuestService가 AI Manager를 직접 알 필요 없이
	 * "Quest가 완료되었다"는 사실만 전달한다.
	 */
	private final ApplicationEventPublisher
			applicationEventPublisher;


	/* =====================================================
	 * Quest 조회 (관리자용 - 비활성 포함 전체)
	 * ===================================================== */

	@Transactional(readOnly = true)
	public List<QuestResponse> findAll() {

		return questRepository
				.findAll()
				.stream()
				.map(QuestResponse::from)
				.toList();
	}


	/* =====================================================
	 * Quest 조회 (사용자용 - 활성 퀘스트만, 레벨 잠금 표시 포함)
	 * ===================================================== */

	@Transactional(readOnly = true)
	public List<QuestResponse> findAllForUser(Long userId) {

		Integer viewerLevel = rpgStatsService.findMyLevel(userId);

		return questRepository
				.findByIsActiveTrue()
				.stream()
				.map(quest -> QuestResponse.from(quest, viewerLevel))
				.toList();
	}


	@Transactional(readOnly = true)
	public QuestResponse findById(
			Long questId
	) {

		return QuestResponse.from(
				getQuestOrThrow(
						questId
				)
		);
	}


	@Transactional(readOnly = true)
	public QuestResponse findByIdForUser(
			Long questId,
			Long userId
	) {

		Integer viewerLevel = rpgStatsService.findMyLevel(userId);

		return QuestResponse.from(
				getQuestOrThrow(questId),
				viewerLevel
		);
	}


	@Transactional(readOnly = true)
	public List<UserQuestLogResponse> findMyLogs(
			Long userId
	) {

		return userQuestLogRepository
				.findByUser_IdOrderByLogIdDesc(
						userId
				)
				.stream()
				.map(
						this::toLogResponseWithStepProgress
				)
				.toList();
	}


	// CHAIN 퀘스트인 로그에만 단계 진행률(N/M)을 채워서 응답한다. 일반 퀘스트는 그대로.
	private UserQuestLogResponse toLogResponseWithStepProgress(
			UserQuestLog log
	) {

		if (
				log.getQuest().getQuestType()
						!= QuestType.CHAIN
		) {
			return UserQuestLogResponse.from(log);
		}

		long totalSteps =
				questStepRepository.countByQuest_QuestId(
						log.getQuest().getQuestId()
				);

		long clearedSteps =
				userQuestStepLogRepository.countByQuestLog_LogId(
						log.getLogId()
				);

		return UserQuestLogResponse.from(
				log,
				(int) clearedSteps,
				(int) totalSteps
		);
	}


	/* =====================================================
	 * Quest 시작
	 * ===================================================== */

	/**
	 * 이미 진행 중/완료된 로그가 존재하면
	 * 중복 생성하지 않고 기존 로그를 반환한다.
	 *
	 * 퀘스트에 REQUIRED_LEVEL이 설정되어 있으면, 사용자의 현재 RPG 레벨이
	 * 그보다 낮을 경우 시작 자체를 막는다(지도 상 "잠김" 상태와 서버단 방어를 일치시킴).
	 */
	public UserQuestLogResponse start(
			Long userId,
			Long questId
	) {
		// 같은 사용자의 두 시작 요청이 동시에 들어와도 로그/도전 횟수는 한 번만 생성한다.
		User user = lockUserForQuestOperation(userId);
		Quest quest = getQuestOrThrow(questId);

		// 기존 진행 기록은 운영자가 퀘스트를 비활성화했더라도 조회할 수 있다.
		var existingLog = userQuestLogRepository.findByUser_IdAndQuest_QuestId(userId, questId);
		if (existingLog.isPresent()) {
			return UserQuestLogResponse.from(existingLog.get());
		}

		if (!Boolean.TRUE.equals(quest.getIsActive())) {
			throw new IllegalArgumentException("비공개 또는 비활성화된 퀘스트는 새로 시작할 수 없습니다.");
		}

		// 현재 실제 인증 구현이 있는 LOCATION/CHAIN만 수락할 수 있다.
		// 다른 유형은 관리자 작성은 가능하지만 인증기 구현 전에는 수락/보상을 막는다.
		if (quest.getQuestType() != QuestType.LOCATION && quest.getQuestType() != QuestType.CHAIN) {
			throw new IllegalArgumentException("해당 퀘스트 유형은 인증 기능을 준비 중이므로 아직 시작할 수 없습니다.");
		}

		Integer userLevel = rpgStatsService.findMyLevel(userId);
		int questRequiredLevel = quest.getRequiredLevel() != null ? quest.getRequiredLevel() : 1;
		int chapterRequiredLevel = quest.getChapter() != null && quest.getChapter().getRequiredLevel() != null
				? quest.getChapter().getRequiredLevel() : 1;
		int requiredLevel = Math.max(questRequiredLevel, chapterRequiredLevel);

		// 챕터의 잠금은 단순 UI 표시가 아니라 서버에서도 반드시 검증한다.
		if (userLevel < requiredLevel) {
			throw new IllegalArgumentException(
					"레벨이 부족하여 시작할 수 없는 퀘스트입니다. 필요 레벨: "
							+ requiredLevel + ", 현재 레벨: " + userLevel
			);
		}

		// 단계가 정의되지 않은 연쇄 퀘스트를 수락하면 사용자가 영원히 진행할 수 없게 된다.
		if (quest.getQuestType() == QuestType.CHAIN
				&& questStepRepository.countByQuest_QuestId(questId) == 0) {
			throw new IllegalArgumentException("등록된 단계가 없는 연쇄 퀘스트는 시작할 수 없습니다.");
		}

		UserQuestLog log = userQuestLogRepository.save(
				UserQuestLog.builder()
						.user(user)
						.quest(quest)
						.status(QuestStatus.PROGRESS)
						.build()
		);
		rpgStatsService.onQuestStarted(user);
		return UserQuestLogResponse.from(log);
	}


	/* =====================================================
	 * Quest Clear
	 * ===================================================== */

	/**
	 * 실제 GPS 좌표를 검증해 Quest를 성공/실패 처리한다.
	 *
	 * 성공 시:
	 *
	 * 1. Quest Log SUCCESS
	 * 2. Point / EXP 지급, 레벨업 반영, 칭호 자동 평가 (RpgStatsService)
	 * 3. QuestCompletedApplicationEvent 발행
	 *
	 *
	 * Event는 여기서 DB에 직접 저장하지 않는다.
	 */
	public UserQuestLogResponse clear(
			Long userId,
			Long questId,
			QuestClearRequest request
	) {

		// 사용자 포인트와 퀘스트 로그를 같은 트랜잭션 안에서 직렬화한다.
		lockUserForQuestOperation(userId);
		Quest quest =
				getQuestOrThrow(
						questId
				);


		/*
		 * 연쇄(CHAIN) 퀘스트는 좌표 하나만 검증하는 이 엔드포인트로 클리어할 수 없다.
		 * 단계별로 /quests/{questId}/steps/{stepId}/clear 를 순서대로 호출해야 한다.
		 */
		if (quest.getQuestType() == QuestType.CHAIN) {
			throw new IllegalArgumentException(
					"연쇄 퀘스트는 단계별 클리어 API(/quests/" + questId + "/steps/{stepId}/clear)를 사용해주세요."
			);
		}
		// PHOTO/DISCOVERY/ACTION은 아직 별도 인증기가 없으므로 GPS만으로 보상받을 수 없다.
		if (quest.getQuestType() != QuestType.LOCATION) {
			throw new IllegalArgumentException(
					"해당 퀘스트 유형은 현장 복합 인증 기능을 준비 중입니다. GPS만으로 완료할 수 없습니다."
			);
		}


		UserQuestLog log =
				userQuestLogRepository
						.findByUser_IdAndQuest_QuestId(
								userId,
								questId
						)
						.orElseThrow(
								() ->
										new QuestNotFoundException(
												"아직 시작하지 않은 퀘스트입니다. 먼저 퀘스트를 시작해주세요."
										)
						);


		/*
		 * 이미 성공한 Quest를 다시 성공시키면
		 * Point 중복 지급 문제가 발생할 수 있으므로 막는다.
		 *
		 * FAILED는 다시 위치를 이동해서 재도전할 수 있다.
		 */
		if (
				log.getStatus()
						== QuestStatus.SUCCESS
		) {

			throw new IllegalArgumentException(
					"이미 클리어 완료된 퀘스트입니다."
			);
		}


		/*
		 * GPS 신호 자체가 너무 부정확한 경우에는
		 * 거리 계산을 수행하지 않는다.
		 */
		validateGpsRequest(request);


		int clearRadius =
				quest.getClearRadius() != null
						? quest.getClearRadius()
						: Quest.DEFAULT_CLEAR_RADIUS_METERS;


		double distance =
				GeoUtils.distanceMeters(

						quest.getTargetLat(),
						quest.getTargetLng(),

						request.getCurrentLat(),
						request.getCurrentLng()
				);


		/*
		 * 목표 반경 밖이면 실패.
		 *
		 * 실패하더라도 사용자가 이동한 뒤 다시 시도할 수 있다.
		 */
		if (
				distance > clearRadius
		) {

			log.fail(
					request.getCurrentLat(),
					request.getCurrentLng()
			);

			return UserQuestLogResponse.from(
					log
			);
		}


		/* =================================================
		 * 성공 처리
		 * ================================================= */

		User user =
				log.getUser();


		Long rewardPoint =
				quest.getRewardPoint() != null
						? quest.getRewardPoint()
						: 0L;

		Long rewardExp =
				quest.getRewardExp() != null
						? quest.getRewardExp()
						: 0L;


		log.success(
				rewardPoint,
				rewardExp,
				request.getCurrentLat(),
				request.getCurrentLng()
		);


		/*
		 * Point 지급.
		 */
		user.setTotalPoints(
				user.getTotalPoints()
						+ rewardPoint
		);


		/*
		 * EXP 지급 + 레벨업 + 칭호 자동 평가.
		 * (레벨업 자체는 이 트랜잭션 안에서 즉시 반영되지만, 현재 응답 DTO에는
		 *  레벨업 여부를 아직 내려주지 않는다 - 프런트는 /quests/me/stats를
		 *  다시 호출해 최신 레벨을 확인한다. "레벨업!" 연출은 다음 단계(RPG UI)에서
		 *  이 반환값을 활용해 추가하면 된다.)
		 */
		rpgStatsService.onQuestCompleted(
				user,
				rewardExp
		);


		/*
		 * =================================================
		 * Domain Event 발행
		 * =================================================
		 *
		 * 현재 Transaction 안에서 Event를 발행한다.
		 *
		 * 하지만 AI Manager Listener는
		 *
		 * AFTER_COMMIT
		 *
		 * 으로 설정할 것이므로,
		 *
		 * Quest Transaction이 실제 COMMIT 된 경우에만
		 * AI_MANAGER_EVENTS를 기록한다.
		 *
		 * 따라서 Quest Transaction이 rollback되면
		 * Event도 저장되지 않는다.
		 */
		applicationEventPublisher.publishEvent(

				new QuestCompletedApplicationEvent(

						log.getLogId(),

						userId,

						quest.getQuestId(),

						quest.getQuestName(),

						rewardPoint,

						rewardExp,

						log.getClearDate()
				)
		);


		return UserQuestLogResponse.from(
				log
		);
	}


	/* =====================================================
	 * 연쇄(CHAIN) 퀘스트 - 단계별 진행
	 * ===================================================== */

	/**
	 * 연쇄 퀘스트의 전체 단계 목록을, 조회하는 사용자의 클리어 여부(cleared)와 함께 반환한다.
	 *
	 * 아직 퀘스트를 시작하지 않았더라도 단계 목록 자체는 볼 수 있어야 하므로(미리보기),
	 * 로그가 없으면 전부 cleared=false로 내려준다.
	 */
	@Transactional(readOnly = true)
	public List<QuestStepResponse> findStepsForUser(
			Long questId,
			Long userId
	) {

		List<QuestStep> steps =
				questStepRepository.findByQuest_QuestIdOrderByStepOrderAsc(
						questId
				);

		Set<Long> clearedStepIds =
				userQuestLogRepository
						.findByUser_IdAndQuest_QuestId(
								userId,
								questId
						)
						.map(
								log ->
										userQuestStepLogRepository.findClearedStepIdsByLogId(
												log.getLogId()
										)
						)
						.orElseGet(
								Set::of
						);

		return steps
				.stream()
				.map(
						step ->
								QuestStepResponse.from(
										step,
										clearedStepIds
								)
				)
				.toList();
	}


	/**
	 * 연쇄 퀘스트의 "다음 차례" 단계를 GPS로 인증한다.
	 *
	 * - 순서를 건너뛸 수 없다: stepId가 다음 차례 단계와 다르면 거부한다.
	 * - 반경 밖이면 아무 것도 저장하지 않고(재시도 가능) stepCleared=false로 응답한다.
	 * - 마지막 단계를 클리어하면 clear()의 성공 처리와 동일하게 포인트/EXP 지급,
	 *   레벨업/칭호 반영(RpgStatsService), QuestCompletedApplicationEvent 발행까지 수행한다.
	 */
	public QuestStepClearResponse clearStep(
			Long userId,
			Long questId,
			Long stepId,
			QuestClearRequest request
	) {

		// 단계 성공 로그와 최종 보상이 경쟁 요청에 의해 두 번 기록되지 않도록 잠근다.
		lockUserForQuestOperation(userId);
		Quest quest =
				getQuestOrThrow(
						questId
				);

		if (
				quest.getQuestType()
						!= QuestType.CHAIN
		) {

			throw new IllegalArgumentException(
					"연쇄 퀘스트가 아닙니다."
			);
		}


		UserQuestLog log =
				userQuestLogRepository
						.findByUser_IdAndQuest_QuestId(
								userId,
								questId
						)
						.orElseThrow(
								() ->
										new QuestNotFoundException(
												"아직 시작하지 않은 퀘스트입니다. 먼저 퀘스트를 시작해주세요."
										)
						);

		if (
				log.getStatus()
						== QuestStatus.SUCCESS
		) {

			throw new IllegalArgumentException(
					"이미 클리어 완료된 퀘스트입니다."
			);
		}


		List<QuestStep> steps =
				questStepRepository.findByQuest_QuestIdOrderByStepOrderAsc(
						questId
				);

		if (
				steps.isEmpty()
		) {

			throw new IllegalArgumentException(
					"등록된 단계가 없는 연쇄 퀘스트입니다. 관리자에게 문의해주세요."
			);
		}


		Set<Long> clearedStepIds =
				userQuestStepLogRepository.findClearedStepIdsByLogId(
						log.getLogId()
				);

		QuestStep nextStep =
				steps
						.stream()
						.filter(
								step ->
										!clearedStepIds.contains(
												step.getStepId()
										)
						)
						.findFirst()
						.orElseThrow(
								() ->
										new IllegalArgumentException(
												"이미 모든 단계를 완료했습니다."
										)
						);

		if (
				!nextStep.getStepId().equals(
						stepId
				)
		) {

			throw new IllegalArgumentException(
					"이 단계는 아직 진행할 수 없습니다. 먼저 " + nextStep.getStepOrder()
							+ "번째 단계(" + nextStep.getStepName() + ")를 완료해주세요."
			);
		}


		validateGpsRequest(request);


		int clearRadius =
				nextStep.getClearRadius() != null
						? nextStep.getClearRadius()
						: Quest.DEFAULT_CLEAR_RADIUS_METERS;

		double distance =
				GeoUtils.distanceMeters(

						nextStep.getTargetLat(),
						nextStep.getTargetLng(),

						request.getCurrentLat(),
						request.getCurrentLng()
				);

		int totalStepCount =
				steps.size();

		if (
				distance > clearRadius
		) {

			// 반경 밖 - 아무 것도 저장하지 않는다. 사용자는 이동 후 같은 단계를 다시 시도하면 된다.
			return QuestStepClearResponse.builder()
					.stepCleared(false)
					.questCompleted(false)
					.clearedStepCount(clearedStepIds.size())
					.totalStepCount(totalStepCount)
					.nextStepId(nextStep.getStepId())
					.questLog(
							UserQuestLogResponse.from(
									log,
									clearedStepIds.size(),
									totalStepCount
							)
					)
					.build();
		}


		/* =================================================
		 * 이 단계 클리어 성공
		 * ================================================= */

		userQuestStepLogRepository.save(
				UserQuestStepLog.builder()
						.questLog(log)
						.step(nextStep)
						.currentLat(request.getCurrentLat())
						.currentLng(request.getCurrentLng())
						.build()
		);

		int newClearedCount =
				clearedStepIds.size() + 1;

		boolean isLastStep =
				newClearedCount == totalStepCount;

		if (
				!isLastStep
		) {

			QuestStep followingStep =
					steps.get(
							newClearedCount
					);

			return QuestStepClearResponse.builder()
					.stepCleared(true)
					.questCompleted(false)
					.clearedStepCount(newClearedCount)
					.totalStepCount(totalStepCount)
					.nextStepId(followingStep.getStepId())
					.questLog(
							UserQuestLogResponse.from(
									log,
									newClearedCount,
									totalStepCount
							)
					)
					.build();
		}


		/* =================================================
		 * 마지막 단계 클리어 - 연쇄 퀘스트 전체 완료 처리
		 * (clear()의 성공 처리 블록과 동일한 로직)
		 * ================================================= */

		User user =
				log.getUser();

		Long rewardPoint =
				quest.getRewardPoint() != null
						? quest.getRewardPoint()
						: 0L;

		Long rewardExp =
				quest.getRewardExp() != null
						? quest.getRewardExp()
						: 0L;

		log.success(
				rewardPoint,
				rewardExp,
				request.getCurrentLat(),
				request.getCurrentLng()
		);

		user.setTotalPoints(
				user.getTotalPoints()
						+ rewardPoint
		);

		rpgStatsService.onQuestCompleted(
				user,
				rewardExp
		);

		applicationEventPublisher.publishEvent(

				new QuestCompletedApplicationEvent(

						log.getLogId(),

						userId,

						quest.getQuestId(),

						quest.getQuestName(),

						rewardPoint,

						rewardExp,

						log.getClearDate()
				)
		);

		return QuestStepClearResponse.builder()
				.stepCleared(true)
				.questCompleted(true)
				.clearedStepCount(newClearedCount)
				.totalStepCount(totalStepCount)
				.nextStepId(null)
				.questLog(
						UserQuestLogResponse.from(
								log,
								newClearedCount,
								totalStepCount
						)
				)
				.build();
	}


	/* =====================================================
	 * 관리자 Quest CRUD
	 * ===================================================== */

	public QuestResponse create(
			QuestRequest request
	) {

		QuestChapter chapter =
				resolveChapter(
						request.getChapterId()
				);

		Quest quest =
				Quest.builder()
						.questName(
								request.getQuestName()
						)
						.description(
								request.getDescription()
						)
						.targetLat(
								request.getTargetLat()
						)
						.targetLng(
								request.getTargetLng()
						)
						.rewardPoint(
								request.getRewardPoint() != null
										? request.getRewardPoint()
										: 0L
						)
						.rewardExp(
								request.getRewardExp() != null
										? request.getRewardExp()
										: 0L
						)
						.clearRadius(
								request.getClearRadius() != null
										? request.getClearRadius()
										: Quest.DEFAULT_CLEAR_RADIUS_METERS
						)
						.questType(
								request.getQuestType() != null
										? request.getQuestType()
										: QuestType.LOCATION
						)
						.difficulty(
								request.getDifficulty() != null
										? request.getDifficulty()
										: 1
						)
						.imageUrl(
								request.getImageUrl()
						)
						.thumbnailUrl(
								request.getThumbnailUrl()
						)
						.chapter(
								chapter
						)
						.requiredLevel(
								request.getRequiredLevel() != null
										? request.getRequiredLevel()
										: 1
						)
						.isActive(
								request.getIsActive() != null
										? request.getIsActive()
										: true
						)
						.build();


		return QuestResponse.from(
				questRepository.save(
						quest
				)
		);
	}


	public QuestResponse update(
			Long questId,
			QuestRequest request
	) {

		Quest quest =
				getQuestOrThrow(
						questId
				);

		QuestChapter chapter =
				resolveChapter(
						request.getChapterId()
				);

		// 사용자 기록이 존재하면 완료 조건/보상/레벨을 변경할 수 없다.
		// 발행 버전 시스템이 도입되기 전까지는 새 퀘스트 복제로 수정해야 한다.
		if (userQuestLogRepository.existsByQuest_QuestId(questId) && (
				!sameCoordinate(quest.getTargetLat(), request.getTargetLat())
				|| !sameCoordinate(quest.getTargetLng(), request.getTargetLng())
				|| (request.getRewardPoint() != null && !java.util.Objects.equals(quest.getRewardPoint(), request.getRewardPoint()))
				|| (request.getRewardExp() != null && !java.util.Objects.equals(quest.getRewardExp(), request.getRewardExp()))
				|| (request.getClearRadius() != null && !java.util.Objects.equals(quest.getClearRadius(), request.getClearRadius()))
				|| (request.getQuestType() != null && quest.getQuestType() != request.getQuestType())
				|| (request.getRequiredLevel() != null && !java.util.Objects.equals(quest.getRequiredLevel(), request.getRequiredLevel()))
				|| (request.getChapterId() != null && !java.util.Objects.equals(
						quest.getChapter() != null ? quest.getChapter().getChapterId() : null,
						request.getChapterId()))
		)) {
			throw new IllegalArgumentException(
					"시작 또는 완료 이력이 있는 퀘스트의 인증 조건과 보상은 수정할 수 없습니다. 새 퀘스트를 등록해주세요."
			);
		}

		quest.update(

				request.getQuestName(),

				request.getDescription(),

				request.getTargetLat(),

				request.getTargetLng(),

				request.getRewardPoint() != null
						? request.getRewardPoint()
						: quest.getRewardPoint(),

				request.getRewardExp() != null
						? request.getRewardExp()
						: quest.getRewardExp(),

				request.getClearRadius() != null
						? request.getClearRadius()
						: quest.getClearRadius(),

				request.getQuestType() != null
						? request.getQuestType()
						: quest.getQuestType(),

				request.getDifficulty() != null
						? request.getDifficulty()
						: quest.getDifficulty(),

				request.getImageUrl() != null
						? request.getImageUrl()
						: quest.getImageUrl(),

				request.getThumbnailUrl() != null
						? request.getThumbnailUrl()
						: quest.getThumbnailUrl(),

				request.getChapterId() != null
						? chapter
						: quest.getChapter(),

				request.getRequiredLevel() != null
						? request.getRequiredLevel()
						: quest.getRequiredLevel(),

				request.getIsActive() != null
						? request.getIsActive()
						: quest.getIsActive()
		);


		return QuestResponse.from(
				quest
		);
	}


	public void delete(
			Long questId
	) {

		Quest quest =
				getQuestOrThrow(
						questId
				);


		try {

			questRepository.delete(
					quest
			);

			questRepository.flush();

		} catch (
				DataIntegrityViolationException e
		) {

			/*
			 * USER_QUEST_LOGS가 남아있는 Quest를
			 * 강제로 삭제하면 수행 이력/Point 근거가 사라진다.
			 */
			throw new QuestDeleteConflictException(
					"이 퀘스트를 완료했거나 진행 중인 사용자가 있어 삭제할 수 없습니다. "
							+ "(해당 유저 기록이 모두 사라지는 것을 방지하기 위한 정책입니다.)"
			);
		}
	}


	/* =====================================================
	 * Internal
	 * ===================================================== */

	// Oracle NUMBER 자릿수 표현(scale)이 달라도 동일한 위경도는 변경으로 취급하지 않는다.
	private boolean sameCoordinate(java.math.BigDecimal first, java.math.BigDecimal second) {
		if (first == null || second == null) {
			return first == second;
		}
		return first.compareTo(second) == 0;
	}

	private User lockUserForQuestOperation(Long userId) {
		// 이미 마스코트 보상에서도 사용하는 사용자 행 잠금을 재사용한다.
		// 퀘스트 시작/일반 인증/CHAIN 인증은 반드시 이 잠금을 가장 먼저 획득해야 한다.
		return userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));
	}

	private void validateGpsRequest(QuestClearRequest request) {
		if (request == null || request.getCurrentLat() == null || request.getCurrentLng() == null
				|| request.getCurrentLat().compareTo(java.math.BigDecimal.valueOf(-90)) < 0
				|| request.getCurrentLat().compareTo(java.math.BigDecimal.valueOf(90)) > 0
				|| request.getCurrentLng().compareTo(java.math.BigDecimal.valueOf(-180)) < 0
				|| request.getCurrentLng().compareTo(java.math.BigDecimal.valueOf(180)) > 0) {
			throw new IllegalArgumentException("올바른 GPS 위도/경도를 전달해주세요.");
		}
		Double accuracy = request.getAccuracyMeters();
		if (accuracy != null && (!Double.isFinite(accuracy) || accuracy < 0)) {
			throw new IllegalArgumentException("GPS 오차 반경은 0 이상의 유한한 숫자여야 합니다.");
		}
		if (accuracy != null && accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
			throw new IllegalArgumentException(
					"GPS 신호가 약합니다. 실외의 개방된 장소에서 다시 시도해주세요. (오차 반경: "
							+ accuracy + "m)"
			);
		}
	}

	private Quest getQuestOrThrow(
			Long questId
	) {

		return questRepository
				.findById(
						questId
				)
				.orElseThrow(
						() ->
								new QuestNotFoundException(
										"존재하지 않는 퀘스트입니다. id="
												+ questId
								)
				);
	}


	// chapterId가 null이면 "미분류" 그대로 null을 반환한다(필수 아님)
	private QuestChapter resolveChapter(
			Long chapterId
	) {

		if (chapterId == null) {
			return null;
		}

		return questChapterRepository
				.findById(chapterId)
				.orElseThrow(
						() ->
								new QuestChapterNotFoundException(
										"존재하지 않는 챕터입니다. id=" + chapterId
								)
				);
	}
}
