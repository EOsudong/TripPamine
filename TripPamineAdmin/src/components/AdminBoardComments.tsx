// 게시글 상세 모달 안의 댓글 관리 영역.
// - 원댓글 + 답글(중첩) 목록, 숨김/삭제/복구
// - 댓글에 운영자 답글 달기, 게시글에 운영자 댓글 달기
// - 내가 쓴 운영자 댓글/답글 수정
import { useEffect, useState, type FormEvent, type ReactNode } from "react"
import { ApiError } from "../api/client"
import {
    changeBoardCommentStatus,
    createAdminComment,
    getBoardComments,
    replyToComment,
    updateAdminComment,
} from "../api/boards"
import type { AdminBoardComment, BoardStatus } from "../types/api"

const MAX_LENGTH = 1000

const statusLabel: Record<BoardStatus, string> = {
    ACTIVE: "노출",
    HIDDEN: "숨김",
    DELETED: "삭제",
}

const statusStyle: Record<BoardStatus, string> = {
    ACTIVE: "bg-emerald-50 text-emerald-600",
    HIDDEN: "bg-amber-50 text-amber-600",
    DELETED: "bg-slate-100 text-slate-500",
}

function errorMessage(err: unknown, fallback: string) {
    return err instanceof ApiError ? err.message : fallback
}

function formatDate(value: string | null | undefined) {
    if (!value) return "-"
    return value.slice(0, 16).replace("T", " ")
}

interface AdminBoardCommentsProps {
    postId: number
    canWrite: boolean // 삭제된 게시글이면 운영자 댓글/답글 작성 불가
    onChanged: () => void // 댓글 수가 바뀌면 바깥 목록 새로고침
}

export default function AdminBoardComments({ postId, canWrite, onChanged }: AdminBoardCommentsProps) {
    const [comments, setComments] = useState<AdminBoardComment[]>([])
    const [error, setError] = useState<string | null>(null)
    const [busyId, setBusyId] = useState<number | null>(null)

    const [newComment, setNewComment] = useState("")
    const [replyTargetId, setReplyTargetId] = useState<number | null>(null)
    const [replyContent, setReplyContent] = useState("")
    const [editingId, setEditingId] = useState<number | null>(null)
    const [editingContent, setEditingContent] = useState("")
    const [submitting, setSubmitting] = useState(false)

    async function load() {
        try {
            const page = await getBoardComments(postId)
            setComments(page.content)
        } catch (err) {
            setError(errorMessage(err, "댓글을 불러오지 못했습니다."))
        }
    }

    useEffect(() => {
        load()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [postId])

    // 쓰기 작업 공통: 실행 → 목록 다시 불러오기 → 바깥 목록 갱신
    async function run(action: () => Promise<unknown>, fallback: string, countChanged = true) {
        setError(null)
        setSubmitting(true)
        try {
            await action()
            await load()
            if (countChanged) onChanged()
            return true
        } catch (err) {
            setError(errorMessage(err, fallback))
            return false
        } finally {
            setSubmitting(false)
        }
    }

    async function handleStatus(comment: AdminBoardComment, next: BoardStatus) {
        setBusyId(comment.commentId)
        await run(() => changeBoardCommentStatus(comment.commentId, next), "댓글 상태 변경에 실패했습니다.")
        setBusyId(null)
    }

    async function handleNewComment(e: FormEvent) {
        e.preventDefault()
        if (!newComment.trim()) return
        const ok = await run(() => createAdminComment(postId, newComment.trim()), "댓글 등록에 실패했습니다.")
        if (ok) setNewComment("")
    }

    async function handleReply(e: FormEvent, commentId: number) {
        e.preventDefault()
        if (!replyContent.trim()) return
        const ok = await run(() => replyToComment(commentId, replyContent.trim()), "답글 등록에 실패했습니다.")
        if (ok) {
            setReplyTargetId(null)
            setReplyContent("")
        }
    }

    async function handleEdit(commentId: number) {
        if (!editingContent.trim()) return
        const ok = await run(() => updateAdminComment(commentId, editingContent.trim()), "댓글 수정에 실패했습니다.", false)
        if (ok) setEditingId(null)
    }

    const total = comments.reduce((sum, c) => sum + 1 + c.replies.length, 0)

    function renderItem(comment: AdminBoardComment, isReply: boolean): ReactNode {
        const isAdmin = comment.writerType === "ADMIN"
        const busy = busyId === comment.commentId

        return (
            <div className="flex gap-3">
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 text-xs mb-1 flex-wrap">
                        {isReply && <span className="text-slate-300">↳</span>}
                        <span className={`font-semibold ${isAdmin ? "text-indigo-600" : "text-slate-700"}`}>{comment.writerName}</span>
                        <span className="text-slate-400">{formatDate(comment.createDate)}</span>
                        {comment.updateDate && <span className="text-slate-400">(수정됨)</span>}
                        <span className={`px-1.5 py-0.5 rounded-full font-semibold ${statusStyle[comment.status]}`}>
                            {statusLabel[comment.status]}
                        </span>
                    </div>

                    {editingId === comment.commentId ? (
                        <div>
                            <textarea
                                value={editingContent}
                                onChange={(e) => setEditingContent(e.target.value.slice(0, MAX_LENGTH))}
                                rows={3}
                                className="w-full px-3 py-2 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm resize-none"
                            />
                            <div className="flex justify-end gap-1 mt-1">
                                <SmallButton tone="slate" onClick={() => setEditingId(null)}>취소</SmallButton>
                                <SmallButton tone="indigo" disabled={submitting || !editingContent.trim()} onClick={() => handleEdit(comment.commentId)}>
                                    저장
                                </SmallButton>
                            </div>
                        </div>
                    ) : (
                        <p className={`text-sm whitespace-pre-wrap break-words ${comment.status === "ACTIVE" ? "text-slate-700" : "text-slate-400"}`}>
                            {comment.content}
                        </p>
                    )}
                </div>

                <div className="shrink-0 flex items-start flex-wrap justify-end gap-0.5 max-w-[11rem]">
                    {canWrite && !isReply && (
                        <SmallButton
                            tone="indigo"
                            onClick={() => {
                                setReplyTargetId(replyTargetId === comment.commentId ? null : comment.commentId)
                                setReplyContent("")
                            }}
                        >
                            답글
                        </SmallButton>
                    )}
                    {comment.mine && editingId !== comment.commentId && (
                        <SmallButton
                            tone="slate"
                            onClick={() => {
                                setEditingId(comment.commentId)
                                setEditingContent(comment.content)
                            }}
                        >
                            수정
                        </SmallButton>
                    )}
                    {comment.status === "ACTIVE" ? (
                        <>
                            <SmallButton tone="amber" disabled={busy} onClick={() => handleStatus(comment, "HIDDEN")}>숨김</SmallButton>
                            <SmallButton tone="red" disabled={busy} onClick={() => handleStatus(comment, "DELETED")}>삭제</SmallButton>
                        </>
                    ) : (
                        <SmallButton tone="emerald" disabled={busy} onClick={() => handleStatus(comment, "ACTIVE")}>
                            {comment.status === "DELETED" ? "복구" : "노출"}
                        </SmallButton>
                    )}
                </div>
            </div>
        )
    }

    return (
        <section className="mt-8">
            <h4 className="font-semibold text-slate-800 text-sm mb-3">댓글 {total}</h4>
            {error && <p className="text-sm text-red-600 mb-3">{error}</p>}

            {comments.length === 0 ? (
                <p className="text-sm text-slate-400 mb-4">댓글이 없습니다.</p>
            ) : (
                <ul className="divide-y divide-slate-100 border border-slate-100 rounded-xl mb-4">
                    {comments.map((comment) => (
                        <li key={comment.commentId} className="px-4 py-3">
                            {renderItem(comment, false)}

                            {comment.replies.length > 0 && (
                                <ul className="mt-2 ml-5 space-y-2">
                                    {comment.replies.map((reply) => (
                                        <li
                                            key={reply.commentId}
                                            className={`rounded-lg px-3 py-2 ${reply.writerType === "ADMIN" ? "bg-indigo-50/60" : "bg-slate-50"}`}
                                        >
                                            {renderItem(reply, true)}
                                        </li>
                                    ))}
                                </ul>
                            )}

                            {replyTargetId === comment.commentId && (
                                <form onSubmit={(e) => handleReply(e, comment.commentId)} className="mt-2 ml-5">
                                    <textarea
                                        value={replyContent}
                                        onChange={(e) => setReplyContent(e.target.value.slice(0, MAX_LENGTH))}
                                        rows={2}
                                        autoFocus
                                        placeholder={`${comment.writerName || "이 댓글"}에 운영자로 답글 달기`}
                                        className="w-full px-3 py-2 rounded-lg border-2 border-indigo-200 focus:border-indigo-400 outline-none text-sm resize-none"
                                    />
                                    <div className="flex justify-end gap-1 mt-1">
                                        <SmallButton tone="slate" onClick={() => setReplyTargetId(null)}>취소</SmallButton>
                                        <button
                                            type="submit"
                                            disabled={submitting || !replyContent.trim()}
                                            className="px-3 py-1.5 rounded-lg bg-indigo-500 hover:bg-indigo-600 disabled:bg-slate-300 text-white text-xs font-semibold"
                                        >
                                            답글 등록
                                        </button>
                                    </div>
                                </form>
                            )}
                        </li>
                    ))}
                </ul>
            )}

            {canWrite ? (
                <form onSubmit={handleNewComment} className="border-t border-slate-100 pt-4">
                    <label className="text-xs font-semibold text-slate-600 block mb-1.5">운영자 댓글 작성</label>
                    <textarea
                        value={newComment}
                        onChange={(e) => setNewComment(e.target.value.slice(0, MAX_LENGTH))}
                        rows={2}
                        placeholder="게시글에 운영자 댓글을 남깁니다 (예: 문의 답변, 안내)"
                        className="w-full px-3 py-2 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm resize-none"
                    />
                    <div className="flex justify-end mt-1">
                        <button
                            type="submit"
                            disabled={submitting || !newComment.trim()}
                            className="px-4 py-2 rounded-lg bg-indigo-500 hover:bg-indigo-600 disabled:bg-slate-300 text-white text-xs font-semibold"
                        >
                            댓글 등록
                        </button>
                    </div>
                </form>
            ) : (
                <p className="text-xs text-slate-400">삭제된 게시글에는 댓글을 작성할 수 없습니다.</p>
            )}
        </section>
    )
}

const toneClass = {
    slate: "text-slate-600 hover:bg-slate-100",
    indigo: "text-indigo-600 hover:bg-indigo-50",
    amber: "text-amber-600 hover:bg-amber-50",
    emerald: "text-emerald-600 hover:bg-emerald-50",
    red: "text-red-600 hover:bg-red-50",
}

function SmallButton({
    children,
    onClick,
    disabled,
    tone,
}: {
    children: ReactNode
    onClick: () => void
    disabled?: boolean
    tone: keyof typeof toneClass
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            className={`px-2 py-1 rounded-md text-xs font-semibold transition-colors disabled:text-slate-300 disabled:hover:bg-transparent ${toneClass[tone]}`}
        >
            {children}
        </button>
    )
}
