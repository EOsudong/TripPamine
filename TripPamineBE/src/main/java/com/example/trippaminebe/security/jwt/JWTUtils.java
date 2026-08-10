package com.example.trippaminebe.security.jwt;

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


}
