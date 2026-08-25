// 백엔드와 통신하는 모든 요청이 거쳐가는 공통 함수.
// - 로그인 후 저장해둔 토큰을 자동으로 Authorization 헤더에 실어줌
// - 에러 응답을 일관된 형태로 던져줌 (각 페이지에서 매번 처리 안 해도 되게)
const TOKEN_KEY = "adminToken" // localStorage 키. 일반 사용자용 "userToken"과 절대 겹치지 않게 이름을 분리함

export function getAdminToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAdminToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearAdminToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE" | "PUT"
  body?: unknown
  auth?: boolean // true면 Authorization 헤더 자동 첨부 (기본값 true)
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true } = options

  const headers: Record<string, string> = { "Content-Type": "application/json" }
  if (auth) {
    const token = getAdminToken()
    if (token) headers["Authorization"] = `Bearer ${token}`
  }

  const res = await fetch(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  // 204 No Content처럼 본문이 없는 응답 처리 (예: 회원 정지 API)
  if (res.status === 204) {
    return undefined as T
  }

  const data = await res.json().catch(() => null)

  if (!res.ok) {
    // 백엔드 IllegalArgumentException 메시지가 있으면 그걸 그대로 보여줌
    const message = data?.message ?? "요청 처리 중 오류가 발생했습니다."
    throw new ApiError(message, res.status)
  }

  return data as T
}
