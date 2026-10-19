package com.example.trippaminebe.domain.user.service;

import com.example.trippaminebe.domain.user.dto.request.NicknameUpdateRequestDto;
import com.example.trippaminebe.domain.user.dto.response.NicknameCheckResponseDto;

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
  UserResponseDto getUserInfo(Long userId);

  // 프로필 수정
  UserResponseDto updateProfile(Long userId, UpdateRequestDto request);

  // 회원탈퇴
  void withdraw(Long userId);

  // 이메일 중복 확인
  Boolean isEmailAvailable(String email);

  // [닉네임 변경] 닉네임 사용 가능 여부 (형식 + 중복). userId가 있으면 본인 현재 닉네임은 사용 가능으로 본다.
  NicknameCheckResponseDto checkNickname(String userName, Long userId);

  // [닉네임 변경] 내 닉네임 변경
  UserResponseDto changeNickname(Long userId, NicknameUpdateRequestDto request);

}