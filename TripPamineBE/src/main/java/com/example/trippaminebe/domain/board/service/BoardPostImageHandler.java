package com.example.trippaminebe.domain.board.service;

import com.example.trippaminebe.domain.board.entity.BoardPost;
import com.example.trippaminebe.domain.board.entity.BoardPostImage;
import com.example.trippaminebe.domain.board.exception.BoardImageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 게시글 ↔ 첨부 이미지 동기화 로직 (회원/관리자 서비스 공용)
@Component
@RequiredArgsConstructor
public class BoardPostImageHandler {

  private final BoardImageStorage storage;

  // 새 글 작성 시: 업로드된 이미지를 순서대로 붙인다
  public void attachNew(BoardPost post, List<MultipartFile> files) {
    List<MultipartFile> valid = nonEmpty(files);
    checkCount(valid.size());
    appendFiles(post, valid, 0);
  }

  // 글 수정 시: keepImageIds에 없는 기존 이미지는 삭제하고, 새 이미지를 뒤에 붙인다
  public void sync(BoardPost post, List<Long> keepImageIds, List<MultipartFile> files) {
    Set<Long> keep = keepImageIds == null ? Set.of() : new HashSet<>(keepImageIds);
    List<MultipartFile> valid = nonEmpty(files);

    List<BoardPostImage> toRemove = post.getImages().stream()
        .filter(image -> !keep.contains(image.getImageId()))
        .toList();

    int remaining = post.getImages().size() - toRemove.size();
    checkCount(remaining + valid.size());

    for (BoardPostImage image : toRemove) {
      post.removeImage(image);
      storage.deleteAfterCommit(image.getStoredName());
    }

    // 남은 이미지 순서를 0부터 다시 매기고 새 이미지를 이어 붙임
    int order = 0;
    for (BoardPostImage image : post.getImages()) {
      image.changeSortOrder(order++);
    }
    appendFiles(post, valid, order);
  }

  private void appendFiles(BoardPost post, List<MultipartFile> files, int startOrder) {
    int order = startOrder;
    for (MultipartFile file : files) {
      BoardImageStorage.StoredImage stored = storage.store(file);
      post.addImage(BoardPostImage.builder()
          .storedName(stored.storedName())
          .originalName(stored.originalName())
          .imageUrl(stored.imageUrl())
          .sortOrder(order++)
          .build());
    }
  }

  private void checkCount(int count) {
    if (count > BoardImageStorage.MAX_IMAGES_PER_POST) {
      throw new BoardImageException("이미지는 게시글당 최대 " + BoardImageStorage.MAX_IMAGES_PER_POST + "장까지 첨부할 수 있습니다.");
    }
  }

  private List<MultipartFile> nonEmpty(List<MultipartFile> files) {
    if (files == null) return List.of();
    List<MultipartFile> result = new ArrayList<>();
    for (MultipartFile file : files) {
      if (file != null && !file.isEmpty()) result.add(file);
    }
    return result;
  }
}
