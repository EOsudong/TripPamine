package com.example.trippaminebe.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPhotoService {

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

      return fastApiWebClient.post()
          .uri("/api/best-shot")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .bodyValue(builder.build())
          .retrieve()
          .bodyToMono(String.class)
          .block();
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

      return fastApiWebClient.post()
          .uri("/api/multi-mosaic")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .bodyValue(builder.build())
          .retrieve()
          .bodyToMono(String.class)
          .block();
   }

   /**
    * 3. 수동 핀포인트 모자이크 중계
    */
   public byte[] getManualMosaic(MultipartFile image, MultipartFile mask) {
      MultipartBodyBuilder builder = new MultipartBodyBuilder();
      builder.part("image", getFileResource(image)).filename(image.getOriginalFilename());
      builder.part("mask", getFileResource(mask)).filename("mask.png");

      return fastApiWebClient.post()
          .uri("/api/mosaic")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .bodyValue(builder.build())
          .retrieve()
          .bodyToMono(byte[].class)
          .block();
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