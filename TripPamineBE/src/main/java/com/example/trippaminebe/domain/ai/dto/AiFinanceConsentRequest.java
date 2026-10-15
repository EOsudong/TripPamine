
package com.example.trippaminebe.domain.ai.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 금융 정보 활용 설정을 변경할 때 받는 값.
 *
 * true  : 사용자가 허용함
 * false : 사용자가 사용 중지를 선택함
 *
 * 값을 전달하지 않으면 잘못된 요청으로 처리한다.
 */
public record AiFinanceConsentRequest(

		@NotNull
		Boolean enabled

) {
}
