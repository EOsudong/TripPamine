package com.example.trippaminebe.domain.music.controller;

import com.example.trippaminebe.domain.music.dto.TravelMusicRequestDto;
import com.example.trippaminebe.domain.music.dto.TravelMusicResponseDto;
import com.example.trippaminebe.domain.music.service.TravelMusicRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class TravelMusicController {

    private final TravelMusicRecommendationService recommendationService;


    @PostMapping("/recommend")
    public ResponseEntity<TravelMusicResponseDto> recommend(
            @RequestBody TravelMusicRequestDto request
    ) {

        return ResponseEntity.ok(
                recommendationService.recommend(request)
        );
    }
}