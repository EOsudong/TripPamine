package com.example.trippaminebe.domain.diary.service;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

// recommendation.OpenAiRecommendationService 와 동일한 openai-java SDK 사용 패턴
// AI 여행 다이어리(본문/제목/도파민 점수/키워드)를 JSON으로 생성
@Service
public class OpenAiDiaryGenerationService {

    private final OpenAIClient client;

    public OpenAiDiaryGenerationService() {
        String apiKey = System.getProperty("OPENAI_API_KEY");

        this.client = OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .build();
    }

    public String generateDiaryJson (
        TravelPlan plan,
        BigDecimal totalSpent,
        String topCategory,
        long questSuccessCount,
        long questTotalCount,
        String placesText
    ) {

        String companionCountText = plan.getCompanionCount() == null
            ? "인원 미설정"
            : plan.getCompanionCount() + "명 (본인 포함)";

        String spentText = totalSpent.signum() == 0
            ? "기록 없음"
            : totalSpent + "원";

        String questText = questTotalCount == 0
            ? "기록 없음"
            : "총 " + questTotalCount + "개 중 " + questSuccessCount + "개 성공";

        String categoryText = totalSpent.signum() == 0 ? "기록 없음" : topCategory;

        String prompt = """
            당신은 여행자의 감정과 소비 패턴을 분석해 짧은 여행 일기를 써주는 AI 에디터입니다.
            "도파민"이라는 표현을 활용해, 자극적이고 재미있는 톤으로 작성해주세요.

            여행 이름: %s
            동행자: %s
            동행 인원: %s
            총 지출: %s
            가장 많이 지출한 카테고리: %s
            수행한 퀘스트: %s
            일정에 계획된 방문 장소 (실제 방문 여부는 알 수 없음): %s
            
            (총 지출은 모든 인원의 합계 금액입니다. 1인당 금액으로 오해하지 마세요.)

            아래 JSON 형식으로만 응답하세요. 마크다운, 설명 문구는 절대 포함하지 마세요.
            {
              "title": "도파민 넘치는 여행 일기 제목 (30자 이내)",
              "content": "위 데이터를 바탕으로 한 3~5문단의 여행 일기 본문",
              "dopamineScore": 0,
              "keywords": {
                "emotion": ["감정 키워드1", "감정 키워드2"],
                "expend": ["소비 키워드1", "소비 키워드2"],
                "place": ["장소 관련 키워드1", "장소 관련 키워드2"]
              }
            }

            본문에는 위 방문 장소의 이름을 자연스럽게 언급하고, 목록에 없는 장소는 지어내지 마세요.
            place 키워드도 위 장소 중에서 고르세요.
            
            "기록 없음"으로 표시된 항목은 일기에서 언급하지 마세요.
            
            dopamineScore는 0~100 사이의 정수로, 과소비/퀘스트 성공률이 높을수록 높게 산정하세요.
            """.formatted(
            plan.getPlanName(),
            plan.getCompanionType(),
            companionCountText,
            spentText,
            categoryText,
            questText,
            placesText
        );

        ResponseCreateParams params =
            ResponseCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI)
                .input(prompt)
                .build();

        Response response = client.responses().create(params);

        return response.output().stream()
            .flatMap(item -> item.message().stream())
            .flatMap(message -> message.content().stream())
            .flatMap(content -> content.outputText().stream())
            .map(outputText -> outputText.text())
            .collect(Collectors.joining());
    }
}
