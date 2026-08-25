package com.example.trippaminebe.domain.mysterytour.service;

import com.example.trippaminebe.domain.mysterytour.dto.MysteryTourCreateRequest;
import com.example.trippaminebe.domain.mysterytour.dto.MysteryTourCreateResponse;
import com.example.trippaminebe.domain.mysterytour.entity.MysteryQuest;
import com.example.trippaminebe.domain.mysterytour.entity.MysteryTour;
import com.example.trippaminebe.domain.mysterytour.repository.MysteryQuestRepository;
import com.example.trippaminebe.domain.mysterytour.repository.MysteryTourRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.trippaminebe.domain.mysterytour.dto.MysteryQuestResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MysteryTourService {

    private final MysteryTourRepository mysteryTourRepository;
    private final MysteryQuestRepository mysteryQuestRepository;
    private final UserRepository userRepository;
    private final MysteryTourAiService mysteryTourAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public MysteryTourCreateResponse createMysteryTour(

            Long userId,
            MysteryTourCreateRequest request
    ) {
        boolean alreadyExists =
                mysteryTourRepository
                        .findFirstByUser_IdAndStatusOrderByCreatedAtDesc(
                                userId,
                                "READY"
                        )
                        .isPresent();

        if (alreadyExists) {
            throw new IllegalStateException(
                    "이미 진행 중인 미스터리 투어가 있습니다."
            );
        }
        // 1. 로그인 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다. userId=" + userId
                        )
                );

        try {

            // 2. OpenAI 호출
            String aiJson =
                    mysteryTourAiService.generateMysteryTour(request);

            JsonNode root =
                    objectMapper.readTree(aiJson);

            String destination =
                    root.path("destination").asText();

            // 3. 미스터리 투어 저장
            MysteryTour mysteryTour =
                    MysteryTour.builder()
                            .user(user)
                            .travelDate(request.getTravelDate())
                            .travelDays(request.getTravelDays())
                            .peopleCount(request.getPeopleCount())
                            .budget(request.getBudget())
                            .radiusKm(request.getRadiusKm())
                            .departure(request.getDeparture())
                            .travelStyle(request.getTravelStyle())
                            .destination(destination)
                            .aiPlanJson(aiJson)
                            .status("READY")
                            .build();

            MysteryTour savedTour =
                    mysteryTourRepository.save(mysteryTour);

            // 4. AI 퀘스트 저장
            JsonNode quests =
                    root.path("quests");

            int questCount = 0;

            for (JsonNode questNode : quests) {

                int order =
                        questNode.path("order").asInt();

                MysteryQuest quest =
                        MysteryQuest.builder()
                                .mysteryTour(savedTour)
                                .questOrder(order)
                                .questName(
                                        questNode.path("name").asText()
                                )
                                .questDesc(
                                        questNode.path("description").asText()
                                )
                                .verifyType(
                                        questNode.path("verifyType").asText()
                                )
                                .targetLat(
                                        getDecimal(
                                                questNode,
                                                "targetLat"
                                        )
                                )
                                .targetLng(
                                        getDecimal(
                                                questNode,
                                                "targetLng"
                                        )
                                )
                                .timeLimitMin(
                                        getInteger(
                                                questNode,
                                                "timeLimitMin"
                                        )
                                )
                                .rewardPoint(
                                        questNode
                                                .path("rewardPoint")
                                                .asInt(0)
                                )
                                .status(
                                        order == 1
                                                ? "OPEN"
                                                : "LOCKED"
                                )
                                .build();

                mysteryQuestRepository.save(quest);

                questCount++;
            }

            // 5. 목적지는 일부러 응답에 안 보냄
            return MysteryTourCreateResponse.builder()
                    .mysteryTourId(
                            savedTour.getMysteryTourId()
                    )
                    .travelDate(
                            savedTour.getTravelDate()
                    )
                    .travelDays(
                            savedTour.getTravelDays()
                    )
                    .peopleCount(
                            savedTour.getPeopleCount()
                    )
                    .budget(
                            savedTour.getBudget()
                    )
                    .questCount(questCount)
                    .status(
                            savedTour.getStatus()
                    )
                    .destinationLocked(true)
                    .build();

        } catch (Exception e) {

            throw new RuntimeException(
                    "미스터리 투어 생성 중 오류가 발생했습니다.",
                    e
            );
        }
    }

    private BigDecimal getDecimal(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);

        if (
                value == null ||
                        value.isNull()
        ) {
            return null;
        }

        return value.decimalValue();
    }

    private Integer getInteger(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);

        if (
                value == null ||
                        value.isNull()
        ) {
            return null;
        }

        return value.asInt();
    }

    @Transactional(readOnly = true)
    public MysteryTourCreateResponse getActiveMysteryTour(Long userId) {

        MysteryTour tour = mysteryTourRepository
                .findFirstByUser_IdAndStatusInOrderByCreatedAtDesc(
                        userId,
                        List.of("READY", "STARTED")
                )
                .orElse(null);

        if (tour == null) {
            return null;
        }

        long questCount =
                mysteryQuestRepository
                        .countByMysteryTour_MysteryTourId(
                                tour.getMysteryTourId()
                        );

        return MysteryTourCreateResponse.builder()
                .mysteryTourId(tour.getMysteryTourId())
                .travelDate(tour.getTravelDate())
                .travelDays(tour.getTravelDays())
                .peopleCount(tour.getPeopleCount())
                .budget(tour.getBudget())
                .questCount((int) questCount)
                .status(tour.getStatus())
                .destinationLocked(true)
                .build();
    }

    @Transactional(readOnly = true)
    public MysteryQuestResponse getCurrentQuest(Long mysteryTourId) {

        MysteryQuest quest =
                mysteryQuestRepository
                        .findFirstByMysteryTour_MysteryTourIdAndStatusOrderByQuestOrderAsc(
                                mysteryTourId,
                                "OPEN"
                        )
                        .orElse(null);

        if (quest == null) {
            return null;
        }

        return MysteryQuestResponse.builder()
                .mysteryQuestId(quest.getMysteryQuestId())
                .questOrder(quest.getQuestOrder())
                .questName(quest.getQuestName())
                .questDesc(quest.getQuestDesc())
                .verifyType(quest.getVerifyType())
                .targetLat(quest.getTargetLat())
                .targetLng(quest.getTargetLng())
                .rewardPoint(quest.getRewardPoint())
                .status(quest.getStatus())
                .build();
    }

    @Transactional
    public MysteryQuestResponse completeQuest(
            Long mysteryTourId,
            Long mysteryQuestId
    ) {

        MysteryQuest quest = mysteryQuestRepository
                .findById(mysteryQuestId)
                .orElseThrow(() ->
                        new IllegalArgumentException("퀘스트를 찾을 수 없습니다.")
                );

        if (!quest.getMysteryTour().getMysteryTourId().equals(mysteryTourId)) {
            throw new IllegalArgumentException("해당 투어의 퀘스트가 아닙니다.");
        }

        if (!"OPEN".equals(quest.getStatus())) {
            throw new IllegalStateException("현재 진행할 수 없는 퀘스트입니다.");
        }

        // 현재 퀘스트 완료
        quest.setStatus("COMPLETED");
        quest.setCompletedAt(LocalDateTime.now());

        // 다음 퀘스트 찾기
        MysteryQuest nextQuest = mysteryQuestRepository
                .findFirstByMysteryTour_MysteryTourIdAndQuestOrderGreaterThanOrderByQuestOrderAsc(
                        mysteryTourId,
                        quest.getQuestOrder()
                )
                .orElse(null);

        // 다음 퀘스트가 있으면 OPEN
        if (nextQuest != null) {

            nextQuest.setStatus("OPEN");

            return MysteryQuestResponse.builder()
                    .mysteryQuestId(nextQuest.getMysteryQuestId())
                    .questOrder(nextQuest.getQuestOrder())
                    .questName(nextQuest.getQuestName())
                    .questDesc(nextQuest.getQuestDesc())
                    .verifyType(nextQuest.getVerifyType())
                    .targetLat(nextQuest.getTargetLat())
                    .targetLng(nextQuest.getTargetLng())
                    .rewardPoint(nextQuest.getRewardPoint())
                    .status(nextQuest.getStatus())
                    .build();
        }

        // 다음 퀘스트가 없으면 투어 종료
        MysteryTour tour = mysteryTourRepository
                .findById(mysteryTourId)
                .orElseThrow(() ->
                        new IllegalArgumentException("미스터리 투어를 찾을 수 없습니다.")
                );

        tour.setStatus("COMPLETED");

        return null;
    }

    @Transactional
    public void startMysteryTour(Long mysteryTourId) {

        MysteryTour tour = mysteryTourRepository
                .findById(mysteryTourId)
                .orElseThrow(() ->
                        new IllegalArgumentException("미스터리 투어를 찾을 수 없습니다.")
                );

        tour.setStatus("STARTED");
        tour.setStartedAt(LocalDateTime.now());

        MysteryQuest firstQuest =
                mysteryQuestRepository
                        .findFirstByMysteryTour_MysteryTourIdOrderByQuestOrderAsc(
                                mysteryTourId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException("퀘스트가 존재하지 않습니다.")
                        );

        firstQuest.setStatus("OPEN");
    }

    @Transactional
    public void cancelMysteryTour(Long mysteryTourId) {

        MysteryTour tour = mysteryTourRepository
                .findById(mysteryTourId)
                .orElseThrow(() ->
                        new IllegalArgumentException("미스터리 투어를 찾을 수 없습니다.")
                );

        if (!"READY".equals(tour.getStatus())) {
            throw new IllegalStateException(
                    "이미 시작된 미스터리 투어는 취소할 수 없습니다."
            );
        }

        tour.setStatus("CANCELLED");
    }
}
