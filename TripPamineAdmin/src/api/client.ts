// 관리자 API 공통 클라이언트.
// - JWT 자동 첨부
// - JWT exp 만료 선제 감지
// - 인증 API의 401 응답 시 세션 정리 후 로그인 화면 이동
const TOKEN_KEY = "adminToken"
const MAX_TIMER_DELAY = 2_147_483_647
let redirectInProgress = false

interface JwtPayload {
  exp?: number
}

function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const payloadPart = token.split(".")[1]
    if (!payloadPart) return null

    const base64 = payloadPart.replace(/-/g, "+").replace(/_/g, "/")
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")
    const binary = window.atob(padded)
    const bytes = Uint8Array.from(binary, (character) =>
        character.charCodeAt(0),
    )
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtPayload
  } catch {
    return null
  }
}

export function getAdminToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAdminToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
  sessionStorage.removeItem("adminSessionExpired")
  redirectInProgress = false
}

export function clearAdminToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function getAdminTokenExpirationMillis(token: string): number | null {
  const payload = decodeJwtPayload(token)
  return typeof payload?.exp === "number" ? payload.exp * 1000 : null
}

export function isAdminTokenExpired(
    token: string,
    currentTime: number = Date.now(),
): boolean {
  const expiration = getAdminTokenExpirationMillis(token)
  return expiration == null || expiration <= currentTime
}

export function expireAdminSession(): void {
  clearAdminToken()
  sessionStorage.setItem("adminSessionExpired", "true")

  if (redirectInProgress || window.location.pathname === "/login") {
    return
  }

  redirectInProgress = true
  window.location.replace("/login?reason=session-expired")
}

export function getSafeTimerDelay(expirationMillis: number): number {
  return Math.min(
      Math.max(0, expirationMillis - Date.now()),
      MAX_TIMER_DELAY,
  )
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
  auth?: boolean
}

export async function apiRequest<T>(
    path: string,
    options: RequestOptions = {},
): Promise<T> {
  const { method = "GET", body, auth = true } = options
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  }

  if (auth) {
    const token = getAdminToken()
    if (token && isAdminTokenExpired(token)) {
      expireAdminSession()
      throw new ApiError("관리자 로그인이 만료되었습니다.", 401)
    }
    if (token) headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  if (response.status === 204) {
    return undefined as T
  }

  const data = await response.json().catch(() => null)

  if (!response.ok) {
    if (auth && response.status === 401) {
      expireAdminSession()
    }

    const message =
        data?.message ??
        (response.status === 401
            ? "관리자 로그인이 만료되었습니다."
            : "요청 처리 중 오류가 발생했습니다.")
    throw new ApiError(message, response.status)
  }

  return data as T
}
