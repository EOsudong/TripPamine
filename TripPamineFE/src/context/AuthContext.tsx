import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react"
import { logoutApi } from "../api/auth"
import {
  clearUserSession,
  expireUserSession,
  getSafeUserTimerDelay,
  getUserAccessToken,
  getUserTokenExpirationMillis,
  isUserTokenExpired,
  storeUserSession,
} from "../auth/session"

interface AuthContextType {
  isLoggedIn: boolean
  login: (accessToken: string, userId: number) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

function hasValidSession(): boolean {
  const token = getUserAccessToken()
  if (!token) return false

  if (isUserTokenExpired(token)) {
    clearUserSession()
    return false
  }

  return true
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(hasValidSession)

  const login = (accessToken: string, userId: number) => {
    storeUserSession(accessToken, userId)
    setIsLoggedIn(true)
  }

  const logout = async () => {
    try {
      await logoutApi()
    } catch (error) {
      console.error("로그아웃 API 실패:", error)
    } finally {
      clearUserSession()
      setIsLoggedIn(false)
    }
  }

  useEffect(() => {
    if (!isLoggedIn) return

    let timerId: number | undefined

    const checkSession = () => {
      if (timerId !== undefined) window.clearTimeout(timerId)

      const currentToken = getUserAccessToken()
      if (!currentToken || isUserTokenExpired(currentToken)) {
        setIsLoggedIn(false)
        expireUserSession()
        return
      }

      const expiration = getUserTokenExpirationMillis(currentToken)
      if (expiration == null) {
        setIsLoggedIn(false)
        expireUserSession()
        return
      }

      timerId = window.setTimeout(
          checkSession,
          getSafeUserTimerDelay(expiration),
      )
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") checkSession()
    }

    checkSession()
    window.addEventListener("focus", checkSession)
    document.addEventListener("visibilitychange", handleVisibilityChange)

    return () => {
      if (timerId !== undefined) window.clearTimeout(timerId)
      window.removeEventListener("focus", checkSession)
      document.removeEventListener("visibilitychange", handleVisibilityChange)
    }
  }, [isLoggedIn])

  return (
      <AuthContext.Provider value={{ isLoggedIn, login, logout }}>
        {children}
      </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth는 AuthProvider 안에서 사용해야 합니다.")
  }
  return context
}
