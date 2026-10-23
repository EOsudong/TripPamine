package com.example.trippaminebe.domain.music.dto;

public record LastFmTrackDto(

        String title,

        String artist,

        String sourceTag,

        int rank,

        String imageUrl

) {
}