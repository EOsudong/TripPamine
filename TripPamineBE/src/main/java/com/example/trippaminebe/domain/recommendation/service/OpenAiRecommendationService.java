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

    String companionCountText = plan.getCompanionCount() != null
        ? plan.getCompanionCount() + "명(본인 포함)"
        : "인원 미정";

        String prompt = """
                당신은 대한민국 국내 여행 전문 플래너입니다.
                
                제시된 사용자의 여행 조건을 분석하여, 실제 실행 가능한 구체적인 여행 일정을 추천해주세요.
                
                [작성 지침]
                1. 인원 및 이동:
                  - 여행 인원 수에 적합한 장소와 이동 수단을 고려해주세요.
                  - 인원이 미정인 경우 일반적인 기준으로 추천해주세요.
                2. 예산 산정 기준:
                  - 제공된 '총 예산'은 모든 동행 인원을 포함한 총 금액입니다.
                  - '예상 비용'(estimatedCost)은 전체와 장소별 모두 '모든 인원의 총합 비용' 기준으로 산정해주세요.
                  - 전체 여행 비용 합계가 입력된 '총 예산'을 초과하지 않도록 구성해주세요.
                                
                여행 이름: %s
                총 예산: %s원
                동행자: %s
                여행 인원: %s
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
                방문 일정 작성 규칙:
                1. 모든 방문 장소에 startTime과 endTime을 반드시 포함하세요.
                2. 시간은 24시간제 HH:mm 형식으로 작성하세요.
                3. 각 장소의 방문 시작 시간은 종료 시간보다 빨라야 합니다.
                4. 장소 간 이동 시간을 고려하여 현실적인 방문 시간을 배정하세요.
                5. 같은 날짜의 장소별 방문 시간이 서로 겹치지 않도록 작성하세요.
                6. 식사 시간과 관광지 운영 시간을 고려하여 일정을 구성하세요.
                7. 첫날과 마지막 날은 여행 출발일 및 종료일의 시간을 고려하세요.
                8. 방문 시간은 해당 여행지의 현지 시간 기준으로 작성하세요.
                위 JSON 이외의 설명이나 마크다운은 출력하지 마세요.
                """.formatted(
                plan.getPlanName(),
                plan.getTotalBudget(),
                plan.getCompanionType(),
                companionCountText,
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

    public String generateDayRecommendation(TravelPlan plan, int day, java.util.List<String> currentPlaces) {
        String placesStr = String.join(", ", currentPlaces);

        String prompt = """
                당신은 대한민국 국내 여행 전문 플래너입니다.
                
                여행 이름: %s
                총 예산: %s원
                동행자: %s
                
                사용자가 %d일차에 방문하고 싶어하는 장소 목록입니다: [%s]
                
                위 장소들의 지리적 위치와 이동 동선을 고려하여 최적의 순서로 재배치하고, 
                각 장소별 추천 이유와 예상 비용을 다시 산정해주세요. (필요시 동선상 매우 자연스러운 장소 1곳 추가 가능)
                
                반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "day": %d,
                  "places": [
                     {
                       "name": "장소명",
                       "description": "최적 동선 추천 이유",
                       "estimatedCost": 0,
                       "startTime": "09:00",
                       "endTime": "10:30"
                     }
                   ]
                }
                방문 일정 작성 규칙:
                1. 모든 장소에 startTime과 endTime을 포함하세요.
                2. 시간은 24시간제 HH:mm 형식으로 작성하세요.
                3. 장소 간 이동 시간을 고려하여 방문 시간을 배정하세요.
                4. 방문 시간이 서로 겹치지 않도록 작성하세요.
                5. 실제 관광 및 식사에 필요한 시간을 고려하세요.
                위 JSON 이외의 설명이나 마크다운은 출력하지 마세요.
                """.formatted(
                plan.getPlanName(),
                plan.getTotalBudget(),
                plan.getCompanionType(),
                day,
                placesStr,
                day
        );

        ResponseCreateParams params = ResponseCreateParams.builder()
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