package com.example.trippaminebe.domain.mascot.entity;

import jakarta.persistence.*;
import lombok.*;

// MASCOTS: 지역별 캐릭터 마스터. 실제 3D 모델(Phase 3, ARCore)이 아니라
// 의사(pseudo) AR 화면에서 카메라 위에 겹치는 2D 스프라이트(투명 배경 PNG) 기준으로 설계했다.
@Entity
@Table(name = "MASCOTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mascot {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_MASCOTS_GEN")
    @SequenceGenerator(name = "SEQ_MASCOTS_GEN", sequenceName = "SEQ_MASCOTS", allocationSize = 1)
    @Column(name = "MASCOT_ID")
    private Long mascotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REGION_ID", nullable = false)
    private Region region;

    @Column(name = "MASCOT_NAME", nullable = false, length = 100)
    private String mascotName;

    @Enumerated(EnumType.STRING)
    @Column(name = "RARITY", length = 20)
    @Builder.Default
    private MascotRarity rarity = MascotRarity.COMMON;

    // 의사 AR 화면(ArCatchActivity)에서 카메라 프리뷰 위에 겹칠 2D 스프라이트 이미지 URL(투명 배경 PNG 권장)
    @Column(name = "IMAGE_URL", length = 500)
    private String imageUrl;

    // 정식 ARCore 3D 모델(glb 등)을 쓰게 되면 채울 필드. 지금은 비워둬도 된다.
    @Column(name = "MODEL_URL", length = 500)
    private String modelUrl;

    @Column(name = "REWARD_POINT")
    @Builder.Default
    private Long rewardPoint = 0L;

    public void update(Region region, String mascotName, MascotRarity rarity,
                        String imageUrl, String modelUrl, Long rewardPoint) {
        this.region = region;
        this.mascotName = mascotName;
        this.rarity = rarity;
        this.imageUrl = imageUrl;
        this.modelUrl = modelUrl;
        this.rewardPoint = rewardPoint;
    }
}
