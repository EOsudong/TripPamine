package com.example.trippaminebe.domain.account.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int AES_256_KEY_LENGTH_BYTES = 32;
  private static final String KEY_NAME = "ENCRYPT_SECRET_KEY";

  @Override
  public String convertToDatabaseColumn(String attribute) {
    return attribute == null ? null : encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    return dbData == null ? null : decrypt(dbData);
  }

  private String encrypt(String plainText) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      new SecureRandom().nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          secretKey(),
          new GCMParameterSpec(TAG_LENGTH_BITS, iv)
      );

      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

      return Base64.getEncoder().encodeToString(combined);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("민감정보 암호화에 실패했습니다.", e);
    }
  }

  private String decrypt(String cipherTextBase64) {
    try {
      byte[] combined = Base64.getDecoder().decode(cipherTextBase64);
      if (combined.length <= IV_LENGTH_BYTES) {
        throw new IllegalStateException("저장된 암호문 형식이 올바르지 않습니다.");
      }

      byte[] iv = new byte[IV_LENGTH_BYTES];
      byte[] cipherText = new byte[combined.length - IV_LENGTH_BYTES];
      System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
      System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE,
          secretKey(),
          new GCMParameterSpec(TAG_LENGTH_BITS, iv)
      );

      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("민감정보 복호화에 실패했습니다.", e);
    }
  }

  private SecretKeySpec secretKey() {
    // TripPamineBeApplication은 .env 값을 System.setProperty()로 등록한다.
    // IDE/서버에서 직접 지정한 OS 환경변수도 사용할 수 있도록 두 위치를 모두 확인한다.
    String base64Key = firstNonBlank(
        System.getProperty(KEY_NAME),
        System.getenv(KEY_NAME)
    );

    if (base64Key == null) {
      throw new IllegalStateException(
          KEY_NAME + "가 설정되지 않았습니다. TripPamineBE/.env 또는 실행 환경변수를 확인하세요."
      );
    }

    final byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(KEY_NAME + "는 올바른 Base64 문자열이어야 합니다.", e);
    }

    if (keyBytes.length != AES_256_KEY_LENGTH_BYTES) {
      throw new IllegalStateException(
          KEY_NAME + "는 Base64 디코딩 후 정확히 32바이트여야 합니다. 현재: "
              + keyBytes.length + "바이트"
      );
    }

    return new SecretKeySpec(keyBytes, "AES");
  }

  private String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    return null;
  }
}