
import { api } from "./axios";

// 1. DTO (Date Transfer Object) 타입 정의

// 회원가입 요청 데이터 타입 (백엔드 DTO 필드명과 일치시키기)
export interface SignupRequest {
  email: string;
  password: string;
  name: string;
  userName: string;
  phoneNumber: string;
}

// 회원가입 응답 데이터 타입 (백엔드 Return 형태에 맞춰 수정)
export interface SignupResponse {
  userId: number;
  email: string;
  userName: string;
  accessToken: string;
  tokenType: string;
}

// 로그인 요청 데이터 타입 (백엔드 DTO 필드명과 일치시키기)
export interface LoginRequest {
  email: string;
  password: string;
}

// 로그인 응답 데이터 타입 (백엔드 Return 형태에 맞춰 수정)
export interface LoginResponse {
  userId: number;
  accessToken: string;
  tokenType: string;
}

// 2. API 함수 정의
/**
 * 회원가입 API
 * @param signupData 회원가입 폼 데이터 (email, password, name, userName, phoneNumber)
 * @returns 회원가입 성공 시 응답 데이터 (userId, email, userName, accessToken)
 */
export const signupApi = async (
  signupData: SignupRequest,
): Promise<SignupResponse> => {
  // POST /users/auth/signup 엔드포인트 호출
  const response = await api.post<SignupResponse>("/users/auth/signup", signupData);
  return response.data;
};

/**
 * 이메일 중복 확인 API
 * @param email 확인할 이메일 주소
 * @returns 이메일 사용 가능 여부 (true: 사용 가능, false: 이미 존재)
 */
export const checkEmailApi = async (email: string): Promise<boolean> => {
  // GET /users/auth/check-email 엔드포인트 호출
  const response = await api.get< boolean >("/users/auth/check-email", {
    params: { email },
  });
  return response.data;
};

/**
 * 로그인 API
 * @param loginData 로그인 폼 데이터 (email, password)
 * @returns 로그인 성공 시 응답 데이터 (email, accessToken)
 */
export const loginApi = async (
  loginData: LoginRequest
): Promise<LoginResponse> => {
  // POST /users/auth/login 엔드포인트 호출
  const response = await api.post<LoginResponse>("/users/auth/login", loginData);
  return response.data;
};
