package com.example.trippaminebe.domain.diary.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// TRAVEL_DIARIES: 여행 종료 후 AI가 가계부 지출 패턴/방문 장소/퀘스트 성공률을 분석해
// 생성한 일기 본문과 도파민 감정 점수를 저장하는 메인 테이블
@Entity
@Table(name = "TRAVEL_DIARIES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelDiary {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_TRAVEL_DIARIES_GEN")
    @SequenceGenerator(name = "SEQ_TRAVEL_DIARIES_GEN", sequenceName = "SEQ_TRAVEL_DIARIES", allocationSize = 1)
    @Column(name = "DIARY_ID")
    private Long diaryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PLAN_ID", nullable = false)
    private TravelPlan travelPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "AI_TITLE", nullable = false, length = 150)
    private String aiTitle;

    @Lob
    @Column(name = "AI_CONTENT", nullable = false)
    private String aiContent;

    @Column(name = "DOPAMINE_SCORE", nullable = false)
    @Builder.Default
    private Long dopamineScore = 0L;

    @Column(name = "AI_IMAGE_URL", length = 500)
    private String aiImageUrl;

    @Column(name = "SHARE_YN", nullable = false, columnDefinition = "CHAR(1)")
    @Builder.Default
    private String shareYn = "N";

    @Column(name = "CREATE_DATE", insertable = false, updatable = false)
    private LocalDateTime createDate;

    @OneToMany(mappedBy = "travelDiary", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DiaryKeyword> keywords = new ArrayList<>();

    // JPA 연관관계 편의 메서드(1:N 양방향 관계 설정)
    public void addKeyword(DiaryKeyword keyword) {
        this.keywords.add(keyword);
        keyword.assignDiary(this);
    }

    // 공유 여부 토글 도메인 메서드
    public void toggleShare() {
        this.shareYn = "Y".equals(this.shareYn) ? "N" : "Y";
    }
}
