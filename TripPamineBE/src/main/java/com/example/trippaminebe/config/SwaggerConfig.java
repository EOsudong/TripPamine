package com.example.trippaminebe.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI openAPI() {
    String jwtSchemeName = "jwtAuth";

    // API 요청 헤더에 JWT 토큰 입력 버튼(Authorize) 추가
    SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

    Components components = new Components()
        .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
            .name(jwtSchemeName)
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
        );

    return new OpenAPI()
        .info(new Info()
            .title("TripPamine API Document")
            .description("TripPamine 백엔드 API 명세서")
            .version("1.0.0"))
        .components(components)
        .addSecurityItem(securityRequirement
        );
  }


}
