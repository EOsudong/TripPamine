package com.example.trippaminebe.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class FastApiServerManager {

   private static final Logger log = LoggerFactory.getLogger(FastApiServerManager.class);
   private static final String VISION_SERVICE_NAME = "trippamine-vision";
   private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
   private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);
   private static final long MAX_RETRY_DELAY_MS = TimeUnit.MINUTES.toMillis(5);

   private final HttpClient healthClient = HttpClient.newBuilder()
       .connectTimeout(PROBE_TIMEOUT)
       .build();
   private final ObjectMapper objectMapper = new ObjectMapper();

   @Value("${fastapi.url:http://localhost:8000}")
   private String fastApiUrl;

   @Value("${fastapi.auto-start:true}")
   private boolean autoStart;

   @Value("${fastapi.watchdog-enabled:true}")
   private boolean watchdogEnabled;

   @Value("${fastapi.watchdog-interval-ms:30000}")
   private long watchdogIntervalMs;

   // Spring 전역 스케줄러와 분리해 AI 모델 로딩이 다른 예약 작업을 막지 않도록 한다.
   private ScheduledExecutorService watchdogExecutor;
   private volatile boolean shuttingDown;
   private boolean wasReady = true;
   private int retryCount;
   private long nextRetryAtNanos;

   // Spring Boot에서 직접 생성한 프로세스만 종료한다. 사용자가 수동으로 띄운 서버는 보존한다.
   private Process ownedProcess;

   @EventListener(ApplicationReadyEvent.class)
   public synchronized void onApplicationReady() {
      // 최초 서버 준비가 끝난 뒤 감시 스레드를 활성화한다.
      try {
         startFastApiServer();
      } finally {
         startWatchdog();
      }
   }

   private synchronized void startFastApiServer() {
      if (shuttingDown) {
         return;
      }
      if (!autoStart) {
         log.info("[Vision AI] 자동 실행이 비활성화되어 있습니다.");
         return;
      }

      URI serverUri;
      try {
         serverUri = URI.create(fastApiUrl);
      } catch (IllegalArgumentException e) {
         log.warn("[Vision AI] fastapi.url 설정이 올바르지 않습니다: {}", fastApiUrl);
         return;
      }

      // 자동 실행은 현재 PC의 8000번 포트에서만 수행한다. 원격 AI 서버는 직접 제어하지 않는다.
      String host = serverUri.getHost();
      int port = serverUri.getPort() == -1 ? 80 : serverUri.getPort();
      if (!"http".equalsIgnoreCase(serverUri.getScheme()) || !isLocalHost(host) || port != 8000) {
         log.info("[Vision AI] 로컬 8000번 포트 설정이 아니므로 자동 실행하지 않습니다: {}", fastApiUrl);
         return;
      }
      String probeHost = "localhost".equalsIgnoreCase(host) ? "127.0.0.1" : host;
      URI healthUri = URI.create("http://" + (probeHost.contains(":") ? "[" + probeHost + "]" : probeHost)
          + ":" + port + "/health");

      // 포트가 열려 있다는 사실만으로 우리 Vision AI 서버라고 판단하지 않는다.
      if (isVisionReady(healthUri)) {
         log.info("[Vision AI] 준비된 Vision 서버를 재사용합니다: {}", healthUri);
         return;
      }
      if (isPortOccupied(probeHost, port)) {
         log.warn("[Vision AI] {}:{} 포트가 점유됐지만 /health 응답이 정상이 아닙니다. 중복 실행하지 않습니다.",
             probeHost, port);
         return;
      }

      File visionDir = findVisionDirectory();
      if (visionDir == null) {
         log.warn("[Vision AI] vision-ai-test/main.py를 찾지 못해 자동 실행하지 않습니다.");
         return;
      }

      // 기존 venv의 python.exe를 직접 시작해 Windows cmd.exe 자식 프로세스 잔류를 방지한다.
      boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
      File python = new File(visionDir, windows ? "venv/Scripts/python.exe" : "venv/bin/python");
      if (!python.isFile()) {
         log.warn("[Vision AI] 전용 가상환경을 찾지 못했습니다: {}", python.getAbsolutePath());
         return;
      }

      try {
         ProcessBuilder builder = new ProcessBuilder(
             python.getAbsolutePath(), "-m", "uvicorn", "main:app",
             "--host", probeHost, "--port", String.valueOf(port));
         builder.directory(visionDir);
         builder.redirectErrorStream(true);
         builder.inheritIO();
         ownedProcess = builder.start();
         log.info("[Vision AI] 서버 실행 시도: {} (PID={})", visionDir.getAbsolutePath(), ownedProcess.pid());

         if (waitUntilReady(healthUri)) {
            log.info("[Vision AI] 모델 준비 완료: {}", healthUri);
         } else {
            log.warn("[Vision AI] 제한 시간 내 모델 준비 실패. 직접 실행한 프로세스를 종료합니다.");
            stopOwnedProcess();
         }
      } catch (IOException e) {
         log.error("[Vision AI] Python 서버 시작 실패: {}", e.getMessage(), e);
         stopOwnedProcess();
      }
   }

   // AI 서버가 실행 중에 예기치 않게 종료됐을 때만 제한적으로 재시도한다.
   private void startWatchdog() {
      if (!autoStart || !watchdogEnabled || shuttingDown || watchdogExecutor != null) {
         return;
      }
      if (watchdogIntervalMs < 5000) {
         log.warn("[Vision AI] 감시 간격이 5초 미만이어서 30초 기본값을 사용합니다: {}ms", watchdogIntervalMs);
         watchdogIntervalMs = 30000;
      }
      watchdogExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
         Thread thread = new Thread(task, "vision-ai-watchdog");
         thread.setDaemon(true);
         return thread;
      });
      watchdogExecutor.scheduleWithFixedDelay(
          this::checkAndRecoverSafely,
          watchdogIntervalMs, watchdogIntervalMs, TimeUnit.MILLISECONDS);
      log.info("[Vision AI] 서버 상태 감시 시작 ({}ms 간격)", watchdogIntervalMs);
   }

   private void checkAndRecoverSafely() {
      try {
         checkAndRecover();
      } catch (RuntimeException e) {
         // 주기적 실행이 예외 한 번으로 영구 중단되지 않도록 보호한다.
         log.error("[Vision AI] 상태 감시 중 예상하지 못한 오류", e);
      }
   }

   private synchronized void checkAndRecover() {
      if (shuttingDown || !autoStart) {
         return;
      }

      URI healthUri = resolveLocalHealthUri();
      if (healthUri == null) {
         return;
      }
      if (isVisionReady(healthUri)) {
         if (!wasReady) {
            log.info("[Vision AI] 서버 정상 상태 복구: {}", healthUri);
         }
         wasReady = true;
         retryCount = 0;
         nextRetryAtNanos = 0;
         return;
      }
      if (wasReady) {
         log.warn("[Vision AI] 서버 상태 확인 실패: {}", healthUri);
      }
      wasReady = false;

      // 모델 추론 중 /health만 지연될 수도 있으므로, 포트 점유 중에는 프로세스를 죽이지 않는다.
      if (isPortOccupied(healthUri.getHost(), healthUri.getPort())) {
         return;
      }

      long now = System.nanoTime();
      if (now < nextRetryAtNanos) {
         return;
      }

      // 응답도 없고 수신 포트도 닫혀 있을 때만 직접 실행한 프로세스의 잔재를 정리한다.
      if (ownedProcess != null) {
         stopOwnedProcess();
      }
      retryCount++;
      long delay = Math.min(MAX_RETRY_DELAY_MS,
          watchdogIntervalMs * (1L << Math.min(retryCount - 1, 4)));
      nextRetryAtNanos = now + TimeUnit.MILLISECONDS.toNanos(delay);
      log.warn("[Vision AI] 서버 재시작 시도 #{} (실패 시 최소 {}초 후 재시도)",
          retryCount, delay / 1000);
      startFastApiServer();

      // 재시작 성공은 다음 감시에서도 확인한다. 실패 시 점진적 간격을 유지한다.
      if (isVisionReady(healthUri)) {
         log.info("[Vision AI] 자동 재시작 후 모델 준비 완료");
         wasReady = true;
         retryCount = 0;
         nextRetryAtNanos = 0;
      }
   }

   private URI resolveLocalHealthUri() {
      try {
         URI serverUri = URI.create(fastApiUrl);
         String host = serverUri.getHost();
         int port = serverUri.getPort() == -1 ? 80 : serverUri.getPort();
         if (!"http".equalsIgnoreCase(serverUri.getScheme()) || !isLocalHost(host) || port != 8000) {
            return null;
         }
         String probeHost = "localhost".equalsIgnoreCase(host) ? "127.0.0.1" : host;
         return URI.create("http://" + (probeHost.contains(":") ? "[" + probeHost + "]" : probeHost)
             + ":" + port + "/health");
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   @PreDestroy
   public void stopFastApiServer() {
      shuttingDown = true;
      // 대기 중인 모델 로딩을 먼저 중단한 뒤 소유 프로세스만 종료한다.
      if (watchdogExecutor != null) {
         watchdogExecutor.shutdownNow();
      }
      synchronized (this) {
         stopOwnedProcess();
      }
   }

   private boolean waitUntilReady(URI healthUri) {
      long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
      while (!shuttingDown && System.nanoTime() < deadline) {
         if (ownedProcess == null || !ownedProcess.isAlive()) {
            log.warn("[Vision AI] Python 프로세스가 준비 전에 종료됐습니다.");
            return false;
         }
         if (isVisionReady(healthUri)) {
            return true;
         }
         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
         }
      }
      return false;
   }

   private boolean isVisionReady(URI healthUri) {
      try {
         HttpRequest request = HttpRequest.newBuilder(healthUri)
             .timeout(PROBE_TIMEOUT)
             .GET()
             .build();
         HttpResponse<String> response = healthClient.send(request, HttpResponse.BodyHandlers.ofString());
         if (response.statusCode() != 200) {
            return false;
         }
         JsonNode body = objectMapper.readTree(response.body());
         return VISION_SERVICE_NAME.equals(body.path("service").asText())
             && "ready".equals(body.path("status").asText());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return false;
      } catch (IOException | RuntimeException e) {
         return false;
      }
   }

   private File findVisionDirectory() {
      File currentDir = new File(System.getProperty("user.dir"));
      File parentDir = currentDir.getParentFile();
      File sibling = parentDir == null ? null : new File(parentDir, "vision-ai-test");
      if (sibling != null && new File(sibling, "main.py").isFile()) {
         return sibling;
      }
      File child = new File(currentDir, "vision-ai-test");
      return new File(child, "main.py").isFile() ? child : null;
   }

   private boolean isPortOccupied(String host, int port) {
      try (Socket socket = new Socket()) {
         socket.connect(new InetSocketAddress(host, port), 500);
         return true;
      } catch (IOException e) {
         return false;
      }
   }

   private boolean isLocalHost(String host) {
      return "localhost".equalsIgnoreCase(host)
          || "127.0.0.1".equals(host)
          || "::1".equals(host);
   }

   private void stopOwnedProcess() {
      if (ownedProcess == null) {
         return;
      }
      Process process = ownedProcess;
      ownedProcess = null;

      // 해당 프로세스의 자식만 정리한다. 포트 기반 강제 종료나 전체 python.exe 종료는 금지한다.
      List<ProcessHandle> children = process.descendants().toList();
      for (int i = children.size() - 1; i >= 0; i--) {
         children.get(i).destroy();
      }
      process.destroy();
      try {
         process.waitFor(3, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      for (ProcessHandle child : children) {
         if (child.isAlive()) {
            child.destroyForcibly();
         }
      }
      if (process.isAlive()) {
         process.destroyForcibly();
      }
      log.info("[Vision AI] 직접 실행한 Python 프로세스 종료 요청 완료");
   }
}