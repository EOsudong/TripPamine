package com.example.trippaminebe.domain.user.entity;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor

public enum UserSocialProvider {
  KAKAO,
  NAVER,
  GOOGLE,
}
