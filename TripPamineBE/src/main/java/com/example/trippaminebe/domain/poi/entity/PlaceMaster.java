package com.example.trippaminebe.domain.poi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * 실제 Oracle PLACES 매핑. 자동 장소 수집/등록은 이 단계에서 수행하지 않는다.
 * @Immutable: 이 엔티티를 통해 장소 레코드가 수정되지 않도록 제한한다.
 */
@Entity
@Table(name = "PLACES")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceMaster {

    @Id
    @Column(name = "PLACE_ID")
    private Long placeId;

    @Column(name = "PLACE_TYPE", nullable = false, length = 30)
    private String placeType;

    @Column(name = "PLACE_NAME", nullable = false, length = 200)
    private String placeName;

    @Column(name = "ADDRESS", length = 500)
    private String address;

    @Column(name = "LATITUDE", precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 11, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Column(name = "SOURCE_TYPE", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "SOURCE_PLACE_ID", length = 100)
    private String sourcePlaceId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ACTIVE_YN", nullable = false, length = 1)
    private String activeYn;
}
