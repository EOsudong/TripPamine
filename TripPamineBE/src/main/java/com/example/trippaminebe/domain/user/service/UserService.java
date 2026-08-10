package com.example.trippaminebe.domain.user.service;

import com.example.trippaminebe.domain.user.dto.UserDto;
import com.example.trippaminebe.domain.user.dto.request.LoginRequestDto;
import com.example.trippaminebe.domain.user.dto.request.SignUpRequestDto;
import com.example.trippaminebe.domain.user.dto.request.UpdateRequestDto;
import com.example.trippaminebe.domain.user.dto.response.LoginResponseDto;
import com.example.trippaminebe.domain.user.dto.response.SignUpResponseDto;
import com.example.trippaminebe.domain.user.dto.response.UserResponseDto;

import java.util.Map;

public interface UserService {

  // 로그인
  LoginResponseDto login(LoginRequestDto request);

  // 회원가입
  SignUpResponseDto register(SignUpRequestDto request);

  // 회원조회
  UserResponseDto getUserInfo(UserResponseDto userResponseDto);

  // 프로필 수정
  UserResponseDto updateProfile(Long userId, UpdateRequestDto request);

  // 회원탈퇴
  void withdraw(Long userId);

}
