package com.example.trippaminebe.domain.music.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelMusicRequestDto {

    private String placeType;
    private String weather;
    private String timeOfDay;
    private String travelStyle;
    private String preferredGenre;
}