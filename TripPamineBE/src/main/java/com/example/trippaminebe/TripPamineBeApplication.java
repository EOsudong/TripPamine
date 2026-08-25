package com.example.trippaminebe;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.File;

@SpringBootApplication
public class TripPamineBeApplication {

	public static void main(String[] args) {
		// 현재 실행 위치(User Dir) 확인
		String userDir = System.getProperty("user.dir");

		// .env 파일이 현재 작업 디렉토리에 없으면 절대 경로 지정
		Dotenv dotenv;
		File defaultEnv = new File(userDir, ".env");

		File projectRoot;
		if (defaultEnv.exists()) {
			dotenv = Dotenv.configure().load();
			projectRoot = new File(userDir);
		} else {
			// 프로젝트 실제 경로를 지정하여 로드
			dotenv = Dotenv.configure()
					.directory(userDir+"\\TripPamineBE")
					.ignoreIfMissing()
					.load();
			projectRoot = new File(userDir, "TripPamineBE");
		}
		dotenv.entries().forEach(entry -> {
			System.setProperty(entry.getKey(), entry.getValue());
		});

		// Oracle Wallet 경로를 절대 경로로 설정 (실행 위치에 무관하게 동작)
		File walletDir = new File(projectRoot, "src/main/resources/wallets");
		System.setProperty("oracle.net.tns_admin", walletDir.getAbsolutePath());

		SpringApplication.run(TripPamineBeApplication.class, args);
	}
}