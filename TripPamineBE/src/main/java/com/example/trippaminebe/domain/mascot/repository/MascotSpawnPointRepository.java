package com.example.trippaminebe.domain.mascot.repository;

import com.example.trippaminebe.domain.mascot.entity.MascotSpawnPoint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MascotSpawnPointRepository extends JpaRepository<MascotSpawnPoint, Long> {

    // 지도 조회용. quest.repository.QuestRepository와 마찬가지 활성 스폰 지점을 다 가져온 뒤
    // 반경 필터링은 서비스 레이어(MascotService)에서 GeoUtils로 계산한다.
    // 스폰 지점 개수가 많아지면 나중에 위경도 bounding box WHERE 조건을 추가하는 걸 고려할 것.
    // [수정] 응답에 마스코트 이름/이미지를 함께 내려주므로 mascot을 같이 가져온다(스폰마다 추가 쿼리 방지).
    @EntityGraph(attributePaths = "mascot")
    List<MascotSpawnPoint> findByActiveYn(String activeYn);

    // 관리자 마스코트 삭제 시 스폰 지점이 남아있는지 확인
    boolean existsByMascot_MascotId(Long mascotId);
}
