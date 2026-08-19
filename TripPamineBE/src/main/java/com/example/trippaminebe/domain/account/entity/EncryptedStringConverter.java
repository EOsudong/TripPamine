package com.example.trippaminebe.domain.account.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

// autoApply = false : 이 컨버터를 모든 String 필드에 자동으로 적용하지 않겠다는 뜻.
// (실수로 다른 String 필드까지 암호화되는 걸 방지하기 위한 안전장치).
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  // 사용할 암호화 알고리즘/운영모드/패딩 방식. AES 대칭키 + GCM 모드 + 패딩 없음(GCM은 패딩이 필요 없음)
  private static final String ALGO = "AES/GCM/NoPadding";
  // GCM에서 권장되는 IV(nonce) 길이는 12바이트(96bit) — 이보다 짧거나 길면 보안성이 떨어짐
  private static final int IV_LENGTH_BYTES = 12;
  // 인증 태그(변조 검증용) 길이, 128bit가 GCM 표준 권장값
  private static final int TAG_LENGTH_BIT = 128;
  // 암호화 키를 어느 환경변수에서 읽어올지 이름만 상수로 분리
  private static final String KEY_ENV_NAME = "ENCRYPT_SECRET_KEY";

  // JPA가 엔티티 필드 값을 DB 컬럼에 저장하기 직전에 호출하는 메서드
  // (Java 객체 → DB에 실제로 들어갈 값으로 변환)
  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      // null은 그대로 null로 저장 (암호화 시도조차 하지 않음 — NPE 방지)
      return null;
    }
    return encrypt(attribute);
  }

  // JPA가 DB에서 값을 읽어와 엔티티 필드에 채워넣기 직전에 호출하는 메서드
  // (DB에 저장된 값 → Java 객체로 변환, 위 메서드의 반대 방향)
  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return decrypt(dbData);
  }

  // 평문(plainText)을 AES-256-GCM으로 암호화해서 Base64 문자열로 반환
  private String encrypt(String plainText) {
    try {
      // 1) 이번 암호화 한 번에만 쓸 랜덤 IV 생성.
      //    같은 평문이라도 IV가 다르면 암호문도 매번 달라짐(패턴 노출 방지).
      byte[] iv = new byte[IV_LENGTH_BYTES];
      new SecureRandom().nextBytes(iv);

      // 2) Cipher 객체를 "암호화 모드"로 초기화 — secretKey()로 읽어온 키 + 방금 만든 IV 사용
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

      // 3) 실제 암호화 수행. 결과 뒤쪽에 GCM 인증 태그(128bit)가 자동으로 덧붙여져서 나옴
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      // 4) 복호화할 때 같은 IV가 필요하므로, IV를 암호문 맨 앞에 붙여서 하나의 바이트배열로 합침
      //    (IV 자체는 비밀값이 아니라 공개되어도 안전 — 매번 새로 생성되기만 하면 됨)
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

      // 5) DB(VARCHAR 등 문자열 컬럼)에 그대로 저장할 수 있도록 바이트배열을 Base64 문자열로 인코딩
      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      // Cipher 관련 체크 예외(InvalidKeyException 등)를 매번 호출부에서 처리하기 번거로우니
      // 런타임 예외로 감싸서 던짐. 원본 예외(e)는 cause로 보존해 로그에서 원인 추적 가능.
      throw new IllegalStateException("민감정보 암호화에 실패했습니다.", e);
    }
  }

  // Base64로 인코딩된 암호문을 받아 원래 평문으로 복호화
  private String decrypt(String cipherTextBase64) {
    try {
      // 1) Base64 문자열을 다시 바이트배열로 디코딩 (IV + 암호문이 합쳐진 상태)
      byte[] combined = Base64.getDecoder().decode(cipherTextBase64);

      // 2) 앞의 12바이트는 IV, 나머지는 실제 암호문이므로 다시 분리
      byte[] iv = new byte[IV_LENGTH_BYTES];
      byte[] cipherText = new byte[combined.length - IV_LENGTH_BYTES];
      System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
      System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

      // 3) Cipher를 "복호화 모드"로 초기화 — 암호화할 때와 동일한 키 + 방금 꺼낸 IV 사용
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

      // 4) 복호화 수행. 만약 저장된 값이 중간에 변조됐다면 인증 태그 검증에 실패해서
      //    여기서 AEADBadTagException이 터짐 (위 catch에서 IllegalStateException으로 감싸짐)
      byte[] plain = cipher.doFinal(cipherText);

      return new String(plain, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("민감정보 복호화에 실패했습니다.", e);
    }
  }

  // 환경변수에서 Base64로 인코딩된 32바이트(256bit) 키를 읽어와 AES용 SecretKeySpec으로 변환
  private SecretKeySpec secretKey() {
    String base64Key = System.getenv(KEY_ENV_NAME);
    if (base64Key == null || base64Key.isBlank()) {
      // 키가 아예 설정 안 된 상태로 서버가 뜨면, 계좌 저장/조회 시점에야 에러가 나서
      // 원인 파악이 어려우므로 최대한 친절하게 "무엇을, 어떻게 해결해야 하는지"까지 메시지에 담음
      throw new IllegalStateException(
          "환경변수 " + KEY_ENV_NAME + " 가 설정되지 않았습니다. "
              + "`openssl rand -base64 32` 로 키를 생성한 뒤 실행 환경변수로 등록하세요."
      );
    }
    // Base64 문자열 → 원본 바이트(32바이트) → AES 키 객체로 변환
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    return new SecretKeySpec(keyBytes, "AES");
  }
}