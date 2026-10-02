import AppLayout from "../layout/AppLayout";

import AiManagerDashboard from "../features/ai-manager/AiManagerDashboard";


/**
 * TripPamine 메인.
 *
 * 메인은 AI Manager 전용 Workspace로 사용한다.
 *
 * Header / Sidebar / Footer는 AppLayout이 담당한다.
 */
export default function Home() {
    return (
        <AppLayout
            pageClassName="bg-[#F7F9FC]"
        >
            <AiManagerDashboard />
        </AppLayout>
    );
}