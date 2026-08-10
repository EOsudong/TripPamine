package com.example.trippaminebe.domain.user.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class UserDto {
  private String email;
  private String password;
  private String userName;
  private String phoneNumber;
  private String name;


}
