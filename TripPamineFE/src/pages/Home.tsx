// 메인 페이지.
// Header/Sidebar는 여기서 sidebarOpen 상태를 직접 관리하며,
// 아래 섹션 컴포넌트들(Hero, FestivalSection 등)을 순서대로 나열해서 조립하기만 합니다.
// (MyPage.jsx, Detail.jsx도 같은 sidebarOpen 패턴을 각자 페이지 안에서 똑같이 사용합니다)
import { useState } from "react";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import Footer from "../components/Footer";
import Hero from "../components/Hero";
import AiPlannerCard from "../components/AiPlannerCard";
import WhyUs from "../components/WhyUs";

export default function Home() {
  const [sidebarOpen, setSidebarOpen] = useState(false); // 사이드바 펼침 여부 (Header 햄버거 버튼으로 토글)

  return (
    <div className="min-h-screen bg-slate-50">
      {/* 사이드바 (z-40, 헤더 z-50보다 아래에서 시작) */}
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* 상단 고정 헤더 — 화면 전체 너비, 항상 맨 위(z-50) */}
      <Header
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() => setSidebarOpen((v) => !v)}
      />

      {/* 실제 콘텐츠 영역.
          사이드바가 열려있으면(lg 화면 기준) 왼쪽 여백을 64(펼침 너비)만큼,
          닫혀있으면 16(아이콘만 보이는 너비)만큼 줘서 사이드바와 겹치지 않도록 함 */}
      <main
        className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}
      >
        <Hero /> {/* AI 여행 조건 입력 폼 (히어로) */}
        <AiPlannerCard /> {/* AI 추천 과정 소개 카드 */}
        {/* 전국 축제 & 행사 / 인기 국내 여행지 섹션은 삭제되었습니다.
            해당 내용은 이제 사이드바 "정보" 그룹 -> 국내 축제 및 행사 / 국내 관광 여행지 페이지(/tour/festivals, /tour/destinations)에서
            한국관광공사 오픈API 실데이터로 확인할 수 있습니다. */}
        <WhyUs /> {/* 서비스 장점 소개 */}
        <Footer />
      </main>
    </div>
  );
}
