package com.example.trippaminebe.config;

import com.example.trippaminebe.domain.user.service.custom.CustomUserDetailService;
import com.example.trippaminebe.security.jwt.JWTAuthenticationFilter;
import com.example.trippaminebe.security.jwt.JWTUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.example.trippaminebe.domain.admin.service.custom.CustomAdminDetailService;
import com.example.trippaminebe.security.jwt.AdminJWTAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor

public class SecurityConfig {
  private final JWTUtils jwtUtils;
  private final CustomUserDetailService customUserDetailService;
  // 필드 추가
  private final CustomAdminDetailService customAdminDetailService; // Admin 로그인 검증용 서비스 주입

  /*
    로그인 API(/users/login)컨트롤러에 AuthenticationManager주입을 위한 빈 등록
    - 인증 총괄 수행하는 빈
    - 로직) 로그인 컨트롤러 => AuthenticationManager객체의 authenticate(...)호출 => 사용자 검증 => JWT발급
    */
  @Bean
  public AuthenticationManager authenticationManager(
      CustomUserDetailService customUserDetailService,
      PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider =
        new DaoAuthenticationProvider(customUserDetailService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  /*
  비밀번호 해시 생성 및 로그인 시 비밀번호 검증하기 위한 빈 등록
  */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    JWTAuthenticationFilter jwtAuthenticationFilter = new JWTAuthenticationFilter(jwtUtils, customUserDetailService);
    //관리자 전용 JWT 필터 - /admin 경로에서만 동작 (shouldNotFilter로 범위 제한됨)
    AdminJWTAuthenticationFilter adminJwtAuthenticationFilter = new AdminJWTAuthenticationFilter(jwtUtils, customAdminDetailService);
    http
        // CORS 설정
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        .formLogin(form -> form.disable())
        .httpBasic(httpBasic -> httpBasic.disable())

        // CSRF 비활성화 : JWT를 HTTP Authentication Header에 실어 보내는 경우 CSRF 공격 위험이 없음
        .csrf(csrf -> csrf.disable())

        // 토큰 기반 인증을 사용함으로 세션기반 인증 무효화
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // API 요청별 접근 설정
        .authorizeHttpRequests(auth -> auth
            //인증 없이 접근 허용할 엔드포인트 (로그인, 회원가입, Swagger 등)
            .requestMatchers(
                "/admin/auth/login",
                //  로그인
                "/users/auth/login",
                //  회원가입
                "/users/auth/signup",
                "/users/logout",
                "/users/auth/check-email",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            ).permitAll()
            // 위에서 지정한 경로 외의 나머지 모든 요청은 인증이 반드시 필요하도록 설정
            .anyRequest().authenticated()
        )

        // JWT 필터 위지 지정 : UsernamePasswordAuthenticationFilter 실행 이전에 커스텀 JWT 필터 배치
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(adminJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:5173"));
    config.setAllowedOriginPatterns(List.of("*")); // 모든 헤더 허용
    config.setAllowedMethods(Arrays.asList("GET", "POST","PATCH", "PUT", "DELETE", "OPTIONS")); // 허용할 HTTP 메서드
    config.setAllowedHeaders(List.of( // 보안상 허용할 수 있는 HTTP 헤더 목록
        "Authorization",
        "Content-Type",
        "X-Requested-With"
    ));
    // Client(프론트)에서 Authorization 헤더를 읽을 수 있게 노출
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(true); // 쿠키나 인증 헤더를 포함할지 여부

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config); // 모든 API 경로에 적용
    return source;
  }
}
