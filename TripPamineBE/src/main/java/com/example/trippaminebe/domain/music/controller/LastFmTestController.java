package com.example.trippaminebe.domain.music.controller;

import com.example.trippaminebe.domain.music.dto.LastFmTrackDto;
import com.example.trippaminebe.domain.music.service.LastFmMusicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class LastFmTestController {

    private final LastFmMusicService lastFmMusicService;


    @GetMapping("/lastfm-test")
    public ResponseEntity<List<LastFmTrackDto>> testLastFm(
            @RequestParam(defaultValue = "indie") String tag
    ) {

        List<LastFmTrackDto> tracks =
                lastFmMusicService.getTopTracksByTag(
                        tag,
                        10
                );


        return ResponseEntity.ok(
                tracks
        );
    }
}