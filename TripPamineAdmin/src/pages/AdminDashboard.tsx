// 로그인 직후 보이는 첫 화면. 지금은 통계 API가 없어서 안내 카드만 두고,
// 나중에 필요해지면 이 자리에 실제 통계 위젯을 추가하면 됨.
import { Link } from "react-router-dom"
import AdminLayout from "../components/AdminLayout"

export default function AdminDashboard() {
    return (
        <AdminLayout>
            <h1 className="text-xl font-bold text-slate-800 mb-1">대시보드</h1>
            <p className="text-sm text-slate-500 mb-8">TripPamine 관리자 콘솔에 오신 걸 환영해요.</p>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                <Link
                    to="/users"
                    className="block bg-white rounded-2xl border border-slate-200 p-6 hover:border-indigo-300 hover:shadow-sm transition-all"
                >
                    <p className="text-xs font-semibold text-indigo-500 uppercase tracking-wide mb-2">회원 관리</p>
                    <p className="text-sm text-slate-500">전체 회원 목록을 조회하고, 문제 회원을 정지시킬 수 있어요.</p>
                </Link>

                <Link
                    to="/quests"
                    className="block bg-white rounded-2xl border border-slate-200 p-6 hover:border-indigo-300 hover:shadow-sm transition-all"
                >
                    <p className="text-xs font-semibold text-indigo-500 uppercase tracking-wide mb-2">퀘스트 관리</p>
                    <p className="text-sm text-slate-500">
                        GPS 반경 검증 기반 실시간 퀘스트를 등록/수정/삭제할 수 있어요.
                    </p>
                </Link>

                <Link
                    to="/quests/chapters"
                    className="block bg-white rounded-2xl border border-slate-200 p-6 hover:border-indigo-300 hover:shadow-sm transition-all"
                >
                    <p className="text-xs font-semibold text-indigo-500 uppercase tracking-wide mb-2">챕터 관리</p>
                    <p className="text-sm text-slate-500">
                        퀘스트를 지역/순서별로 묶는 챕터를 등록하고, 진입 레벨을 설정할 수 있어요.
                    </p>
                </Link>

                <Link
                    to="/titles"
                    className="block bg-white rounded-2xl border border-slate-200 p-6 hover:border-indigo-300 hover:shadow-sm transition-all"
                >
                    <p className="text-xs font-semibold text-indigo-500 uppercase tracking-wide mb-2">칭호 관리</p>
                    <p className="text-sm text-slate-500">
                        유저에게 자동 지급되는 칭호와 그 획득 조건(완료 수/레벨 등)을 관리할 수 있어요.
                    </p>
                </Link>

                <Link
                    to="/mascots"
                    className="block bg-white rounded-2xl border border-slate-200 p-6 hover:border-indigo-300 hover:shadow-sm transition-all"
                >
                    <p className="text-xs font-semibold text-indigo-500 uppercase tracking-wide mb-2">마스코트 관리</p>
                    <p className="text-sm text-slate-500">
                        마스코트 등록 및 수정, 삭제를 설정할 수 있어요.
                    </p>
                </Link>
                
                <Link
                    to="/boards"
                    className="block bg-white rounded-2xl border border-slate-200 p-6 hover:border-indigo-300 hover:shadow-sm transition-all"
                >
                    <p className="text-xs font-semibold text-indigo-500 uppercase tracking-wide mb-2">게시판 관리</p>
                    <p className="text-sm text-slate-500">
                        공지/후기/Q&A 게시글과 댓글을 조회하고, 숨김·고정·삭제 처리를 할 수 있어요.
                    </p>
                </Link>
            </div>
        </AdminLayout>
    )
}