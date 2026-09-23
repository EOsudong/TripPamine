package com.example.trippaminebe.domain.mascot.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// USER_MASCOT_COLLECTIONS: 유저별 "도감" - 실제로 포획에 성공한 기록.
// quest.entity.UserQuestLog와 같은 뼈대(유저+대상+시각)를 그대로 따른다.
@Entity
@Table(name = "USER_MASCOT_COLLECTIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMascotCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USER_MASCOT_COLLECTIONS_GEN")
    @SequenceGenerator(name = "SEQ_USER_MASCOT_COLLECTIONS_GEN", sequenceName = "SEQ_USER_MASCOT_COLLECTIONS", allocationSize = 1)
    @Column(name = "COLLECTION_ID")
    private Long collectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MASCOT_ID", nullable = false)
    private Mascot mascot;

    // 어느 스폰 지점에서 잡았는지 (같은 마스코트를 여러 지점에서 스폰시켰을 수 있으므로 기록해둔다)
    @Column(name = "SPAWN_ID")
    private Long spawnId;

    @Column(name = "CAUGHT_LAT", precision = 10, scale = 7)
    private BigDecimal caughtLat;

    @Column(name = "CAUGHT_LNG", precision = 11, scale = 7)
    private BigDecimal caughtLng;

    @Column(name = "CAUGHT_AT")
    private LocalDateTime caughtAt;

    public static UserMascotCollection of(User user, Mascot mascot, Long spawnId,
                                           BigDecimal caughtLat, BigDecimal caughtLng) {
        return UserMascotCollection.builder()
                .user(user)
                .mascot(mascot)
                .spawnId(spawnId)
                .caughtLat(caughtLat)
                .caughtLng(caughtLng)
                .caughtAt(LocalDateTime.now())
                .build();
    }
}
