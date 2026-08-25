package com.example.trippaminebe.domain.quest.exception;

// 진행/완료 기록(USER_QUEST_LOGS)이 남아있는 퀘스트를 하드 삭제하려 할 때 발생 (FK_LOGS_QUESTS 위반)
public class QuestDeleteConflictException extends RuntimeException {
	public QuestDeleteConflictException(String message) {
		super(message);
	}
}
