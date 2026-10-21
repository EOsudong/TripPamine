package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse;
import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse.TravelItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 검증된 DB 여행 목록만 직접 응답하고 삭제된 이름을 재활용하지 않는지 확인한다. */
class AiManagerVerifiedAnswerServiceTest {

    private final AiTravelOverviewService overviewService = mock(AiTravelOverviewService.class);
    private final AiManagerVerifiedAnswerService answerService =
            new AiManagerVerifiedAnswerService(overviewService);

    @Test
    void 현재등록된여행만조회한다() {
        when(overviewService.getOverview(9L)).thenReturn(oneActivePlan());

        String answer = answerService.findAnswer(
                9L, "내가 등록한 여행지 알려줘", List.of());

        assertThat(answer)
                .contains("총 1개", "제주 2박3일 가족여행")
                .doesNotContain("가평", "울릉도 오징어", "제주도 1박2일여행");
        verify(overviewService).getOverview(9L);
    }

    @Test
    void 삭제했다고정정하면기존답변대신DB를다시조회한다() {
        when(overviewService.getOverview(9L)).thenReturn(oneActivePlan());

        String answer = answerService.findAnswer(
                9L,
                "다시 조회해봐 아니야 삭제했어",
                List.of("내가 등록한 여행지 알려줘", "아닌데?"));

        assertThat(answer)
                .startsWith("다시 조회했습니다.")
                .contains("제주 2박3일 가족여행")
                .doesNotContain("가평", "울릉도 오징어");
        verify(overviewService).getOverview(9L);
    }

    @Test
    void 실제저장된여행이없으면가상의여행을출력하지않는다() {
        when(overviewService.getOverview(9L)).thenReturn(
                new AiTravelOverviewResponse(
                        LocalDateTime.of(2026, 10, 21, 17, 0),
                        0, 0, 0, 0, List.of()
                ));

        String answer = answerService.findAnswer(
                9L, "내가 등록한 여행지 알려줘", List.of());

        assertThat(answer).contains("저장된 여행계획이 없습니다");
    }

    private static AiTravelOverviewResponse oneActivePlan() {
        TravelItem plan = new TravelItem(
                53L,
                "제주 2박3일 가족여행",
                "JEJU",
                "FAMILY",
                null,
                null,
                null,
                "PLANNED",
                "여행 준비 중",
                null
        );

        return new AiTravelOverviewResponse(
                LocalDateTime.of(2026, 10, 21, 17, 0),
                1, 1, 0, 0,
                List.of(plan)
        );
    }
}
