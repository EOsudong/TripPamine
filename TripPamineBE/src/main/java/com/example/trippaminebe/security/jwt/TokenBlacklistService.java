package com.example.trippaminebe.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// JWT 블랙리스트 (로그아웃된 토큰 목록) - 인메모리 구현.
//
// [메모리 누수 수정] 예전 구현은 Set<String>에 토큰을 추가만 하고 절대 지우지 않았다.
// 로그아웃이 쌓일수록 서버가 재시작될 때까지 메모리가 무한히 늘어나는 문제였다 - 토큰 자체가
// 만료돼서 어차피 더 이상 유효하지 않은 상태가 돼도 블랙리스트에는 계속 남아있었다.
// 이제 각 토큰의 실제 만료 시각(JWT exp 클레임)을 같이 저장해서
//   1) isBlacklisted() 조회 시점에 이미 만료된 항목은 그 자리에서 지우고(지연 정리),
//   2) 조회조차 안 되는 토큰도 방치되지 않도록 5분마다 스케줄러로 만료 항목을 청소한다.
//
// [알아두면 좋은 한계] 여전히 서버 프로세스 메모리에만 저장되는 구조라서,
//   - 서버가 재배포/재시작되면 블랙리스트가 초기화된다 - 재시작 직후엔 로그아웃했던 토큰이
//     (원래 만료 시각까지는) 다시 유효한 것처럼 동작할 수 있다.
//   - 인스턴스를 여러 개로 수평 확장하면 인스턴스 간에 블랙리스트가 공유되지 않는다.
// 지금은 단일 인스턴스 운영을 전제로 메모리 누수만 우선 해결한 상태이고, 여러 인스턴스로
// 확장하게 되면 Redis 같은 공유 저장소로 옮기는 걸 권장한다.
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

  // 만료 시각을 못 구한(파싱 자체가 안 되는) 토큰을 위한 기본 보관 기간.
  // JWT_EXPIRATION(jwt.expiration) 설정값을 몰라도 안전하게 동작하도록 넉넉히 24시간으로 둔다.
  private static final long FALLBACK_RETENTION_MILLIS = TimeUnit.HOURS.toMillis(24);

  private final JWTUtils jwtUtils;

  // token -> 만료 시각(epoch millis)
  private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

  public void blacklist(String token) {
    Date expiration = jwtUtils.getExpirationFromToken(token);
    long expiryMillis = expiration != null
        ? expiration.getTime()
        : System.currentTimeMillis() + FALLBACK_RETENTION_MILLIS;
    blacklist.put(token, expiryMillis);
  }

  public boolean isBlacklisted(String token) {
    Long expiryMillis = blacklist.get(token);
    if (expiryMillis == null) {
      return false;
    }
    if (expiryMillis <= System.currentTimeMillis()) {
      // 이미 만료된 토큰 - 어차피 validateToken()이 이 토큰을 걸러내므로 더 들고 있을 이유가 없다.
      blacklist.remove(token);
      return false;
    }
    return true;
  }

  // 5분마다 만료된 블랙리스트 항목을 청소한다.
  // (참고: @EnableScheduling은 tour 도메인 설정 클래스에 이미 선언되어 있어 애플리케이션
  //  전역에 적용된다 - 여기서 다시 선언할 필요 없음)
  @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
  public void cleanupExpiredTokens() {
    long now = System.currentTimeMillis();
    blacklist.entrySet().removeIf(entry -> entry.getValue() <= now);
  }
}
