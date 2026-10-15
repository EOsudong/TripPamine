// React Router 설정 파일.
// URL 주소(경로)와 어떤 페이지 컴포넌트를 보여줄지 여기서 한 번에 관리합니다.
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Home from "../pages/Home";
import Login from "../pages/Login";
import Join from "../pages/Join";
import FindId from "../pages/FindId";
import FindPw from "../pages/FindPw";
import MyPage from "../pages/MyPage";
import Detail from "../pages/Detail";
import OAuthCallback from "../pages/Oauthcallback";
import TourCategoryPage from "../pages/TourCategoryPage";
import TourDetailPage from "../pages/TourDetailPage";
import AiRecommendPage from "../pages/AiRecommendPage";
import EmotionAnalysisPage from "../pages/EmotionAnalysisPage";
import QuestPage from "../pages/QuestPage"
import QuestDetailPage from "../pages/QuestDetailPage"
import FinancialProductsPage from "../pages/FinancialProductsPage"
import MysteryTourPlay from "../pages/MysteryTourPlay"   // ← 추가
import TravelPage from "../pages/TravelPage";
import BoardListPage from "../pages/BoardListPage";
import BoardDetailPage from "../pages/BoardDetailPage";
import BoardWritePage from "../pages/BoardWritePage";
import AssetDashboardPage from "../pages/AssetDashboardPage";
import ProtectedRoute from "../components/ProtectedRoute";
import MascotHuntPage from "../pages/MascotHuntPage";
import MascotDexPage from "../pages/MascotDexPage";
import AiPhotoEditorPage from "../pages/AiPhotoEditorPage";

export default function Router() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Home />} /> {/* 메인 페이지 */}
                <Route path="/travel"
                       element={
                           <ProtectedRoute>
                               <TravelPage />
                           </ProtectedRoute>
                       }
                />
                <Route path="/login" element={<Login />} /> {/* 로그인 */}
                <Route path="/join" element={<Join />} /> {/* 회원가입 */}
                <Route path="/find-id" element={<FindId />} /> {/* 아이디 찾기 */}
                <Route path="/find-pw" element={<FindPw />} /> {/* 비밀번호 찾기 */}
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
                {/* 사용자 감정 분석: JWT 인증 필수 */}
                <Route
                    path="/ai/emotion"
                    element={
                        <ProtectedRoute>
                            <EmotionAnalysisPage />
                        </ProtectedRoute>
                    }
                />


                {/* ============================= */}
                {/* 자산관리 대시보드 */}
                {/* ============================= */}

                <Route
                    path="/assets"
                    element={
                        <ProtectedRoute>
                            <AssetDashboardPage />
                        </ProtectedRoute>
                    }
                />


                {/* OAuth 로그인 Callback */}
                <Route
                    path="/oauth/callback"
                    element={<OAuthCallback />}
                />


                {/* 여행 상세 */}
                <Route
                    path="/detail/:type/:id"
                    element={<Detail />}
                />


                {/* 관광 카테고리 */}
                <Route
                    path="/tour/:categoryKey"
                    element={<TourCategoryPage />}
                />


                {/* 관광 상세 */}
                <Route
                    path="/tour/:categoryKey/:contentId"
                    element={<TourDetailPage />}
                />


                {/* AI 여행 추천 */}
                <Route
                    path="/ai-recommend"
                    element={<AiRecommendPage />}
                />

                {/* AI 사진 편집 스튜디오 */}
                <Route
                    path="/ai-photo-editor"
                    element={
                        <ProtectedRoute>
                            <AiPhotoEditorPage />
                        </ProtectedRoute>
                    }
                />

                {/* 퀘스트 */}
                <Route
                    path="/quests"
                    element={
                        <ProtectedRoute>
                            <QuestPage />
                        </ProtectedRoute>
                    }
                />


                {/* 퀘스트 상세 */}
                <Route
                    path="/quests/:questId"
                    element={
                        <ProtectedRoute>
                            <QuestDetailPage />
                        </ProtectedRoute>
                    }
                />
                {/* 사이드바 "정보" 그룹의 "금융상품" 메뉴. 금융감독원 오픈API(금융상품 한눈에)로 받아오는
            정기예금/적금 정보를 보여주는 공개 페이지 — tour 페이지들과 마찬가지로 로그인 없이도
            접근 가능하도록 ProtectedRoute로 감싸지 않습니다. */}
                <Route path="/finance" element={<FinancialProductsPage />} />

                {/* 미스터리 투어 생성 후 이동하는 진행 화면 */}
                <Route
                    path="/mystery-tour/:mysteryTourId"
                    element={
                        <ProtectedRoute>
                            <MysteryTourPlay />
                        </ProtectedRoute>
                    }
                />
                {/* 게시판: 목록/상세는 비로그인도 볼 수 있고, 작성/수정은 로그인 필요 */}
                {/* [마스코트 추가] 탐험 지도 / 도감 - 로그인 필요 */}
                <Route
                    path="/mascots"
                    element={
                        <ProtectedRoute>
                            <MascotHuntPage />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/mascots/dex"
                    element={
                        <ProtectedRoute>
                            <MascotDexPage />
                        </ProtectedRoute>
                    }
                />
                <Route path="/boards" element={<BoardListPage />} />
                <Route
                    path="/boards/write"
                    element={
                        <ProtectedRoute>
                            <BoardWritePage />
                        </ProtectedRoute>
                    }
                />
                <Route path="/boards/:postId" element={<BoardDetailPage />} />
                <Route
                    path="/boards/:postId/edit"
                    element={
                        <ProtectedRoute>
                            <BoardWritePage />
                        </ProtectedRoute>
                    }
                />
            </Routes>

        </BrowserRouter>
    );
}