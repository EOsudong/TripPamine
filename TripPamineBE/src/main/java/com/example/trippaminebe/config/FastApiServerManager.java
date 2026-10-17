package com.example.trippaminebe.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class FastApiServerManager {

   // FastAPI 프로젝트 폴더
   private final String visionDir =
           "/Users/kimmanten/Desktop/project/vision-ai-test";

   // 실행된 FastAPI 프로세스를 저장
   private Process fastApiProcess;


   /**
    * Spring Boot 서버가 실행될 때
    * FastAPI 서버도 자동 실행
    */
   @PostConstruct
   public void startFastApiServer() {

      try {

         // Mac용 실행 파일
         File scriptFile = new File(
                 visionDir,
                 "run_vision.sh"
         );


         // run_vision.sh 존재 여부 확인
         if (!scriptFile.exists()) {

            System.out.println(
                    "❌ [FastAPI] run_vision.sh 파일을 찾을 수 없습니다."
            );

            System.out.println(
                    "경로: " + scriptFile.getAbsolutePath()
            );

            return;
         }


         // Mac에서는 cmd.exe가 아니라
         // /bin/zsh 사용
         ProcessBuilder processBuilder =
                 new ProcessBuilder(
                         "/bin/zsh",
                         scriptFile.getAbsolutePath()
                 );


         // FastAPI 프로젝트 폴더에서 실행
         processBuilder.directory(
                 new File(visionDir)
         );


         // 에러 로그와 일반 로그 합치기
         processBuilder.redirectErrorStream(true);


         // IntelliJ Console에서
         // FastAPI 로그도 같이 확인
         processBuilder.inheritIO();


         // FastAPI 서버 실행
         fastApiProcess =
                 processBuilder.start();


         System.out.println(
                 "🚀 [FastAPI] 비전 AI 서버 자동 실행 요청 완료"
         );

         System.out.println(
                 "📂 [FastAPI] 실행 위치: "
                         + visionDir
         );

         System.out.println(
                 "📄 [FastAPI] 실행 파일: "
                         + scriptFile.getAbsolutePath()
         );


      } catch (Exception e) {

         System.out.println(
                 "❌ [FastAPI] 비전 AI 서버 실행 실패: "
                         + e.getMessage()
         );

         e.printStackTrace();
      }
   }


   /**
    * Spring Boot 서버가 종료될 때
    * 실행했던 FastAPI 서버도 같이 종료
    */
   @PreDestroy
   public void stopFastApiServer() {

      try {

         if (fastApiProcess != null
                 && fastApiProcess.isAlive()) {


            // FastAPI 하위 프로세스 종료
            fastApiProcess
                    .descendants()
                    .forEach(processHandle -> {

                       try {

                          processHandle.destroy();

                       } catch (Exception ignored) {
                       }

                    });


            // FastAPI 메인 프로세스 종료
            fastApiProcess.destroy();


            System.out.println(
                    "🛑 [FastAPI] 비전 AI 서버 종료 완료"
            );

         } else {

            System.out.println(
                    "ℹ️ [FastAPI] 종료할 FastAPI 서버가 없습니다."
            );
         }


      } catch (Exception e) {

         System.out.println(
                 "❌ [FastAPI] 비전 AI 서버 종료 실패: "
                         + e.getMessage()
         );
      }
   }
}