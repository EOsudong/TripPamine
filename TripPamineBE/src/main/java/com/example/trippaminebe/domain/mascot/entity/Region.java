package com.example.trippaminebe.domain.mascot.entity;

import jakarta.persistence.*;
import lombok.*;

// REGIONS: 마스코트가 소속된 지역(시/도 단위로 시작, 필요하면 시/군/구까지 세분화 가능).
// db/mascot_schema.sql 참고해서 테이블/시퀀스를 먼저 만들어야 한다.
@Entity
@Table(name = "REGIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_REGIONS_GEN")
    @SequenceGenerator(name = "SEQ_REGIONS_GEN", sequenceName = "SEQ_REGIONS", allocationSize = 1)
    @Column(name = "REGION_ID")
    private Long regionId;

    // 예) "서울특별시", "부산광역시 해운대구"
    @Column(name = "REGION_NAME", nullable = false, length = 100)
    private String regionName;

    // 한국관광공사 TourAPI(tour 도메인)나 행정구역 코드 체계와 맞추고 싶을 때 사용하는 선택 필드.
    // 지금 당장 안 쓰더라도 나중에 지역별 관광 데이터와 마스코트를 연결할 때 유용하다.
    @Column(name = "SIDO_CODE", length = 20)
    private String sidoCode;

    public void update(String regionName, String sidoCode) {
        this.regionName = regionName;
        this.sidoCode = sidoCode;
    }
}
