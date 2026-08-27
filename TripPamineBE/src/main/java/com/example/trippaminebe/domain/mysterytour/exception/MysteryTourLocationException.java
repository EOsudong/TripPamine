package com.example.trippaminebe.domain.mysterytour.exception;

/** AI가 만든 GPS 퀘스트의 실제 장소 또는 좌표를 확정할 수 없을 때 발생한다. */
public class MysteryTourLocationException extends RuntimeException {

    public MysteryTourLocationException(String message) {
        super(message);
    }
}
