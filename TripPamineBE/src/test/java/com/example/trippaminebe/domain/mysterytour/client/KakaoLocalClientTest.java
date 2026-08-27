package com.example.trippaminebe.domain.mysterytour.client;

import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class KakaoLocalClientTest {

    private MockRestServiceServer server;
    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new KakaoLocalClient(
                restTemplate,
                "https://dapi.kakao.com",
                "test-rest-api-key"
        );
    }

    @Test
    void 키워드_검색_결과의_y를_위도_x를_경도로_변환한다() {
        server.expect(method(GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-rest-api-key"))
                .andExpect(queryParam("query", "경기도 가평군 가평역"))
                .andExpect(queryParam("size", "5"))
                .andRespond(withSuccess("""
                        {
                          "documents": [
                            {
                              "id": "9118030",
                              "place_name": "가평역 경춘선",
                              "address_name": "경기 가평군 가평읍 달전리 603-29",
                              "road_address_name": "경기 가평군 가평읍 문화로 13-42",
                              "x": "127.5107398714205",
                              "y": "37.8145367800551"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<KakaoPlace> result = client.searchFirstPlace("경기도 가평군 가평역");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().placeName()).isEqualTo("가평역 경춘선");
        assertThat(result.orElseThrow().latitude().toPlainString())
                .isEqualTo("37.8145367800551");
        assertThat(result.orElseThrow().longitude().toPlainString())
                .isEqualTo("127.5107398714205");
        server.verify();
    }

    @Test
    void 검색_결과가_없으면_empty를_반환한다() {
        server.expect(method(GET))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        Optional<KakaoPlace> result = client.searchFirstPlace("존재하지 않는 테스트 장소");

        assertThat(result).isEmpty();
        server.verify();
    }
}
