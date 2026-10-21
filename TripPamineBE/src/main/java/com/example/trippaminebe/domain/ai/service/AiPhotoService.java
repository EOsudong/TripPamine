package com.example.trippaminebe.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AiPhotoService {

   private static final Logger log = LoggerFactory.getLogger(AiPhotoService.class);
   private final WebClient fastApiWebClient;

   /**
    * 1. 베스트 샷 추천 중계
    */
   public String getBestShot(List<MultipartFile> images) {
      MultipartBodyBuilder builder = new MultipartBodyBuilder();
      for (MultipartFile file : images) {
         builder.part("images", getFileResource(file))
             .filename(file.getOriginalFilename());
      }

      return forward("best-shot", () -> fastApiWebClient.post()
          .uri("/api/best-shot")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .bodyValue(builder.build())
          .retrieve()
          .bodyToMono(String.class)
          .block());
   }

   /**
    * 2. 다중 행인 자동 모자이크 중계
    */
   public String getMultiMosaic(List<MultipartFile> images) {
      MultipartBodyBuilder builder = new MultipartBodyBuilder();
      for (MultipartFile file : images) {
         builder.part("images", getFileResource(file))
             .filename(file.getOriginalFilename());
      }

      return forward("multi-mosaic", () -> fastApiWebClient.post()
          .uri("/api/multi-mosaic")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .bodyValue(builder.build())
          .retrieve()
          .bodyToMono(String.class)
          .block());
   }

   /**
    * 3. 수동 핀포인트 모자이크 중계
    */
   public byte[] getManualMosaic(MultipartFile image, MultipartFile mask) {
      MultipartBodyBuilder builder = new MultipartBodyBuilder();
      builder.part("image", getFileResource(image)).filename(image.getOriginalFilename());
      builder.part("mask", getFileResource(mask)).filename("mask.png");

      return forward("mosaic", () -> fastApiWebClient.post()
          .uri("/api/mosaic")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .bodyValue(builder.build())
          .retrieve()
          .bodyToMono(byte[].class)
          .block());
   }


   // 서버 연결 실패와 AI 추론 오류를 분리해 프론트엔드에 일관된 상태 코드를 제공한다.
   private <T> T forward(String operation, Supplier<T> call) {
      try {
         return call.get();
      } catch (WebClientRequestException e) {
         log.warn("[Vision AI] {} 서버 연결 실패: {}", operation, e.getMessage());
         throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
             "AI 사진 편집 서버에 연결할 수 없습니다.", e);
      } catch (WebClientResponseException e) {
         log.warn("[Vision AI] {} 처리 실패: HTTP {}", operation, e.getStatusCode().value());
         HttpStatus status = e.getStatusCode().is4xxClientError()
             ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
         throw new ResponseStatusException(status,
             status == HttpStatus.BAD_REQUEST
                 ? "사진 형식 또는 요청 내용이 올바르지 않습니다."
                 : "AI 사진 처리 중 서버 오류가 발생했습니다.", e);
      }
   }

   private ByteArrayResource getFileResource(MultipartFile file) {
      try {
         return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
               return file.getOriginalFilename();
            }
         };
      } catch (IOException e) {
         throw new RuntimeException("파일 변환 중 오류가 발생했습니다.", e);
      }
   }
}