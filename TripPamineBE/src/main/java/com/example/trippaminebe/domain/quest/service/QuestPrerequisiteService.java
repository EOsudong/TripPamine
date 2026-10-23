package com.example.trippaminebe.domain.quest.service;

import com.example.trippaminebe.domain.quest.dto.response.QuestPrerequisiteResponse;
import com.example.trippaminebe.domain.quest.dto.response.QuestResponse;
import com.example.trippaminebe.domain.quest.entity.Quest;
import com.example.trippaminebe.domain.quest.entity.QuestPrerequisite;
import com.example.trippaminebe.domain.quest.entity.QuestPrerequisiteId;
import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.entity.QuestType;
import com.example.trippaminebe.domain.quest.exception.QuestDeleteConflictException;
import com.example.trippaminebe.domain.quest.exception.QuestNotFoundException;
import com.example.trippaminebe.domain.quest.repository.QuestPrerequisiteRepository;
import com.example.trippaminebe.domain.quest.repository.QuestRepository;
import com.example.trippaminebe.domain.quest.repository.QuestStepRepository;
import com.example.trippaminebe.domain.quest.repository.UserQuestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 독립 퀘스트 간의 선행 관계를 담당한다. 기존 CHAIN 단계(QUEST_STEPS)와 분리한다.
 * 이번 단계는 모든 선행 퀘스트 성공을 요구하는 AND 규칙만 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QuestPrerequisiteService {

    private final QuestPrerequisiteRepository prerequisiteRepository;
    private final QuestRepository questRepository;
    private final QuestStepRepository questStepRepository;
    private final UserQuestLogRepository userQuestLogRepository;

    @Transactional(readOnly = true)
    public List<QuestPrerequisiteResponse> findAll(Long questId) {
        requireQuest(questId);
        List<Long> requiredIds = prerequisiteRepository
                .findById_QuestIdOrderById_PrerequisiteQuestIdAsc(questId)
                .stream().map(link -> link.getId().getPrerequisiteQuestId()).toList();
        if (requiredIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Quest> questsById = new HashMap<>();
        questRepository.findAllById(requiredIds).forEach(quest -> questsById.put(quest.getQuestId(), quest));
        return requiredIds.stream()
                .map(id -> questsById.get(id))
                .filter(quest -> quest != null)
                .map(quest -> new QuestPrerequisiteResponse(
                        quest.getQuestId(), quest.getQuestName(), quest.getIsActive()))
                .toList();
    }

    public List<QuestPrerequisiteResponse> add(Long questId, Long prerequisiteQuestId) {
        if (prerequisiteQuestId == null || prerequisiteQuestId <= 0) {
            throw new IllegalArgumentException("올바른 선행 퀘스트 ID를 입력해주세요.");
        }
        if (questId.equals(prerequisiteQuestId)) {
            throw new IllegalArgumentException("자기 자신을 선행 퀘스트로 지정할 수 없습니다.");
        }

        // 두 퀘스트를 ID 순서로 잠가 역방향 동시 등록과 삭제 충돌을 완화한다.
        long first = Math.min(questId, prerequisiteQuestId);
        long second = Math.max(questId, prerequisiteQuestId);
        Quest firstQuest = requireLockedQuest(first);
        Quest secondQuest = requireLockedQuest(second);
        Quest targetQuest = questId.equals(first) ? firstQuest : secondQuest;
        Quest requiredQuest = prerequisiteQuestId.equals(first) ? firstQuest : secondQuest;

        QuestPrerequisiteId linkId = new QuestPrerequisiteId(questId, prerequisiteQuestId);
        if (prerequisiteRepository.existsById(linkId)) {
            return findAll(questId); // 동일한 관계를 중복 저장하지 않음
        }
        // 기존 시작 이력이 있는 후속 퀘스트에 새 잠금 조건을 소급 적용하지 않는다.
        if (userQuestLogRepository.existsByQuest_QuestId(questId)) {
            throw new QuestDeleteConflictException(
                    "이미 시작한 사용자가 있는 퀘스트에는 선행 조건을 추가할 수 없습니다. 새 퀘스트를 복제해주세요.");
        }
        // 공개 중인 후속 퀘스트에 현재 완료 불가능한 선행 퀘스트를 연결하지 않는다.
        if (Boolean.TRUE.equals(targetQuest.getIsActive())) {
            boolean playable = Boolean.TRUE.equals(requiredQuest.getIsActive())
                    && (requiredQuest.getQuestType() == QuestType.LOCATION
                        || (requiredQuest.getQuestType() == QuestType.CHAIN
                            && questStepRepository.countByQuest_QuestId(prerequisiteQuestId) > 0));
            if (!playable) {
                throw new IllegalArgumentException(
                        "공개 중인 퀘스트에는 실제 수락·완료 가능한 선행 퀘스트만 연결할 수 있습니다.");
            }
        }
        assertNoCycle(questId, prerequisiteQuestId);
        prerequisiteRepository.save(new QuestPrerequisite(questId, prerequisiteQuestId));
        return findAll(questId);
    }

    public void remove(Long questId, Long prerequisiteQuestId) {
        requireLockedQuest(questId);
        if (!prerequisiteRepository.existsById(new QuestPrerequisiteId(questId, prerequisiteQuestId))) {
            throw new IllegalArgumentException("등록되지 않은 선행 퀘스트 관계입니다.");
        }
        prerequisiteRepository.deleteById(new QuestPrerequisiteId(questId, prerequisiteQuestId));
    }

    @Transactional(readOnly = true)
    public void assertStartAllowed(Long questId, Long userId) {
        List<Long> missing = missingRequiredIds(questId, successfulQuestIds(userId));
        if (!missing.isEmpty()) {
            List<String> names = questRepository.findAllById(missing).stream()
                    .map(q -> q.getQuestName() + " (#" + q.getQuestId() + ")")
                    .toList();
            throw new IllegalArgumentException("선행 퀘스트를 먼저 완료해주세요: " + String.join(", ", names));
        }
    }

    @Transactional(readOnly = true)
    public List<QuestResponse> toUserResponses(List<Quest> quests, Long userId, Integer viewerLevel) {
        if (quests.isEmpty()) {
            return List.of();
        }
        List<Long> questIds = quests.stream().map(Quest::getQuestId).toList();
        Map<Long, List<Long>> dependencies = new HashMap<>();
        prerequisiteRepository.findById_QuestIdIn(questIds).forEach(link ->
                dependencies.computeIfAbsent(link.getId().getQuestId(), ignored -> new ArrayList<>())
                        .add(link.getId().getPrerequisiteQuestId()));
        Set<Long> completed = successfulQuestIds(userId);
        Set<Long> alreadyStarted = new HashSet<>(userQuestLogRepository.findQuestIdsByUserId(userId));

        return quests.stream().map(quest -> {
            List<Long> required = dependencies.getOrDefault(quest.getQuestId(), List.of());
            // 이미 수락한 사용자에게 변경된 선행 조건을 새로 적용하지 않는다.
            List<Long> missing = alreadyStarted.contains(quest.getQuestId())
                    ? List.of()
                    : required.stream().filter(id -> !completed.contains(id)).toList();
            return QuestResponse.from(quest, viewerLevel).withPrerequisites(required, missing);
        }).toList();
    }

    @Transactional(readOnly = true)
    public QuestResponse toUserResponse(Quest quest, Long userId, Integer viewerLevel) {
        return toUserResponses(List.of(quest), userId, viewerLevel).get(0);
    }

    @Transactional(readOnly = true)
    public boolean hasLinks(Long questId) {
        return prerequisiteRepository.existsById_QuestId(questId)
                || prerequisiteRepository.existsById_PrerequisiteQuestId(questId);
    }

    private Set<Long> successfulQuestIds(Long userId) {
        return new HashSet<>(userQuestLogRepository.findQuestIdsByUserIdAndStatus(userId, QuestStatus.SUCCESS));
    }

    private List<Long> missingRequiredIds(Long questId, Set<Long> completed) {
        return prerequisiteRepository.findById_QuestIdOrderById_PrerequisiteQuestIdAsc(questId)
                .stream().map(link -> link.getId().getPrerequisiteQuestId())
                .filter(id -> !completed.contains(id)).toList();
    }

    private void assertNoCycle(Long questId, Long prerequisiteQuestId) {
        // 후속 → 선행 방향으로 따라가며 자신에게 도달하면 순환 관계다.
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(prerequisiteQuestId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (current.equals(questId)) {
                throw new IllegalArgumentException("선행 관계가 순환됩니다. 이미 연결된 퀘스트 경로를 확인해주세요.");
            }
            if (!visited.add(current)) {
                continue;
            }
            prerequisiteRepository.findById_QuestIdOrderById_PrerequisiteQuestIdAsc(current)
                    .forEach(link -> queue.addLast(link.getId().getPrerequisiteQuestId()));
        }
    }

    private Quest requireQuest(Long questId) {
        return questRepository.findById(questId).orElseThrow(
                () -> new QuestNotFoundException("존재하지 않는 퀘스트입니다. id=" + questId));
    }

    private Quest requireLockedQuest(Long questId) {
        return questRepository.findByIdForUpdate(questId).orElseThrow(
                () -> new QuestNotFoundException("존재하지 않는 퀘스트입니다. id=" + questId));
    }
}
