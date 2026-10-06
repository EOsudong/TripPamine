package com.example.trippaminebe.domain.weather.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import jakarta.persistence.*;
import lombok.*;

@Entity // DB테이블과 연결된 클래스라는 것을 선언
@Table(name = "WEATHER_CACHE_ENTRY") // 연결할 DB테이블명 선언
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class WeatherCacheEntry {

  @Id // 행을 구분하는 고유번호 (PK) 선언
  @Column(name = "CACHE_ENTRY_ID") // name 뒤에 있는 컬럼과 연결된다
  @SequenceGenerator(name = "seqWeatherCacheEntry", sequenceName = "SEQ_WEATHER_CACHE_ENTRY", allocationSize = 1)
  // DB의 시퀀스를 name 뒤의 별명으로 등록하고, 번호를 1씩 올린다.
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqWeatherCacheEntry")
  // 값을 자동으로 받아서 채우는 형식으로 사용
  private Long id;

  @Column(name="CACHE_KEY", nullable=false, unique=true, length=50)
  private String cacheKey;

  @Lob
  @Column(name = "PAYLOAD_JSON", nullable=false)
  private String payloadJson;

  @Column(name = "FETCHED_AT", nullable=false)
  private LocalDateTime fetchedAt;

  @Builder
  private WeatherCacheEntry(String cacheKey, String payloadJson, LocalDateTime fetchedAt) {
    this.cacheKey = cacheKey;
    this.payloadJson = payloadJson;
    this.fetchedAt = fetchedAt;
  } // WeatherCacheEntry로 한 번에 사용하겠다

  public void refreshCacheEntry(String payloadJson, LocalDateTime fetchedAt) {
    this.payloadJson = payloadJson;
    this.fetchedAt = fetchedAt;
  } // 값 새로고침

  public static String weatherCacheKey(double latitude, double longitude, LocalDate date){
    return String.format(Locale.US,
        "owm:%.2f:%.2f:%s",
        latitude,
        longitude,
        date.format(DateTimeFormatter.BASIC_ISO_DATE));
  } ///weatherCacheKey

} ///class
