package com.example.trippaminebe.domain.recommendation.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "AI_TRAVEL_RECOMMENDATIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTravelRecommendation {

  @Id
  @GeneratedValue(
      strategy = GenerationType.SEQUENCE,
      generator = "ai_travel_recommend_seq"
  )
  @SequenceGenerator(
      name = "ai_travel_recommend_seq",
      sequenceName = "SEQ_AI_TRAVEL_RECOMMENDATIONS",
      allocationSize = 1
  )
  @Column(name = "RECOMMEND_ID")
  private Long recommendId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "PLAN_ID",
      nullable = false,
      unique = true
  )
  private TravelPlan travelPlan;

  @Lob
  @Column(name = "RECOMMEND_JSON", nullable = false)
  private String recommendJson;

  @Column(name = "CREATED_AT", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "UPDATED_AT", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();

    if (createdAt == null) {
      createdAt = now;
    }

    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
