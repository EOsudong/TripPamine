package com.example.trippaminebe.domain.nego.repository;

import com.example.trippaminebe.domain.nego.entity.AiNegoLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiNegoLogRepository extends JpaRepository<AiNegoLog, Long> {

    // 현재 유효한(만료되지 않은) 네고 알림만 최신순으로 조회 - 홈 화면 실시간 핫딜 배너용
    List<AiNegoLog> findByUser_IdAndExpiredAtAfterOrderByNegoIdDesc(Long userId, LocalDateTime now);

    // 특정 유저 + 특정 상품에 대해 아직 만료되지 않은 제안이 이미 발송되었는지 확인 (중복 발송 방지)
    Optional<AiNegoLog> findFirstByUser_IdAndItemNameAndExpiredAtAfter(Long userId, String itemName, LocalDateTime now);

    // 전환율(구매 전환 통계) 산정용 - 관리자 대시보드에서 활용 가능
    long countByConversionYn(String conversionYn);
}
