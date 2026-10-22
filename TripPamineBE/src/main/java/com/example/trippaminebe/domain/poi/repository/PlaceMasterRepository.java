package com.example.trippaminebe.domain.poi.repository;

import com.example.trippaminebe.domain.poi.entity.PlaceMaster;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

/** 외부 Kakao 장소 ID들을 한 번에 내부 장소 마스터에 대응시킨다. */
public interface PlaceMasterRepository extends Repository<PlaceMaster, Long> {
    List<PlaceMaster> findBySourceTypeAndSourcePlaceIdIn(String sourceType, Collection<String> sourcePlaceIds);
}
