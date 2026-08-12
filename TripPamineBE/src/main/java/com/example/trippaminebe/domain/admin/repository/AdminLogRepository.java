package com.example.trippaminebe.domain.admin.repository;

import com.example.trippaminebe.domain.admin.entity.AdminLogs;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminLogRepository extends JpaRepository<AdminLogs, Long> {

}