// 앱의 최상위 컴포넌트.
// 실제 화면 구성이나 로직은 없고, 라우팅 설정(Router)을 그대로 렌더링만 합니다.
import { AuthProvider } from "./context/AuthContext"
import { NegoProvider } from "./context/NegoContext"
import NegoHotDealBanner from "./components/NegoHotDealBanner"
import Router from "./router/Router"

export default function App() {
  return (
  <AuthProvider>
      <NegoProvider>
        <Router />
        {/* position:fixed 배너라 라우트와 무관하게 전역에서 노출된다. */}
        <NegoHotDealBanner />
      </NegoProvider>
  </AuthProvider>
  )
}
