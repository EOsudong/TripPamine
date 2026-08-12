package com.example.trippaminebe.security.jwt.aspect;

import com.example.trippaminebe.domain.admin.entity.AdminLogs;
import com.example.trippaminebe.domain.admin.repository.AdminRepository;
import com.example.trippaminebe.domain.admin.repository.AdminLogRepository;
import com.example.trippaminebe.domain.admin.service.custom.CustomAdminDetails;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// @AdminLoggable이 붙은 메서드가 "예외 없이 정상 종료"되면 자동으로 실행되는 공통 로직.
// 실패(예외 발생)한 시도는 로그 안 남김 - 성공한 액션만 기록하는 정책
@Aspect
@Component
@RequiredArgsConstructor
public class AdminLogAspect {

  private final AdminLogRepository adminLogRepository;
  private final AdminRepository adminRepository;

  @AfterReturning("@annotation(adminLoggable)")
  @Transactional
  public void logAdminAction(JoinPoint joinPoint, com.example.trippaminebe.security.jwt.aspect.AdminLoggable adminLoggable) {
    // 1. 현재 로그인한 관리자 정보 확인 (Security Context에서 꺼냄)
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof CustomAdminDetails adminDetails)) {
      // 관리자 인증 정보가 없으면(원칙적으로 발생 안 함) 로그를 남기지 않고 조용히 종료
      return;
    }

    // 2. 메서드 파라미터 중 첫 번째 Long 타입 값을 "대상 ID"로 추정해서 사용 (예: userId, questId)
    Long targetId = null;
    for (Object arg : joinPoint.getArgs()) {
      if (arg instanceof Long) {
        targetId = (Long) arg;
        break;
      }
    }

    // 3. 현재 요청의 IP 주소 조회
    String ipAddress = null;
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
      ipAddress = attrs.getRequest().getRemoteAddr();
    }

    // 4. ADMIN_LOGS에 기록 저장 (getReferenceById로 불필요한 조회 쿼리 없이 참조만 걺)
    AdminLogs log = AdminLogs.builder()
        .admin(adminRepository.getReferenceById(adminDetails.getAdmin().getId()))
        .actionType(adminLoggable.actionType())
        .targetTable(adminLoggable.targetTable())
        .targetId(targetId)
        .ipAddress(ipAddress)
        .build();

    adminLogRepository.save(log);
  }
}