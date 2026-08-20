package com.example.trippaminebe.security.jwt;

import com.example.trippaminebe.domain.admin.service.custom.CustomAdminDetailService;
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

// 요청마다 한 번씩 실행되는 필터. Authorization 헤더의 JWT를 검증해서, 유효하면 SecurityContext에
// "이 요청은 이 관리자가 보낸 것"이라고 등록해줌.
// /admin 경로에만 적용되도록 shouldNotFilter()로 범위를 제한
@RequiredArgsConstructor
public class AdminJWTAuthenticationFilter extends OncePerRequestFilter {
  private final JWTUtils jwtUtils;
  private final CustomAdminDetailService customAdminDetailService;

  // "/admin"으로 시작하는 요청에만 이 필터를 적용 (그 외 경로는 이 필터를 건너뜀)
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/admin");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    UsernamePasswordAuthenticationToken authentication = null;
    try {
      String token = resolveToken(request);

      if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
        String adminLoginId = jwtUtils.getLoginIdFromToken(token);

        // 토큰에서 아이디를 뽑았고, 아직 이 요청에 인증 정보가 없을 때만 인증 처리 진행
        if (adminLoginId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
          UserDetails adminDetails = customAdminDetailService.loadUserByUsername(adminLoginId);

          if (adminDetails != null) {
            authentication = new UsernamePasswordAuthenticationToken(
                adminDetails,
                null,
                adminDetails.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
          }
        }
      }
    } catch (UsernameNotFoundException e) {
      // 존재하지 않거나 정지된 관리자일 경우 - 인증 없이 다음 필터로 넘어감 (로그만 남김)
      logger.warn("관리자 인증 실패 - 존재하지 않는 관리자입니다: " + e.getMessage());
    } catch (Exception e) {
      logger.error("Admin Security Context 인증 설정 실패", e);
    }
    // 기존 코드가 try-catch 밖에서 무조건 실행되어,
    // authentication이 실제로 세팅된 경우에만 SecurityContext에 반영하도록 수정.
    if (authentication != null) {
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // 다음 필터로 진행
    filterChain.doFilter(request, response);
  }

  // Header에서 "Authorization: Bearer <token>" 형태의 토큰 추출
  private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7); // "Bearer " 제거
    }
    return null;
  }
}