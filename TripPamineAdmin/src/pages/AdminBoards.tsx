// 게시판 관리 화면.
// - 전체 게시글(숨김/삭제 포함) 목록 + 카테고리/상태/검색 필터
// - 공지 작성/수정 (관리자 작성 글만 수정 가능, 이미지 첨부, 상단 고정)
// - 게시글 숨김/노출/삭제/복구, 상단 고정 토글
// - 상세 보기에서 댓글 숨김/삭제/복구
// 사용하는 백엔드 API: /admin/boards/** (AdminBoardController)
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react"
import AdminLayout from "../components/AdminLayout"
import AdminBoardComments from "../components/AdminBoardComments"
import { ApiError } from "../api/client"
import {
    changeBoardPostPinned,
    changeBoardPostStatus,
    createBoardPost,
    getBoardPost,
    getBoardPosts,
    updateBoardPost,
} from "../api/boards"
import type {
    AdminBoardImage,
    AdminBoardPostDetail,
    AdminBoardPostSummary,
    BoardCategory,
    BoardSearchType,
    BoardStatus,
} from "../types/api"

const categoryLabel: Record<BoardCategory, string> = {
    NOTICE: "공지사항",
    REVIEW: "여행후기",
    QNA: "질문",
    FREE: "자유",
}

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

const searchTypeLabel: Record<BoardSearchType, string> = {
    TITLE_CONTENT: "제목+내용",
    TITLE: "제목",
    CONTENT: "내용",
    WRITER: "작성자",
}

const MAX_IMAGES = 5
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"]

function errorMessage(err: unknown, fallback: string) {
    return err instanceof ApiError ? err.message : fallback
}

function formatDate(value: string | null | undefined) {
    if (!value) return "-"
    return value.slice(0, 16).replace("T", " ")
}

export default function AdminBoards() {
    const [posts, setPosts] = useState<AdminBoardPostSummary[]>([])
    const [page, setPage] = useState(0)
    const [totalPages, setTotalPages] = useState(0)
    const [totalElements, setTotalElements] = useState(0)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    // 필터 (적용된 값)
    const [category, setCategory] = useState<BoardCategory | "">("")
    const [status, setStatus] = useState<BoardStatus | "">("")
    const [searchType, setSearchType] = useState<BoardSearchType>("TITLE_CONTENT")
    const [keyword, setKeyword] = useState("")
    const [keywordInput, setKeywordInput] = useState("")

    const [busyId, setBusyId] = useState<number | null>(null) // 처리 중인 게시글 (버튼 중복 클릭 방지)
    const [detailId, setDetailId] = useState<number | null>(null)
    const [editor, setEditor] = useState<{ mode: "create" } | { mode: "edit"; post: AdminBoardPostDetail } | null>(null)

    async function loadPosts(targetPage: number = page) {
        setLoading(true)
        setError(null)
        try {
            const result = await getBoardPosts({ category, status, searchType, keyword, page: targetPage })
            setPosts(result.content)
            setTotalPages(result.totalPages)
            setTotalElements(result.totalElements)
        } catch (err) {
            setError(errorMessage(err, "게시글 목록을 불러오지 못했습니다."))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadPosts(page)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [page, category, status, keyword, searchType])

    function handleSearch(e: FormEvent) {
        e.preventDefault()
        setPage(0)
        setKeyword(keywordInput.trim())
    }

    async function handleStatus(post: AdminBoardPostSummary, next: BoardStatus) {
        if (next === "DELETED" && !window.confirm(`"${post.title}" 글을 삭제 처리할까요? (복구 가능)`)) return
        setBusyId(post.postId)
        setError(null)
        try {
            await changeBoardPostStatus(post.postId, next)
            await loadPosts(page)
        } catch (err) {
            setError(errorMessage(err, "상태 변경에 실패했습니다."))
        } finally {
            setBusyId(null)
        }
    }

    async function handlePin(post: AdminBoardPostSummary) {
        setBusyId(post.postId)
        setError(null)
        try {
            await changeBoardPostPinned(post.postId, !post.pinned)
            await loadPosts(page)
        } catch (err) {
            setError(errorMessage(err, "상단 고정 변경에 실패했습니다."))
        } finally {
            setBusyId(null)
        }
    }

    async function openEditor(postId: number) {
        setError(null)
        try {
            const post = await getBoardPost(postId)
            setDetailId(null)
            setEditor({ mode: "edit", post })
        } catch (err) {
            setError(errorMessage(err, "게시글을 불러오지 못했습니다."))
        }
    }

    return (
        <AdminLayout>
            <div className="flex items-start justify-between gap-4 mb-6">
                <div>
                    <h1 className="text-xl font-bold text-slate-800 mb-1">게시판 관리</h1>
                    <p className="text-sm text-slate-500">
                        공지를 작성하고, 문제가 있는 게시글·댓글을 숨기거나 삭제할 수 있어요. 삭제는 소프트 삭제라 언제든 복구할 수 있습니다.
                    </p>
                </div>
                <button
                    onClick={() => setEditor({ mode: "create" })}
                    className="shrink-0 px-4 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-600 text-white text-sm font-semibold transition-colors"
                >
                    + 공지 작성
                </button>
            </div>

            {/* 필터 */}
            <form onSubmit={handleSearch} className="bg-white rounded-2xl border border-slate-200 p-4 mb-4 flex flex-wrap gap-2 items-center">
                <select
                    value={category}
                    onChange={(e) => {
                        setPage(0)
                        setCategory(e.target.value as BoardCategory | "")
                    }}
                    className="h-9 px-3 rounded-lg border border-slate-200 text-sm text-slate-700"
                    aria-label="카테고리"
                >
                    <option value="">전체 카테고리</option>
                    {(Object.keys(categoryLabel) as BoardCategory[]).map((key) => (
                        <option key={key} value={key}>{categoryLabel[key]}</option>
                    ))}
                </select>
                <select
                    value={status}
                    onChange={(e) => {
                        setPage(0)
                        setStatus(e.target.value as BoardStatus | "")
                    }}
                    className="h-9 px-3 rounded-lg border border-slate-200 text-sm text-slate-700"
                    aria-label="상태"
                >
                    <option value="">전체 상태</option>
                    {(Object.keys(statusLabel) as BoardStatus[]).map((key) => (
                        <option key={key} value={key}>{statusLabel[key]}</option>
                    ))}
                </select>
                <select
                    value={searchType}
                    onChange={(e) => setSearchType(e.target.value as BoardSearchType)}
                    className="h-9 px-3 rounded-lg border border-slate-200 text-sm text-slate-700"
                    aria-label="검색 기준"
                >
                    {(Object.keys(searchTypeLabel) as BoardSearchType[]).map((key) => (
                        <option key={key} value={key}>{searchTypeLabel[key]}</option>
                    ))}
                </select>
                <input
                    value={keywordInput}
                    onChange={(e) => setKeywordInput(e.target.value)}
                    placeholder="검색어"
                    className="flex-1 min-w-40 h-9 px-3 rounded-lg border border-slate-200 text-sm"
                />
                <button type="submit" className="h-9 px-4 rounded-lg bg-slate-800 text-white text-sm font-semibold hover:bg-slate-700">
                    검색
                </button>
                <span className="text-xs text-slate-400 ml-auto">총 {totalElements.toLocaleString()}건</span>
            </form>

            {error && (
                <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2.5 mb-4">{error}</p>
            )}

            <div className="bg-white rounded-2xl border border-slate-200 overflow-x-auto">
                <table className="w-full text-sm min-w-[880px]">
                    <thead>
                    <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
                        <th className="px-4 py-3 w-16">번호</th>
                        <th className="px-4 py-3 w-24">카테고리</th>
                        <th className="px-4 py-3">제목</th>
                        <th className="px-4 py-3 w-32">작성자</th>
                        <th className="px-4 py-3 w-28">조회/좋아요/댓글</th>
                        <th className="px-4 py-3 w-20">상태</th>
                        <th className="px-4 py-3 w-36">작성일</th>
                        <th className="px-4 py-3 w-56 text-right">관리</th>
                    </tr>
                    </thead>
                    <tbody>
                    {loading ? (
                        <tr>
                            <td colSpan={8} className="px-5 py-10 text-center text-slate-400">불러오는 중...</td>
                        </tr>
                    ) : posts.length === 0 ? (
                        <tr>
                            <td colSpan={8} className="px-5 py-10 text-center text-slate-400">게시글이 없습니다.</td>
                        </tr>
                    ) : (
                        posts.map((post) => (
                            <tr key={post.postId} className="border-b border-slate-100 last:border-0 align-top">
                                <td className="px-4 py-3.5 text-slate-400">{post.postId}</td>
                                <td className="px-4 py-3.5 text-slate-600">{categoryLabel[post.category]}</td>
                                <td className="px-4 py-3.5">
                                    <button
                                        onClick={() => setDetailId(post.postId)}
                                        className="text-left text-slate-800 font-medium hover:text-indigo-600 hover:underline"
                                    >
                                        {post.pinned && <span className="mr-1">📌</span>}
                                        {post.title}
                                        {post.imageCount > 0 && <span className="ml-1 text-xs text-slate-400">🖼{post.imageCount}</span>}
                                        {(post.reportCount ?? 0) > 0 && (
                                            <span className="ml-1.5 px-1.5 py-0.5 rounded bg-red-50 text-red-600 text-[11px] font-semibold">
                                                신고 {post.reportCount}
                                            </span>
                                        )}
                                    </button>
                                </td>
                                <td className="px-4 py-3.5">
                        <span className={post.writerType === "ADMIN" ? "text-indigo-600 font-semibold" : "text-slate-600"}>
                          {post.writerName}
                        </span>
                                </td>
                                <td className="px-4 py-3.5 text-slate-500 text-xs">
                                    {post.viewCount} / {post.likeCount} / {post.commentCount}
                                </td>
                                <td className="px-4 py-3.5">
                        <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusStyle[post.status]}`}>
                          {statusLabel[post.status]}
                        </span>
                                </td>
                                <td className="px-4 py-3.5 text-slate-500 text-xs">{formatDate(post.createDate)}</td>
                                <td className="px-4 py-3.5 text-right whitespace-nowrap">
                                    <RowButton disabled={busyId === post.postId} onClick={() => handlePin(post)} tone="slate">
                                        {post.pinned ? "고정해제" : "고정"}
                                    </RowButton>
                                    {post.writerType === "ADMIN" && (
                                        <RowButton disabled={busyId === post.postId} onClick={() => openEditor(post.postId)} tone="indigo">
                                            수정
                                        </RowButton>
                                    )}
                                    {post.status === "ACTIVE" ? (
                                        <RowButton disabled={busyId === post.postId} onClick={() => handleStatus(post, "HIDDEN")} tone="amber">
                                            숨김
                                        </RowButton>
                                    ) : (
                                        <RowButton disabled={busyId === post.postId} onClick={() => handleStatus(post, "ACTIVE")} tone="emerald">
                                            {post.status === "DELETED" ? "복구" : "노출"}
                                        </RowButton>
                                    )}
                                    {post.status !== "DELETED" && (
                                        <RowButton disabled={busyId === post.postId} onClick={() => handleStatus(post, "DELETED")} tone="red">
                                            삭제
                                        </RowButton>
                                    )}
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
                        className="px-3 py-1.5 rounded-lg text-sm text-slate-500 hover:bg-slate-100 disabled:opacity-30 transition-colors"
                    >
                        이전
                    </button>
                    <span className="text-sm text-slate-500">{page + 1} / {totalPages}</span>
                    <button
                        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                        className="px-3 py-1.5 rounded-lg text-sm text-slate-500 hover:bg-slate-100 disabled:opacity-30 transition-colors"
                    >
                        다음
                    </button>
                </div>
            )}

            {detailId !== null && (
                <PostDetailModal
                    postId={detailId}
                    onClose={() => setDetailId(null)}
                    onEdit={(postId) => openEditor(postId)}
                    onCommentChanged={() => loadPosts(page)}
                />
            )}

            {editor && (
                <PostEditorModal
                    initial={editor.mode === "edit" ? editor.post : null}
                    onClose={() => setEditor(null)}
                    onSaved={() => {
                        setEditor(null)
                        loadPosts(editor.mode === "create" ? 0 : page)
                        if (editor.mode === "create") setPage(0)
                    }}
                />
            )}
        </AdminLayout>
    )
}

const toneClass = {
    slate: "text-slate-600 hover:bg-slate-100",
    indigo: "text-indigo-600 hover:bg-indigo-50",
    amber: "text-amber-600 hover:bg-amber-50",
    emerald: "text-emerald-600 hover:bg-emerald-50",
    red: "text-red-600 hover:bg-red-50",
}

function RowButton({
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
            onClick={onClick}
            disabled={disabled}
            className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-colors disabled:text-slate-300 disabled:hover:bg-transparent ${toneClass[tone]}`}
        >
            {children}
        </button>
    )
}

// ===================== 상세 + 댓글 관리 모달 =====================

function PostDetailModal({
                             postId,
                             onClose,
                             onEdit,
                             onCommentChanged,
                         }: {
    postId: number
    onClose: () => void
    onEdit: (postId: number) => void
    onCommentChanged: () => void
}) {
    const [post, setPost] = useState<AdminBoardPostDetail | null>(null)
    const [error, setError] = useState<string | null>(null)

    async function load() {
        try {
            setPost(await getBoardPost(postId))
        } catch (err) {
            setError(errorMessage(err, "게시글을 불러오지 못했습니다."))
        }
    }

    useEffect(() => {
        load()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [postId])

    return (
        <div className="fixed inset-0 bg-slate-900/50 flex items-center justify-center p-4 z-50" onClick={onClose}>
            <div className="bg-white rounded-2xl w-full max-w-3xl max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                <div className="sticky top-0 bg-white border-b border-slate-100 px-6 py-4 flex items-center justify-between">
                    <h2 className="font-bold text-slate-800">게시글 상세</h2>
                    <div className="flex gap-2">
                        {post?.writerType === "ADMIN" && (
                            <button onClick={() => onEdit(postId)} className="px-3 py-1.5 rounded-lg text-sm font-semibold text-indigo-600 hover:bg-indigo-50">
                                수정
                            </button>
                        )}
                        <button onClick={onClose} className="px-3 py-1.5 rounded-lg text-sm text-slate-500 hover:bg-slate-100">
                            닫기
                        </button>
                    </div>
                </div>

                <div className="p-6">
                    {error && <p className="text-sm text-red-600 mb-3">{error}</p>}
                    {!post ? (
                        !error && <p className="text-sm text-slate-400">불러오는 중...</p>
                    ) : (
                        <>
                            <div className="flex items-center gap-2 mb-2 text-xs">
                                <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-600 font-semibold">{categoryLabel[post.category]}</span>
                                <span className={`px-2 py-0.5 rounded-full font-semibold ${statusStyle[post.status]}`}>{statusLabel[post.status]}</span>
                                {post.pinned && <span>📌 상단 고정</span>}
                            </div>
                            <h3 className="text-lg font-bold text-slate-900 mb-1">{post.title}</h3>
                            <p className="text-xs text-slate-400 mb-5">
                                {post.writerName} · {formatDate(post.createDate)} · 조회 {post.viewCount} · 좋아요 {post.likeCount}
                            </p>
                            <p className="text-sm text-slate-700 leading-6 whitespace-pre-wrap break-words">{post.content}</p>

                            {post.images.length > 0 && (
                                <div className="grid grid-cols-3 gap-2 mt-5">
                                    {post.images.map((img) => (
                                        <a key={img.imageId} href={img.imageUrl} target="_blank" rel="noreferrer" className="block aspect-square rounded-lg overflow-hidden bg-slate-100">
                                            <img src={img.imageUrl} alt={img.originalName ?? ""} className="w-full h-full object-cover" />
                                        </a>
                                    ))}
                                </div>
                            )}

                            <AdminBoardComments
                                postId={post.postId}
                                canWrite={post.status !== "DELETED"}
                                onChanged={onCommentChanged}
                            />
                        </>
                    )}
                </div>
            </div>
        </div>
    )
}

// ===================== 작성/수정 모달 =====================

interface NewImage {
    key: string
    file: File
    previewUrl: string
}

function PostEditorModal({
                             initial,
                             onClose,
                             onSaved,
                         }: {
    initial: AdminBoardPostDetail | null
    onClose: () => void
    onSaved: () => void
}) {
    const isEdit = initial !== null
    const [category, setCategory] = useState<BoardCategory>(initial?.category ?? "NOTICE")
    const [title, setTitle] = useState(initial?.title ?? "")
    const [content, setContent] = useState(initial?.content ?? "")
    const [pinned, setPinned] = useState(initial?.pinned ?? false)
    const [existingImages, setExistingImages] = useState<AdminBoardImage[]>(initial?.images ?? [])
    const [newImages, setNewImages] = useState<NewImage[]>([])
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const newImagesRef = useRef<NewImage[]>([])
    newImagesRef.current = newImages
    useEffect(() => () => newImagesRef.current.forEach((img) => URL.revokeObjectURL(img.previewUrl)), [])

    const remaining = MAX_IMAGES - existingImages.length - newImages.length

    function handleFiles(files: FileList | null) {
        if (!files) return
        const accepted: NewImage[] = []
        for (const file of Array.from(files)) {
            if (accepted.length >= remaining) {
                setError(`이미지는 최대 ${MAX_IMAGES}장까지 첨부할 수 있습니다.`)
                break
            }
            if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
                setError("jpg, png, gif, webp 이미지만 첨부할 수 있습니다.")
                continue
            }
            if (file.size > MAX_IMAGE_BYTES) {
                setError(`'${file.name}'은(는) 5MB를 넘습니다.`)
                continue
            }
            accepted.push({ key: `${file.name}-${Math.random().toString(36).slice(2)}`, file, previewUrl: URL.createObjectURL(file) })
        }
        setNewImages((prev) => [...prev, ...accepted])
    }

    async function handleSubmit(e: FormEvent) {
        e.preventDefault()
        if (!title.trim() || !content.trim()) {
            setError("제목과 내용을 입력해주세요.")
            return
        }
        setSubmitting(true)
        setError(null)
        const request = {
            category,
            title: title.trim(),
            content,
            pinned,
            keepImageIds: existingImages.map((img) => img.imageId),
        }
        const files = newImages.map((img) => img.file)
        try {
            if (isEdit) await updateBoardPost(initial.postId, request, files)
            else await createBoardPost(request, files)
            onSaved()
        } catch (err) {
            setError(errorMessage(err, "저장에 실패했습니다."))
            setSubmitting(false)
        }
    }

    return (
        <div className="fixed inset-0 bg-slate-900/50 flex items-center justify-center p-4 z-50">
            <form onSubmit={handleSubmit} className="bg-white rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto p-6 space-y-4">
                <h2 className="font-bold text-slate-800">{isEdit ? "게시글 수정" : "공지 작성"}</h2>

                <div className="flex gap-3 items-center flex-wrap">
                    <select
                        value={category}
                        onChange={(e) => setCategory(e.target.value as BoardCategory)}
                        className="h-10 px-3 rounded-lg border-2 border-slate-200 text-sm text-slate-700"
                        aria-label="카테고리"
                    >
                        {(Object.keys(categoryLabel) as BoardCategory[]).map((key) => (
                            <option key={key} value={key}>{categoryLabel[key]}</option>
                        ))}
                    </select>
                    <label className="flex items-center gap-2 text-sm text-slate-600">
                        <input type="checkbox" checked={pinned} onChange={(e) => setPinned(e.target.checked)} className="w-4 h-4" />
                        상단 고정
                    </label>
                </div>

                <input
                    value={title}
                    onChange={(e) => setTitle(e.target.value.slice(0, 200))}
                    placeholder="제목"
                    className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm"
                />
                <textarea
                    value={content}
                    onChange={(e) => setContent(e.target.value.slice(0, 20000))}
                    placeholder="내용"
                    rows={12}
                    className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-indigo-400 outline-none text-sm resize-y"
                />

                <div>
                    <p className="text-xs font-semibold text-slate-600 mb-2">
                        이미지 ({MAX_IMAGES - remaining}/{MAX_IMAGES}) <span className="font-normal text-slate-400">jpg·png·gif·webp, 장당 5MB</span>
                    </p>
                    <div className="grid grid-cols-5 gap-2">
                        {existingImages.map((img) => (
                            <EditorThumb key={`e-${img.imageId}`} src={img.imageUrl} onRemove={() => setExistingImages((prev) => prev.filter((i) => i.imageId !== img.imageId))} />
                        ))}
                        {newImages.map((img) => (
                            <EditorThumb
                                key={img.key}
                                src={img.previewUrl}
                                onRemove={() => {
                                    URL.revokeObjectURL(img.previewUrl)
                                    setNewImages((prev) => prev.filter((i) => i.key !== img.key))
                                }}
                            />
                        ))}
                        {remaining > 0 && (
                            <label className="aspect-square rounded-lg border-2 border-dashed border-slate-200 flex items-center justify-center text-slate-400 text-xs cursor-pointer hover:border-indigo-300 hover:text-indigo-500">
                                + 추가
                                <input
                                    type="file"
                                    multiple
                                    accept={ALLOWED_IMAGE_TYPES.join(",")}
                                    className="sr-only"
                                    onChange={(e) => {
                                        handleFiles(e.target.files)
                                        e.target.value = ""
                                    }}
                                />
                            </label>
                        )}
                    </div>
                </div>

                {error && <p className="text-xs text-red-500">{error}</p>}

                <div className="flex gap-2 pt-2">
                    <button
                        type="button"
                        onClick={onClose}
                        className="flex-1 py-2.5 rounded-lg border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50"
                    >
                        취소
                    </button>
                    <button
                        type="submit"
                        disabled={submitting}
                        className="flex-1 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-600 disabled:bg-slate-300 text-white text-sm font-semibold"
                    >
                        {submitting ? "저장 중..." : isEdit ? "수정" : "등록"}
                    </button>
                </div>
            </form>
        </div>
    )
}

function EditorThumb({ src, onRemove }: { src: string; onRemove: () => void }) {
    return (
        <div className="relative aspect-square rounded-lg overflow-hidden bg-slate-100">
            <img src={src} alt="" className="w-full h-full object-cover" />
            <button
                type="button"
                onClick={onRemove}
                aria-label="이미지 삭제"
                className="absolute right-1 top-1 w-5 h-5 rounded-full bg-black/60 text-white text-[10px] flex items-center justify-center"
            >
                ✕
            </button>
        </div>
    )
}



