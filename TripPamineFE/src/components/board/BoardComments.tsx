// 게시글 상세 하단 댓글 영역: 목록(더보기 페이징) / 작성 / 본인 댓글 수정·삭제
import { useCallback, useEffect, useState, type FormEvent } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { useAuth } from "../../context/AuthContext"
import {
    boardApi,
    extractBoardErrorMessage,
    formatBoardDateTime,
    type BoardComment,
} from "../../api/board"

interface BoardCommentsProps {
    postId: number
    // 댓글 수가 바뀌면 상위(상세 페이지)의 댓글 수 표시도 갱신
    onCountChange: (delta: number) => void
}

const PAGE_SIZE = 20
const MAX_LENGTH = 1000

export default function BoardComments({ postId, onCountChange }: BoardCommentsProps) {
    const { isLoggedIn } = useAuth()
    const navigate = useNavigate()
    const location = useLocation()

    const [comments, setComments] = useState<BoardComment[]>([])
    const [page, setPage] = useState(0)
    const [hasMore, setHasMore] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    const [content, setContent] = useState("")
    const [submitting, setSubmitting] = useState(false)

    const [editingId, setEditingId] = useState<number | null>(null)
    const [editingContent, setEditingContent] = useState("")

    const load = useCallback(
        async (targetPage: number) => {
            setLoading(true)
            setError(null)
            try {
                const data = await boardApi.getComments(postId, targetPage, PAGE_SIZE)
                setComments((prev) => (targetPage === 0 ? data.content : [...prev, ...data.content]))
                setPage(data.number)
                setHasMore(!data.last)
            } catch (err) {
                setError(extractBoardErrorMessage(err, "댓글을 불러오지 못했어요."))
            } finally {
                setLoading(false)
            }
        },
        [postId],
    )

    useEffect(() => {
        load(0)
    }, [load])

    function requireLogin() {
        navigate("/login", { state: { from: location } })
    }

    async function handleSubmit(e: FormEvent) {
        e.preventDefault()
        if (!isLoggedIn) return requireLogin()
        const trimmed = content.trim()
        if (!trimmed) return

        setSubmitting(true)
        setError(null)
        try {
            const created = await boardApi.createComment(postId, trimmed)
            setContent("")
            onCountChange(1)
            // 아직 다 불러오지 않은 상태면 마지막에 붙이지 않고 처음부터 다시 로드
            if (hasMore) await load(0)
            else setComments((prev) => [...prev, created])
        } catch (err) {
            setError(extractBoardErrorMessage(err, "댓글 등록에 실패했어요."))
        } finally {
            setSubmitting(false)
        }
    }

    async function handleUpdate(commentId: number) {
        const trimmed = editingContent.trim()
        if (!trimmed) return
        try {
            const updated = await boardApi.updateComment(commentId, trimmed)
            setComments((prev) => prev.map((c) => (c.commentId === commentId ? updated : c)))
            setEditingId(null)
        } catch (err) {
            setError(extractBoardErrorMessage(err, "댓글 수정에 실패했어요."))
        }
    }

    async function handleDelete(commentId: number) {
        if (!window.confirm("댓글을 삭제할까요?")) return
        try {
            await boardApi.deleteComment(commentId)
            setComments((prev) => prev.filter((c) => c.commentId !== commentId))
            onCountChange(-1)
        } catch (err) {
            setError(extractBoardErrorMessage(err, "댓글 삭제에 실패했어요."))
        }
    }

    return (
        <section className="mt-6 bg-white rounded-2xl border border-slate-100 p-5 md:p-6">
            <h3 className="font-bold text-slate-800 text-sm mb-4">댓글</h3>

            {/* 작성 폼 */}
            <form onSubmit={handleSubmit} className="mb-5">
                <textarea
                    value={content}
                    onChange={(e) => setContent(e.target.value.slice(0, MAX_LENGTH))}
                    onFocus={() => !isLoggedIn && requireLogin()}
                    placeholder={isLoggedIn ? "댓글을 남겨주세요" : "로그인 후 댓글을 작성할 수 있어요"}
                    rows={3}
                    className="w-full px-3 py-2.5 rounded-xl border border-slate-200 text-sm resize-none focus:outline-none focus:border-sky-400"
                />
                <div className="flex items-center justify-between mt-2">
                    <span className="text-xs text-slate-400">
                        {content.length}/{MAX_LENGTH}
                    </span>
                    <button
                        type="submit"
                        disabled={submitting || !content.trim()}
                        className="px-4 py-2 rounded-xl bg-sky-500 text-white text-sm font-semibold hover:bg-sky-600 disabled:bg-slate-200 disabled:text-slate-400 transition-colors"
                    >
                        {submitting ? "등록 중..." : "등록"}
                    </button>
                </div>
            </form>

            {error && <p className="text-sm text-rose-500 mb-3">{error}</p>}

            {/* 목록 */}
            {comments.length === 0 && !loading ? (
                <p className="text-center text-sm text-slate-400 py-6">첫 댓글을 남겨보세요.</p>
            ) : (
                <ul className="divide-y divide-slate-100">
                    {comments.map((comment) => (
                        <li key={comment.commentId} className="py-3.5">
                            <div className="flex items-center justify-between gap-2 mb-1">
                                <div className="flex items-center gap-2 min-w-0">
                                    <Avatar url={comment.writerProfileImageUrl} name={comment.writerName} />
                                    <span className="text-sm font-semibold text-slate-700 truncate">{comment.writerName}</span>
                                    <span className="text-xs text-slate-400 shrink-0">
                                        {formatBoardDateTime(comment.createDate)}
                                        {comment.updateDate && " (수정됨)"}
                                    </span>
                                </div>
                                {comment.mine && editingId !== comment.commentId && (
                                    <div className="flex gap-1 shrink-0">
                                        <button
                                            onClick={() => {
                                                setEditingId(comment.commentId)
                                                setEditingContent(comment.content)
                                            }}
                                            className="px-2 py-1 text-xs text-slate-500 hover:text-slate-800"
                                        >
                                            수정
                                        </button>
                                        <button
                                            onClick={() => handleDelete(comment.commentId)}
                                            className="px-2 py-1 text-xs text-slate-500 hover:text-rose-500"
                                        >
                                            삭제
                                        </button>
                                    </div>
                                )}
                            </div>

                            {editingId === comment.commentId ? (
                                <div className="pl-9">
                                    <textarea
                                        value={editingContent}
                                        onChange={(e) => setEditingContent(e.target.value.slice(0, MAX_LENGTH))}
                                        rows={3}
                                        className="w-full px-3 py-2 rounded-xl border border-slate-200 text-sm resize-none focus:outline-none focus:border-sky-400"
                                    />
                                    <div className="flex justify-end gap-2 mt-1.5">
                                        <button onClick={() => setEditingId(null)} className="px-3 py-1.5 text-xs text-slate-500 hover:text-slate-800">
                                            취소
                                        </button>
                                        <button
                                            onClick={() => handleUpdate(comment.commentId)}
                                            disabled={!editingContent.trim()}
                                            className="px-3 py-1.5 rounded-lg bg-slate-900 text-white text-xs font-semibold disabled:bg-slate-200"
                                        >
                                            저장
                                        </button>
                                    </div>
                                </div>
                            ) : (
                                <p className="pl-9 text-sm text-slate-700 whitespace-pre-wrap break-words">{comment.content}</p>
                            )}
                        </li>
                    ))}
                </ul>
            )}

            {hasMore && (
                <button
                    onClick={() => load(page + 1)}
                    disabled={loading}
                    className="w-full mt-3 py-2.5 rounded-xl border border-slate-200 text-sm text-slate-600 hover:bg-slate-50 disabled:text-slate-300"
                >
                    {loading ? "불러오는 중..." : "댓글 더보기"}
                </button>
            )}
        </section>
    )
}

function Avatar({ url, name }: { url: string | null; name: string }) {
    if (url) {
        return <img src={url} alt="" className="w-7 h-7 rounded-full object-cover bg-slate-100 shrink-0" />
    }
    return (
        <span className="w-7 h-7 rounded-full bg-sky-100 text-sky-600 text-xs font-bold flex items-center justify-center shrink-0">
            {name.slice(0, 1)}
        </span>
    )
}
