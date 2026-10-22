package com.example.trippaminebe.domain.poi.repository;

import com.example.trippaminebe.domain.poi.entity.PlaceTagEntity;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

/** PLACE_ID로 태그를 일괄 조회해 장소마다 반복 쿼리가 발생하지 않게 한다. */
public interface PlaceTagRepository extends Repository<PlaceTagEntity, Long> {
    List<PlaceTagEntity> findByPlaceIdIn(Collection<Long> placeIds);
}
