package com.example.trippaminebe.domain.replanning.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReplanningPreviewStore {

  // 미리보기 결과 유효시간: 10분
  private static final Duration TTL = Duration.ofMinutes(10);

  // previewId → 미리보기 데이터
  private final Map<String, PreviewData> previews =
      new ConcurrentHashMap<>();

  /**
   * 미리보기 데이터
   *
   * userId: 요청한 사용자
   * planId: 여행 계획 ID
   * day: 변경 대상 여행 일차
   * proposal: AI가 생성한 재계획 결과
   * expiresAt: 만료 시간
   */
  public record PreviewData(
      Long userId,
      Long planId,
      int day,
      Map<String, Object> proposal,
      BigDecimal remainingBudget,
      Instant expiresAt
  ) {
  }

  /**
   * AI 재계획 미리보기 결과 저장
   */
  public String save(
      Long userId,
      Long planId,
      int day,
      Map<String, Object> proposal,
      BigDecimal remainingBudget
  ) {
    String previewId = UUID.randomUUID().toString();

    PreviewData data = new PreviewData(
        userId,
        planId,
        day,
        Map.copyOf(proposal),
        remainingBudget,
        Instant.now().plus(TTL)
    );

    previews.put(previewId, data);

    return previewId;
  }

  /**
   * 미리보기 결과 조회
   */
  public PreviewData get(
      String previewId,
      Long userId,
      Long planId
  ) {
    PreviewData data = previews.get(previewId);

    if (data == null) {
      throw new IllegalArgumentException(
          "재계획 미리보기가 존재하지 않습니다."
      );
    }

    if (Instant.now().isAfter(data.expiresAt())) {
      previews.remove(previewId);

      throw new IllegalArgumentException(
          "재계획 미리보기가 만료되었습니다."
      );
    }

    if (!data.userId().equals(userId)
        || !data.planId().equals(planId)) {

      throw new SecurityException(
          "다른 사용자의 재계획 결과에는 접근할 수 없습니다."
      );
    }

    return data;
  }

  /**
   * 확정 완료 후 미리보기 삭제
   */
  public void remove(String previewId) {
    previews.remove(previewId);
  }
}