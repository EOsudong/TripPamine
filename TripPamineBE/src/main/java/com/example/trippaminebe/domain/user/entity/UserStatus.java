package com.example.trippaminebe.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor

// 사용자활동 상태(활동,탈퇴,정지,휴면)
public enum UserStatus {
  ACTIVE,
  WITHDRAW,
  SUSPENDED,
  SLEEP
}
