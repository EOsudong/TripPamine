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
import com.example.trippaminebe.domain.quest.exception.QuestDeleteConflictException;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class QuestService {

	// 클라이언트가 보낸 GPS Horizontal Accuracy(오차 반경)가 이 값을 넘으면 "약한 GPS 신호"로 보고
	// 거리 검증 자체를 하지 않고 리젝한다 (기지국/Wi-Fi 기반의 부정확한 위치일 확률이 높음).
	private static final double MAX_ACCEPTABLE_ACCURACY_METERS = 200.0;

	private final QuestRepository questRepository;
	private final UserQuestLogRepository userQuestLogRepository;
	private final UserRepository userRepository;

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

	// 퀘스트 시작 - 이미 진행 중/완료된 로그가 있으면 그대로 반환 (중복 시작 방지)
	public UserQuestLogResponse start(Long userId, Long questId) {
		Quest quest = getQuestOrThrow(questId);

		UserQuestLog log = userQuestLogRepository.findByUser_IdAndQuest_QuestId(userId, questId)
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

		return UserQuestLogResponse.from(log);
	}

	// 퀘스트 클리어 인증 - 현재 GPS 좌표가 목표 반경 내면 성공 처리 및 포인트 지급
	public UserQuestLogResponse clear(Long userId, Long questId, QuestClearRequest request) {
		Quest quest = getQuestOrThrow(questId);

		UserQuestLog log = userQuestLogRepository.findByUser_IdAndQuest_QuestId(userId, questId)
				.orElseThrow(() -> new QuestNotFoundException("아직 시작하지 않은 퀘스트입니다. 먼저 퀘스트를 시작해주세요."));

		// SUCCESS만 재시도를 막는다. FAILED는 "반경 밖이라 실패"일 뿐 사용자가 실시간으로 걸어서
		// 목표 지점에 더 가까이 이동한 뒤 재시도할 수 있어야 하므로 재검증을 허용한다.
		if (log.getStatus() == QuestStatus.SUCCESS) {
			throw new IllegalArgumentException("이미 클리어 완료된 퀘스트입니다.");
		}

		// GPS 오차 반경이 너무 크면 "위치가 부정확해서 검증 불가" 상태로 보고 즉시 리젝한다.
		// (거리 계산까지 진행해서 FAILED로 확정 짓지 않음 - 신호만 좋아지면 재시도로 성공할 수 있는 상황이라
		//  사용자 경험상 "너무 멀어서 실패"와는 분명히 구분해줘야 함)
		if (request.getAccuracyMeters() != null && request.getAccuracyMeters() > MAX_ACCEPTABLE_ACCURACY_METERS) {
			throw new IllegalArgumentException(
					"GPS 신호가 약합니다. 실외의 개방된 장소에서 다시 시도해주세요. (오차 반경: "
							+ request.getAccuracyMeters() + "m)"
			);
		}

		int clearRadius = quest.getClearRadius() != null
				? quest.getClearRadius()
				: Quest.DEFAULT_CLEAR_RADIUS_METERS;

		double distance = GeoUtils.distanceMeters(
				quest.getTargetLat(), quest.getTargetLng(),
				request.getCurrentLat(), request.getCurrentLng()
		);

		if (distance > clearRadius) {
			log.fail();
			return UserQuestLogResponse.from(log);
		}

		log.success();

		// 성공 보상 포인트를 유저 계정에 즉시 반영
		User user = log.getUser();
		Long rewardPoint = quest.getRewardPoint() != null ? quest.getRewardPoint() : 0L;
		user.setTotalPoints(user.getTotalPoints() + rewardPoint);

		return UserQuestLogResponse.from(log);
	}

	// ===================== 관리자용 =====================

	public QuestResponse create(QuestRequest request) {
		Quest quest = Quest.builder()
				.questName(request.getQuestName())
				.targetLat(request.getTargetLat())
				.targetLng(request.getTargetLng())
				.rewardPoint(request.getRewardPoint() != null ? request.getRewardPoint() : 0L)
				.clearRadius(request.getClearRadius() != null ? request.getClearRadius() : Quest.DEFAULT_CLEAR_RADIUS_METERS)
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
		try {
			questRepository.delete(quest);
			questRepository.flush();
		} catch (DataIntegrityViolationException e) {
			// ORA-02292 (FK_LOGS_QUESTS): 이미 유저들이 진행/완료한 USER_QUEST_LOGS가 남아있는 경우.
			// 히스토리(포인트 지급 이력 등)를 하드 삭제로 날려버리면 안 되므로, 삭제 대신
			// 안내 메시지를 반환해 관리자가 의도적으로 판단하게 한다.
			throw new QuestDeleteConflictException(
					"이 퀘스트를 완료했거나 진행 중인 사용자가 있어 삭제할 수 없습니다. "
							+ "(해당 유저 기록이 모두 사라지는 것을 방지하기 위한 정책입니다.)"
			);
		}
	}

	private Quest getQuestOrThrow(Long questId) {
		return questRepository.findById(questId)
				.orElseThrow(() -> new QuestNotFoundException("존재하지 않는 퀘스트입니다. id=" + questId));
	}
}
