package com.example.trippaminebe.security.jwt.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 이 어노테이션이 붙은 컨트롤러 메서드가 "성공적으로" 실행되면, AdminLogAspect가
// 자동으로 ADMIN_LOGS에 기록을 남김. 사람이 매번 로그 저장 코드를 직접 안 써도 됨
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AdminLoggable {
  String actionType();          // 예: "USER_SUSPEND", "QUEST_CREATE"
  String targetTable() default ""; // 예: "USERS"
}