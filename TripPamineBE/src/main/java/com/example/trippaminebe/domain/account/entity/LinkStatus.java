package com.example.trippaminebe.domain.account.entity;

/**
 * USER_ACCOUNTS.LINK_STATUS 매핑용 Enum
 * DB에는 CHAR(1) 'Y' / 'N' 으로 저장됨 (CK_ACCOUNTS_LINK_STATUS)
 */
public enum LinkStatus {
  ACTIVE("Y"),   // 연동 활성 상태 - 지금 실시간 조회/이체 가능
  INACTIVE("N"); // 연동 해지 상태 - unlink() 호출 시 이 값으로 바뀜 (soft delete)

  private final String code; // DB에 실제로 저장되는 문자 값 ('Y' 또는 'N')

  LinkStatus(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  // DB에서 읽어온 'Y'/'N' 문자열을 Enum으로 되돌리는 메서드 (LinkStatusConverter에서 사용)
  public static LinkStatus fromCode(String code) {
    for (LinkStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown LINK_STATUS code: " + code);
  }
}
