package com.example.trippaminebe.domain.diary.exception;

public class DiaryAlreadyExistsException extends RuntimeException {

  // 이미 만들어진 다이어리가 있을 때의 예외를 잡기 위한 Exception file

  private final Long diaryId;

  public DiaryAlreadyExistsException(String message, Long diaryId) {
    super(message);
    this.diaryId = diaryId;
  }

  public Long getDiaryId() {
    return diaryId;
  }
}