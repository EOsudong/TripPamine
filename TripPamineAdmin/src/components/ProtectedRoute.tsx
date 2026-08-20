// 로그인 안 한 상태로 /users 같은 관리자 화면에 직접 주소를 쳐서 들어오는 걸 막는 컴포넌트.
// 토큰이 없으면 바로 /login으로 돌려보냄.
import type { ReactNode } from "react"
import { Navigate } from "react-router-dom"
import { getAdminToken } from "../api/client"

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const token = getAdminToken()

  if (!token) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
