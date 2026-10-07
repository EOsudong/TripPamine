package com.example.trippaminebe.domain.quest.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// USER_QUEST_LOGS: 사용자별 퀘스트 진행/성공/실패 추적 로그
@Entity
@Table(name = "USER_QUEST_LOGS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuestLog {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USER_QUEST_LOGS_GEN")
	@SequenceGenerator(name = "SEQ_USER_QUEST_LOGS_GEN", sequenceName = "SEQ_USER_QUEST_LOGS", allocationSize = 1)
	@Column(name = "LOG_ID")
	private Long logId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "USER_ID", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "QUEST_ID", nullable = false)
	private Quest quest;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", length = 20)
	@Builder.Default
	private QuestStatus status = QuestStatus.PROGRESS;

	@Column(name = "CLEAR_DATE")
	private LocalDateTime clearDate;

	@Column(name = "STARTED_AT", insertable = false, updatable = false)
	private LocalDateTime startedAt;

	// 클리어 시점의 보상 스냅샷(관리자가 나중에 퀘스트 보상값을 바꿔도 과거 기록은 보존)
	@Column(name = "EARNED_POINT")
	private Long earnedPoint;

	@Column(name = "EARNED_EXP")
	private Long earnedExp;

	// 마지막 인증 시도 좌표(성공/실패 모두 기록)
	@Column(name = "CURRENT_LAT", precision = 10, scale = 7)
	private BigDecimal currentLat;

	@Column(name = "CURRENT_LNG", precision = 11, scale = 7)
	private BigDecimal currentLng;

	@Column(name = "CREATED_AT", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "UPDATED_AT")
	private LocalDateTime updatedAt;

	// GPS 반경 검증 성공 시 호출 - 클리어 처리 도메인 메서드
	public void success(Long earnedPoint, Long earnedExp, BigDecimal currentLat, BigDecimal currentLng) {
		this.status = QuestStatus.SUCCESS;
		this.clearDate = LocalDateTime.now();
		this.earnedPoint = earnedPoint;
		this.earnedExp = earnedExp;
		this.currentLat = currentLat;
		this.currentLng = currentLng;
		this.updatedAt = LocalDateTime.now();
	}

	// GPS 반경 밖이거나 조건 불충족 시 실패 처리 도메인 메서드
	public void fail(BigDecimal currentLat, BigDecimal currentLng) {
		this.status = QuestStatus.FAILED;
		this.clearDate = LocalDateTime.now();
		this.currentLat = currentLat;
		this.currentLng = currentLng;
		this.updatedAt = LocalDateTime.now();
	}
}
