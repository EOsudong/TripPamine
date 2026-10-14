package com.example.trippaminebe.domain.mascot.repository;

import com.example.trippaminebe.domain.mascot.entity.Mascot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MascotRepository extends JpaRepository<Mascot, Long> {

    // 도감 화면에서 "이 지역 마스코트 다 모았는지" 진행률 계산 등에 쓸 수 있음
    List<Mascot> findByRegion_RegionId(Long regionId);

    // 도감(GET /mascots/dex)용 전체 목록. 지역을 함께 가져와서(fetch join) 지역별 묶음 시 추가 쿼리가 나가지 않게 한다.
    @Query("select m from Mascot m join fetch m.region r order by r.regionId asc, m.mascotId asc")
    List<Mascot> findAllWithRegionOrdered();

    // 관리자 지역 삭제 시 소속 마스코트가 남아있는지 확인
    boolean existsByRegion_RegionId(Long regionId);
}
