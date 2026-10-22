package com.example.trippaminebe.common.cache;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * 매일 갱신되는 DB 캐시의 기동 시 보완 정책.
 *
 * 기동 시간과 매일 정기 갱신 주기를 비교하여 같은 날 서버 재시작으로 외부 API를
 * 불필요하게 다시 호출하지 않는다. DB에는 기존처럼 LocalDateTime이 저장되므로,
 * 비교 기준을 DB 타임스탬프의 기존 JVM 시간대로 변환해 스키마 변경 없이 사용한다.
 */
public final class DailyCacheRefreshPolicy {

  private DailyCacheRefreshPolicy() {
    // 정적 정책 함수만 제공하므로 인스턴스 생성을 방지한다.
  }

  /**
   * 보완 갱신할 키를 반환한다.
   * 1) 예정된 마지막 정기 갱신 이후 성공 기록이 하나도 없으면 전체 키를 선택한다.
   * 2) 성공 기록이 하나라도 있으면 아직 DB에 없는 키만 선택한다.
   * 3) 전부 존재하고 해당 정기 갱신 기록이 있으면 빈 목록(재호출 없음)을 반환한다.
   *
   * 기존 FETCHED_AT은 성공적으로 저장한 시각이므로 일부 외부 API 호출이 실패해도
   * 나머지 성공 기록이 있다면 모든 키를 재호출하는 현상을 방지한다.
   */
  public static List<String> selectStartupKeys(
      List<String> expectedKeys,
      Map<String, LocalDateTime> fetchedAtByKey,
      LocalTime dailyRefreshAt,
      ZoneId schedulerZone,
      ZoneId storedTimestampZone,
      Instant now
  ) {
    if (expectedKeys.isEmpty()) {
      return List.of();
    }

    ZonedDateTime scheduledNow = now.atZone(schedulerZone);
    ZonedDateTime scheduledCutoff = scheduledNow.toLocalDate()
        .atTime(dailyRefreshAt).atZone(schedulerZone);
    if (scheduledNow.isBefore(scheduledCutoff)) {
      scheduledCutoff = scheduledCutoff.minusDays(1);
    }
    LocalDateTime storedCutoff = scheduledCutoff
        .withZoneSameInstant(storedTimestampZone).toLocalDateTime();

    boolean refreshedSinceCutoff = expectedKeys.stream().anyMatch(key -> {
      LocalDateTime fetchedAt = fetchedAtByKey.get(key);
      return fetchedAt != null && !fetchedAt.isBefore(storedCutoff);
    });
    if (!refreshedSinceCutoff) {
      return List.copyOf(expectedKeys);
    }

    return expectedKeys.stream()
        .filter(key -> fetchedAtByKey.get(key) == null)
        .toList();
  }
}
