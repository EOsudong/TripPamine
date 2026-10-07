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
import QuestPage from "../pages/QuestPage";
import QuestDetailPage from "../pages/QuestDetailPage";
import FinancialProductsPage from "../pages/FinancialProductsPage";
import MysteryTourPlay from "../pages/MysteryTourPlay";

import AssetDashboardPage from "../pages/AssetDashboardPage";

import ProtectedRoute from "../components/ProtectedRoute";


export default function Router() {

    return (

        <BrowserRouter>

            <Routes>

                {/* 메인 */}
                <Route
                    path="/"
                    element={<Home />}
                />


                {/* 로그인 */}
                <Route
                    path="/login"
                    element={<Login />}
                />


                {/* 회원가입 */}
                <Route
                    path="/join"
                    element={<Join />}
                />


                {/* 아이디 찾기 */}
                <Route
                    path="/find-id"
                    element={<FindId />}
                />


                {/* 비밀번호 찾기 */}
                <Route
                    path="/find-pw"
                    element={<FindPw />}
                />


                {/* 마이페이지 */}
                <Route
                    path="/mypage"
                    element={
                        <ProtectedRoute>
                            <MyPage />
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


                {/* 금융상품 */}
                <Route
                    path="/finance"
                    element={<FinancialProductsPage />}
                />


                {/* 미스터리 투어 */}
                <Route
                    path="/mystery-tour/:mysteryTourId"
                    element={
                        <ProtectedRoute>
                            <MysteryTourPlay />
                        </ProtectedRoute>
                    }
                />

            </Routes>

        </BrowserRouter>
    );
}