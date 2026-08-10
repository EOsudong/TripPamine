package com.example.trippaminebe.security.jwt;

import com.example.trippaminebe.domain.user.service.custom.CustomUserDetailService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
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
  private final CustomUserDetailService customUserDetailService;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    UsernamePasswordAuthenticationToken authentication = null;
    try {
      String token = resolveToken(request);

      if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
        String email = jwtUtils.getEmailFromToken(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

          // UserDetails 조회
          UserDetails userDetails = customUserDetailService.loadUserByUsername(email);

          // 회원이 존재하고 탈퇴 상태가 아닐 때만 인증 객체 생성
          if (userDetails != null) {
            authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities() // null이 아님이 보장됨
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
          }
        }
      }
    } catch (UsernameNotFoundException e) {
      // 존재하지 않거나 탈퇴된 회원일 경우 SecurityContext를 비우고 로그 남김
      logger.warn("인증 실패 - 존재하지 않는 사용자입니다: " + e.getMessage());
    } catch (Exception e) {
      logger.error("Security Context 인증 설정 실패", e);
    }
    SecurityContextHolder
        .getContext().setAuthentication(authentication);

    // 다음 필터로 진행
    filterChain.doFilter(request, response);
  }

  // Header에서 "Authorization: Bearer <token>" 형태의 토큰 추출
  private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7); //"Bearer " 제거
    }
    return null;
  }
}
