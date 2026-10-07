const ACCESS_TOKEN_KEY = "accessToken"
const USER_ID_KEY = "userId"
const USER_KEY = "user"
const SESSION_EXPIRED_KEY = "userSessionExpired"
const MAX_TIMER_DELAY = 2_147_483_647

let redirectInProgress = false


interface JwtPayload {
    exp?: number
}


// =====================================================
// JWT Payload 디코딩
// =====================================================

function decodeJwtPayload(
    token: string
): JwtPayload | null {

    try {

        const payloadPart =
            token.split(".")[1]

        if (!payloadPart) {
            return null
        }


        const base64 =
            payloadPart
                .replace(/-/g, "+")
                .replace(/_/g, "/")


        const padded =
            base64.padEnd(
                Math.ceil(base64.length / 4) * 4,
                "="
            )


        const binary =
            window.atob(padded)


        const bytes =
            Uint8Array.from(
                binary,
                (character) =>
                    character.charCodeAt(0)
            )


        return JSON.parse(
            new TextDecoder().decode(bytes)
        ) as JwtPayload

    } catch {

        return null

    }
}


// =====================================================
// Access Token 가져오기
// =====================================================

export function getUserAccessToken():
    string | null {

    return localStorage.getItem(
        ACCESS_TOKEN_KEY
    )
}


// =====================================================
// ★ 현재 로그인 사용자 ID 가져오기
// =====================================================

export function getUserId():
    number | null {

    const storedUserId =
        localStorage.getItem(
            USER_ID_KEY
        )


    if (!storedUserId) {
        return null
    }


    const userId =
        Number(storedUserId)


    if (
        !Number.isFinite(userId)
        || userId <= 0
    ) {

        return null

    }


    return userId
}


// =====================================================
// 로그인 세션 저장
// =====================================================

export function storeUserSession(

    accessToken: string,

    userId: number

): void {


    localStorage.setItem(
        ACCESS_TOKEN_KEY,
        accessToken
    )


    localStorage.setItem(
        USER_ID_KEY,
        String(userId)
    )


    sessionStorage.removeItem(
        SESSION_EXPIRED_KEY
    )


    redirectInProgress = false
}


// =====================================================
// 로그인 세션 삭제
// =====================================================

export function clearUserSession(): void {

    localStorage.removeItem(
        ACCESS_TOKEN_KEY
    )

    localStorage.removeItem(
        USER_ID_KEY
    )

    localStorage.removeItem(
        USER_KEY
    )

}


// =====================================================
// JWT 만료 시간
// =====================================================

export function getUserTokenExpirationMillis(

    token: string

): number | null {


    const payload =
        decodeJwtPayload(token)


    return typeof payload?.exp === "number"

        ? payload.exp * 1000

        : null
}


// =====================================================
// JWT 만료 여부
// =====================================================

export function isUserTokenExpired(

    token: string,

    currentTime: number = Date.now()

): boolean {


    const expiration =
        getUserTokenExpirationMillis(
            token
        )


    return (
        expiration == null
        || expiration <= currentTime
    )
}


// =====================================================
// setTimeout 최대 시간 처리
// =====================================================

export function getSafeUserTimerDelay(

    expirationMillis: number

): number {


    return Math.min(

        Math.max(

            0,

            expirationMillis
            - Date.now()

        ),

        MAX_TIMER_DELAY

    )
}


// =====================================================
// 로그인 세션 만료
// =====================================================

export function expireUserSession(): void {


    clearUserSession()


    sessionStorage.setItem(
        SESSION_EXPIRED_KEY,
        "true"
    )


    if (

        redirectInProgress

        || window.location.pathname
        === "/login"

    ) {

        return

    }


    redirectInProgress = true


    window.location.replace(
        "/login?reason=session-expired"
    )
}