package com.example.trippaminebe.domain.weather.client;

import com.example.trippaminebe.domain.weather.utils.KmaBaseTime;
import com.example.trippaminebe.domain.weather.utils.KmaGridConverter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;

class KmaForecastApiClientManualTest {

  @Test
  void 실제호출_서울시청() {
    // 환경변수에 키가 없으면 이 테스트는 건너뛴다 (팀원 빌드가 깨지지 않게)
    String key = System.getenv("KMA_SERVICE_KEY");
    Assumptions.assumeTrue(key != null && !key.isBlank());

    KmaForecastApiClient client = new KmaForecastApiClient();
    ReflectionTestUtils.setField(client, "baseUrl",
        "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0");
    ReflectionTestUtils.setField(client, "serviceKey", key);

    KmaGridConverter.Grid grid = KmaGridConverter.toGrid(37.5665, 126.9780);
    KmaBaseTime.BaseTime base =
        KmaBaseTime.calculate(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

    String json = client.fetchUltraShortForecast(
        grid.nx(), grid.ny(), base.baseDate(), base.baseTime());

    assertThat(json).contains("NORMAL_SERVICE");   // 정상 응답이면 들어 있어야 하는 글자
  }
}
