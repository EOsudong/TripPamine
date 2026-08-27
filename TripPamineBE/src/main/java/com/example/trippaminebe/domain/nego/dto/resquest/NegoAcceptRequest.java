package com.example.trippaminebe.domain.nego.dto.resquest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotNull;

// AI 네고 핫딜 수락(결제) 요청 DTO
@Getter
@Setter
@Schema(description = "AI 네고 핫딜 수락 요청 DTO")
public class NegoAcceptRequest {

	@NotNull(message = "결제할 계좌를 선택해주세요.")
	@Schema(description = "결제에 사용할 USER_ACCOUNTS.ACCOUNT_ID", example = "1")
	private Long accountId;
}