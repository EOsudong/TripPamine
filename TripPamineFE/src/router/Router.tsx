// React Router 설정 파일.
// URL 주소(경로)와 어떤 페이지 컴포넌트를 보여줄지 여기서 한 번에 관리합니다.
import { BrowserRouter, Routes, Route } from "react-router-dom"
import Home from "../pages/Home"
import Login from "../pages/Login"
import Join from "../pages/Join"
import FindId from "../pages/FindId"
import FindPw from "../pages/FindPw"
import MyPage from "../pages/MyPage"
import Detail from "../pages/Detail"
import OAuthCallback from "../pages/Oauthcallback"
import TourCategoryPage from "../pages/TourCategoryPage"
import TourDetailPage from "../pages/TourDetailPage"
import AiRecommendPage from "../pages/AiRecommendPage";
import QuestPage from "../pages/QuestPage"
import QuestDetailPage from "../pages/QuestDetailPage"
import ProtectedRoute from "../components/ProtectedRoute"

export default function Router() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Home />} />           {/* 메인 페이지 */}
                <Route path="/login" element={<Login />} />       {/* 로그인 */}
                <Route path="/join" element={<Join />} />         {/* 회원가입 */}
                <Route path="/find-id" element={<FindId />} />    {/* 아이디 찾기 */}
                <Route path="/find-pw" element={<FindPw />} />    {/* 비밀번호 찾기 */}
                {/* 마이페이지 (북마크/저장된 코스/가계부) — 로그인한 사용자만 접근 가능.
            로그인 안 한 상태로 들어오면 ProtectedRoute가 /login으로 보내고, 로그인 성공 후 다시 여기로 돌아옴 */}
                <Route
                    path="/mypage"
                    element={
                        <ProtectedRoute>
                            <MyPage />
                        </ProtectedRoute>
                    }
                />
                {/* 소셜 로그인(카카오/구글/네이버) 성공 후 백엔드가 accessToken을 쿼리로 붙여
          여기로 리다이렉트함.*/}
                <Route path="/oauth/callback" element={<OAuthCallback />} />
                {/* /detail/destination/jeju , /detail/festival/boryeong-mud 처럼
            type(destination|festival)과 id 값을 URL에서 그대로 받아서 Detail.jsx에서 분기 처리 */}
                <Route path="/detail/:type/:id" element={<Detail />} />
                {/* 사이드바 "정보" 그룹의 대분류 3개(국내 축제 및 행사/관광 여행지/관광 산업) 공용 페이지.
            categoryKey는 data/tourCategories.ts의 key("festivals"|"destinations"|"industry")와 매칭됨 */}
                <Route path="/tour/:categoryKey" element={<TourCategoryPage />} />
                {/* 위 목록 페이지에서 카드를 클릭하면 이동하는 상세 페이지 (TourItemCard.tsx -> TourDetailPage.tsx) */}
                <Route path="/tour/:categoryKey/:contentId" element={<TourDetailPage />} />
                <Route path="/ai-recommend" element={<AiRecommendPage />} />
                <Route
                    path="/quests"
                    element={
                        <ProtectedRoute>
                            <QuestPage />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/quests/:questId"
                    element={
                        <ProtectedRoute>
                            <QuestDetailPage />
                        </ProtectedRoute>
                    }
                />
            </Routes>
        </BrowserRouter>

    )
}


