package com.example.trippaminebe.domain.mascot.repository;

import java.time.LocalDateTime;

// UserMascotCollectionRepository.findCatchStats()의 결과 한 줄(마스코트별 집계)을 받는 인터페이스 프로젝션.
// JPQL의 별칭(as mascotId ...)과 getter 이름이 일치해야 한다.
public interface MascotCatchStat {

    Long getMascotId();

    Long getCatchCount();

    LocalDateTime getFirstCaughtAt();

    LocalDateTime getLastCaughtAt();
}
