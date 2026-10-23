package com.example.trippaminebe.domain.music.dto;

public record MusicDto(

        String title,

        String artist,

        String mood,

        String youtubeUrl,

        String spotifyUrl,

        String imageUrl

) {
}