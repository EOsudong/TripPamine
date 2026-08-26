package com.example.trippaminebe.config;

import com.example.trippaminebe.domain.admin.service.custom.CustomAdminDetailService;
import com.example.trippaminebe.domain.user.service.custom.CustomOAuth2UserService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetailsService;
import com.example.trippaminebe.security.jwt.AdminJWTAuthenticationFilter;
import com.example.trippaminebe.security.jwt.JWTAuthenticationFilter;
import com.example.trippaminebe.security.jwt.JWTUtils;
import com.example.trippaminebe.security.jwt.TokenBlacklistService;
import com.example.trippaminebe.security.oauth2.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor

public class SecurityConfig {
	private final JWTUtils jwtUtils;
	private final CustomUserDetailsService customUserDetailsService;
	private final TokenBlacklistService tokenBlacklistService;
	// 필드 추가
	private final CustomAdminDetailService customAdminDetailService; // Admin 로그인 검증용 서비스 주입
	private final CustomOAuth2UserService customOAuth2UserService; // 주입 추가
	private final OAuth2LoginSuccessHandler oAuth;

	/*
		로그인 API(/users/login)컨트롤러에 AuthenticationManager주입을 위한 빈 등록
		- 인증 총괄 수행하는 빈
		- 로직) 로그인 컨트롤러 => AuthenticationManager객체의 authenticate(...)호출 => 사용자 검증 => JWT발급
		*/
	@Bean
	public AuthenticationManager authenticationManager(
			CustomUserDetailsService customUserDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider =
				new DaoAuthenticationProvider(customUserDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}


	@Bean
	public SecurityFilterChain filterChain(
			HttpSecurity http,
			// corsConfigurationSource()가 @Value 파라미터를 받게 되면서 더 이상 이 메서드 안에서
			// 직접 호출할 수 없다. 스프링이 만들어 둔 빈을 주입받아 쓴다.
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		JWTAuthenticationFilter jwtAuthenticationFilter =
				new JWTAuthenticationFilter(
						jwtUtils,
						customUserDetailsService,
						tokenBlacklistService
				);
		//관리자 전용 JWT 필터 - /admin 경로에서만 동작 (shouldNotFilter로 범위 제한됨)
		AdminJWTAuthenticationFilter adminJwtAuthenticationFilter = new AdminJWTAuthenticationFilter(jwtUtils, customAdminDetailService);
		http
				// CORS 설정
				.cors(cors -> cors.configurationSource(corsConfigurationSource))

				.formLogin(form -> form.disable())
				.httpBasic(httpBasic -> httpBasic.disable())

				// CSRF 비활성화 : JWT를 HTTP Authentication Header에 실어 보내는 경우 CSRF 공격 위험이 없음
				.csrf(csrf -> csrf.disable())

				// 토큰 기반 인증을 사용함으로 세션기반 인증 무효화
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))


				.exceptionHandling(exception -> exception
						.authenticationEntryPoint((request, response, authException) -> {
							response.setContentType("application/json;charset=UTF-8");
							response.setStatus(org.springframework.http.HttpStatus.UNAUTHORIZED.value());
							response.getWriter().write(
									"{\"status\":401,\"message\":\"인증이 필요합니다. 로그인 후 Authorization 헤더에 Bearer 토큰을 담아 요청해주세요.\"}"
							);
						})
						.accessDeniedHandler((request, response, accessDeniedException) -> {
							response.setContentType("application/json;charset=UTF-8");
							response.setStatus(org.springframework.http.HttpStatus.FORBIDDEN.value());
							response.getWriter().write(
									"{\"status\":403,\"message\":\"접근 권한이 없습니다.\"}"
							);
						})
				)

				// API 요청별 접근 설정
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						//인증 없이 접근 허용할 엔드포인트 (로그인, 회원가입, Swagger 등)
						.requestMatchers(
								"/admin/auth/login",
								"/users/auth/login",
								"/users/auth/signup",
								"/users/auth/check-email",
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/v3/api-docs",
								"/v3/api-docs/**",
								// [보안 수정] "/travel-plans/**" 를 이 목록에서 제거했습니다.
								// 여행 계획은 전부 "내 계획"을 다루는 API인데 permitAll로 열려 있어서,
								// 토큰 없이도 등록/조회/수정/삭제가 가능했습니다. (컨트롤러가 비로그인 요청을
								// 하드코딩된 테스트 계정 ID로 처리하고 있어 401 대신 조용히 성공했습니다)
								// 이제 아래 anyRequest().authenticated() 규칙에 걸려 인증이 필수입니다.
								"/oauth2/**",
								"/login/oauth2/**",
								"/tour/**",
								"/recommendations/**",
								// [Mock 은행 연동 추가] Mock 오픈뱅킹 서버 엔드포인트.
								// 실제 은행 API도 우리 서비스의 사용자 JWT를 알지 못하는 것과 동일하게,
								// MockOpenBankingClient가 서버 대 서버로 호출하는 이 경로는 인증 없이 열어둔다.
								"/mock-bank/**",
								// [금융상품 정보 추가] 금융감독원 오픈API(금융상품 한눈에) 연동 데이터.
								"/finance/**",
								// 개발용 GPS 테스트 API만 인증 없이 허용
								"/quests/*/test-location"
						).permitAll()
						.requestMatchers("/users/**")
						.authenticated()
						// 위에서 지정한 경로 외의 나머지 모든 요청은 인증이 반드시 필요하도록 설정
						.anyRequest().authenticated()
				)
				// 소셜 로그인(OAuth2) 핸들러 연결
				.oauth2Login(oauth2 -> oauth2
						.redirectionEndpoint(
								redirection -> redirection.baseUri(
										"/login/oauth2/code/*"))
						.userInfoEndpoint(
								userInfo -> userInfo.userService(customOAuth2UserService)) // UserService 연결
						.successHandler(oAuth) // SuccessHandler 연결
				)

				// JWT 필터 위지 지정 : UsernamePasswordAuthenticationFilter 실행 이전에 커스텀 JWT 필터 배치
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(adminJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	// CORS 허용 Origin 목록. application.yaml의 app.cors.allowed-origins 값을 쓰고,
	// 환경변수 CORS_ALLOWED_ORIGINS로 배포 환경마다 덮어쓸 수 있다.
	// (예: 폰 테스트용 터널 주소를 쓸 때 CORS_ALLOWED_ORIGINS=https://xxx.trycloudflare.com)
	//
	// [보안 수정] 예전에는 아래 두 줄이 같이 있었다.
	//     config.setAllowedOrigins(List.of("http://localhost:5173"));
	//     config.setAllowedOriginPatterns(List.of("*"));   // 주석엔 "모든 헤더 허용"이라 적혀 있었음
	// Spring의 CorsConfiguration.checkOrigin()은 allowedOrigins에서 일치하는 걸 못 찾으면
	// allowedOriginPatterns로 폴백하기 때문에, "*" 패턴 때문에 결국 모든 Origin이 통과했고
	// 위의 localhost 화이트리스트는 아무 역할도 하지 못하는 죽은 코드였다.
	// allowCredentials(true)와 조합되면 더 위험하므로 패턴을 제거하고 화이트리스트만 남긴다.
	// [수정] 원래는 파라미터 타입이 List<String>이었다. @Value가 콤마 구분 문자열을 List로
	// 쪼개주는 건 ConversionService에 의존하는 동작이라, 환경에 따라 통째로 한 덩어리
	// ["http://a,http://b"] 로 들어올 수 있다. 그러면 어떤 Origin도 일치하지 않아
	// 모든 CORS 요청이 403 "Invalid CORS request"로 막힌다.
	// 문자열로 받아 직접 쪼개서 이 불확실성을 없애고, 기동 시 파싱 결과를 로그로 찍어
	// 실제로 어떤 Origin이 허용됐는지 눈으로 확인할 수 있게 한다.
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			ConfigurableEnvironment environment,
			@Value("${app.cors.allowed-origins}") String allowedOriginsProperty) {

		// [진단용] app.cors.allowed-origins 값을 갖고 있는 설정 소스를 우선순위 순서대로 전부 출력한다.
		// 가장 먼저 찍히는 게 실제로 이긴 소스다. yaml 파일에서 온 값은 플레이스홀더가 풀리기 전의
		// 원문(${CORS_ALLOWED_ORIGINS:...})으로 나오므로, 어느 파일의 텍스트인지 바로 구분된다.
		// 원인을 확인한 뒤에는 이 for 블록만 지우면 된다.
		for (PropertySource<?> source : environment.getPropertySources()) {
			if (source instanceof EnumerablePropertySource<?> enumerable) {
				Object rawValue = enumerable.getProperty("app.cors.allowed-origins");
				if (rawValue != null) {
					log.info("[CORS][진단] source='{}'\n           원문={}", source.getName(), rawValue);
				}
			}
		}

		List<String> allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toList();

		log.info("[CORS] 허용 Origin {}개: {}", allowedOrigins.size(), allowedOrigins);

		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")); // 허용할 HTTP 메서드
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