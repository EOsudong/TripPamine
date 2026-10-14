package com.example.trippaminebe.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

@Component
public class FastApiServerManager {

   /**
    * Spring Boot 애플리케이션 구동 완료 후 자동 실행
    */
   @EventListener(ApplicationReadyEvent.class)
   public void startFastApiServer() {
      String userDir = System.getProperty("user.dir");
      File defaultEnv = new File(userDir, ".env");
      File projectRoot = defaultEnv.exists() ? new File(userDir) : new File(userDir, "TripPamineBE");

      // 1. 8000번 포트 이미 사용 중인지 검사
      if (isPortInUse("127.0.0.1", 8000)) {
         System.out.println("ℹ️ [FastAPI] 8000번 포트에서 비전 AI 서버가 이미 실행 중입니다. 중복 실행을 건너뜁니다.");
         return;
      }

      try {
         File visionDir = new File(projectRoot.getParentFile(), "vision-ai-test");
         if (!visionDir.exists()) {
            visionDir = new File(projectRoot, "vision-ai-test");
         }

         File batFile = new File(visionDir, "run_vision.bat");

         if (batFile.exists()) {
            System.out.println("--------------------------------------------------");
            System.out.println("🚀 [FastAPI] 비전 AI 서버 자동 실행 호출: " + batFile.getAbsolutePath());
            System.out.println("--------------------------------------------------");

            // cmd.exe로 run_vision.bat 실행
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "", "/b", "run_vision.bat");
            pb.directory(visionDir);
            pb.start();
         } else {
            System.err.println("⚠️ [FastAPI] run_vision.bat 파일을 찾을 수 없어 실행을 건너뜁니다: " + batFile.getAbsolutePath());
         }
      } catch (IOException e) {
         System.err.println("❌ [FastAPI] 비전 AI 서버 실행 실패: " + e.getMessage());
      }
   }

   /**
    * Spring Boot 종료 시 프로세스 자동 정리 (@PreDestroy)
    */
   @PreDestroy
   public void stopFastApiServer() {
      System.out.println("🛑 [FastAPI] Spring Boot 종료에 따른 비전 AI 프로세스 정리를 수행합니다.");
      try {
         new ProcessBuilder("cmd.exe", "/c", "taskkill /F /IM python.exe /T").start();
      } catch (Exception ignored) {
      }
   }

   private boolean isPortInUse(String host, int port) {
      try (Socket socket = new Socket(host, port)) {
         return true;
      } catch (IOException e) {
         return false;
      }
   }
}


