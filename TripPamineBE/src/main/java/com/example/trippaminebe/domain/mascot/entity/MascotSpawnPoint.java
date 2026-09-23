package com.example.trippaminebe.domain.mascot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;

// MASCOT_SPAWN_POINTS: 실제 지도 위에서 마스코트를 "만날 수 있는" 좌표.
// quest.entity.Quest와 완전히 같은 뼈대(목표 좌표 + 인정 반경)를 그대로 재사용한다.
@Entity
@Table(name = "MASCOT_SPAWN_POINTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MascotSpawnPoint {

    public static final int DEFAULT_CATCH_RADIUS_METERS = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_MASCOT_SPAWN_POINTS_GEN")
    @SequenceGenerator(name = "SEQ_MASCOT_SPAWN_POINTS_GEN", sequenceName = "SEQ_MASCOT_SPAWN_POINTS", allocationSize = 1)
    @Column(name = "SPAWN_ID")
    private Long spawnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MASCOT_ID", nullable = false)
    private Mascot mascot;

    @Column(name = "SPOT_NAME", length = 100)
    private String spotName; // 예) "경복궁", "해운대 해수욕장" - 관리자 화면/도감에서 보여줄 장소명

    @Column(name = "TARGET_LAT", nullable = false, precision = 10, scale = 7)
    private BigDecimal targetLat;

    @Column(name = "TARGET_LNG", nullable = false, precision = 11, scale = 7)
    private BigDecimal targetLng;

    // 포획 인정 반경(m). quest.entity.Quest.clearRadius와 동일한 개념.
    @Column(name = "CATCH_RADIUS", nullable = false)
    @Builder.Default
    private Integer catchRadius = DEFAULT_CATCH_RADIUS_METERS;

    // 특정 퀘스트를 클리어해야만 스폰되게 하고 싶을 때 쓰는 선택 필드.
    // Quest 엔티티를 직접 @ManyToOne으로 참조하지 않고 ID만 저장한다
    @Column(name = "LINKED_QUEST_ID")
    private Long linkedQuestId;

//    @Column(name = "ACTIVE_YN", length = 1)
//    @Builder.Default
//    private String activeYn = "Y";

    @JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "ACTIVE_YN", length = 1)
    @Builder.Default
    private String activeYn = "Y";

    public void update(String spotName, BigDecimal targetLat, BigDecimal targetLng,
                        Integer catchRadius, Long linkedQuestId, String activeYn) {
        this.spotName = spotName;
        this.targetLat = targetLat;
        this.targetLng = targetLng;
        this.catchRadius = catchRadius;
        this.linkedQuestId = linkedQuestId;
        this.activeYn = activeYn;
    }
}
