package com.example.trippaminebe.domain.nego.repository;

import com.example.trippaminebe.domain.nego.entity.AiNegoLog;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiNegoLogRepository extends JpaRepository<AiNegoLog, Long> {

    // 현재 유효한(만료되지 않은) 네고 알림만 최신순으로 조회 - 홈 화면 실시간 핫딜 배너용
    List<AiNegoLog> findByUser_IdAndConversionYnAndExpiredAtAfterOrderByNegoIdDesc(
            Long userId, String conversionYn, LocalDateTime now);

    // 특정 유저 + 특정 상품에 대해 아직 만료되지 않은 제안이 이미 발송되었는지 확인 (중복 발송 방지)
    Optional<AiNegoLog> findFirstByUser_IdAndItemNameAndExpiredAtAfter(Long userId, String itemName, LocalDateTime now);

    // 이미 거래 완료된 사용자·여행명 조합은 시간이 지나도 동일 오퍼를 다시 만들지 않는다.
    boolean existsByUser_IdAndItemNameAndConversionYn(Long userId, String itemName, String conversionYn);

    // 동일 오퍼의 동시 수락 요청을 직렬화해서 모의 잔액·가계부가 중복 차감되지 않도록 보호한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from AiNegoLog n where n.negoId = :negoId")
    Optional<AiNegoLog> findByIdForUpdate(@Param("negoId") Long negoId);

    // 전환율(구매 전환 통계) 산정용 - 관리자 대시보드에서 활용 가능
    long countByConversionYn(String conversionYn);
}