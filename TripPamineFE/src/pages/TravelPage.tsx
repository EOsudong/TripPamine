import AiPlannerCard from "../components/AiPlannerCard";
import Hero from "../components/Hero";

import AppLayout from "../layout/AppLayout";


/**
 * 여행 Workspace.
 *
 * 기존 메인 페이지에서 분리한:
 *
 * - 여행 계획 CRUD
 * - 미스터리 투어
 * - AI 여행 기능 소개
 *
 * 를 담당한다.
 */
export default function TravelPage() {
    return (
        <AppLayout
            pageClassName="bg-slate-50"
        >
            <Hero />

            <AiPlannerCard />
        </AppLayout>
    );
}