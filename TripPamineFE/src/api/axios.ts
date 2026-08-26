import axios from "axios"
import {
    expireUserSession,
    getUserAccessToken,
    isUserTokenExpired,
} from "../auth/session"

export const API_BASE_URL = (
    import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"
).replace(/\/$/, "")

export const api = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        "Content-Type": "application/json",
    },
})

const SESSION_EXPIRY_EXCLUDED_PATHS = [
    "/users/auth/login",
    "/users/auth/signup",
    "/users/auth/check-email",
    "/users/auth/logout",
]

function isSessionExpiryExcluded(url?: string): boolean {
    if (!url) return false
    return SESSION_EXPIRY_EXCLUDED_PATHS.some((path) => url.includes(path))
}

api.interceptors.request.use(
    (config) => {
        const token = getUserAccessToken()

        if (
            token &&
            !isSessionExpiryExcluded(config.url) &&
            isUserTokenExpired(token)
        ) {
            expireUserSession()
            return Promise.reject(new Error("로그인이 만료되었습니다."))
        }

        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }

        return config
    },
    (error) => Promise.reject(error),
)

api.interceptors.response.use(
    (response) => response,
    (error) => {
        const requestUrl = error.config?.url as string | undefined
        const hasSession = Boolean(getUserAccessToken())

        if (
            error.response?.status === 401 &&
            hasSession &&
            !isSessionExpiryExcluded(requestUrl)
        ) {
            expireUserSession()
        }

        return Promise.reject(error)
    },
)

export default api
