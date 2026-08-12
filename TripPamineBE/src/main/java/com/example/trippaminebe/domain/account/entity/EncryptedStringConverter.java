package com.example.trippaminebe.domain.account.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * ACCOUNT_NUMBER, FINTECH_USE_NUM 등 민감 필드 암호화 저장용 Converter.
 *
 * TODO: 실제 암호화 로직 연결 필요.
 *   - 키 관리: 소스코드에 키를 직접 넣지 말고 환경변수/Vault/KMS 등에서 주입
 *   - 알고리즘: AES-GCM 권장 (팀 보안 정책 확인)
 *   - 이 클래스는 자리만 잡아둔 상태이며, 아래 encrypt/decrypt는
 *     실제 암복호화 유틸(예: AesGcmEncryptor 같은 별도 컴포넌트)로 교체해야 함
 *
 * autoApply=false 로 두고 엔티티 필드에 @Convert(converter = EncryptedStringConverter.class)
 * 를 명시적으로 붙이는 방식 (실수로 다른 문자열 필드까지 암호화되는 것 방지)
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  // 저장 시 호출됨: 평문 -> 암호문으로 변환해서 DB에 저장
  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    // TODO: 실제 암호화 로직으로 교체
    return encrypt(attribute);
  }

  // 조회 시 호출됨: DB의 암호문 -> 평문으로 복원해서 자바 객체에 채움
  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    // TODO: 실제 복호화 로직으로 교체
    return decrypt(dbData);
  }

  private String encrypt(String plainText) {
    // placeholder - 팀 암호화 정책 확정 후 구현
    throw new UnsupportedOperationException("암호화 로직 미구현: 팀 보안 정책 확인 후 구현 필요");
  }

  private String decrypt(String cipherText) {
    // placeholder - 팀 암호화 정책 확정 후 구현
    throw new UnsupportedOperationException("복호화 로직 미구현: 팀 보안 정책 확인 후 구현 필요");
  }
}
