package com.example.trippaminebe.domain.mysterytour.service;

import com.example.trippaminebe.domain.mysterytour.client.KakaoLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import com.example.trippaminebe.domain.mysterytour.exception.MysteryTourLocationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysteryQuestLocationResolverTest {

    private final KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
    private final MysteryQuestLocationResolver resolver =
            new MysteryQuestLocationResolver(kakaoLocalClient);

    @Test
    void 행정구역이_포함된_장소명으로_좌표를_검색한다() {
        KakaoPlace place = place();
        when(kakaoLocalClient.searchFirstPlace("경기도 가평군 가평역"))
                .thenReturn(Optional.of(place));

        KakaoPlace result = resolver.resolve("경기도 가평군", "경기도 가평군 가평역");

        assertThat(result).isEqualTo(place);
        verify(kakaoLocalClient).searchFirstPlace("경기도 가평군 가평역");
    }

    @Test
    void 원래_검색이_비면_목적지를_붙여서_재검색한다() {
        KakaoPlace place = place();
        when(kakaoLocalClient.searchFirstPlace("가평역"))
                .thenReturn(Optional.empty());
        when(kakaoLocalClient.searchFirstPlace("경기도 가평군 가평역"))
                .thenReturn(Optional.of(place));

        KakaoPlace result = resolver.resolve("경기도 가평군", "가평역");

        assertThat(result).isEqualTo(place);
        verify(kakaoLocalClient).searchFirstPlace("경기도 가평군 가평역");
    }

    @Test
    void 두_번의_검색이_모두_비면_생성을_중단한다() {
        when(kakaoLocalClient.searchFirstPlace("존재하지 않는 장소"))
                .thenReturn(Optional.empty());
        when(kakaoLocalClient.searchFirstPlace("경기도 가평군 존재하지 않는 장소"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("경기도 가평군", "존재하지 않는 장소"))
                .isInstanceOf(MysteryTourLocationException.class)
                .hasMessageContaining("실제 위치를 찾을 수 없는 장소");
    }

    private KakaoPlace place() {
        return new KakaoPlace(
                "9118030",
                "가평역 경춘선",
                "경기 가평군 가평읍 달전리 603-29",
                "경기 가평군 가평읍 문화로 13-42",
                new BigDecimal("37.8145367800551"),
                new BigDecimal("127.5107398714205")
        );
    }
}
