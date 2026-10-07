// 게시글 상세 페이지. 라우트: /boards/:postId (비로그인도 접근 가능)
// - 본문/첨부 이미지/좋아요/댓글
// - 본인 글이면 수정·삭제 버튼 노출 (백엔드가 내려주는 mine 값 기준)
import { useEffect, useRef, useState } from "react"
import { Link, useLocation, useNavigate, useParams } from "react-router-dom"
import BoardLayout from "../components/board/BoardLayout"
import BoardComments from "../components/board/BoardComments"
import CategoryBadge from "../components/board/CategoryBadge"
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
            <Link to={backTo} className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800 mb-4">
                ← 목록으로
            </Link>

            {loading ? (
                <div className="bg-white rounded-2xl border border-slate-100 p-6 space-y-3">
                    <div className="h-6 w-2/3 rounded bg-slate-100 animate-pulse" />
                    <div className="h-4 w-1/3 rounded bg-slate-100 animate-pulse" />
                    <div className="h-40 rounded bg-slate-100 animate-pulse mt-6" />
                </div>
            ) : error || !post ? (
                <div className="text-center py-16 text-slate-400 text-sm bg-white rounded-2xl border border-dashed border-slate-200">
                    {error ?? "게시글을 찾을 수 없어요."}
                </div>
            ) : (
                <>
                    <article className="bg-white rounded-2xl border border-slate-100 overflow-hidden">
                        {/* 제목 영역 */}
                        <header className="px-5 md:px-7 pt-6 pb-5 border-b border-slate-100">
                            <div className="flex items-center gap-2 mb-2">
                                {post.pinned && <span className="text-xs">📌</span>}
                                <CategoryBadge category={post.category} />
                            </div>
                            <h1 className="text-xl md:text-2xl font-bold text-slate-900 break-words">{post.title}</h1>
                            <div className="flex items-center justify-between gap-3 mt-4 flex-wrap">
                                <div className="flex items-center gap-2.5">
                                    {post.writerProfileImageUrl ? (
                                        <img src={post.writerProfileImageUrl} alt="" className="w-9 h-9 rounded-full object-cover bg-slate-100" />
                                    ) : (
                                        <span
                                            className={`w-9 h-9 rounded-full text-sm font-bold flex items-center justify-center ${
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
                                <div className="flex items-center gap-3 text-xs text-slate-400">
                                    <span>조회 {post.viewCount.toLocaleString()}</span>
                                    <span>댓글 {post.commentCount.toLocaleString()}</span>
                                </div>
                            </div>
                        </header>

                        {/* 본문 */}
                        <div className="px-5 md:px-7 py-6">
                            <p className="text-[15px] leading-7 text-slate-700 whitespace-pre-wrap break-words">{post.content}</p>

                            {post.images.length > 0 && (
                                <div className="mt-6 grid gap-3 grid-cols-1 sm:grid-cols-2">
                                    {post.images.map((image) => (
                                        <button
                                            key={image.imageId}
                                            onClick={() => setViewerImage(boardImageSrc(image.imageUrl))}
                                            className="block rounded-xl overflow-hidden bg-slate-100 border border-slate-100"
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
                                className={`flex items-center gap-2 px-5 py-2.5 rounded-full border text-sm font-semibold transition-colors ${
                                    post.liked
                                        ? "bg-rose-50 border-rose-200 text-rose-500"
                                        : "bg-white border-slate-200 text-slate-500 hover:border-rose-200 hover:text-rose-500"
                                }`}
                            >
                                <span className="text-base">{post.liked ? "♥" : "♡"}</span>
                                좋아요 {post.likeCount.toLocaleString()}
                            </button>
                        </div>
                    </article>

                    {/* 본인 글 관리 */}
                    {post.mine && (
                        <div className="flex justify-end gap-2 mt-3">
                            <button
                                onClick={() => navigate(`/boards/${post.postId}/edit`, { state: { backTo } })}
                                className="px-4 py-2 rounded-xl border border-slate-200 bg-white text-sm text-slate-600 hover:border-slate-400"
                            >
                                수정
                            </button>
                            <button
                                onClick={handleDelete}
                                className="px-4 py-2 rounded-xl border border-rose-200 bg-white text-sm text-rose-500 hover:bg-rose-50"
                            >
                                삭제
                            </button>
                        </div>
                    )}

                    <BoardComments
                        postId={post.postId}
                        onCountChange={(delta) =>
                            setPost((prev) => (prev ? { ...prev, commentCount: Math.max(prev.commentCount + delta, 0) } : prev))
                        }
                    />
                </>
            )}

            {/* 이미지 크게 보기 */}
            {viewerImage && (
                <div
                    className="fixed inset-0 z-[60] bg-black/80 flex items-center justify-center p-4"
                    onClick={() => setViewerImage(null)}
                    role="dialog"
                    aria-label="이미지 크게 보기"
                >
                    <img src={viewerImage} alt="" className="max-w-full max-h-full rounded-lg" />
                </div>
            )}
        </BoardLayout>
    )
}
