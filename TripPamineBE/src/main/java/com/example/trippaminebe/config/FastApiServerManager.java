package com.example.trippaminebe.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

@Component
public class FastApiServerManager {

   private Process fastApiProcess;

   @PostConstruct
   public void startFastApiServer() {
      // 1. 이미 8000번 포트가 실행 중이라면 중복 실행 방지
      if (isPortInUse("127.0.0.1", 8000)) {
         System.out.println("ℹ️ [FastAPI] 8000번 포트에서 비전 AI 서버가 이미 실행 중입니다.");
         return;
      }

      try {
         // 2. 상대 경로로 vision-ai-test 디렉토리 찾기 (절대 경로 하드코딩 방지)
         String userDir = System.getProperty("user.dir");
         File currentDir = new File(userDir);
         File visionDir = new File(currentDir.getParentFile(), "vision-ai-test");
         if (!visionDir.exists()) {
            visionDir = new File(currentDir, "vision-ai-test");
         }

         if (!visionDir.exists()) {
            System.err.println("⚠️ [FastAPI] vision-ai-test 디렉토리를 찾을 수 없습니다: " + visionDir.getAbsolutePath());
            return;
         }

         // 3. OS(Windows vs Mac/Linux)에 따른 실행 명령어 분기
         boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
         ProcessBuilder processBuilder;

         if (isWindows) {
            File batFile = new File(visionDir, "run_vision.bat");
            if (!batFile.exists()) {
               System.err.println("⚠️ [FastAPI] run_vision.bat 파일이 없습니다: " + batFile.getAbsolutePath());
               return;
            }
            processBuilder = new ProcessBuilder("cmd.exe", "/c", "start", "", "/b", "run_vision.bat");
         } else {
            File shFile = new File(visionDir, "run_vision.sh");
            if (!shFile.exists()) {
               System.err.println("⚠️ [FastAPI] run_vision.sh 파일이 없습니다: " + shFile.getAbsolutePath());
               return;
            }
            processBuilder = new ProcessBuilder("/bin/bash", shFile.getAbsolutePath());
         }

         processBuilder.directory(visionDir);
         processBuilder.redirectErrorStream(true);
         processBuilder.inheritIO();

         fastApiProcess = processBuilder.start();

         System.out.println("🚀 [FastAPI] 비전 AI 서버 자동 실행 성공 (" + (isWindows ? "Windows .bat" : "Mac .sh") + ")");
         System.out.println("📂 [FastAPI] 실행 위치: " + visionDir.getAbsolutePath());

      } catch (Exception e) {
         System.err.println("❌ [FastAPI] 비전 AI 서버 실행 실패: " + e.getMessage());
         e.printStackTrace();
      }
   }

   @PreDestroy
   public void stopFastApiServer() {
      if (fastApiProcess != null && fastApiProcess.isAlive()) {
         try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            if (isWindows) {
               // Windows: /T 옵션으로 자식 프로세스(python.exe)까지 프로세스 트리 전체 강제 종료
               Runtime.getRuntime().exec("taskkill /F /T /PID " + fastApiProcess.pid());
            } else {
               // Mac/Linux: ProcessHandle을 통해 모든 하위 프로세스 강제 종료
               fastApiProcess.descendants().forEach(ProcessHandle::destroyForcibly);
               fastApiProcess.destroyForcibly();
            }
            System.out.println("🛑 [FastAPI] 비전 AI 서버가 완전히 종료되었습니다.");
         } catch (Exception e) {
            System.err.println("⚠️ [FastAPI] 서버 종료 중 오류 발생: " + e.getMessage());
         }
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