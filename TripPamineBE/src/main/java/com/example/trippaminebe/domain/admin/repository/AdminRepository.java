package com.example.trippaminebe.domain.admin.repository;


import com.example.trippaminebe.domain.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface AdminRepository extends JpaRepository<Admin, Long> {

  // 로그인 시 아이디로 관리자 조회 (존재 여부 + 비밀번호 검증에 사용)
  Optional<Admin> findByAdminLoginId(String adminLoginId);

}