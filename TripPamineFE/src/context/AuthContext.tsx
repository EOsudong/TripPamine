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
  getUserId,
  getUserTokenExpirationMillis,
  isUserTokenExpired,
  storeUserSession,
} from "../auth/session"


// =====================================================
// AuthContext Type
// =====================================================

interface AuthContextType {

  isLoggedIn: boolean

  userId: number | null

  login: (
      accessToken: string,
      userId: number
  ) => void

  logout: () => Promise<void>

}


// =====================================================
// Context
// =====================================================

const AuthContext =
    createContext<AuthContextType | undefined>(
        undefined
    )


// =====================================================
// 현재 로그인 상태 확인
// =====================================================

function hasValidSession(): boolean {

  const token =
      getUserAccessToken()


  if (!token) {
    return false
  }


  if (
      isUserTokenExpired(token)
  ) {

    clearUserSession()

    return false
  }


  return true
}


// =====================================================
// Auth Provider
// =====================================================

export function AuthProvider({

  children,

}: {

  children: ReactNode

}) {


  // ===================================================
  // 로그인 상태
  // ===================================================

  const [
      isLoggedIn,
      setIsLoggedIn
  ] = useState(
      hasValidSession
  )


  // ===================================================
  // ★ 현재 로그인 사용자 ID
  // ===================================================

  const [
      userId,
      setUserId
  ] = useState<number | null>(
      () => getUserId()
  )


  // ===================================================
  // 로그인
  // ===================================================

  const login = (

      accessToken: string,

      loginUserId: number

  ) => {


    storeUserSession(

        accessToken,

        loginUserId

    )


    setUserId(
        loginUserId
    )


    setIsLoggedIn(
        true
    )
  }


  // ===================================================
  // 로그아웃
  // ===================================================

  const logout =
      async () => {


        try {

          await logoutApi()

        } catch (error) {

          console.error(
              "로그아웃 API 실패:",
              error
          )

        } finally {


          clearUserSession()


          setUserId(
              null
          )


          setIsLoggedIn(
              false
          )

        }

      }


  // ===================================================
  // 세션 만료 감시
  // ===================================================

  useEffect(() => {


    if (!isLoggedIn) {
      return
    }


    let timerId:
        number | undefined


    const checkSession = () => {


      if (
          timerId !== undefined
      ) {

        window.clearTimeout(
            timerId
        )

      }


      const currentToken =
          getUserAccessToken()


      if (

          !currentToken

          || isUserTokenExpired(
              currentToken
          )

      ) {


        setUserId(
            null
        )


        setIsLoggedIn(
            false
        )


        expireUserSession()


        return
      }


      const expiration =
          getUserTokenExpirationMillis(
              currentToken
          )


      if (
          expiration == null
      ) {


        setUserId(
            null
        )


        setIsLoggedIn(
            false
        )


        expireUserSession()


        return
      }


      timerId =
          window.setTimeout(

              checkSession,

              getSafeUserTimerDelay(
                  expiration
              )

          )

    }


    const handleVisibilityChange =
        () => {


          if (
              document.visibilityState
              === "visible"
          ) {

            checkSession()

          }

        }


    checkSession()


    window.addEventListener(
        "focus",
        checkSession
    )


    document.addEventListener(

        "visibilitychange",

        handleVisibilityChange

    )


    return () => {


      if (
          timerId !== undefined
      ) {

        window.clearTimeout(
            timerId
        )

      }


      window.removeEventListener(
          "focus",
          checkSession
      )


      document.removeEventListener(

          "visibilitychange",

          handleVisibilityChange

      )

    }

  }, [isLoggedIn])


  // ===================================================
  // Provider
  // ===================================================

  return (

      <AuthContext.Provider

          value={{

            isLoggedIn,

            userId,

            login,

            logout,

          }}

      >

        {children}

      </AuthContext.Provider>

  )
}


// =====================================================
// useAuth
// =====================================================

export function useAuth() {


  const context =
      useContext(
          AuthContext
      )


  if (!context) {

    throw new Error(

        "useAuth는 AuthProvider 안에서 사용해야 합니다."

    )

  }


  return context
}