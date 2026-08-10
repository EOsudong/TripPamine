package com.example.trippaminebe.domain.user.dto.request;

import com.example.trippaminebe.domain.user.entity.UserGrade;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequestDto {
  private Long userId;
  private String email;
  private String name;
  private String userName;
  private String phoneNumber;
  private Long totalPoints;
  private UserGrade grade;
  private String subscribeYn;
  private String profileImageUrl;
  private UserStatus status;
  private LocalDateTime createDate;
}
