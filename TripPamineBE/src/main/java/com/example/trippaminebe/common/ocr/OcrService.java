package com.example.trippaminebe.common.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class OcrService {

    @Value("${google.vision.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractText(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("OCR 이미지 파일이 존재하지 않습니다.");
        }

        try {
            String url =
                    "https://vision.googleapis.com/v1/images:annotate?key="
                            + apiKey;

            String base64Image =
                    Base64.getEncoder()
                            .encodeToString(file.getBytes());

            Map<String, Object> requestBody = Map.of(
                    "requests", List.of(
                            Map.of(
                                    "image", Map.of(
                                            "content", base64Image
                                    ),
                                    "features", List.of(
                                            Map.of(
                                                    "type",
                                                    "TEXT_DETECTION"
                                            )
                                    )
                            )
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(requestBody, headers);

            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            url,
                            entity,
                            String.class
                    );

            JsonNode root =
                    objectMapper.readTree(response.getBody());

            JsonNode responses =
                    root.path("responses");

            if (!responses.isArray() || responses.isEmpty()) {
                return "";
            }

            return responses
                    .get(0)
                    .path("fullTextAnnotation")
                    .path("text")
                    .asText("");

        } catch (Exception e) {
            throw new RuntimeException(
                    "OCR 처리 중 오류가 발생했습니다.",
                    e
            );
        }
    }
}