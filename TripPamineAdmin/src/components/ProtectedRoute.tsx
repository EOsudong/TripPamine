import { useEffect, useState } from "react"
import type { ReactNode } from "react"
import { Navigate } from "react-router-dom"
import {
  clearAdminToken,
  getAdminToken,
  getAdminTokenExpirationMillis,
  getSafeTimerDelay,
  isAdminTokenExpired,
} from "../api/client"

type SessionState = "valid" | "missing" | "expired"

function getInitialSessionState(): SessionState {
  const token = getAdminToken()
  if (!token) return "missing"

  if (isAdminTokenExpired(token)) {
    clearAdminToken()
    return "expired"
  }

  return "valid"
}

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const [sessionState, setSessionState] = useState<SessionState>(
      getInitialSessionState,
  )

  useEffect(() => {
    if (sessionState !== "valid") return

    let timerId: number | undefined

    const checkSession = () => {
      if (timerId !== undefined) window.clearTimeout(timerId)

      const currentToken = getAdminToken()
      if (!currentToken || isAdminTokenExpired(currentToken)) {
        clearAdminToken()
        sessionStorage.setItem("adminSessionExpired", "true")
        setSessionState("expired")
        return
      }

      const expiration = getAdminTokenExpirationMillis(currentToken)
      if (expiration == null) {
        clearAdminToken()
        sessionStorage.setItem("adminSessionExpired", "true")
        setSessionState("expired")
        return
      }

      timerId = window.setTimeout(
          checkSession,
          getSafeTimerDelay(expiration),
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
  }, [sessionState])

  if (sessionState !== "valid") {
    const loginPath =
        sessionState === "expired"
            ? "/login?reason=session-expired"
            : "/login"
    return <Navigate to={loginPath} replace />
  }

  return <>{children}</>
}
