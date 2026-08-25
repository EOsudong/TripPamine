package com.example.trippaminebe.domain.mysterytour.controller;

import com.example.trippaminebe.domain.mysterytour.dto.MysteryTourCreateRequest;
import com.example.trippaminebe.domain.mysterytour.dto.MysteryTourCreateResponse;
import com.example.trippaminebe.domain.mysterytour.service.MysteryTourService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.trippaminebe.domain.mysterytour.dto.MysteryQuestResponse;

@RestController
@RequestMapping("/mystery-tours")
@RequiredArgsConstructor
public class MysteryTourController {

    private final MysteryTourService mysteryTourService;

    @PostMapping
    public ResponseEntity<MysteryTourCreateResponse> createMysteryTour(
            @RequestParam Long userId,
            @RequestBody MysteryTourCreateRequest request
    ) {

        MysteryTourCreateResponse response =
                mysteryTourService.createMysteryTour(userId, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    public ResponseEntity<MysteryTourCreateResponse> getActiveMysteryTour(
            @RequestParam Long userId
    ) {

        MysteryTourCreateResponse response =
                mysteryTourService.getActiveMysteryTour(userId);

        if (response == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{mysteryTourId}/start")
    public ResponseEntity<Void> startMysteryTour(
            @PathVariable Long mysteryTourId
    ) {
        mysteryTourService.startMysteryTour(mysteryTourId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{mysteryTourId}")
    public ResponseEntity<Void> cancelMysteryTour(
            @PathVariable Long mysteryTourId
    ) {
        mysteryTourService.cancelMysteryTour(mysteryTourId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{mysteryTourId}/quests/current")
    public ResponseEntity<MysteryQuestResponse> getCurrentQuest(
            @PathVariable Long mysteryTourId
    ) {

        MysteryQuestResponse response =
                mysteryTourService.getCurrentQuest(mysteryTourId);

        if (response == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{mysteryTourId}/quests/{mysteryQuestId}/complete")
    public ResponseEntity<MysteryQuestResponse> completeQuest(
            @PathVariable Long mysteryTourId,
            @PathVariable Long mysteryQuestId
    ) {

        MysteryQuestResponse response =
                mysteryTourService.completeQuest(
                        mysteryTourId,
                        mysteryQuestId
                );

        if (response == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }
}