package com.example.trippaminebe.domain.nego.controller;

import com.example.trippaminebe.domain.nego.dto.request.MyRealTripTourSearchRequest;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripCatalogResponse;
import com.example.trippaminebe.domain.nego.service.MyRealTripCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 검증된 제휴 상품 목록. 기존 원본 검색 API는 그대로 유지한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/nego/partners/myrealtrip")
public class MyRealTripCatalogController {

    private final MyRealTripCatalogService myRealTripCatalogService;

    @PostMapping("/tours/search/normalized")
    @Operation(summary = "마이리얼트립 투어·티켓 검색(정규화된 상품 목록)")
    public ResponseEntity<MyRealTripCatalogResponse> searchTours(
            @Valid @RequestBody MyRealTripTourSearchRequest request
    ) {
        return ResponseEntity.ok(myRealTripCatalogService.searchTours(
                request.keyword().trim(), request.page() == null ? 1 : request.page()));
    }
}