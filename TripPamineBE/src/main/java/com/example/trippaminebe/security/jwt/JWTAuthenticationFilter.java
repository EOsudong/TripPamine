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
public class JWTAuthenticationFilter
        extends OncePerRequestFilter {

  // =========================================
  // JWT 관련 객체
  // =========================================

  private final JWTUtils jwtUtils;

  private final CustomUserDetailsService
          customUserDetailsService;

  private final TokenBlacklistService
          tokenBlacklistService;


  // =========================================
  // JWT Filter 제외 경로
  // =========================================

  @Override
  protected boolean shouldNotFilter(
          HttpServletRequest request
  ) {

    String uri = request.getRequestURI();

    return uri.startsWith("/users/auth/login")
            || uri.startsWith("/users/auth/signup")
            || uri.startsWith("/users/auth/check-email")
            || uri.startsWith("/oauth2")
            || uri.startsWith("/login/oauth2")
            || uri.startsWith("/swagger-ui")
            || uri.startsWith("/admin")
            || uri.startsWith("/v3/api-docs");
  }


  // =========================================
  // JWT 인증
  // =========================================

  @Override
  protected void doFilterInternal(

          HttpServletRequest request,

          HttpServletResponse response,

          FilterChain filterChain

  ) throws ServletException, IOException {


    try {

      // =====================================
      // Authorization Header에서
      // JWT 추출
      // =====================================

      String token =
              resolveToken(request);


      // =====================================
      // JWT가 존재하고
      // 정상적인 토큰인지 확인
      // =====================================

      if (

              StringUtils.hasText(token)

                      && jwtUtils.validateToken(token)

                      && !tokenBlacklistService
                      .isBlacklisted(token)

      ) {


        // =================================
        // JWT에서 사용자 이메일 추출
        // =================================

        String email =
                jwtUtils
                        .getEmailFromToken(token);


        // =================================
        // 아직 인증 정보가 없는 경우
        // 사용자 인증 처리
        // =================================

        if (

                email != null

                        && SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        == null

        ) {


          // =============================
          // 사용자 조회
          // =============================

          UserDetails userDetails =
                  customUserDetailsService
                          .loadUserByUsername(
                                  email
                          );


          // =============================
          // Spring Security
          // Authentication 생성
          // =============================

          UsernamePasswordAuthenticationToken
                  authentication =

                  new UsernamePasswordAuthenticationToken(

                          userDetails,

                          null,

                          userDetails.getAuthorities()

                  );


          // =============================
          // 요청 정보 추가
          // =============================

          authentication.setDetails(

                  new WebAuthenticationDetailsSource()
                          .buildDetails(
                                  request
                          )

          );


          // =============================
          // SecurityContext에
          // 로그인 사용자 저장
          // =============================

          SecurityContextHolder
                  .getContext()
                  .setAuthentication(
                          authentication
                  );
        }
      }

    } catch (
            UsernameNotFoundException e
    ) {

      logger.warn(
              "인증 실패 - 존재하지 않는 사용자입니다: "
                      + e.getMessage()
      );

    } catch (
            Exception e
    ) {

      logger.error(
              "JWT 인증 처리 중 오류 발생",
              e
      );
    }


    // =========================================
    // 다음 Filter 진행
    // =========================================

    filterChain.doFilter(
            request,
            response
    );
  }


  // =========================================
  // Authorization Header에서 JWT 추출
  //
  // Authorization: Bearer JWT
  // =========================================

  private String resolveToken(
          HttpServletRequest request
  ) {

    String bearerToken =
            request.getHeader(
                    "Authorization"
            );


    if (

            StringUtils.hasText(
                    bearerToken
            )

                    && bearerToken.startsWith(
                    "Bearer "
            )

    ) {

      return bearerToken.substring(7);
    }


    return null;
  }
}