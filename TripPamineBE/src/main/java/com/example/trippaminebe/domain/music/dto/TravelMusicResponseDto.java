package com.example.trippaminebe.domain.music.dto;

import java.util.List;

public record TravelMusicResponseDto(

        String theme,
        String emoji,
        String description,
        List<MusicDto> songs

) {
}