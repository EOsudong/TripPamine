package com.example.trippaminebe.domain.board.service;

import com.example.trippaminebe.domain.board.exception.BoardImageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// 게시판 첨부 이미지를 서버 로컬 디스크에 저장/삭제하는 컴포넌트.
//
// - 저장 위치: application.yaml의 app.board.upload-dir (기본 ./uploads/board)
// - 접근 URL : /board-images/{저장파일명}  (BoardWebConfig에서 정적 리소스로 매핑)
// - 트랜잭션 연동: DB 저장이 롤백되면 방금 저장한 파일도 지우고,
//                  이미지 삭제는 DB 커밋이 성공한 뒤에만 실제 파일을 지운다.
//   → "DB에는 있는데 파일이 없음" / "파일만 남은 쓰레기" 상황을 최소화.
//
// 나중에 S3 등으로 옮길 때는 이 클래스만 교체하면 된다.
@Slf4j
@Component
public class BoardImageStorage {

  public static final String URL_PREFIX = "/board-images/";
  public static final int MAX_IMAGES_PER_POST = 5;
  public static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

  private final Path uploadDir;

  public BoardImageStorage(@Value("${app.board.upload-dir:./uploads/board}") String uploadDir) {
    this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.uploadDir);
    } catch (IOException e) {
      throw new IllegalStateException("게시판 이미지 저장 폴더를 만들 수 없습니다: " + this.uploadDir, e);
    }
    log.info("[BOARD] 이미지 저장 경로: {}", this.uploadDir);
  }

  public Path getUploadDir() {
    return uploadDir;
  }

  // 파일 1개 저장 후 저장 정보 반환
  public StoredImage store(MultipartFile file) {
    validate(file);

    String extension = extractExtension(file.getOriginalFilename());
    String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
    Path target = uploadDir.resolve(storedName).normalize();

    try (InputStream in = file.getInputStream()) {
      Files.copy(in, target);
    } catch (IOException e) {
      throw new BoardImageException("이미지 저장에 실패했습니다.", e);
    }

    // 이후 DB 작업이 실패해 트랜잭션이 롤백되면 방금 저장한 파일을 지운다
    runAfterCompletion(status -> {
      if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
        deleteQuietly(storedName);
      }
    });

    return new StoredImage(storedName, cleanOriginalName(file.getOriginalFilename()), URL_PREFIX + storedName);
  }

  // DB 커밋이 성공한 후에 파일 삭제 (트랜잭션 밖에서 호출되면 즉시 삭제)
  public void deleteAfterCommit(String storedName) {
    runAfterCompletion(status -> {
      if (status == TransactionSynchronization.STATUS_COMMITTED) {
        deleteQuietly(storedName);
      }
    });
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BoardImageException("비어 있는 파일은 첨부할 수 없습니다.");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new BoardImageException("이미지는 한 장당 5MB 이하만 첨부할 수 있습니다.");
    }
    String extension = extractExtension(file.getOriginalFilename());
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new BoardImageException("jpg, png, gif, webp 형식의 이미지만 첨부할 수 있습니다.");
    }
    // 확장자만 바꾼 다른 파일(예: html → jpg)을 막기 위해 파일 앞부분(매직 넘버)도 확인
    if (!hasImageSignature(file)) {
      throw new BoardImageException("올바른 이미지 파일이 아닙니다.");
    }
  }

  private boolean hasImageSignature(MultipartFile file) {
    byte[] head = new byte[12];
    int read;
    try (InputStream in = file.getInputStream()) {
      read = in.readNBytes(head, 0, head.length);
    } catch (IOException e) {
      return false;
    }
    if (read < 4) return false;
    head = Arrays.copyOf(head, read);

    boolean jpeg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
    boolean png = (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
    boolean gif = head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8';
    boolean webp = read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
        && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    return jpeg || png || gif || webp;
  }

  private String extractExtension(String filename) {
    String ext = StringUtils.getFilenameExtension(filename);
    return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
  }

  private String cleanOriginalName(String filename) {
    if (filename == null) return null;
    String name = StringUtils.getFilename(StringUtils.cleanPath(filename));
    if (name != null && name.length() > 255) {
      name = name.substring(name.length() - 255);
    }
    return name;
  }

  private void deleteQuietly(String storedName) {
    try {
      Path target = uploadDir.resolve(storedName).normalize();
      // 저장 폴더 밖의 경로는 절대 지우지 않음
      if (target.startsWith(uploadDir)) {
        Files.deleteIfExists(target);
      }
    } catch (IOException e) {
      log.warn("[BOARD] 이미지 파일 삭제 실패: {}", storedName, e);
    }
  }

  private void runAfterCompletion(java.util.function.IntConsumer action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          action.accept(status);
        }
      });
    } else {
      // 트랜잭션 밖에서 호출된 경우: 커밋된 것으로 간주
      action.accept(TransactionSynchronization.STATUS_COMMITTED);
    }
  }

  public record StoredImage(String storedName, String originalName, String imageUrl) {
  }
}
