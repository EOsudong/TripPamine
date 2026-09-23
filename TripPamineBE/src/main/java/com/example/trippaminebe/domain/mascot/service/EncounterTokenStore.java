package com.example.trippaminebe.domain.mascot.service;

import com.example.trippaminebe.domain.mascot.exception.EncounterTokenInvalidException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// encounter -> catch 사이의 단기 포획 세션을 들고 있는 저장소.
//
// 왜 필요한가: 프론트가 "반경 안에 들어왔다"고 판단해서 안드로이드 AR 화면을 띄우더라도,
// 그 판단은 클라이언트 GPS 값을 믿은 것뿐이다. 포획이 최종 확정되는 시점(/catch)에
// "그 encounter가 실제로 서버 검증(GeoUtils 반경+정확도)을 통과해서 발급된 게 맞는지"를
// 토큰으로 다시 확인해야, 위치 조작 앱으로 좌표만 바꿔서 /catch를 직접 호출하는 부정 포획을 막을 수 있다.
// (quest 도메인은 클리어를 1스텝(=clear API 한 번)으로 끝내지만, 마스코트는 "조우 -> AR 미니게임 -> 확정"
//  이렇게 2스텝이라 이 중간 상태를 어딘가에 들고 있어야 해서 새로 추가한 컴포넌트다.)
@Component
public class EncounterTokenStore {

    private static final long TOKEN_TTL_SECONDS = 60;

    public record EncounterSession(Long userId, Long spawnId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, EncounterSession> sessions = new ConcurrentHashMap<>();

    // 조우(encounter) 성공 시 새 토큰을 발급한다. 발급 김에 만료된 옛 토큰들도 정리(opportunistic cleanup).
    public String issue(Long userId, Long spawnId) {
        cleanupExpired();
        String token = UUID.randomUUID().toString();
        sessions.put(token, new EncounterSession(userId, spawnId, Instant.now().plusSeconds(TOKEN_TTL_SECONDS)));
        return token;
    }

    public int ttlSeconds() {
        return (int) TOKEN_TTL_SECONDS;
    }

    // 포획 확정(/catch) 시 토큰이 유효한지 검증만 하고 소모하지는 않는다.
    // (포획 실패(success=false)면 사용자가 TTL 안에서 재도전할 수 있어야 하므로 여기서 지우지 않음)
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

    // 포획이 최종 성공으로 확정됐을 때만 호출 - 같은 토큰으로 중복 포획하는 것을 막는다.
    public void invalidate(String token) {
        sessions.remove(token);
    }

    private void cleanupExpired() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
