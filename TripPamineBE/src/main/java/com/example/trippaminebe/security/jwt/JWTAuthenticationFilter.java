package com.example.trippaminebe.security.jwt;

import com.example.trippaminebe.domain.user.service.custom.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JWTAuthenticationFilter extends OncePerRequestFilter {
  //JWTUtils 및 커스텀 UserDetailService 생성자 주입
  private final JWTUtils jwtUtils;
  private final CustomUserDetailsService customUserDetailsService;

  //로그아웃 검증 생성자 주입
  private final TokenBlacklistService tokenBlacklistService;

  // JWTAuthenticationFilter "/users"로 시작하는 요청에만 적용되게 제한
  // + "/accounts"로 시작하는 요청에도 적용되게 제한
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return !(uri.startsWith("/users") || uri.startsWith("/accounts"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {

    try {
      // 1. Authorization 헤더에서 JWT 추출
      String token = resolveToken(request);

      // 2. 토큰이 존재하고 유효한 경우에만 인증 처리
      if (StringUtils.hasText(token)
          && jwtUtils.validateToken(token)
          && !tokenBlacklistService.isBlacklisted(token)) {

        // 3. JWT에서 이메일 추출
        String email = jwtUtils.getEmailFromToken(token);

        //로그아웃된 토큰인지 확인
/*        if (tokenBlacklistService.isBlacklisted(token)){
          logger.warn("로그아웃된 JWT입니다.");

          filterChain.doFilter(request,response);

          return;
        }*/


        // 4. 아직 인증되지 않은 경우
        if (email != null && SecurityContextHolder
            .getContext()
            .getAuthentication() == null) {

          // 5. DB에서 사용자 조회
          UserDetails userDetails =
              customUserDetailsService.loadUserByUsername(email);

          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(
                  userDetails,
                  null,
                  userDetails.getAuthorities()
              );

          // 7. 요청 정보 추가
          authentication.setDetails(
              new WebAuthenticationDetailsSource()
                  .buildDetails(request)
          );

          // 8. SecurityContext에 인증 저장
          SecurityContextHolder
              .getContext()
              .setAuthentication(authentication);
        }
      }
    } catch (
        UsernameNotFoundException e) {
      // 존재하지 않거나 탈퇴된 회원일 경우 SecurityContext를 비우고 로그 남김
      logger.warn("인증 실패 - 존재하지 않는 사용자입니다: " + e.getMessage());
    } catch (
        Exception e) {
      logger.error("JWT 인증 처리 중 오류 발생", e);
    }

    // JWT가 없어도 다음 필터로 반드시 진행
    filterChain.doFilter(request, response);
  }

  /**
   * Authorization: Bearer {JWT}
   * 에서 JWT만 추출
   */
// Header에서 "Authorization: Bearer <token>" 형태의 토큰 추출
  private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7); //"Bearer " 제거
    }
    return null;
  }
}
