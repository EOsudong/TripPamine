import Hero from "../components/Hero";
import { useAuth } from "../context/AuthContext";

import AppLayout from "../layout/AppLayout";
import "./TravelPage.css";


/**
 * 여행 Workspace.
 *
 * 기존 메인 페이지에서 분리한:
 *
 * - 여행 계획 CRUD
 * - 미스터리 투어
 *
 * 를 담당한다.
 */
export default function TravelPage() {
    const { userId } = useAuth();

    return (
        <AppLayout
            pageClassName="tw-theme"
            appearance="dark"
        >
            <Hero key={userId ?? "guest"} />
        </AppLayout>
    );
}
