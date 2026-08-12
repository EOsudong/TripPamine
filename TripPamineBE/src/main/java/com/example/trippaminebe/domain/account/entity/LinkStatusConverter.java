package com.example.trippaminebe.domain.account.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// LinkStatus(Enum) <-> DB의 CHAR(1) 'Y'/'N' 값을 자동으로 서로 변환해주는 JPA 컨버터.
// Account.linkStatus 필드에 @Convert(converter = LinkStatusConverter.class)로 연결되어 있음
@Converter(autoApply = false)
public class LinkStatusConverter implements AttributeConverter<LinkStatus, String> {

  // 자바 객체(LinkStatus) -> DB에 저장할 값('Y'/'N')
  @Override
  public String convertToDatabaseColumn(LinkStatus attribute) {
    return attribute == null ? null : attribute.getCode();
  }

  // DB에서 읽은 값('Y'/'N') -> 자바 객체(LinkStatus)
  @Override
  public LinkStatus convertToEntityAttribute(String dbData) {
    return dbData == null ? null : LinkStatus.fromCode(dbData);
  }
}
