import AppLayout from "../layout/AppLayout";

import AiManagerDashboard from "../features/ai-manager/AiManagerDashboard";

import LandingPage from "../features/landing/LandingPage";

import { useAuth } from "../context/AuthContext";


/**
 * TripPamine 메인.
 *
 * - 비로그인: Anime.js 기반 풀스크린 랜딩 페이지 (Sidebar/Header 없음)
 * - 로그인  : AI Manager 전용 Workspace
 *
 * Header / Sidebar / Footer는 AppLayout이 담당한다.
 */
export default function Home() {

    const { isLoggedIn } = useAuth();

    if (!isLoggedIn) {
        return <LandingPage />;
    }

    return (
        <AppLayout
            pageClassName="tp-aurora-light"
        >
            <AiManagerDashboard />
        </AppLayout>
    );
}
