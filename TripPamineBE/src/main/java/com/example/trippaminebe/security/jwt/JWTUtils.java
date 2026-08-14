package com.example.trippaminebe.security.jwt;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTUtils {

  private final SecretKey secretKey; // JWT토큰 서명/ 검증용 비밀키
  private final long expirationTime; // 토큰 유효시간(ms)

  public JWTUtils(
      @Value("${jwt.secret:trippamine_default_secret_key_must_be_at_least_32_bytes_long_123456}") String secret,
      @Value("${jwt.expiration:3600000}") long expirationTime // 기본 1시간 설정(단위: ms)
  ) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationTime = expirationTime;
  }

  // Access Token 생성
  public String createAccessToken(User user, String status) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expirationTime);

    return Jwts.builder()
        .setSubject(user.getEmail())
        .claim("status", status)
        .setIssuedAt(now)
        .setExpiration(expiryDate)
        .signWith(secretKey)
        .compact();
  }


  private Claims getClaims(String token) {
    return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
  }

  // 토큰에서 Email(subject) 추출
  public String getEmailFromToken(String token) {
    return getClaims(token).getSubject();
  }

  // 토큰 유효성 검증
  public boolean validateToken(String token) {
    try {
      getClaims(token);
      return true;
    } catch (ExpiredJwtException e){
      // 토큰 만료 에러 (필요시 로그 남기기 가능)
      return false;
    } catch (JwtException | IllegalArgumentException e) {
      // 위변조 또는 형식 오류
      return false;
    }
  }

  // Access Token 생성 (관리자용 오버로드).
  public String createAccessToken(Admin admin, String status) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expirationTime);

    return Jwts.builder()
        .setSubject(admin.getAdminLoginId()) // 토큰의 주인이 누구인지 - 여기선 관리자 로그인 아이디
        .claim("status", status)      // 토큰 발급 시점의 계정 상태 (참고용 클레임)
        .setIssuedAt(now)                    // 발급 시각
        .setExpiration(expiryDate)           // 만료 시각
        .signWith(secretKey)                 // 비밀키로 서명 (위변조 방지)
        .compact();
  }

  // 토큰에서 로그인 아이디(subject) 추출 - Admin 토큰 파싱 전용.
  // getEmailFromToken()과 내부 로직은 동일하지만, "이메일이 아니라 로그인 아이디를 꺼낸다"는 의미를
  // 헷갈리지 않도록 이름을 따로 둠 (AdminJWTAuthenticationFilter에서 호출)
  public String getLoginIdFromToken(String token) {
    return getClaims(token).getSubject();
  }

  //로그아웃된 토큰인지 확인
  public String tokenBlacklistService(String token){
    return token;
  }
}
