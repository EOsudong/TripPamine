// 백엔드 DTO(Admin*Dto.java, AdminUser*Dto.java)와 필드명을 정확히 맞춘 타입 모음.
// 백엔드에서 DTO 필드가 바뀌면 이 파일도 같이 바꿔야 합니다.

// AdminLoginRequestDto
export interface AdminLoginRequest {
  adminLoginId: string
  password: string
}

// AdminLoginResponseDto
export interface AdminLoginResponse {
  adminLoginId: string
  accessToken: string
  tokenType: string
}

// AdminResponseDto
export type AdminRole = "SUPER" | "STAFF"
export type AdminStatus = "ACTIVE" | "SUSPENDED"

export interface AdminProfile {
  adminId: number
  adminLoginId: string
  adminName: string
  role: AdminRole
  status: AdminStatus
  email: string | null
  lastLoginDate: string | null
  createDate: string | null
}

// AdminUserResponseDto
export type UserGrade = "BRONZE" | "SILVER" | "GOLD" | "PLATINUM"
export type UserStatus = "ACTIVE" | "WITHDRAW" | "SUSPENDED" | "SLEEP"

export interface AdminUser {
  userId: number
  email: string
  name: string | null
  userName: string
  phoneNumber: string | null
  grade: UserGrade
  status: UserStatus
  suspendReason: string | null
  createDate: string | null
}

// Spring Data의 Page<T> 응답 구조 (getUserList가 Page<AdminUserResponseDto>를 반환)
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number // 현재 페이지 (0부터 시작)
  size: number
}

// UserSuspendRequestDto
export interface UserSuspendRequest {
  reason: string
}
