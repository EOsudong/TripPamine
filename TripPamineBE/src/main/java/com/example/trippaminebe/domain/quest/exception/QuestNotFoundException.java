package com.example.trippaminebe.domain.quest.exception;

public class QuestNotFoundException extends RuntimeException {
    public QuestNotFoundException(String message) {
        super(message);
    }
}
