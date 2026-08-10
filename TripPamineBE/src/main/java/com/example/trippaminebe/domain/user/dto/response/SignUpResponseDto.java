package com.example.trippaminebe.domain.user.dto.response;


import com.example.trippaminebe.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder

//회원가입 DTO
public class SignUpResponseDto {
  private Long userId;
  private String email;
  private String userName;
  private String accessToken;

  @Builder.Default
  private String tokenType = "Bearer";

  public static SignUpResponseDto signUpResponse(User user) {
    return SignUpResponseDto.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .userName(user.getUserName())
        .accessToken("")
        .build();
  }

}
