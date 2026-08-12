package com.example.trippaminebe.domain.admin.service;

import com.example.trippaminebe.domain.admin.dto.request.UserSuspendRequestDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminUserResponseDto;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

  private final UserRepository userRepository;

  // 회원 목록 조회
  @Override
  public Page<AdminUserResponseDto> getUserList(Pageable pageable) {
    return userRepository.findAll(pageable)
        .map(AdminUserResponseDto::adminUserResponseDto);
  }

  // 회원 강제 정지
  @Override
  @Transactional
  public void suspendUser(Long userId, UserSuspendRequestDto request, Long adminId) {
    // Long -> String 변환 후 조회함
    User user = userRepository.findById(userId.toString())
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id: " + userId));

    // 이미 정지된 회원인지 검증
    if (user.getStatus() == UserStatus.SUSPENDED) {
      throw new IllegalArgumentException("이미 정지 처리된 회원입니다.");
    }

    // 도메인 메서드 호출로 상태 변경 (Dirty Checking)
    user.suspend(request.getReason(), adminId);
  }
}