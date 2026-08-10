package com.example.trippaminebe.domain.user.dto.response;


import com.example.trippaminebe.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "JWT 토큰 응답 DTO")
public class LoginResponseDto {
  private String email;
  private String accessToken;

  @Builder.Default
  private String tokenType = "Bearer";

  public static LoginResponseDto loginResponseDto(User user, String accessToken) {
    return LoginResponseDto.builder()
        .email(user.getEmail())
        .accessToken(accessToken)
        .build();
  }

}
