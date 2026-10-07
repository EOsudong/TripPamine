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


@RequiredArgsConstructor
public class AdminJWTAuthenticationFilter
        extends OncePerRequestFilter {

  private final JWTUtils jwtUtils;

  private final CustomAdminDetailService
          customAdminDetailService;


  // =========================================
  // 관리자 API에서만 Filter 실행
  // =========================================

  @Override
  protected boolean shouldNotFilter(
          HttpServletRequest request
  ) {

    String uri =
            request.getRequestURI();


    // /admin이 아니면
    // 관리자 JWT Filter를 실행하지 않는다.
    return !uri.startsWith("/admin");
  }


  // =========================================
  // 관리자 JWT 인증
  // =========================================

  @Override
  protected void doFilterInternal(

          HttpServletRequest request,

          HttpServletResponse response,

          FilterChain filterChain

  ) throws ServletException, IOException {


    UsernamePasswordAuthenticationToken
            authentication = null;


    try {

      // =====================================
      // JWT 추출
      // =====================================

      String token =
              resolveToken(request);


      // =====================================
      // JWT 검증
      // =====================================

      if (

              StringUtils.hasText(token)

                      && jwtUtils
                      .validateToken(token)

      ) {


        // =================================
        // 관리자 로그인 ID 추출
        // =================================

        String adminLoginId =
                jwtUtils
                        .getLoginIdFromToken(
                                token
                        );


        // =================================
        // 아직 인증되지 않은 경우
        // =================================

        if (

                adminLoginId != null

                        && SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        == null

        ) {


          // =============================
          // 관리자 조회
          // =============================

          UserDetails adminDetails =
                  customAdminDetailService
                          .loadUserByUsername(
                                  adminLoginId
                          );


          if (
                  adminDetails != null
          ) {


            // =========================
            // Authentication 생성
            // =========================

            authentication =
                    new UsernamePasswordAuthenticationToken(

                            adminDetails,

                            null,

                            adminDetails
                                    .getAuthorities()

                    );


            // =========================
            // 요청 정보 추가
            // =========================

            authentication.setDetails(

                    new WebAuthenticationDetailsSource()
                            .buildDetails(
                                    request
                            )

            );
          }
        }
      }

    } catch (
            UsernameNotFoundException e
    ) {

      logger.warn(

              "관리자 인증 실패 - 존재하지 않는 관리자입니다: "
                      + e.getMessage()

      );

    } catch (
            Exception e
    ) {

      logger.error(

              "Admin Security Context 인증 설정 실패",

              e
      );
    }


    // =========================================
    // 관리자 인증 성공
    // =========================================

    if (
            authentication != null
    ) {

      SecurityContextHolder
              .getContext()
              .setAuthentication(
                      authentication
              );
    }


    // =========================================
    // 다음 Filter
    // =========================================

    filterChain.doFilter(
            request,
            response
    );
  }


  // =========================================
  // Authorization Header에서 JWT 추출
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