package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.service.AiPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai-photo")
@RequiredArgsConstructor
public class AiPhotoController {

   private final AiPhotoService aiPhotoService;

   // 베스트 샷 추천
   @PostMapping(value = "/best-shot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   public ResponseEntity<String> getBestShot(@RequestPart("images") List<MultipartFile> images) {
      String resultJson = aiPhotoService.getBestShot(images);
      return ResponseEntity.ok(resultJson);
   }

   // 다중 행인 자동 모자이크
   @PostMapping(value = "/multi-mosaic", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   public ResponseEntity<String> getMultiMosaic(@RequestPart("images") List<MultipartFile> images) {
      String resultJson = aiPhotoService.getMultiMosaic(images);
      return ResponseEntity.ok(resultJson);
   }

   // 수동 핀포인트 모자이크
   @PostMapping(value = "/mosaic", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   public ResponseEntity<byte[]> getManualMosaic(
       @RequestPart("image") MultipartFile image,
       @RequestPart("mask") MultipartFile mask
   ) {
      byte[] imageBytes = aiPhotoService.getManualMosaic(image, mask);
      return ResponseEntity.ok()
          .contentType(MediaType.IMAGE_JPEG)
          .body(imageBytes);
   }
}