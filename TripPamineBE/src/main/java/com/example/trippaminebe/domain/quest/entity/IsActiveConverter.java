package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Boolean <-> DB의 CHAR(1) 'Y'/'N' 값을 자동으로 서로 변환해주는 JPA 컨버터.
// account.entity.LinkStatusConverter와 동일한 목적 - Quest 도메인의 IS_ACTIVE 플래그 전용.
@Converter(autoApply = false)
public class IsActiveConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return "Y";
        }
        return attribute ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        return "Y".equalsIgnoreCase(dbData);
    }
}
