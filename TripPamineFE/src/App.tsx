// 앱의 최상위 컴포넌트.
// 실제 화면 구성이나 로직은 없고, 라우팅 설정(Router)을 그대로 렌더링만 합니다.
import { AuthProvider } from "./context/AuthContext"
import Router from "./router/Router"

export default function App() {
  return (
  <AuthProvider>
    <Router />
  </AuthProvider>
  )
}
