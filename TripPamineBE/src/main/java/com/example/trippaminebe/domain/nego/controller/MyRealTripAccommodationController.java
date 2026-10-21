package com.example.trippaminebe.domain.nego.controller;

import com.example.trippaminebe.domain.nego.dto.request.MyRealTripAccommodationSearchRequest;
import com.example.trippaminebe.domain.nego.service.MyRealTripAccommodationSearchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기존 투어·티켓 API와 분리된 마이리얼트립 숙박 검색 엔드포인트. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/nego/partners/myrealtrip")
public class MyRealTripAccommodationController {

    private final MyRealTripAccommodationSearchService searchService;

    @PostMapping(value = "/accommodations/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "마이리얼트립 숙소 검색(체크인·체크아웃·성인 인원 적용)")
    public ResponseEntity<String> search(
            @Valid @RequestBody MyRealTripAccommodationSearchRequest request
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(searchService.search(request));
    }
}
