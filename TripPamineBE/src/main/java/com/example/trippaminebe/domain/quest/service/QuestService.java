package com.example.trippaminebe.domain.quest.service;

import com.example.trippaminebe.domain.quest.dto.request.QuestClearRequest;
import com.example.trippaminebe.domain.quest.dto.request.QuestRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestResponse;
import com.example.trippaminebe.domain.quest.dto.response.UserQuestLogResponse;
import com.example.trippaminebe.domain.quest.entity.Quest;
import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.entity.UserQuestLog;
import com.example.trippaminebe.domain.quest.event.QuestCompletedApplicationEvent;
import com.example.trippaminebe.domain.quest.exception.QuestDeleteConflictException;
import com.example.trippaminebe.domain.quest.exception.QuestNotFoundException;
import com.example.trippaminebe.domain.quest.repository.QuestRepository;
import com.example.trippaminebe.domain.quest.repository.UserQuestLogRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Quest 핵심 Business Service.
 *
 * Quest 성공/실패 판정과 포인트 지급은
 * 이 Service가 계속 책임진다.
 *
 * AI Manager Event 저장 책임은 갖지 않는다.
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

	private final UserQuestLogRepository
			userQuestLogRepository;

	private final UserRepository
			userRepository;


	/**
	 * Spring Application Event 발행기.
	 *
	 * QuestService가 AI Manager를 직접 알 필요 없이
	 * "Quest가 완료되었다"는 사실만 전달한다.
	 */
	private final ApplicationEventPublisher
			applicationEventPublisher;


	/* =====================================================
	 * Quest 조회
	 * ===================================================== */

	@Transactional(readOnly = true)
	public List<QuestResponse> findAll() {

		return questRepository
				.findAll()
				.stream()
				.map(QuestResponse::from)
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
	public List<UserQuestLogResponse> findMyLogs(
			Long userId
	) {

		return userQuestLogRepository
				.findByUser_IdOrderByLogIdDesc(
						userId
				)
				.stream()
				.map(
						UserQuestLogResponse::from
				)
				.toList();
	}


	/* =====================================================
	 * Quest 시작
	 * ===================================================== */

	/**
	 * 이미 진행 중/완료된 로그가 존재하면
	 * 중복 생성하지 않고 기존 로그를 반환한다.
	 */
	public UserQuestLogResponse start(
			Long userId,
			Long questId
	) {

		Quest quest =
				getQuestOrThrow(
						questId
				);


		UserQuestLog log =
				userQuestLogRepository
						.findByUser_IdAndQuest_QuestId(
								userId,
								questId
						)
						.orElseGet(() -> {

							User user =
									userRepository
											.findById(
													userId
											)
											.orElseThrow(
													() ->
															new IllegalArgumentException(
																	"존재하지 않는 회원입니다. id="
																			+ userId
															)
											);


							UserQuestLog newLog =
									UserQuestLog
											.builder()
											.user(user)
											.quest(quest)
											.status(
													QuestStatus.PROGRESS
											)
											.build();


							return userQuestLogRepository
									.save(
											newLog
									);
						});


		return UserQuestLogResponse.from(
				log
		);
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
	 * 2. Point 지급
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

		Quest quest =
				getQuestOrThrow(
						questId
				);


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
		if (
				request.getAccuracyMeters() != null
						&&
						request.getAccuracyMeters()
								> MAX_ACCEPTABLE_ACCURACY_METERS
		) {

			throw new IllegalArgumentException(
					"GPS 신호가 약합니다. 실외의 개방된 장소에서 다시 시도해주세요. "
							+ "(오차 반경: "
							+ request.getAccuracyMeters()
							+ "m)"
			);
		}


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

			log.fail();

			return UserQuestLogResponse.from(
					log
			);
		}


		/* =================================================
		 * 성공 처리
		 * ================================================= */

		log.success();


		User user =
				log.getUser();


		Long rewardPoint =
				quest.getRewardPoint() != null
						? quest.getRewardPoint()
						: 0L;


		/*
		 * Point 지급.
		 */
		user.setTotalPoints(
				user.getTotalPoints()
						+ rewardPoint
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

						log.getClearDate()
				)
		);


		return UserQuestLogResponse.from(
				log
		);
	}


	/* =====================================================
	 * 관리자 Quest CRUD
	 * ===================================================== */

	public QuestResponse create(
			QuestRequest request
	) {

		Quest quest =
				Quest.builder()
						.questName(
								request.getQuestName()
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
						.clearRadius(
								request.getClearRadius() != null
										? request.getClearRadius()
										: Quest.DEFAULT_CLEAR_RADIUS_METERS
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


		quest.update(

				request.getQuestName(),

				request.getTargetLat(),

				request.getTargetLng(),

				request.getRewardPoint() != null
						? request.getRewardPoint()
						: quest.getRewardPoint(),

				request.getClearRadius() != null
						? request.getClearRadius()
						: quest.getClearRadius()
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
}