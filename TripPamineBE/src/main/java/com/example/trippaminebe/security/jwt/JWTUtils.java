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
      @Value("${JWT_SECRET_KEY}") String secret,
      @Value("${jwt.expiration}") long expirationTime // 기본 1시간 설정(단위: ms)
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

  // 토큰의 만료 시각(exp 클레임)을 꺼낸다. TokenBlacklistService가 "이 토큰을 언제까지
  // 블랙리스트에 들고 있어야 하는지" 판단하는 데 사용 - 토큰 자체가 만료되면 어차피
  // validateToken()에서 걸러지므로 블랙리스트에 영원히 남아있을 필요가 없다.

  // 이미 만료된 토큰이 로그아웃 요청과 함께 들어오는 경우에도(드물지만) exp 값 자체는
  // 여전히 필요하므로, ExpiredJwtException이 던져지면 예외 안에 담긴 Claims에서
  // 만료 시각을 꺼내 재사용한다 (jjwt는 만료 예외에도 파싱된 claims를 담아준다).
  // 서명이 잘못됐거나 형식이 깨진 토큰처럼 애초에 파싱 자체가 불가능한 경우엔 null을 반환하고,
  // 호출부(TokenBlacklistService)가 기본 보관 기간을 적용한다.
  public Date getExpirationFromToken(String token) {
    try {
      return getClaims(token).getExpiration();
    } catch (ExpiredJwtException e) {
      return e.getClaims().getExpiration();
    } catch (JwtException | IllegalArgumentException e) {
      return null;
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
}
