const ACCESS_TOKEN_KEY = "accessToken"
const USER_ID_KEY = "userId"
const USER_KEY = "user"
const SESSION_EXPIRED_KEY = "userSessionExpired"
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

export function getUserAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function storeUserSession(accessToken: string, userId: number): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(USER_ID_KEY, String(userId))
    sessionStorage.removeItem(SESSION_EXPIRED_KEY)
    redirectInProgress = false
}

export function clearUserSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(USER_ID_KEY)
    localStorage.removeItem(USER_KEY)
}

export function getUserTokenExpirationMillis(token: string): number | null {
    const payload = decodeJwtPayload(token)
    return typeof payload?.exp === "number" ? payload.exp * 1000 : null
}

export function isUserTokenExpired(
    token: string,
    currentTime: number = Date.now(),
): boolean {
    const expiration = getUserTokenExpirationMillis(token)
    return expiration == null || expiration <= currentTime
}

export function getSafeUserTimerDelay(expirationMillis: number): number {
    return Math.min(
        Math.max(0, expirationMillis - Date.now()),
        MAX_TIMER_DELAY,
    )
}

export function expireUserSession(): void {
    clearUserSession()
    sessionStorage.setItem(SESSION_EXPIRED_KEY, "true")

    if (redirectInProgress || window.location.pathname === "/login") {
        return
    }

    redirectInProgress = true
    window.location.replace("/login?reason=session-expired")
}
