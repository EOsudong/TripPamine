package com.example.trippaminebe.security.oauth2;


import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import com.example.trippaminebe.security.jwt.JWTUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;


@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JWTUtils jwtUtils;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication
  ) throws IOException, ServletException {

    log.info("========== OAuth2 SuccessHandler 진입 ==========");

    // CustomOAuth2UserService.loadUser()에서 이미 저장/조회가 끝난 User를 그대로 사용
    CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();

    log.info("Principal: {}", authentication.getPrincipal());

    User user = customUserDetails.getUser();
    log.info("User ID: {}", user.getId());
    log.info("User Email: {}", user.getEmail());
    log.info("User Name: {}", user.getUserName());

    // Access Token 생성
    String accessToken = jwtUtils.createAccessToken(user, "ACTIVE");

    log.info("소셜 로그인 성공 - 사용자 이메일: {}, 발급된 JWT 생성 완료", user.getEmail());

    // 프론트엔드로 리다이렉트 (SecurityConfig cors 설정에 맞춘 http://localhost:5173)
    String redirectUrl = UriComponentsBuilder
        .fromUriString("http://localhost:5173/oauth/callback")
        .queryParam("accessToken", accessToken)
        .build()
        .toUriString();

    log.info("Redirect URL 생성 완료");

    getRedirectStrategy().sendRedirect(request, response, redirectUrl);

    log.info("========== OAuth2 SuccessHandler 종료 ==========");
  }
}