package com.example.trippaminebe.domain.mascot.service;

import com.example.trippaminebe.domain.mascot.exception.EncounterTokenInvalidException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// encounter -> catch 사이의 단기 포획 세션을 들고 있는 저장소.
//
// 왜 필요한가: 프론트가 "반경 안에 들어왔다"고 판단해서 안드로이드 AR 화면을 띄우더라도,
// 그 판단은 클라이언트 GPS 값을 믿은 것뿐이다. 포획이 최종 확정되는 시점(/catch)에
// "그 encounter가 실제로 서버 검증(GeoUtils 반경+정확도)을 통과해서 발급된 게 맞는지"를
// 토큰으로 다시 확인해야, 위치 조작 앱으로 좌표만 바꿔서 /catch를 직접 호출하는 부정 포획을 막을 수 있다.
//
// [수정 내역]
// 1. 조우 시점의 "사용자 실제 좌표"를 세션에 저장 → 도감의 포획 위치(CAUGHT_LAT/LNG)에 사용
//    (예전에는 스폰 지점 좌표가 저장됐다)
// 2. 토큰 하나당 포획 시도 횟수 제한(MAX_ATTEMPTS). 실패하면 남은 횟수가 줄고, 다 쓰면 토큰이 폐기되어
//    다시 조우(=반경 안 GPS 재검증)부터 해야 한다. 서버 확률 판정을 무한 재시도로 뚫는 것을 막는다.
// 3. 성공 시 consume()으로 원자적으로 제거 → 같은 토큰으로 동시에 두 번 /catch를 보내도 한 번만 성공.
//
// [한계] 서버 메모리에 저장하므로 재시작 시 진행 중인 세션은 사라진다(TTL 60초라 영향 적음).
@Component
public class EncounterTokenStore {

    private static final long TOKEN_TTL_SECONDS = 60;
    public static final int MAX_ATTEMPTS = 3;

    public record EncounterSession(
            Long userId,
            Long spawnId,
            BigDecimal encounterLat,
            BigDecimal encounterLng,
            Instant expiresAt,
            AtomicInteger failures
    ) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public int remainingAttempts() {
            return Math.max(0, MAX_ATTEMPTS - failures.get());
        }
    }

    private final Map<String, EncounterSession> sessions = new ConcurrentHashMap<>();

    // 조우(encounter) 성공 시 새 토큰을 발급한다. 발급 김에 만료된 옛 토큰들도 정리(opportunistic cleanup).
    public String issue(Long userId, Long spawnId, BigDecimal lat, BigDecimal lng) {
        cleanupExpired();
        String token = UUID.randomUUID().toString();
        sessions.put(token, new EncounterSession(
                userId, spawnId, lat, lng,
                Instant.now().plusSeconds(TOKEN_TTL_SECONDS),
                new AtomicInteger(0)
        ));
        return token;
    }

    public int ttlSeconds() {
        return (int) TOKEN_TTL_SECONDS;
    }

    // 토큰이 유효한지 검증만 한다(소모하지 않음).
    public EncounterSession validate(String token, Long userId, Long spawnId) {
        EncounterSession session = sessions.get(token);
        if (session == null || session.isExpired()) {
            throw new EncounterTokenInvalidException("포획 세션이 만료되었거나 존재하지 않습니다. 다시 마스코트에게 접근해주세요.");
        }
        if (!session.userId().equals(userId) || !session.spawnId().equals(spawnId)) {
            throw new EncounterTokenInvalidException("이 포획 세션은 요청한 사용자/스폰 지점과 일치하지 않습니다.");
        }
        return session;
    }

    // 포획 실패 1회 기록. 남은 시도 횟수를 반환하고, 0이 되면 토큰을 폐기한다.
    public int recordFailure(String token, EncounterSession session) {
        int failures = session.failures().incrementAndGet();
        int remaining = Math.max(0, MAX_ATTEMPTS - failures);
        if (remaining == 0) {
            sessions.remove(token);
        }
        return remaining;
    }

    // 포획 성공 확정 시 토큰을 원자적으로 제거한다. 이미 다른 요청이 가져갔으면 false.
    public boolean consume(String token) {
        return sessions.remove(token) != null;
    }

    private void cleanupExpired() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
