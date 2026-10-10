package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.asset.entity.AssetSnapshot;
import com.example.trippaminebe.domain.asset.repository.AssetSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;


/**
 * =========================================================
 * 하루 1개 Snapshot 저장 전용 컴포넌트
 * =========================================================
 *
 * [수정 배경]
 *
 * 예전에는 Dashboard 조회(GET) 트랜잭션 안에서
 * "오늘 Snapshot이 없으면 INSERT" 를 했다.
 *
 * 개발 모드의 React StrictMode 처럼 같은 요청이 거의 동시에 두 번 오면
 * 두 요청 모두 "없음" 으로 판단해 같은 날짜 행이 2개 생겼고,
 * 그 다음부터 findByUserIdAndSnapshotDate(Optional) 가
 * IncorrectResultSizeDataAccessException → 500 에러를 냈다.
 *
 * [해결]
 *
 * 1. 같은 날짜 행을 List 로 받아서 첫 행만 갱신하고, 이미 생긴 중복 행은 삭제한다.
 * 2. 별도 트랜잭션(REQUIRES_NEW)에서 saveAndFlush 하므로
 *    UNIQUE 제약 위반이 나도 바깥 Dashboard 조회 트랜잭션은 영향을 받지 않는다.
 *    호출하는 쪽(AssetDashboardService)에서 한 번 재시도하면
 *    두 번째 시도에서는 먼저 들어간 행을 찾아 UPDATE 하게 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetSnapshotWriter {

    private final AssetSnapshotRepository assetSnapshotRepository;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(
            Long userId,
            LocalDate snapshotDate,
            long totalAsset,
            long totalIncome,
            long totalExpense
    ) {

        List<AssetSnapshot> sameDay =
                assetSnapshotRepository
                        .findByUserIdAndSnapshotDateOrderBySnapshotIdAsc(
                                userId,
                                snapshotDate
                        );


        // 1) 오늘 행이 없으면 새로 저장
        if (sameDay.isEmpty()) {

            assetSnapshotRepository.saveAndFlush(
                    AssetSnapshot.builder()
                            .userId(userId)
                            .snapshotDate(snapshotDate)
                            .totalAsset(totalAsset)
                            .totalIncome(totalIncome)
                            .totalExpense(totalExpense)
                            // 기존 DB 구조와 호환을 위해 유지 (Dashboard 에서는 사용하지 않음)
                            .travelExpense(0L)
                            .build()
            );

            return;
        }


        // 2) 있으면 첫 행만 갱신
        AssetSnapshot keep =
                sameDay.get(0);

        keep.setTotalAsset(totalAsset);
        keep.setTotalIncome(totalIncome);
        keep.setTotalExpense(totalExpense);


        // 3) 예전에 이미 생겨버린 중복 행 정리
        if (sameDay.size() > 1) {

            List<AssetSnapshot> duplicates =
                    sameDay.subList(1, sameDay.size());

            log.warn(
                    "[AssetSnapshot] userId={} date={} 중복 Snapshot {}건 정리",
                    userId,
                    snapshotDate,
                    duplicates.size()
            );

            assetSnapshotRepository.deleteAll(duplicates);
        }

        assetSnapshotRepository.saveAndFlush(keep);
    }
}
