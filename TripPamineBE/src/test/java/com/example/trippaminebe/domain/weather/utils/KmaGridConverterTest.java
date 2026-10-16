package com.example.trippaminebe.domain.weather.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmaGridConverterTest {

  @Test
  void 위경도_nxny_테스트_부산() {

    double lat = 35.1796;
    double lon = 129.0756;

    KmaGridConverter.Grid grid = KmaGridConverter.toGrid(lat, lon);

    assertThat(grid).isEqualTo(new KmaGridConverter.Grid(98, 76));
  }

  @Test
  void 위경도_nxny_테스트_서울시청() {

    double lat = 37.5665;
    double lon = 126.9780;

    KmaGridConverter.Grid grid = KmaGridConverter.toGrid(lat, lon);

    assertThat(grid).isEqualTo(new KmaGridConverter.Grid(60, 127));
  }

  @Test
  void 위경도_nxny_테스트_속초() {

    double lat = 38.2070;
    double lon = 128.5918;

    KmaGridConverter.Grid grid = KmaGridConverter.toGrid(lat, lon);

    assertThat(grid).isEqualTo(new KmaGridConverter.Grid(87, 141));
  }
}