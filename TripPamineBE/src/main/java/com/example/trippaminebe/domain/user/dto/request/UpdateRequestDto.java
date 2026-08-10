package com.example.trippaminebe.domain.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRequestDto {
  //프로필 수정
  private String phoneNumber;
  private String userName;
  private String profileImage;
}
