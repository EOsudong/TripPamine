package com.example.trippaminebe.domain.user.dto.response;

import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserGrade;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {
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

  public static UserResponse userResponse(User user) {
    return UserResponse.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .name(user.getName())
        .userName(user.getUserName())
        .phoneNumber(user.getPhoneNumber())
        .totalPoints(user.getTotalPoints())
        .grade(user.getGrade())
        .subscribeYn(user.getSubscribeYn())
        .profileImageUrl(user.getProfileImageUrl())
        .status(user.getStatus())
        .createDate(user.getCreateDate())
        .build();
  }


}
