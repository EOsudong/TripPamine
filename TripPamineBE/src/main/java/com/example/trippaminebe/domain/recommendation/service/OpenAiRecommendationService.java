package com.example.trippaminebe.domain.recommendation.service;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class OpenAiRecommendationService {

  private final OpenAIClient client;

  public OpenAiRecommendationService() {

    String apiKey = System.getProperty("OPENAI_API_KEY");

    this.client = OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        .build();
  }

  public String generateRecommendation(TravelPlan plan) {

    String prompt = """
                당신은 대한민국 국내 여행 전문 플래너입니다.

                아래 사용자의 여행 조건을 분석해서
                실제 여행에 사용할 수 있는 구체적인 여행 일정을 추천해주세요.

                여행 이름: %s
                총 예산: %s원
                동행자: %s
                출발일: %s
                종료일: %s
                미스터리 투어 여부: %s

                반드시 JSON 형식으로만 응답하세요.

                형식:
                {
                  "title": "추천 여행 제목",
                  "summary": "추천 이유",
                  "estimatedCost": 0,
                  "days": [
                    {
                      "day": 1,
                      "places": [
                        {
                          "name": "장소명",
                          "description": "추천 이유",
                          "estimatedCost": 0
                        }
                      ]
                    }
                  ]
                }

                위 JSON 이외의 설명이나 마크다운은 출력하지 마세요.
                """.formatted(
        plan.getPlanName(),
        plan.getTotalBudget(),
        plan.getCompanionType(),
        plan.getStartDate(),
        plan.getEndDate(),
        plan.getBlindYn()
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
        .collect(java.util.stream.Collectors.joining());
  }
}