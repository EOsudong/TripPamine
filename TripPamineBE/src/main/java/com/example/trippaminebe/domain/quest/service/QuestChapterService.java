package com.example.trippaminebe.domain.quest.service;

import com.example.trippaminebe.domain.quest.dto.request.QuestChapterRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestChapterResponse;
import com.example.trippaminebe.domain.quest.entity.QuestChapter;
import com.example.trippaminebe.domain.quest.exception.QuestChapterNotFoundException;
import com.example.trippaminebe.domain.quest.repository.QuestChapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 퀘스트 챕터(QUEST_CHAPTERS) CRUD. QuestService와 패턴을 맞춘 단순 Service.
@Service
@RequiredArgsConstructor
@Transactional
public class QuestChapterService {

    private final QuestChapterRepository questChapterRepository;

    @Transactional(readOnly = true)
    public List<QuestChapterResponse> findAll(Integer viewerLevel) {
        return questChapterRepository.findAllByOrderByOrderNoAscChapterIdAsc()
            .stream()
            .map(chapter -> QuestChapterResponse.from(chapter, viewerLevel))
            .toList();
    }

    public QuestChapterResponse create(QuestChapterRequest request) {
        QuestChapter chapter = QuestChapter.builder()
            .chapterName(request.getChapterName())
            .description(request.getDescription())
            .region(request.getRegion())
            .orderNo(request.getOrderNo() != null ? request.getOrderNo() : 0)
            .requiredLevel(request.getRequiredLevel() != null ? request.getRequiredLevel() : 1)
            .imageUrl(request.getImageUrl())
            .build();

        return QuestChapterResponse.from(questChapterRepository.save(chapter));
    }

    public QuestChapterResponse update(Long chapterId, QuestChapterRequest request) {
        QuestChapter chapter = getChapterOrThrow(chapterId);

        chapter.update(
            request.getChapterName(),
            request.getDescription(),
            request.getRegion(),
            request.getOrderNo(),
            request.getRequiredLevel(),
            request.getImageUrl()
        );

        return QuestChapterResponse.from(chapter);
    }

    public void delete(Long chapterId) {
        QuestChapter chapter = getChapterOrThrow(chapterId);

        try {
            questChapterRepository.delete(chapter);
            questChapterRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 이 챕터에 배정된 QUESTS가 남아있으면(FK_QUESTS_CHAPTER) 삭제할 수 없다.
            throw new IllegalArgumentException(
                "이 챕터에 속한 퀘스트가 남아있어 삭제할 수 없습니다. 먼저 퀘스트를 다른 챕터로 옮기거나 챕터 배정을 해제해주세요."
            );
        }
    }

    private QuestChapter getChapterOrThrow(Long chapterId) {
        return questChapterRepository.findById(chapterId)
            .orElseThrow(() -> new QuestChapterNotFoundException("존재하지 않는 챕터입니다. id=" + chapterId));
    }
}
