package com.example.trippaminebe.domain.quest.service;

import com.example.trippaminebe.domain.quest.dto.request.QuestStepRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestStepResponse;
import com.example.trippaminebe.domain.quest.entity.Quest;
import com.example.trippaminebe.domain.quest.entity.QuestStep;
import com.example.trippaminebe.domain.quest.exception.QuestNotFoundException;
import com.example.trippaminebe.domain.quest.exception.QuestStepNotFoundException;
import com.example.trippaminebe.domain.quest.repository.QuestRepository;
import com.example.trippaminebe.domain.quest.repository.QuestStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 연쇄 퀘스트 단계(QUEST_STEPS) 마스터 데이터 관리자 CRUD. QuestChapterService와 동일한 패턴.
// 실제 유저의 단계별 진행/클리어(USER_QUEST_STEP_LOGS)는 QuestService가 담당하고,
// 이 Service는 "어떤 단계가 어떤 순서로, 어디를 목표로 존재하는지"의 마스터 데이터만 다룬다.
@Service
@RequiredArgsConstructor
@Transactional
public class QuestStepService {

    private final QuestStepRepository questStepRepository;
    private final QuestRepository questRepository;

    @Transactional(readOnly = true)
    public List<QuestStepResponse> findAllByQuest(Long questId) {
        return questStepRepository.findByQuest_QuestIdOrderByStepOrderAsc(questId)
            .stream()
            .map(QuestStepResponse::from)
            .toList();
    }

    public QuestStepResponse create(Long questId, QuestStepRequest request) {
        Quest quest = getQuestOrThrow(questId);

        int nextOrder = request.getStepOrder() != null
            ? request.getStepOrder()
            : (int) questStepRepository.countByQuest_QuestId(questId) + 1;

        QuestStep step = QuestStep.builder()
            .quest(quest)
            .stepOrder(nextOrder)
            .stepName(request.getStepName())
            .description(request.getDescription())
            .targetLat(request.getTargetLat())
            .targetLng(request.getTargetLng())
            .clearRadius(request.getClearRadius() != null ? request.getClearRadius() : Quest.DEFAULT_CLEAR_RADIUS_METERS)
            .build();

        try {
            QuestStep saved = questStepRepository.save(step);
            questStepRepository.flush();
            return QuestStepResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // UQ_QUEST_STEPS_ORDER 위반 - 같은 퀘스트 안에 같은 STEP_ORDER가 이미 존재
            throw new IllegalArgumentException(
                "이미 같은 순서(" + nextOrder + ")의 단계가 존재합니다. 다른 순서를 입력해주세요."
            );
        }
    }

    public QuestStepResponse update(Long questId, Long stepId, QuestStepRequest request) {
        QuestStep step = getStepOrThrow(questId, stepId);

        try {
            step.update(
                request.getStepOrder(),
                request.getStepName(),
                request.getDescription(),
                request.getTargetLat(),
                request.getTargetLng(),
                request.getClearRadius()
            );
            questStepRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                "이미 같은 순서의 단계가 존재합니다. 다른 순서를 입력해주세요."
            );
        }

        return QuestStepResponse.from(step);
    }

    public void delete(Long questId, Long stepId) {
        QuestStep step = getStepOrThrow(questId, stepId);

        try {
            questStepRepository.delete(step);
            questStepRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 이미 유저가 이 단계를 클리어한 기록(USER_QUEST_STEP_LOGS)이 남아있으면 삭제할 수 없다.
            throw new IllegalArgumentException(
                "이미 사용자가 클리어한 단계는 삭제할 수 없습니다."
            );
        }
    }

    private Quest getQuestOrThrow(Long questId) {
        return questRepository.findById(questId)
            .orElseThrow(() -> new QuestNotFoundException("존재하지 않는 퀘스트입니다. id=" + questId));
    }

    private QuestStep getStepOrThrow(Long questId, Long stepId) {
        QuestStep step = questStepRepository.findById(stepId)
            .orElseThrow(() -> new QuestStepNotFoundException("존재하지 않는 단계입니다. id=" + stepId));

        if (!step.getQuest().getQuestId().equals(questId)) {
            throw new QuestStepNotFoundException(
                "해당 퀘스트에 속하지 않는 단계입니다. questId=" + questId + ", stepId=" + stepId
            );
        }

        return step;
    }
}
