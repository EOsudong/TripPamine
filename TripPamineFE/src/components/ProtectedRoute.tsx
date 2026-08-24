// 로그인해야만 볼 수 있는 라우트를 감싸는 공용 래퍼.
// - 로그인 상태(AuthContext.isLoggedIn)가 아니면 /login으로 보내고,
//   원래 가려던 위치(location)를 state.from에 실어둬서 로그인 성공 후 그 페이지로 다시 돌아갈 수 있게 함
//   (Login.tsx에서 location.state.from을 읽어서 navigate함)
// - 로그인 상태면 children을 그대로 렌더링
import type { ReactNode } from "react"
import { Navigate, useLocation } from "react-router-dom"
import { useAuth } from "../context/AuthContext"

interface ProtectedRouteProps {
    children: ReactNode
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
    const { isLoggedIn } = useAuth()
    const location = useLocation()

    if (!isLoggedIn) {
        return <Navigate to="/login" replace state={{ from: location }} />
    }

    return <>{children}</>
}
