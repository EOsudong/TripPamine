package com.example.trippaminebe.domain.mysterytour.service;

import com.example.trippaminebe.domain.mysterytour.dto.MysteryTourCreateRequest;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class MysteryTourAiService {

    private final OpenAIClient client;

    public MysteryTourAiService() {

        String apiKey = System.getProperty("OPENAI_API_KEY");

        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public String generateMysteryTour(
            MysteryTourCreateRequest request
    ) {

        String prompt = """
                당신은 대한민국 미스터리 여행 게임을 설계하는 AI 여행 플래너입니다.

                사용자가 입력한 조건 안에서 실제로 수행 가능한
                국내 여행 목적지 1곳과 미스터리 퀘스트 4개를 만들어주세요.

                [사용자 조건]

                여행 날짜: %s
                여행 기간: %d일
                여행 인원: %d명
                총 예산: %d원
                출발 위치: %s
                최대 이동 반경: %dkm
                여행 스타일: %s

                중요 조건:

                1. 목적지는 출발 위치 기준 최대 이동 반경 이내여야 합니다.
                2. 전체 여행 비용은 사용자의 총 예산을 초과하지 않도록 구성합니다.
                3. 여행 기간에 맞게 전체 여행 일정을 구성합니다.
                4. 퀘스트는 여행 기간에 적절하게 분배하여 총 4개 생성합니다.
                5. 퀘스트는 반드시 4개 생성합니다.
                6. 퀘스트는 실제 여행에서 순서대로 수행할 수 있어야 합니다.
                7. 첫 번째 퀘스트는 목적지로 이동하는 미션으로 구성합니다.
                8. 이후 퀘스트는 음식, 관광, 사진, 체험 등을 적절히 구성합니다.
                9. rewardPoint는 퀘스트당 100~300 사이로 설정합니다.
                10. verifyType은 GPS, PHOTO, SIMPLE 중 하나만 사용합니다.
                11. 위도/경도는 확실하지 않으면 null로 출력합니다.
                12. 반드시 JSON만 출력하고 마크다운은 출력하지 마세요.

                JSON 형식:

                {
                  "destination": "경기도 가평",
                  "estimatedBudget": 180000,
                  "summary": "여행 전체에 대한 간단한 설명",
                  "quests": [
                    {
                      "order": 1,
                      "name": "가평으로 이동하라",
                      "description": "3시간 안에 가평에 도착하세요.",
                      "verifyType": "GPS",
                      "targetLat": null,
                      "targetLng": null,
                      "timeLimitMin": 180,
                      "rewardPoint": 200
                    }
                  ]
                }
                """.formatted(
                request.getTravelDate(),
                request.getTravelDays(),
                request.getPeopleCount(),
                request.getBudget(),
                request.getDeparture(),
                request.getRadiusKm(),
                request.getTravelStyle()
        );

        ResponseCreateParams params =
                ResponseCreateParams.builder()
                        .model(ChatModel.GPT_5_2)
                        .input(prompt)
                        .build();

        Response response =
                client.responses().create(params);

        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining());
    }
}