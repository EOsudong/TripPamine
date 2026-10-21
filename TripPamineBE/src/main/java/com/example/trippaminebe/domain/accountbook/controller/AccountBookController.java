package com.example.trippaminebe.domain.accountbook.controller;

import com.example.trippaminebe.domain.accountbook.dto.TransactionRequest;
import com.example.trippaminebe.domain.accountbook.dto.TransactionResponse;
import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.service.AccountBookService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import com.google.protobuf.ByteString;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * [Mock 은행 연동 추가]
 * 거래 내역 추가/수정 시 계좌(accountId)가 함께 들어오면 AccountBookService가
 * AccountBalanceService를 통해 계좌 잔액에 즉시 반영한다.
 */
@RestController
@RequestMapping("/accountbook")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountBookController {

   private final AccountBookService accountBookService;

   // JWT에서 인증된 사용자 고유 ID만 사용한다. 비로그인 가계부 요청은 차단한다.
   private Long requireUserId(CustomUserDetails userDetails) {
      if (userDetails == null || userDetails.getUser() == null
          || userDetails.getUser().getId() == null) {
         throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
      }
      return userDetails.getUser().getId();
   }

   // 1. 거래 내역 조회 (인증된 USER_ID 기준)
   @GetMapping
   public ResponseEntity<List<TransactionResponse>> getTransactions(
       @AuthenticationPrincipal CustomUserDetails userDetails) {
      return ResponseEntity.ok(
          accountBookService.getTransactions(requireUserId(userDetails))
              .stream()
              .map(TransactionResponse::new)
              .toList()
      );
   }

   // 2. 거래 내역 추가
   @PostMapping("/add")
   public ResponseEntity<TransactionResponse> addTransaction(
       @AuthenticationPrincipal CustomUserDetails userDetails,
       @Valid @RequestBody TransactionRequest request) {

      Long userId = requireUserId(userDetails);

      return ResponseEntity.ok(
          new TransactionResponse(
              accountBookService.saveTransaction(
                  userId,
                  toTransactionEntity(request)
              )
          )
      );
   }

   // 3. 거래 내역 수정
   @PutMapping("/update/{id}")
   public ResponseEntity<TransactionResponse> updateTransaction(
       @AuthenticationPrincipal CustomUserDetails userDetails,
       @PathVariable Long id,
       @Valid @RequestBody TransactionRequest request) {

      Long userId = requireUserId(userDetails);

      return ResponseEntity.ok(
          new TransactionResponse(
              accountBookService.updateTransaction(
                  userId,
                  id,
                  toTransactionEntity(request)
              )
          )
      );
   }

   private TransactionEntity toTransactionEntity(TransactionRequest request) {
      TransactionEntity transaction = new TransactionEntity();
      transaction.setDescription(request.getDescription().trim());
      transaction.setAmount(request.getAmount());
      transaction.setType(request.getType().toLowerCase(Locale.ROOT));
      transaction.setCategory(request.getCategory().trim());
      transaction.setAccountId(request.getAccountId());
      transaction.setTransactionDate(parseTransactionDate(request.getTransactionDate()));
      return transaction;
   }

   private LocalDateTime parseTransactionDate(String value) {
      if (value == null || value.isBlank()) {
         return null; // 등록 시 현재 시각, 수정 시 기존 시각 유지
      }

      try {
         // 프론트 등록 API: ISO-8601 UTC/오프셋, 예: 2026-10-21T00:00:00.000Z
         return OffsetDateTime.parse(value.trim())
             .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
             .toLocalDateTime();
      } catch (DateTimeParseException ignored) {
         try {
            // 프론트 수정 API: 오프셋 없는 로컬 시간, 예: 2026-10-21T09:00:00
            return LocalDateTime.parse(value.trim());
         } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "거래일시는 ISO-8601 형식이어야 합니다."
            );
         }
      }
   }

   // 4. 거래 내역 삭제 (서비스: deleteTransaction(Long id))
   @DeleteMapping("/delete/{id}")
   public ResponseEntity<Void> deleteTransaction(
       @AuthenticationPrincipal CustomUserDetails userDetails,
       @PathVariable Long id) {
      accountBookService.deleteTransaction(requireUserId(userDetails), id);
      return ResponseEntity.ok().build();
   }

   @Value("${google.vision.api-key}")
   private String apiKey;

   // 5. 영수증 OCR 분석[cite: 6]
   @PostMapping("/ocr")
   public ResponseEntity<?> analyzeReceipt(@RequestParam("file") MultipartFile file) {
      if (file.isEmpty()) {
         return ResponseEntity.badRequest().body("파일이 존재하지 않습니다.");
      }

      try {
         // 1. Vision REST API URL 구성 (주입받은 apiKey 사용)
         String url = "https://vision.googleapis.com/v1/images:annotate?key=" + apiKey;

         // 2. 업로드된 이미지를 Base64로 인코딩
         String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

         // 3. REST API Request Body 구성
         Map<String, Object> requestBody = Map.of(
             "requests", List.of(
                 Map.of(
                     "image", Map.of("content", base64Image),
                     "features", List.of(Map.of("type", "TEXT_DETECTION"))
                 )
             )
         );

         // 4. RestTemplate을 통한 API 호출
         HttpHeaders headers = new HttpHeaders();
         headers.setContentType(MediaType.APPLICATION_JSON);
         HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

         RestTemplate restTemplate = new RestTemplate();
         ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

         // 5. 응답 결과에서 텍스트 추출
         ObjectMapper objectMapper = new ObjectMapper();
         JsonNode root = objectMapper.readTree(response.getBody());
         JsonNode responses = root.path("responses");
         String extractedText = "";

         if (responses.isArray() && !responses.isEmpty()) {
            JsonNode fullTextAnnotation = responses.get(0).path("fullTextAnnotation");
            extractedText = fullTextAnnotation.path("text").asText("");
         }

         // 6. 추출된 텍스트 파싱 후 결과 반환
         Map<String, Object> result = analyzeReceiptDetails(extractedText);
         return ResponseEntity.ok(result);

      } catch (Exception e) {
         e.printStackTrace();
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
             .body("Google Cloud Vision API 처리 중 오류 발생: " + e.getMessage());
      }
   }

   @SuppressWarnings("unchecked")
   private String parseExtractedText(Map responseBody) {
      try {
         List<Map> responses = (List<Map>) responseBody.get("responses");
         if (responses != null && !responses.isEmpty()) {
            Map firstResponse = responses.get(0);
            List<Map> textAnnotations = (List<Map>) firstResponse.get("textAnnotations");
            if (textAnnotations != null && !textAnnotations.isEmpty()) {
               return (String) textAnnotations.get(0).get("description");
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
      return "";
   }

   private Map<String, Object> analyzeReceiptDetails(String text) {
      Map<String, Object> details = new HashMap<>();
      details.put("description", "영수증 지출");
      details.put("amount", 0L);
      details.put("transactionDate", "");
      details.put("category", "ETC");

      if (text == null || text.trim().isEmpty()) {
         return details;
      }

      String[] lines = text.split("\n");
      List<String> noiseKeywords = Arrays.asList(
          "고객", "가맹점", "보관", "매출", "영수증", "신용", "체크", "카드", "재발급", "승인", "표시", "전표", "회원"
      );

      String detectedTitle = "영수증 지출";
      for (String line : lines) {
         String trimmed = line.trim();
         if (trimmed.isEmpty() || trimmed.length() < 2) continue;

         boolean isNoise = false;
         for (String keyword : noiseKeywords) {
            if (trimmed.contains(keyword)) {
               isNoise = true;
               break;
            }
         }

         if (!isNoise && trimmed.length() <= 15) {
            detectedTitle = trimmed;
            break;
         }
      }
      details.put("description", detectedTitle);
      details.put("category", autoMapCategory(detectedTitle));
      details.put("amount", extractAmount(text));
      details.put("transactionDate", extractTransactionDate(text));

      return details;
   }

   private long extractAmount(String text) {
      long detectedMaxAmount = 0L;
      List<String> amountKeywords = Arrays.asList(
          "합계", "결제금액", "승인금액", "받을금액", "total", "TOTAL", "Total", "금액", "합 계"
      );

      String[] lines = text.split("\n");
      for (int i = 0; i < lines.length; i++) {
         String line = lines[i];
         boolean hasKeyword = false;
         for (String keyword : amountKeywords) {
            if (line.contains(keyword)) {
               hasKeyword = true;
               break;
            }
         }

         if (hasKeyword) {
            String textToSearch = line;
            if (i + 1 < lines.length) {
               textToSearch += " " + lines[i + 1];
            }

            Pattern pattern = Pattern.compile("([0-9,]{3,10})\\s*(원|S|$)??");
            Matcher matcher = pattern.matcher(textToSearch);

            while (matcher.find()) {
               try {
                  String numberStr = matcher.group(1).replace(",", "").trim();
                  long parsedVal = Long.parseLong(numberStr);
                  if (parsedVal >= 100 && parsedVal > detectedMaxAmount) {
                     detectedMaxAmount = parsedVal;
                  }
               } catch (NumberFormatException ignored) {}
            }
         }
      }

      if (detectedMaxAmount == 0) {
         Pattern fallbackPattern = Pattern.compile("\\b([1-9][0-9,]{2,8})\\b");
         Matcher matcher = fallbackPattern.matcher(text);
         while (matcher.find()) {
            try {
               String cleanNum = matcher.group(1).replace(",", "");
               long val = Long.parseLong(cleanNum);
               if (val > detectedMaxAmount && val < 5000000) {
                  detectedMaxAmount = val;
               }
            } catch (NumberFormatException ignored) {}
         }
      }

      return detectedMaxAmount;
   }

   private String extractTransactionDate(String text) {
      if (text == null || text.isEmpty()) return "";

      Pattern dateTimePattern = Pattern.compile(
          "(\\b\\d{2,4}[./-]\\d{2}[./-]\\d{2}\\b)\\s*(\\b\\d{2}:\\d{2}(:\\d{2})?\\b)"
      );
      Matcher matcher = dateTimePattern.matcher(text);

      if (matcher.find()) {
         String datePart = matcher.group(1).replace(".", "-").replace("/", "-");
         String timePart = matcher.group(2);

         if (datePart.indexOf("-") == 2) {
            datePart = "20" + datePart;
         }
         if (timePart.length() > 5) {
            timePart = timePart.substring(0, 5);
         }

         return datePart + "T" + timePart;
      }

      Pattern dateOnlyPattern = Pattern.compile("(\\b\\d{2,4}[./-]\\d{2}[./-]\\d{2}\\b)");
      Matcher dateMatcher = dateOnlyPattern.matcher(text);
      if (dateMatcher.find()) {
         String datePart = dateMatcher.group(1).replace(".", "-").replace("/", "-");
         if (datePart.indexOf("-") == 2) {
            datePart = "20" + datePart;
         }
         return datePart + "T12:00";
      }

      return "";
   }

   private String autoMapCategory(String description) {
      if (description == null || description.isEmpty()) return "ETC";

      if (description.contains("병원") || description.contains("의원") || description.contains("약국") ||
          description.contains("내과") || description.contains("외과") || description.contains("치과") || description.contains("한의원")) {
         return "MEDICAL";
      }
      if (description.contains("식당") || description.contains("푸드") || description.contains("카페") ||
          description.contains("커피") || description.contains("베이커리") || description.contains("음식점") ||
          description.contains("마트") || description.contains("편의점") || description.contains("포차") || description.contains("치킨")) {
         return "FOOD";
      }
      if (description.contains("택시") || description.contains("버스") || description.contains("지하철") ||
          description.contains("주차") || description.contains("주유") || description.contains("하이패스") || description.contains("철도")) {
         return "TRANSPORT";
      }
      if (description.contains("영화") || description.contains("넷플릭스") || description.contains("멜론") ||
          description.contains("노래방") || description.contains("헬스") || description.contains("게임") || description.contains("PC방")) {
         return "CULTURE";
      }
      if (description.contains("다이소") || description.contains("백화점") || description.contains("쿠팡") ||
          description.contains("패션") || description.contains("의류") || description.contains("올리브영")) {
         return "SHOPPING";
      }
      if (description.contains("월세") || description.contains("관리비") || description.contains("전기요금") ||
          description.contains("SKT") || description.contains("KT") || description.contains("LGU")) {
         return "HOUSING";
      }
      if (description.contains("급여") || description.contains("월급") || description.contains("알바") || description.contains("보너스")) {
         return "SALARY";
      }

      return "ETC";
   }
}
