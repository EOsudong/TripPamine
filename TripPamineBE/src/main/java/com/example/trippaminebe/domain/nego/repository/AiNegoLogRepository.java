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

    // 이름 대신 PLAN_ID로 신규 오퍼의 결제 완료 여부를 판별한다.
    boolean existsByUser_IdAndPlanIdAndConversionYn(Long userId, Long planId, String conversionYn);

    // 아직 유효한 동일 여행의 미수락 오퍼 존재 여부. 발급 중복을 막는다.
    boolean existsByUser_IdAndPlanIdAndConversionYnAndExpiredAtAfter(
            Long userId, Long planId, String conversionYn, LocalDateTime now);

    // 이전 버전의 결제 완료 이력 중 PLAN_ID가 없는 경우에만 이름으로 보수적으로 보호한다.
    // 이 제약은 레거시 데이터의 정식 이관/정정 후에만 제거할 수 있다.
    boolean existsByUser_IdAndPlanIdIsNullAndItemNameAndConversionYn(
            Long userId, String itemName, String conversionYn);

    // 결제가 끝난 여행은 같은 PLAN_ID의 다른 미수락 오퍼를 숨긴다.
    // 이름 기반 검사는 PLAN_ID가 없는 과거 결제 이력에 한해서만 사용한다.
    // 레거시 미수락 오퍼(PLAN_ID NULL)는 표시하지 않는다. 서버에서 수락도 차단한다.
    @Query("""
            select n from AiNegoLog n
            where n.user.id = :userId
              and n.planId is not null
              and n.conversionYn = 'N'
              and n.expiredAt > :now
              and exists (
                  select p.planId from TravelPlan p
                  where p.planId = n.planId
                    and p.user.id = :userId
                    and p.delYn = 'N'
              )
              and not exists (
                  select paid.negoId from AiNegoLog paid
                  where paid.user.id = n.user.id
                    and paid.conversionYn = 'Y'
                    and (paid.planId = n.planId
                         or (paid.planId is null and paid.itemName = n.itemName))
              )
            order by n.negoId desc
            """)
    List<AiNegoLog> findDisplayableOffers(@Param("userId") Long userId,
                                          @Param("now") LocalDateTime now);

    // 동일 오퍼의 동시 수락 요청을 직렬화해서 잔액·가계부가 중복 차감되지 않도록 보호한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from AiNegoLog n where n.negoId = :negoId")
    Optional<AiNegoLog> findByIdForUpdate(@Param("negoId") Long negoId);

    // 전환율(구매 전환 통계) 산정용 - 관리자 대시보드에서 활용 가능
    long countByConversionYn(String conversionYn);
}