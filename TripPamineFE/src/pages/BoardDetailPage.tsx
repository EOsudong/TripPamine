// 게시글 상세 페이지. 라우트: /boards/:postId (비로그인도 접근 가능)
// - 본문/첨부 이미지/좋아요/댓글
// - 본인 글이면 수정·삭제 버튼 노출 (백엔드가 내려주는 mine 값 기준)
import { useEffect, useRef, useState } from "react"
import { Link, useLocation, useNavigate, useParams } from "react-router-dom"
import BoardLayout from "../components/board/BoardLayout"
import BoardComments from "../components/board/BoardComments"
import CategoryBadge from "../components/board/CategoryBadge"
import ReportModal from "../components/board/ReportModal"
import { useAuth } from "../context/AuthContext"
import {
    boardApi,
    boardImageSrc,
    extractBoardErrorMessage,
    formatBoardDateTime,
    type BoardPostDetail,
} from "../api/board"

export default function BoardDetailPage() {
    const { postId: postIdParam } = useParams()
    const postId = Number(postIdParam)
    const navigate = useNavigate()
    const location = useLocation()
    const { isLoggedIn } = useAuth()

    // 목록에서 들어왔으면 보던 목록(카테고리/검색/페이지)으로 돌아가기
    const backTo = (location.state as { backTo?: string } | null)?.backTo ?? "/boards"

    const [post, setPost] = useState<BoardPostDetail | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [liking, setLiking] = useState(false)
    const [viewerImage, setViewerImage] = useState<string | null>(null)
    const [reportOpen, setReportOpen] = useState(false)

    // 상세 조회 API는 조회수를 +1 하므로, 개발 모드(StrictMode)에서 effect가 두 번 실행돼도
    // 같은 글에 대해 한 번만 호출되도록 막는다.
    const fetchedPostIdRef = useRef<number | null>(null)

    useEffect(() => {
        if (!Number.isFinite(postId)) {
            setError("잘못된 주소입니다.")
            setLoading(false)
            return
        }
        if (fetchedPostIdRef.current === postId) return
        fetchedPostIdRef.current = postId

        setLoading(true)
        setError(null)
        boardApi
            .getPost(postId)
            .then(setPost)
            .catch((err) => setError(extractBoardErrorMessage(err, "게시글을 불러오지 못했어요.")))
            .finally(() => setLoading(false))
    }, [postId])

    async function handleLike() {
        if (!post) return
        if (!isLoggedIn) {
            navigate("/login", { state: { from: location } })
            return
        }
        setLiking(true)
        try {
            const result = await boardApi.toggleLike(post.postId)
            setPost({ ...post, liked: result.liked, likeCount: result.likeCount })
        } catch (err) {
            window.alert(extractBoardErrorMessage(err, "좋아요 처리에 실패했어요."))
        } finally {
            setLiking(false)
        }
    }

    function handleReportClick() {
        if (!isLoggedIn) {
            navigate("/login", { state: { from: location } })
            return
        }
        setReportOpen(true)
    }

    async function handleDelete() {
        if (!post) return
        if (!window.confirm("게시글을 삭제할까요? 삭제한 글은 되돌릴 수 없어요.")) return
        try {
            await boardApi.deletePost(post.postId)
            navigate(backTo, { replace: true })
        } catch (err) {
            window.alert(extractBoardErrorMessage(err, "게시글 삭제에 실패했어요."))
        }
    }

    return (
        <BoardLayout>
            <Link to={backTo} className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-white/70 px-3.5 py-1.5 text-sm font-semibold text-slate-500 backdrop-blur transition hover:bg-white hover:text-slate-900">
                ← 목록으로
            </Link>

            {loading ? (
                <div className="tp-card rounded-3xl p-6 space-y-3">
                    <div className="h-6 w-2/3 rounded-full bg-slate-100 animate-pulse" />
                    <div className="h-4 w-1/3 rounded bg-slate-100 animate-pulse" />
                    <div className="h-40 rounded bg-slate-100 animate-pulse mt-6" />
                </div>
            ) : error || !post ? (
                <div className="tp-empty text-center py-16 text-slate-400 text-sm">
                    {error ?? "게시글을 찾을 수 없어요."}
                </div>
            ) : (
                <>
                    <article className="tp-card rounded-3xl overflow-hidden">
                        {/* 제목 영역 */}
                        <header className="relative overflow-hidden border-b border-slate-100 px-5 pb-6 pt-7 md:px-8">
                            <div className="pointer-events-none absolute -right-16 -top-24 h-56 w-56 rounded-full bg-gradient-to-br from-violet-300/30 via-cyan-200/20 to-transparent blur-3xl" />
                            <div className="flex items-center gap-2 mb-2">
                                {post.pinned && <span className="text-xs">📌</span>}
                                <CategoryBadge category={post.category} />
                            </div>
                            <h1 className="relative break-words font-display text-2xl font-bold tracking-tight text-slate-900 md:text-3xl">{post.title}</h1>
                            <div className="relative mt-5 flex flex-wrap items-center justify-between gap-3">
                                <div className="flex items-center gap-2.5">
                                    {post.writerProfileImageUrl ? (
                                        <img src={post.writerProfileImageUrl} alt="" className="h-10 w-10 rounded-2xl bg-slate-100 object-cover" />
                                    ) : (
                                        <span
                                            className={`flex h-10 w-10 items-center justify-center rounded-2xl text-sm font-bold ${
                                                post.writerType === "ADMIN" ? "bg-rose-100 text-rose-600" : "bg-sky-100 text-sky-600"
                                            }`}
                                        >
                                            {post.writerName.slice(0, 1)}
                                        </span>
                                    )}
                                    <div>
                                        <p className="text-sm font-semibold text-slate-800">{post.writerName}</p>
                                        <p className="text-xs text-slate-400">
                                            {formatBoardDateTime(post.createDate)}
                                            {post.updateDate && ` · 수정 ${formatBoardDateTime(post.updateDate)}`}
                                        </p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2 text-xs text-slate-500">
                                    <span className="rounded-full bg-slate-100 px-2.5 py-1">👁 {post.viewCount.toLocaleString()}</span>
                                    <span className="rounded-full bg-slate-100 px-2.5 py-1">💬 {post.commentCount.toLocaleString()}</span>
                                </div>
                            </div>
                        </header>

                        {/* 본문 */}
                        <div className="px-5 py-7 md:px-8">
                            <p className="text-[15px] leading-7 text-slate-700 whitespace-pre-wrap break-words">{post.content}</p>

                            {post.images.length > 0 && (
                                <div className="mt-6 grid gap-3 grid-cols-1 sm:grid-cols-2">
                                    {post.images.map((image) => (
                                        <button
                                            key={image.imageId}
                                            onClick={() => setViewerImage(boardImageSrc(image.imageUrl))}
                                            className="block overflow-hidden rounded-2xl border border-white bg-slate-100 shadow-sm"
                                        >
                                            <img
                                                src={boardImageSrc(image.imageUrl)}
                                                alt={image.originalName ?? "첨부 이미지"}
                                                loading="lazy"
                                                className="w-full max-h-96 object-cover hover:opacity-90 transition-opacity"
                                            />
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>

                        {/* 좋아요 */}
                        <div className="flex justify-center pb-7">
                            <button
                                onClick={handleLike}
                                disabled={liking}
                                aria-pressed={post.liked}
                                className={`flex items-center gap-2 rounded-full px-6 py-3 text-sm font-bold transition ${
                                    post.liked
                                        ? "bg-gradient-to-r from-rose-500 to-pink-500 text-white shadow-[0_10px_24px_-10px_rgba(244,63,94,0.8)]"
                                        : "border border-slate-200 bg-white text-slate-500 hover:border-rose-200 hover:text-rose-500"
                                }`}
                            >
                                <span className="text-base">{post.liked ? "♥" : "♡"}</span>
                                좋아요 {post.likeCount.toLocaleString()}
                            </button>
                        </div>
                    </article>

                    {/* 본인 글이면 수정/삭제, 다른 회원 글이면 신고 (운영자 글은 신고 불가) */}
                    {post.mine ? (
                        <div className="flex justify-end gap-2 mt-3">
                            <button
                                onClick={() => navigate(`/boards/${post.postId}/edit`, { state: { backTo } })}
                                className="tp-btn-ghost"
                            >
                                수정
                            </button>
                            <button
                                onClick={handleDelete}
                                className="tp-btn-danger"
                            >
                                삭제
                            </button>
                        </div>
                    ) : (
                        post.writerType === "USER" && (
                            <div className="flex justify-end mt-3">
                                <button
                                    onClick={handleReportClick}
                                    disabled={post.reported}
                                    className="px-3 py-2 rounded-xl text-xs text-slate-400 hover:text-rose-500 disabled:hover:text-slate-400 disabled:cursor-default"
                                >
                                    {post.reported ? "신고 접수됨" : "🚨 신고"}
                                </button>
                            </div>
                        )
                    )}

                    <BoardComments
                        postId={post.postId}
                        onCountChange={(delta) =>
                            setPost((prev) => (prev ? { ...prev, commentCount: Math.max(prev.commentCount + delta, 0) } : prev))
                        }
                    />
                </>
            )}

            {reportOpen && post && (
                <ReportModal
                    postId={post.postId}
                    onClose={() => setReportOpen(false)}
                    onReported={() => {
                        setReportOpen(false)
                        setPost((prev) => (prev ? { ...prev, reported: true } : prev))
                        window.alert("신고가 접수됐어요. 운영자가 확인 후 조치할게요.")
                    }}
                />
            )}

            {/* 이미지 크게 보기 */}
            {viewerImage && (
                <div
                    className="fixed inset-0 z-[60] flex items-center justify-center bg-night-950/90 p-4 backdrop-blur"
                    onClick={() => setViewerImage(null)}
                    role="dialog"
                    aria-label="이미지 크게 보기"
                >
                    <img src={viewerImage} alt="" className="max-h-full max-w-full rounded-2xl" />
                </div>
            )}
        </BoardLayout>
    )
}
