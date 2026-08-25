package com.example.trippaminebe.domain.quest.service;

import com.example.trippaminebe.domain.quest.dto.request.QuestClearRequest;
import com.example.trippaminebe.domain.quest.dto.request.QuestRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestResponse;
import com.example.trippaminebe.domain.quest.dto.response.UserQuestLogResponse;
import com.example.trippaminebe.domain.quest.entity.Quest;
import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.entity.UserQuestLog;
import com.example.trippaminebe.domain.quest.exception.QuestNotFoundException;
import com.example.trippaminebe.domain.quest.repository.QuestRepository;
import com.example.trippaminebe.domain.quest.repository.UserQuestLogRepository;
import com.example.trippaminebe.domain.quest.util.VincentyDistanceCalculator;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QuestService {

	// GPS Horizontal Accuracy 오차 반경 허용치 (넘으면 약한 신호로 보고 리젝)
	private static final double MAX_ACCEPTABLE_ACCURACY_METERS = 200.0;

	// GPS 조작 의심 제한 속도: 초속 100m (시속 360km) - KTX 최대 속도 수준 초과 시 차단
	private static final double MAX_LOGICAL_VELOCITY_MPS = 100.0;

	private final QuestRepository questRepository;
	private final UserQuestLogRepository userQuestLogRepository;
	private final UserRepository userRepository;
	private final VincentyDistanceCalculator distanceCalculator;

	// 실시간 GPS 위조 방지(Anti-Spoofing) 유저 이력 캐시 (유저ID -> 마지막 수신 정보)
	private final ConcurrentHashMap<Long, UserLocationRecord> userLocationHistory = new ConcurrentHashMap<>();

	// 사용자 조회 / 시작 / 퀘스트 클리어

	@Transactional(readOnly = true)
	public List<QuestResponse> findAll() {
		return questRepository.findAll().stream()
				.map(QuestResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public QuestResponse findById(Long questId) {
		return QuestResponse.from(getQuestOrThrow(questId));
	}

	@Transactional(readOnly = true)
	public List<UserQuestLogResponse> findMyLogs(Long userId) {
		return userQuestLogRepository.findByUser_IdOrderByLogIdDesc(userId).stream()
				.map(UserQuestLogResponse::from)
				.toList();
	}

	private Quest getQuestOrThrow(Long questId) {
		return questRepository.findById(questId)
				.orElseThrow(() -> new QuestNotFoundException("존재하지 않는 퀘스트입니다. id=" + questId));
	}

	/**
	 * 퀘스트 시작 - 이미 진행 중/완료된 로그가 있으면 그대로 반환 (중복 시작 방지)
	 */
	public UserQuestLogResponse start(Long userId, Long questId) {
		Quest quest = getQuestOrThrow(questId);

		UserQuestLog logEntity = userQuestLogRepository.findByUser_IdAndQuest_QuestId(userId, questId)
				.orElseGet(() -> {
					User user = userRepository.findById(userId)
							.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));

					UserQuestLog newLog = UserQuestLog.builder()
							.user(user)
							.quest(quest)
							.status(QuestStatus.PROGRESS)
							.build();
					return userQuestLogRepository.save(newLog);
				});

		return UserQuestLogResponse.from(logEntity);
	}

	/**
	 * 퀘스트 클리어 인증 - GPS 위치 정밀 검증, Anti-Spoofing, 성공 시 포인트 반영
	 */
	public UserQuestLogResponse clear(Long userId, Long questId, QuestClearRequest request) {
		Quest quest = getQuestOrThrow(questId);

		UserQuestLog logEntity = userQuestLogRepository.findByUser_IdAndQuest_QuestId(userId, questId)
				.orElseThrow(() -> new QuestNotFoundException("아직 시작하지 않은 퀘스트입니다. 먼저 퀘스트를 시작해주세요."));

		if (logEntity.getStatus() != QuestStatus.PROGRESS) {
			throw new IllegalArgumentException("이미 완료 처리된 퀘스트입니다. 상태: " + logEntity.getStatus());
		}

		// 1. GPS 신호 정확도 검증 (오차 반경 체크)
		if (request.getAccuracyMeters() != null && request.getAccuracyMeters() > MAX_ACCEPTABLE_ACCURACY_METERS) {
			throw new IllegalArgumentException(
					"GPS 신호가 약합니다. 실외의 개방된 장소에서 다시 시도해주세요. (오차 반경: "
							+ request.getAccuracyMeters() + "m)"
			);
		}

		// 2. 실시간 GPS 위치 변조 및 초고속 기동 감지 (Anti-Spoofing 검증)
		if (isSpoofingDetected(userId, request.getCurrentLat(), request.getCurrentLng())) {
			log.warn("[GPS 조작 의심] 유저 ID {}의 위치 변화가 논리적 이동 속도를 초과하였습니다.", userId);
			throw new SecurityException("비정상적인 GPS 신호가 감지되었습니다. 원활한 위치에서 다시 시도해주세요.");
		}

		// 3. 빈센티 공식을 통한 거리 계산 (BigDecimal 매개변수 전달)
		double distance = distanceCalculator.calculateDistance(
				quest.getTargetLat(), quest.getTargetLng(),
				request.getCurrentLat(), request.getCurrentLng()
		);

		int clearRadius = quest.getClearRadius() != null
				? quest.getClearRadius()
				: Quest.DEFAULT_CLEAR_RADIUS_METERS;

		log.info("유저 [ID: {}]와 퀘스트 '{}' 실측 거리: {}m (목표 반경: {}m)",
				userId, quest.getQuestName(), distance, clearRadius);

		// 4. 거리 검증 통과 실패 시
		if (distance > clearRadius) {
			logEntity.fail();
			return UserQuestLogResponse.from(logEntity);
		}

		// 5. 성공 처리 및 포인트 반영
		logEntity.success();

		User user = logEntity.getUser();
		Long rewardPoint = quest.getRewardPoint() != null ? quest.getRewardPoint() : 0L;
		user.setTotalPoints(user.getTotalPoints() + rewardPoint);

		return UserQuestLogResponse.from(logEntity);
	}


	// ===================== 관리자용 CRUD =====================

	public QuestResponse create(QuestRequest request) {
		Quest quest = Quest.builder()
				.questName(request.getQuestName())
				.targetLat(request.getTargetLat())
				.targetLng(request.getTargetLng())
				.rewardPoint(request.getRewardPoint() != null ? request.getRewardPoint() : 0L)
				.clearRadius(request.getClearRadius())
				.build();

		return QuestResponse.from(questRepository.save(quest));
	}

	public QuestResponse update(Long questId, QuestRequest request) {
		Quest quest = getQuestOrThrow(questId);
		quest.update(
				request.getQuestName(),
				request.getTargetLat(),
				request.getTargetLng(),
				request.getRewardPoint() != null ? request.getRewardPoint() : quest.getRewardPoint(),
				request.getClearRadius() != null ? request.getClearRadius() : quest.getClearRadius()
		);
		return QuestResponse.from(quest);
	}

	public void delete(Long questId) {
		Quest quest = getQuestOrThrow(questId);
		questRepository.delete(quest);
	}

	/**
	 * 유저의 직전 위치 기록과 계산하여 순간 속도가 비정상적(Anti-Spoofing)인지 확인합니다.
	 */
	private boolean isSpoofingDetected(Long userId, BigDecimal lat, BigDecimal lng) {
		LocalDateTime now = LocalDateTime.now();
		UserLocationRecord lastRecord = userLocationHistory.get(userId);

		if (lastRecord == null) {
			userLocationHistory.put(userId, new UserLocationRecord(lat, lng, now));
			return false;
		}

		double timeDeltaSeconds = Duration.between(lastRecord.timestamp, now).toMillis() / 1000.0;

		// 1초 미만의 반복 요청은 검토 유예
		if (timeDeltaSeconds < 1.0) {
			return false;
		}

		double movedDistance = distanceCalculator.calculateDistance(lastRecord.latitude, lastRecord.longitude, lat, lng);
		double velocity = movedDistance / timeDeltaSeconds; // m/s

		// 위치 히스토리 갱신
		userLocationHistory.put(userId, new UserLocationRecord(lat, lng, now));

		return velocity > MAX_LOGICAL_VELOCITY_MPS;
	}

	private static class UserLocationRecord {
		final BigDecimal latitude;
		final BigDecimal longitude;
		final LocalDateTime timestamp;

		UserLocationRecord(BigDecimal latitude, BigDecimal longitude, LocalDateTime timestamp) {
			this.latitude = latitude;
			this.longitude = longitude;
			this.timestamp = timestamp;
		}
	}
}