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

    public String generateDiaryJson(
        TravelPlan plan,
        BigDecimal totalSpent,
        String topCategory,
        long questSuccessCount,
        long questTotalCount
    ) {
        String prompt = """
            당신은 여행자의 감정과 소비 패턴을 분석해 짧은 여행 일기를 써주는 AI 에디터입니다.
            "도파민"이라는 표현을 활용해, 자극적이고 재미있는 톤으로 작성해주세요.

            여행 이름: %s
            동행자: %s
            총 지출: %s원
            가장 많이 지출한 카테고리: %s
            수행한 퀘스트: 총 %d개 중 %d개 성공

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

            dopamineScore는 0~100 사이의 정수로, 과소비/퀘스트 성공률이 높을수록 높게 산정하세요.
            """.formatted(
            plan.getPlanName(),
            plan.getCompanionType(),
            totalSpent,
            topCategory,
            questTotalCount,
            questSuccessCount
        );

        ResponseCreateParams params =
            ResponseCreateParams.builder()
                .model(ChatModel.GPT_5_2)
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
