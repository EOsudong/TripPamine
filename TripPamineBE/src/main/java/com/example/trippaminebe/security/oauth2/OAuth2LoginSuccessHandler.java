package com.example.trippaminebe.security.oauth2;


import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import com.example.trippaminebe.security.jwt.JWTUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

  // 소셜 로그인 성공 후 사용자를 돌려보낼 프론트엔드 콜백 주소.
  // application.yaml의 app.oauth2.redirect-uri (기본값 http://localhost:5173/oauth/callback)
  @Value("${app.oauth2.redirect-uri}")
  private String redirectUri;

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

    // 프론트엔드 콜백으로 리다이렉트.
    //
    // [수정] 전에는 "http://localhost:5173/oauth/callback"이 여기에 그대로 박혀 있었다.
    // 그래서 프론트를 localhost가 아닌 주소로 띄우면(휴대폰 GPS 테스트를 하려면 브라우저
    // Geolocation이 HTTPS를 요구하기 때문에 터널 주소를 쓸 수밖에 없다) 소셜 로그인은
    // 성공해도 폰이 localhost:5173으로 리다이렉트돼서 항상 실패했다.
    // 이제 app.oauth2.redirect-uri 설정값을 쓰고, 배포 환경에서는 환경변수
    // OAUTH2_REDIRECT_URI로 덮어쓸 수 있다.
    String redirectUrl = UriComponentsBuilder
        .fromUriString(redirectUri)
        .queryParam("accessToken", accessToken)
        .build()
        .toUriString();

    log.info("Redirect URL 생성 완료");

    getRedirectStrategy().sendRedirect(request, response, redirectUrl);

    log.info("========== OAuth2 SuccessHandler 종료 ==========");
  }
}
