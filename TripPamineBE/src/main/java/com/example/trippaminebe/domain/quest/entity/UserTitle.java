package com.example.trippaminebe.domain.quest.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// USER_TITLES: 사용자가 실제로 획득한 칭호 이력(획득 시점 포함).
// USER_RPG_STATS.currentTitle은 "현재 장착 중인 칭호" 1개만 가리키고,
// 이 테이블은 "지금까지 획득한 모든 칭호"를 보관한다.
@Entity
@Table(name = "USER_TITLES", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_USER_TITLES", columnNames = {"USER_ID", "TITLE_ID"})
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USER_TITLES_GEN")
    @SequenceGenerator(name = "SEQ_USER_TITLES_GEN", sequenceName = "SEQ_USER_TITLES", allocationSize = 1)
    @Column(name = "USER_TITLE_ID")
    private Long userTitleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TITLE_ID", nullable = false)
    private Title title;

    @Column(name = "EARNED_AT", insertable = false, updatable = false)
    private LocalDateTime earnedAt;
}
