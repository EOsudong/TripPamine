import { apiRequest } from "./client"
import type { AdminUser, Page, UserSuspendRequest } from "../types/api"

// GET /admin/users?page=0&size=20 - 회원 목록 조회
export function getUserList(page: number, size: number = 20): Promise<Page<AdminUser>> {
  return apiRequest<Page<AdminUser>>(`/admin/users?page=${page}&size=${size}`)
}

// PATCH /admin/users/{userId}/suspend - 회원 강제 정지
export function suspendUser(userId: number, request: UserSuspendRequest): Promise<void> {
  return apiRequest<void>(`/admin/users/${userId}/suspend`, {
    method: "PATCH",
    body: request,
  })
}

// PATCH /admin/users/{userId}/unsuspend - 회원 정지 해제
export function unsuspendUser(userId: number): Promise<void> {
  return apiRequest<void>(`/admin/users/${userId}/unsuspend`, { method: "PATCH" })
}