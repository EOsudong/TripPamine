import { apiRequest } from "./client"
import type { AdminLoginRequest, AdminLoginResponse, AdminProfile } from "../types/api"

// POST /admin/auth/login - 로그인은 아직 토큰이 없으므로 auth: false
export function adminLogin(request: AdminLoginRequest): Promise<AdminLoginResponse> {
  return apiRequest<AdminLoginResponse>("/admin/auth/login", {
    method: "POST",
    body: request,
    auth: false,
  })
}

// GET /admin/{adminId} - 관리자 프로필 조회
export function getAdminProfile(adminId: number): Promise<AdminProfile> {
  return apiRequest<AdminProfile>(`/admin/${adminId}`)
}
