package com.example.trippaminebe.domain.weather.utils;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

public class KmaBaseTest {
  @Test
  void 열시이십분이면_구시삼십분_발표를_쓴다() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 20);

    KmaBaseTime.BaseTime result = KmaBaseTime.calculate(now);

    assertThat(result).isEqualTo(new KmaBaseTime.BaseTime("20261016", "0930"));
  }

  @Test
  void test2() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 50);

    KmaBaseTime.BaseTime result = KmaBaseTime.calculate(now);

    assertThat(result).isEqualTo(new KmaBaseTime.BaseTime("20261016", "1030"));
  }

  @Test
  void test3() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 16, 0, 20);

    KmaBaseTime.BaseTime result = KmaBaseTime.calculate(now);

    assertThat(result).isEqualTo(new KmaBaseTime.BaseTime("20261015", "2330"));
  }

  @Test
  void test4() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 44);

    KmaBaseTime.BaseTime result = KmaBaseTime.calculate(now);

    assertThat(result).isEqualTo(new KmaBaseTime.BaseTime("20261016", "0930"));
  }

  @Test
  void test5() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 16, 10, 45);

    KmaBaseTime.BaseTime result = KmaBaseTime.calculate(now);

    assertThat(result).isEqualTo(new KmaBaseTime.BaseTime("20261016", "1030"));
  }
}
