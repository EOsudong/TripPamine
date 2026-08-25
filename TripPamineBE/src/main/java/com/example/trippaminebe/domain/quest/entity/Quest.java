package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

// QUESTS: GPS 반경 검증 기반 실시간 RPG 퀘스트 마스터
@Entity
@Table(name = "QUESTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quest {

	// 컬럼 추가 전 DB에 먼저 적용해야 하는 마이그레이션:
	// ALTER TABLE QUESTS ADD CLEAR_RADIUS NUMBER DEFAULT 100 NOT NULL;
	public static final int DEFAULT_CLEAR_RADIUS_METERS = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_QUESTS_GEN")
	@SequenceGenerator(name = "SEQ_QUESTS_GEN", sequenceName = "SEQ_QUESTS", allocationSize = 1)
	@Column(name = "QUEST_ID")
	private Long questId;

	@Column(name = "QUEST_NAME", nullable = false, length = 100)
	private String questName;

	@Column(name = "TARGET_LAT", nullable = false, precision = 10, scale = 7)
	private BigDecimal targetLat;

	@Column(name = "TARGET_LNG", nullable = false, precision = 11, scale = 7)
	private BigDecimal targetLng;

	@Column(name = "REWARD_POINT")
	@Builder.Default
	private Long rewardPoint = 0L;

	// 퀘스트별 클리어 인정 반경(m) - 장소 특성에 따라 관리자가 개별 설정
	// (예: 남산타워 300m, 스타벅스 강남점 50m)
	@Column(name = "CLEAR_RADIUS", nullable = false)
	@Builder.Default
	private Integer clearRadius = DEFAULT_CLEAR_RADIUS_METERS;

	// 퀘스트 정보 수정(관리자용) 도메인 메서드
	public void update(String questName, BigDecimal targetLat, BigDecimal targetLng, Long rewardPoint, Integer clearRadius) {
		this.questName = questName;
		this.targetLat = targetLat;
		this.targetLng = targetLng;
		this.rewardPoint = rewardPoint;
		this.clearRadius = clearRadius;
	}
}
