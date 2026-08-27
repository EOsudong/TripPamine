package com.example.trippaminebe.domain.mysterytour.service;

import com.example.trippaminebe.domain.mysterytour.client.KakaoLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import com.example.trippaminebe.domain.mysterytour.exception.MysteryTourLocationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** AI가 만든 장소 검색어를 Kakao Local의 검증된 좌표로 변환한다. */
@Service
@RequiredArgsConstructor
public class MysteryQuestLocationResolver {

    private final KakaoLocalClient kakaoLocalClient;

    public KakaoPlace resolve(String destination, String placeKeyword) {
        if (placeKeyword == null || placeKeyword.isBlank()) {
            throw new MysteryTourLocationException(
                    "GPS 퀘스트의 장소 검색어가 없습니다. 투어를 다시 생성해주세요."
            );
        }

        String normalizedKeyword = placeKeyword.strip();
        Optional<KakaoPlace> place = kakaoLocalClient.searchFirstPlace(normalizedKeyword);

        // AI가 행정구역을 빼먹어 원래 검색이 실패한 경우에만 목적지를 붙여 한 번 더 검색한다.
        String combinedKeyword = destination == null || destination.isBlank()
                ? normalizedKeyword
                : destination.strip() + " " + normalizedKeyword;
        if (place.isEmpty() && !combinedKeyword.equals(normalizedKeyword)) {
            place = kakaoLocalClient.searchFirstPlace(combinedKeyword);
        }

        return place.orElseThrow(() -> new MysteryTourLocationException(
                "실제 위치를 찾을 수 없는 장소입니다: " + normalizedKeyword
        ));
    }
}
