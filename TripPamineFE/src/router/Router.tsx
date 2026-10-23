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

import QuestPage from "../pages/QuestPage";
import QuestDetailPage from "../pages/QuestDetailPage";

import FinancialProductsPage from "../pages/FinancialProductsPage";

import MysteryTourPlay from "../pages/MysteryTourPlay";
import TravelPage from "../pages/TravelPage";

import BoardListPage from "../pages/BoardListPage";
import BoardDetailPage from "../pages/BoardDetailPage";
import BoardWritePage from "../pages/BoardWritePage";

import AssetDashboardPage from "../pages/AssetDashboardPage";

import ProtectedRoute from "../components/ProtectedRoute";

import MascotHuntPage from "../pages/MascotHuntPage";
import MascotDexPage from "../pages/MascotDexPage";

import AiPhotoEditorPage from "../pages/AiPhotoEditorPage";
import DiaryPage from "../pages/DiaryPage";


// ========================================
// 여행 음악 추천 페이지 추가
// ========================================
import TravelMusicPage from "../pages/TravelMusicPage";


export default function Router() {

  return (
    <BrowserRouter>

      <Routes>

        {/* ============================= */}
        {/* 메인 페이지 */}
        {/* ============================= */}

        <Route
          path="/"
          element={<Home />}
        />


        {/* ============================= */}
        {/* 여행 페이지 */}
        {/* ============================= */}

        <Route
          path="/travel"
          element={
            <ProtectedRoute>
              <TravelPage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* 로그인 / 회원가입 */}
        {/* ============================= */}

        <Route
          path="/login"
          element={<Login />}
        />

        <Route
          path="/join"
          element={<Join />}
        />

        <Route
          path="/find-id"
          element={<FindId />}
        />

        <Route
          path="/find-pw"
          element={<FindPw />}
        />


        {/* ============================= */}
        {/* 마이페이지 */}
        {/* ============================= */}

        <Route
          path="/mypage"
          element={
            <ProtectedRoute>
              <MyPage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* OAuth 로그인 콜백 */}
        {/* ============================= */}

        <Route
          path="/oauth/callback"
          element={<OAuthCallback />}
        />


        {/* ============================= */}
        {/* 여행 상세 */}
        {/* ============================= */}

        <Route
          path="/detail/:type/:id"
          element={<Detail />}
        />


        {/* ============================= */}
        {/* 관광 카테고리 */}
        {/* ============================= */}

        <Route
          path="/tour/:categoryKey"
          element={<TourCategoryPage />}
        />


        <Route
          path="/tour/:categoryKey/:contentId"
          element={<TourDetailPage />}
        />


        {/* ============================= */}
        {/* AI 여행 추천 */}
        {/* ============================= */}

        <Route
          path="/ai-recommend"
          element={<AiRecommendPage />}
        />


        {/* ============================= */}
        {/* 사용자 감정 분석 */}
        {/* ============================= */}

        <Route path="/ai-recommend" element={<AiRecommendPage />} />
        {/* AI 다이어리 */}
        <Route
          path="/diaries"
          element={
              <ProtectedRoute>
                  <DiaryPage />
              </ProtectedRoute>
          }
        />
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
        {/* 자산관리 */}
        {/* ============================= */}

        <Route
          path="/assets"
          element={
            <ProtectedRoute>
              <AssetDashboardPage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* AI 사진 편집 */}
        {/* ============================= */}

        <Route
          path="/ai-photo-editor"
          element={
            <ProtectedRoute>
              <AiPhotoEditorPage />
            </ProtectedRoute>
          }
        />


        {/* ======================================== */}
        {/* 여행 음악 추천 */}
        {/* ======================================== */}

        <Route
          path="/music"
          element={
            <ProtectedRoute>
              <TravelMusicPage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* 퀘스트 */}
        {/* ============================= */}

        <Route
          path="/quests"
          element={
            <ProtectedRoute>
              <QuestPage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* 퀘스트 상세 */}
        {/* ============================= */}

        <Route
          path="/quests/:questId"
          element={
            <ProtectedRoute>
              <QuestDetailPage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* 금융상품 */}
        {/* ============================= */}

        <Route
          path="/finance"
          element={<FinancialProductsPage />}
        />


        {/* ============================= */}
        {/* 미스터리 투어 */}
        {/* ============================= */}

        <Route
          path="/mystery-tour/:mysteryTourId"
          element={
            <ProtectedRoute>
              <MysteryTourPlay />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* 마스코트 탐험 */}
        {/* ============================= */}

        <Route
          path="/mascots"
          element={
            <ProtectedRoute>
              <MascotHuntPage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* 마스코트 도감 */}
        {/* ============================= */}

        <Route
          path="/mascots/dex"
          element={
            <ProtectedRoute>
              <MascotDexPage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* 게시판 목록 */}
        {/* ============================= */}

        <Route
          path="/boards"
          element={<BoardListPage />}
        />


        {/* ============================= */}
        {/* 게시판 작성 */}
        {/* ============================= */}

        <Route
          path="/boards/write"
          element={
            <ProtectedRoute>
              <BoardWritePage />
            </ProtectedRoute>
          }
        />


        {/* ============================= */}
        {/* 게시판 상세 */}
        {/* ============================= */}

        <Route
          path="/boards/:postId"
          element={<BoardDetailPage />}
        />


        {/* ============================= */}
        {/* 게시판 수정 */}
        {/* ============================= */}

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