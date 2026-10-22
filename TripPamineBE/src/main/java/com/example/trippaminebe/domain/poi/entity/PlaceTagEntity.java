package com.example.trippaminebe.domain.poi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** 실제 PLACE_TAGS의 장소별 분류/환경 태그를 읽기 전용으로 조회한다. */
@Entity
@Table(name = "PLACE_TAGS")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceTagEntity {

    @Id
    @Column(name = "TAG_ID")
    private Long tagId;

    // PLACE_ID는 Oracle FK이지만 불필요한 LAZY 로딩을 피하도록 스칼라 값으로 조회한다.
    @Column(name = "PLACE_ID", nullable = false)
    private Long placeId;

    @Column(name = "TAG_TYPE", nullable = false, length = 30)
    private String tagType;

    @Column(name = "TAG_VALUE", nullable = false, length = 100)
    private String tagValue;
}
