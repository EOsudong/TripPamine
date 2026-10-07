package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// QUESTS: GPS 반경 검증 기반 실시간 RPG 퀘스트 마스터
@Entity
@Table(name = "QUESTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quest {

	public static final int DEFAULT_CLEAR_RADIUS_METERS = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_QUESTS_GEN")
	@SequenceGenerator(name = "SEQ_QUESTS_GEN", sequenceName = "SEQ_QUESTS", allocationSize = 1)
	@Column(name = "QUEST_ID")
	private Long questId;

	@Column(name = "QUEST_NAME", nullable = false, length = 100)
	private String questName;

	@Column(name = "DESCRIPTION", length = 500)
	private String description;

	@Column(name = "TARGET_LAT", nullable = false, precision = 10, scale = 7)
	private BigDecimal targetLat;

	@Column(name = "TARGET_LNG", nullable = false, precision = 11, scale = 7)
	private BigDecimal targetLng;

	@Column(name = "REWARD_POINT")
	@Builder.Default
	private Long rewardPoint = 0L;

	@Column(name = "REWARD_EXP", nullable = false)
	@Builder.Default
	private Long rewardExp = 0L;

	// 퀘스트별 클리어 인정 반경(m) - 장소 특성에 따라 관리자가 개별 설정
	// (예: 남산타워 300m, 스타벅스 강남점 50m)
	@Column(name = "CLEAR_RADIUS", nullable = false)
	@Builder.Default
	private Integer clearRadius = DEFAULT_CLEAR_RADIUS_METERS;

	@Enumerated(EnumType.STRING)
	@Column(name = "QUEST_TYPE", nullable = false, length = 20)
	@Builder.Default
	private QuestType questType = QuestType.LOCATION;

	// 1~3 권장 (★☆☆ ~ ★★★)
	@Column(name = "DIFFICULTY", nullable = false)
	@Builder.Default
	private Integer difficulty = 1;

	@Column(name = "IMAGE_URL", length = 500)
	private String imageUrl;

	@Column(name = "THUMBNAIL_URL", length = 500)
	private String thumbnailUrl;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "CHAPTER_ID")
	private QuestChapter chapter;

	// 이 레벨 미만인 사용자는 시작할 수 없음(= 지도 상 잠김 상태)
	@Column(name = "REQUIRED_LEVEL", nullable = false)
	@Builder.Default
	private Integer requiredLevel = 1;

	@Convert(converter = IsActiveConverter.class)
	@Column(name = "IS_ACTIVE", nullable = false, length = 1)
	@Builder.Default
	private Boolean isActive = true;

	@Column(name = "CREATED_AT", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "UPDATED_AT")
	private LocalDateTime updatedAt;

	// 퀘스트 정보 수정(관리자용) 도메인 메서드
	public void update(String questName, String description, BigDecimal targetLat, BigDecimal targetLng,
	                   Long rewardPoint, Long rewardExp, Integer clearRadius, QuestType questType,
	                   Integer difficulty, String imageUrl, String thumbnailUrl, QuestChapter chapter,
	                   Integer requiredLevel, Boolean isActive) {
		this.questName = questName;
		this.description = description;
		this.targetLat = targetLat;
		this.targetLng = targetLng;
		this.rewardPoint = rewardPoint;
		this.rewardExp = rewardExp;
		this.clearRadius = clearRadius;
		this.questType = questType;
		this.difficulty = difficulty;
		this.imageUrl = imageUrl;
		this.thumbnailUrl = thumbnailUrl;
		this.chapter = chapter;
		this.requiredLevel = requiredLevel;
		this.isActive = isActive;
		this.updatedAt = LocalDateTime.now();
	}
}
