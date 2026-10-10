// 게시판 신고 관리 화면.
// - 신고된 게시글을 게시글 단위로 묶어서 보여줌 (신고 많은 순 → 최근 신고 순)
// - "검토"를 누르면 게시글 내용 + 개별 신고 내역을 보고, 대기 신고를 한 번에 처리 (인정/반려 + 게시글 조치)
// 사용하는 백엔드 API: /admin/boards/reports, /admin/boards/{postId}/reports
import { useEffect, useState, type FormEvent } from "react"
import AdminLayout from "../components/AdminLayout"
import { ApiError } from "../api/client"
import { getBoardPost, getPostReports, getReportedPosts, processPostReports } from "../api/boards"
import type {
    AdminBoardPostDetail,
    AdminBoardReport,
    AdminReportedPost,
    BoardReportPostAction,
    BoardReportReason,
    BoardReportStatus,
    BoardStatus,
} from "../types/api"

const reasonLabel: Record<BoardReportReason, string> = {
    SPAM: "스팸/광고",
    ABUSE: "욕설/비방",
    OBSCENE: "음란/선정성",
    PRIVACY: "개인정보 노출",
    FALSE_INFO: "허위 정보",
    ETC: "기타",
}

const reportStatusLabel: Record<BoardReportStatus, string> = {
    PENDING: "대기",
    ACCEPTED: "인정",
    REJECTED: "반려",
}

const reportStatusStyle: Record<BoardReportStatus, string> = {
    PENDING: "bg-red-50 text-red-600",
    ACCEPTED: "bg-slate-800 text-white",
    REJECTED: "bg-slate-100 text-slate-500",
}

const postStatusLabel: Record<BoardStatus, string> = {
    ACTIVE: "노출",
    HIDDEN: "숨김",
    DELETED: "삭제",
}

const postStatusStyle: Record<BoardStatus, string> = {
    ACTIVE: "bg-emerald-50 text-emerald-600",
    HIDDEN: "bg-amber-50 text-amber-600",
    DELETED: "bg-slate-100 text-slate-500",
}

const postActionLabel: Record<BoardReportPostAction, string> = {
    KEEP: "게시글 그대로 두기",
    HIDE: "게시글 숨김",
    DELETE: "게시글 삭제",
    ACTIVATE: "게시글 다시 노출",
}

function errorMessage(err: unknown, fallback: string) {
    return err instanceof ApiError ? err.message : fallback
}

function formatDate(value: string | null | undefined) {
    if (!value) return "-"
    return value.slice(0, 16).replace("T", " ")
}

export default function AdminBoardReports() {
    const [filter, setFilter] = useState<BoardReportStatus | "">("PENDING")
    const [rows, setRows] = useState<AdminReportedPost[]>([])
    const [page, setPage] = useState(0)
    const [totalPages, setTotalPages] = useState(0)
    const [totalElements, setTotalElements] = useState(0)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [reviewPostId, setReviewPostId] = useState<number | null>(null)

    async function load(targetPage: number = page) {
        setLoading(true)
        setError(null)
        try {
            const result = await getReportedPosts(filter, targetPage)
            setRows(result.content)
            setTotalPages(result.totalPages)
            setTotalElements(result.totalElements)
        } catch (err) {
            setError(errorMessage(err, "신고 목록을 불러오지 못했습니다."))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        load(page)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [page, filter])

    return (
        <AdminLayout>
            <h1 className="text-xl font-bold text-slate-800 mb-1">신고 관리</h1>
            <p className="text-sm text-slate-500 mb-6">
                회원이 신고한 게시글을 게시글 단위로 모아 보여줍니다. 대기 신고가 5건 쌓인 글은 확인 전까지 자동으로 숨겨져요.
            </p>

            <div className="flex items-center gap-2 mb-4">
                {([
                    ["PENDING", "처리 대기"],
                    ["", "전체"],
                ] as const).map(([key, label]) => (
                    <button
                        key={key || "all"}
                        onClick={() => {
                            setPage(0)
                            setFilter(key)
                        }}
                        className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                            filter === key ? "bg-slate-800 text-white" : "bg-white text-slate-600 border border-slate-200 hover:border-slate-400"
                        }`}
                    >
                        {label}
                    </button>
                ))}
                <span className="ml-auto text-xs text-slate-400">게시글 {totalElements.toLocaleString()}건</span>
            </div>

            {error && (
                <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2.5 mb-4">{error}</p>
            )}

            <div className="bg-white rounded-2xl border border-slate-200 overflow-x-auto">
                <table className="w-full text-sm min-w-[860px]">
                    <thead>
                    <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
                        <th className="px-4 py-3">게시글</th>
                        <th className="px-4 py-3 w-32">작성자</th>
                        <th className="px-4 py-3 w-28">신고</th>
                        <th className="px-4 py-3">사유</th>
                        <th className="px-4 py-3 w-36">마지막 신고</th>
                        <th className="px-4 py-3 w-20 text-right">처리</th>
                    </tr>
                    </thead>
                    <tbody>
                    {loading ? (
                        <tr>
                            <td colSpan={6} className="px-5 py-10 text-center text-slate-400">불러오는 중...</td>
                        </tr>
                    ) : rows.length === 0 ? (
                        <tr>
                            <td colSpan={6} className="px-5 py-10 text-center text-slate-400">
                                {filter === "PENDING" ? "처리할 신고가 없습니다." : "신고 내역이 없습니다."}
                            </td>
                        </tr>
                    ) : (
                        rows.map((row) => (
                            <tr key={row.post.postId} className="border-b border-slate-100 last:border-0 align-top">
                                <td className="px-4 py-3.5">
                                    <div className="flex items-center gap-2">
                                        <span className={`px-2 py-0.5 rounded-full text-[11px] font-semibold shrink-0 ${postStatusStyle[row.post.status]}`}>
                                            {postStatusLabel[row.post.status]}
                                        </span>
                                        <span className="text-slate-800 font-medium">{row.post.title}</span>
                                    </div>
                                    <p className="text-xs text-slate-400 mt-1">#{row.post.postId} · {row.post.categoryLabel}</p>
                                </td>
                                <td className="px-4 py-3.5 text-slate-600">{row.post.writerName}</td>
                                <td className="px-4 py-3.5">
                                    <span className="font-semibold text-slate-800">{row.reportCount}건</span>
                                    {row.pendingCount > 0 && (
                                        <span className="ml-1.5 px-1.5 py-0.5 rounded bg-red-50 text-red-600 text-[11px] font-semibold">
                                            대기 {row.pendingCount}
                                        </span>
                                    )}
                                </td>
                                <td className="px-4 py-3.5">
                                    <div className="flex flex-wrap gap-1">
                                        {(Object.entries(row.reasonCounts) as [BoardReportReason, number][]).map(([reason, count]) => (
                                            <span key={reason} className="px-2 py-0.5 rounded bg-slate-100 text-slate-600 text-xs">
                                                {reasonLabel[reason]} {count}
                                            </span>
                                        ))}
                                    </div>
                                </td>
                                <td className="px-4 py-3.5 text-xs text-slate-500">{formatDate(row.lastReportedAt)}</td>
                                <td className="px-4 py-3.5 text-right">
                                    <button
                                        onClick={() => setReviewPostId(row.post.postId)}
                                        className="px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:bg-indigo-50"
                                    >
                                        검토
                                    </button>
                                </td>
                            </tr>
                        ))
                    )}
                    </tbody>
                </table>
            </div>

            {totalPages > 1 && (
                <div className="flex items-center justify-center gap-2 mt-5">
                    <button
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                        disabled={page === 0}
                        className="px-3 py-1.5 rounded-lg text-sm text-slate-500 hover:bg-slate-100 disabled:opacity-30"
                    >
                        이전
                    </button>
                    <span className="text-sm text-slate-500">{page + 1} / {totalPages}</span>
                    <button
                        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                        className="px-3 py-1.5 rounded-lg text-sm text-slate-500 hover:bg-slate-100 disabled:opacity-30"
                    >
                        다음
                    </button>
                </div>
            )}

            {reviewPostId !== null && (
                <ReviewModal
                    postId={reviewPostId}
                    onClose={() => setReviewPostId(null)}
                    onProcessed={() => {
                        setReviewPostId(null)
                        load(page)
                    }}
                />
            )}
        </AdminLayout>
    )
}

// ===================== 검토 + 처리 모달 =====================

function ReviewModal({ postId, onClose, onProcessed }: { postId: number; onClose: () => void; onProcessed: () => void }) {
    const [post, setPost] = useState<AdminBoardPostDetail | null>(null)
    const [reports, setReports] = useState<AdminBoardReport[]>([])
    const [error, setError] = useState<string | null>(null)

    const [result, setResult] = useState<"ACCEPTED" | "REJECTED">("ACCEPTED")
    const [postAction, setPostAction] = useState<BoardReportPostAction>("HIDE")
    const [memo, setMemo] = useState("")
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        Promise.all([getBoardPost(postId), getPostReports(postId)])
            .then(([detail, reportPage]) => {
                setPost(detail)
                setReports(reportPage.content)
            })
            .catch((err) => setError(errorMessage(err, "신고 내용을 불러오지 못했습니다.")))
    }, [postId])

    const pendingCount = reports.filter((r) => r.status === "PENDING").length

    // 결과를 바꾸면 흔히 쓰는 게시글 조치를 기본값으로 맞춰준다
    function changeResult(next: "ACCEPTED" | "REJECTED") {
        setResult(next)
        if (next === "ACCEPTED") setPostAction("HIDE")
        else setPostAction(post?.status === "HIDDEN" ? "ACTIVATE" : "KEEP")
    }

    async function handleSubmit(e: FormEvent) {
        e.preventDefault()
        setSubmitting(true)
        setError(null)
        try {
            await processPostReports(postId, { result, postAction, memo: memo.trim() || undefined })
            onProcessed()
        } catch (err) {
            setError(errorMessage(err, "신고 처리에 실패했습니다."))
            setSubmitting(false)
        }
    }

    return (
        <div className="fixed inset-0 bg-slate-900/50 flex items-center justify-center p-4 z-50" onClick={onClose}>
            <div className="bg-white rounded-2xl w-full max-w-3xl max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                <div className="sticky top-0 bg-white border-b border-slate-100 px-6 py-4 flex items-center justify-between">
                    <h2 className="font-bold text-slate-800">신고 검토</h2>
                    <button onClick={onClose} className="px-3 py-1.5 rounded-lg text-sm text-slate-500 hover:bg-slate-100">닫기</button>
                </div>

                <div className="p-6 space-y-6">
                    {error && <p className="text-sm text-red-600">{error}</p>}

                    {post && (
                        <section>
                            <div className="flex items-center gap-2 mb-1.5 text-xs">
                                <span className={`px-2 py-0.5 rounded-full font-semibold ${postStatusStyle[post.status]}`}>{postStatusLabel[post.status]}</span>
                                <span className="text-slate-400">#{post.postId} · {post.categoryLabel} · {post.writerName} · {formatDate(post.createDate)}</span>
                            </div>
                            <h3 className="text-lg font-bold text-slate-900 mb-2">{post.title}</h3>
                            <div className="max-h-56 overflow-y-auto rounded-xl bg-slate-50 border border-slate-100 p-4">
                                <p className="text-sm text-slate-700 leading-6 whitespace-pre-wrap break-words">{post.content}</p>
                                {post.images.length > 0 && (
                                    <div className="grid grid-cols-5 gap-2 mt-3">
                                        {post.images.map((img) => (
                                            <a key={img.imageId} href={img.imageUrl} target="_blank" rel="noreferrer" className="block aspect-square rounded-lg overflow-hidden bg-slate-200">
                                                <img src={img.imageUrl} alt="" className="w-full h-full object-cover" />
                                            </a>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </section>
                    )}

                    <section>
                        <h4 className="font-semibold text-slate-800 text-sm mb-2">신고 내역 {reports.length}건</h4>
                        <ul className="divide-y divide-slate-100 border border-slate-100 rounded-xl">
                            {reports.map((report) => (
                                <li key={report.reportId} className="px-4 py-3">
                                    <div className="flex items-center gap-2 text-xs mb-1 flex-wrap">
                                        <span className={`px-1.5 py-0.5 rounded font-semibold ${reportStatusStyle[report.status]}`}>
                                            {reportStatusLabel[report.status]}
                                        </span>
                                        <span className="font-semibold text-slate-700">{report.reasonLabel}</span>
                                        <span className="text-slate-400">{report.reporterName} · {formatDate(report.createDate)}</span>
                                    </div>
                                    {report.detail && <p className="text-sm text-slate-600 whitespace-pre-wrap break-words">{report.detail}</p>}
                                    {report.status !== "PENDING" && (
                                        <p className="text-xs text-slate-400 mt-1">
                                            처리: {report.handledByName ?? "-"} · {formatDate(report.handledAt)}
                                            {report.handleMemo && ` · ${report.handleMemo}`}
                                        </p>
                                    )}
                                </li>
                            ))}
                        </ul>
                    </section>

                    {pendingCount > 0 ? (
                        <form onSubmit={handleSubmit} className="rounded-xl border-2 border-slate-100 p-4 space-y-3">
                            <h4 className="font-semibold text-slate-800 text-sm">대기 신고 {pendingCount}건 일괄 처리</h4>
                            <div className="flex gap-2">
                                {(["ACCEPTED", "REJECTED"] as const).map((key) => (
                                    <button
                                        key={key}
                                        type="button"
                                        onClick={() => changeResult(key)}
                                        className={`flex-1 py-2 rounded-lg text-sm font-semibold border-2 transition-colors ${
                                            result === key
                                                ? key === "ACCEPTED" ? "border-red-400 bg-red-50 text-red-600" : "border-slate-400 bg-slate-50 text-slate-700"
                                                : "border-slate-200 text-slate-500"
                                        }`}
                                    >
                                        {key === "ACCEPTED" ? "신고 인정" : "신고 반려"}
                                    </button>
                                ))}
                            </div>
                            <select
                                value={postAction}
                                onChange={(e) => setPostAction(e.target.value as BoardReportPostAction)}
                                className="w-full h-10 px-3 rounded-lg border-2 border-slate-200 text-sm text-slate-700"
                                aria-label="게시글 조치"
                            >
                                {(Object.keys(postActionLabel) as BoardReportPostAction[]).map((key) => (
                                    <option key={key} value={key}>{postActionLabel[key]}</option>
                                ))}
                            </select>
                            <textarea
                                value={memo}
                                onChange={(e) => setMemo(e.target.value.slice(0, 500))}
                                rows={2}
                                placeholder="처리 메모 (선택)"
                                className="w-full px-3 py-2 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm resize-none"
                            />
                            <button
                                type="submit"
                                disabled={submitting}
                                className="w-full py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-600 disabled:bg-slate-300 text-white text-sm font-semibold"
                            >
                                {submitting ? "처리 중..." : "처리하기"}
                            </button>
                        </form>
                    ) : (
                        reports.length > 0 && <p className="text-sm text-slate-400">처리 대기 중인 신고가 없습니다.</p>
                    )}
                </div>
            </div>
        </div>
    )
}
