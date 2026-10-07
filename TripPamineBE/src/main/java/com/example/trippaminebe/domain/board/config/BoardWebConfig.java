package com.example.trippaminebe.domain.board.config;

import com.example.trippaminebe.domain.board.service.BoardImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// /board-images/** 요청을 디스크의 업로드 폴더로 연결해서 <img src="/board-images/xxx.jpg">로 바로 볼 수 있게 한다.
// (SecurityConfig에서 GET /board-images/** 는 permitAll 처리)
@Configuration
@RequiredArgsConstructor
public class BoardWebConfig implements WebMvcConfigurer {

  private final BoardImageStorage boardImageStorage;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = boardImageStorage.getUploadDir().toUri().toString(); // file:/.../uploads/board/
    if (!location.endsWith("/")) {
      location += "/";
    }
    registry.addResourceHandler(BoardImageStorage.URL_PREFIX + "**")
        .addResourceLocations(location)
        .setCachePeriod(60 * 60 * 24 * 7); // 파일명이 UUID라 내용이 바뀌지 않으므로 1주일 캐시
  }
}
