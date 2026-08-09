package com.example.trippaminebe.domain.admin.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor

// 관리자계정 상태(활동,정지)
public enum AdminStatus {
  ACTIVE,
  SUSPENDED
}