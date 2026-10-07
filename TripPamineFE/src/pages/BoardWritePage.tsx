// 게시글 작성/수정 페이지 (로그인 필요 - Router에서 ProtectedRoute로 감쌈)
// - /boards/write          : 새 글 작성
// - /boards/:postId/edit   : 본인 글 수정 (기존 이미지 유지/삭제 + 새 이미지 추가)
import { useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from "react"
import { useLocation, useNavigate, useParams } from "react-router-dom"
import BoardLayout from "../components/board/BoardLayout"
import {
    BOARD_ALLOWED_IMAGE_TYPES,
    BOARD_CATEGORY_LABEL,
    BOARD_MAX_IMAGE_BYTES,
    BOARD_MAX_IMAGES,
    USER_WRITABLE_CATEGORIES,
    boardApi,
    boardImageSrc,
    extractBoardErrorMessage,
    type BoardCategory,
    type BoardImage,
} from "../api/board"

const TITLE_MAX = 200
const CONTENT_MAX = 20000

interface NewImage {
    key: string
    file: File
    previewUrl: string
}

export default function BoardWritePage() {
    const { postId: postIdParam } = useParams()
    const isEdit = postIdParam !== undefined
    const postId = Number(postIdParam)
    const navigate = useNavigate()
    const location = useLocation()
    const state = location.state as { category?: BoardCategory; backTo?: string } | null

    const [category, setCategory] = useState<BoardCategory>(
        state?.category && USER_WRITABLE_CATEGORIES.includes(state.category) ? state.category : "REVIEW",
    )
    const [title, setTitle] = useState("")
    const [content, setContent] = useState("")
    const [existingImages, setExistingImages] = useState<BoardImage[]>([]) // 수정 시 서버에 이미 있는 이미지
    const [newImages, setNewImages] = useState<NewImage[]>([])

    const [loading, setLoading] = useState(isEdit)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    // 수정 모드: 기존 글 불러오기
    useEffect(() => {
        if (!isEdit) return
        if (!Number.isFinite(postId)) {
            setError("잘못된 주소입니다.")
            setLoading(false)
            return
        }
        boardApi
            .getPostForEdit(postId)
            .then((post) => {
                setCategory(post.category)
                setTitle(post.title)
                setContent(post.content)
                setExistingImages(post.images)
            })
            .catch((err) => setError(extractBoardErrorMessage(err, "게시글을 불러오지 못했어요.")))
            .finally(() => setLoading(false))
    }, [isEdit, postId])

    // 페이지를 떠날 때 미리보기용 blob URL 정리
    const newImagesRef = useRef<NewImage[]>([])
    newImagesRef.current = newImages
    useEffect(() => {
        return () => newImagesRef.current.forEach((img) => URL.revokeObjectURL(img.previewUrl))
    }, [])

    const imageCount = existingImages.length + newImages.length
    const remainingSlots = BOARD_MAX_IMAGES - imageCount

    const canSubmit = useMemo(
        () => !submitting && !loading && title.trim().length > 0 && content.trim().length > 0,
        [submitting, loading, title, content],
    )

    function handleFiles(e: ChangeEvent<HTMLInputElement>) {
        const files = Array.from(e.target.files ?? [])
        e.target.value = "" // 같은 파일을 다시 고를 수 있도록 초기화
        if (files.length === 0) return

        const accepted: NewImage[] = []
        for (const file of files) {
            if (accepted.length >= remainingSlots) {
                setError(`이미지는 최대 ${BOARD_MAX_IMAGES}장까지 첨부할 수 있어요.`)
                break
            }
            if (!BOARD_ALLOWED_IMAGE_TYPES.includes(file.type)) {
                setError("jpg, png, gif, webp 형식의 이미지만 첨부할 수 있어요.")
                continue
            }
            if (file.size > BOARD_MAX_IMAGE_BYTES) {
                setError(`'${file.name}'은(는) 5MB를 넘어서 첨부할 수 없어요.`)
                continue
            }
            accepted.push({
                key: `${file.name}-${file.lastModified}-${Math.random().toString(36).slice(2)}`,
                file,
                previewUrl: URL.createObjectURL(file),
            })
        }
        setNewImages((prev) => [...prev, ...accepted])
    }

    function removeNewImage(key: string) {
        setNewImages((prev) => {
            const target = prev.find((img) => img.key === key)
            if (target) URL.revokeObjectURL(target.previewUrl)
            return prev.filter((img) => img.key !== key)
        })
    }

    async function handleSubmit(e: FormEvent) {
        e.preventDefault()
        if (!canSubmit) return

        setSubmitting(true)
        setError(null)
        const request = {
            category,
            title: title.trim(),
            content,
            keepImageIds: existingImages.map((img) => img.imageId),
        }
        const files = newImages.map((img) => img.file)

        try {
            const saved = isEdit
                ? await boardApi.updatePost(postId, request, files)
                : await boardApi.createPost(request, files)
            // 상세 페이지로 이동 (replace: 뒤로가기 시 작성 화면으로 돌아오지 않게)
            navigate(`/boards/${saved.postId}`, { replace: true, state: { backTo: state?.backTo ?? "/boards" } })
        } catch (err) {
            setError(extractBoardErrorMessage(err, isEdit ? "게시글 수정에 실패했어요." : "게시글 등록에 실패했어요."))
            setSubmitting(false)
        }
    }

    return (
        <BoardLayout>
            <div className="mb-6">
                <p className="text-sky-500 text-xs font-semibold tracking-widest uppercase mb-1">Community</p>
                <h2 className="text-2xl font-bold text-slate-900">{isEdit ? "글 수정" : "글쓰기"}</h2>
            </div>

            {loading ? (
                <div className="h-96 rounded-2xl bg-white border border-slate-100 animate-pulse" />
            ) : (
                <form onSubmit={handleSubmit} className="bg-white rounded-2xl border border-slate-100 p-5 md:p-7 space-y-5">
                    {/* 카테고리 */}
                    <div>
                        <label className="block text-sm font-semibold text-slate-700 mb-2">카테고리</label>
                        <div className="flex gap-2 flex-wrap">
                            {USER_WRITABLE_CATEGORIES.map((key) => (
                                <button
                                    key={key}
                                    type="button"
                                    onClick={() => setCategory(key)}
                                    className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                                        category === key
                                            ? "bg-sky-500 text-white shadow-sm"
                                            : "bg-white text-slate-600 border border-slate-200 hover:border-sky-300 hover:text-sky-500"
                                    }`}
                                >
                                    {BOARD_CATEGORY_LABEL[key]}
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* 제목 */}
                    <div>
                        <label htmlFor="board-title" className="block text-sm font-semibold text-slate-700 mb-2">
                            제목
                        </label>
                        <input
                            id="board-title"
                            value={title}
                            onChange={(e) => setTitle(e.target.value.slice(0, TITLE_MAX))}
                            placeholder="제목을 입력하세요"
                            className="w-full h-11 px-3 rounded-xl border border-slate-200 text-sm focus:outline-none focus:border-sky-400"
                        />
                    </div>

                    {/* 내용 */}
                    <div>
                        <label htmlFor="board-content" className="block text-sm font-semibold text-slate-700 mb-2">
                            내용
                        </label>
                        <textarea
                            id="board-content"
                            value={content}
                            onChange={(e) => setContent(e.target.value.slice(0, CONTENT_MAX))}
                            placeholder="여행 이야기를 자유롭게 들려주세요"
                            rows={14}
                            className="w-full px-3 py-3 rounded-xl border border-slate-200 text-sm leading-6 resize-y focus:outline-none focus:border-sky-400"
                        />
                        <p className="text-right text-xs text-slate-400 mt-1">
                            {content.length.toLocaleString()}/{CONTENT_MAX.toLocaleString()}
                        </p>
                    </div>

                    {/* 이미지 */}
                    <div>
                        <div className="flex items-center justify-between mb-2">
                            <label className="text-sm font-semibold text-slate-700">
                                이미지 <span className="font-normal text-slate-400">({imageCount}/{BOARD_MAX_IMAGES})</span>
                            </label>
                            <span className="text-xs text-slate-400">jpg·png·gif·webp, 장당 5MB 이하</span>
                        </div>
                        <div className="grid grid-cols-3 sm:grid-cols-5 gap-2">
                            {existingImages.map((img) => (
                                <Thumb
                                    key={`e-${img.imageId}`}
                                    src={boardImageSrc(img.imageUrl)}
                                    onRemove={() => setExistingImages((prev) => prev.filter((i) => i.imageId !== img.imageId))}
                                />
                            ))}
                            {newImages.map((img) => (
                                <Thumb key={img.key} src={img.previewUrl} isNew onRemove={() => removeNewImage(img.key)} />
                            ))}
                            {remainingSlots > 0 && (
                                <label className="aspect-square rounded-xl border-2 border-dashed border-slate-200 flex flex-col items-center justify-center text-slate-400 text-xs cursor-pointer hover:border-sky-300 hover:text-sky-500 transition-colors">
                                    <span className="text-xl leading-none mb-1">+</span>
                                    사진 추가
                                    <input
                                        type="file"
                                        accept={BOARD_ALLOWED_IMAGE_TYPES.join(",")}
                                        multiple
                                        onChange={handleFiles}
                                        className="sr-only"
                                    />
                                </label>
                            )}
                        </div>
                    </div>

                    {error && (
                        <p className="text-sm text-rose-500 bg-rose-50 border border-rose-100 rounded-xl px-4 py-2.5">{error}</p>
                    )}

                    <div className="flex justify-end gap-2 pt-2">
                        <button
                            type="button"
                            onClick={() => navigate(-1)}
                            className="px-5 py-2.5 rounded-xl border border-slate-200 text-sm text-slate-600 hover:border-slate-400"
                        >
                            취소
                        </button>
                        <button
                            type="submit"
                            disabled={!canSubmit}
                            className="px-5 py-2.5 rounded-xl bg-sky-500 text-white text-sm font-semibold hover:bg-sky-600 disabled:bg-slate-200 disabled:text-slate-400 transition-colors"
                        >
                            {submitting ? "저장 중..." : isEdit ? "수정 완료" : "등록"}
                        </button>
                    </div>
                </form>
            )}
        </BoardLayout>
    )
}

function Thumb({ src, onRemove, isNew = false }: { src: string; onRemove: () => void; isNew?: boolean }) {
    return (
        <div className="relative aspect-square rounded-xl overflow-hidden bg-slate-100 border border-slate-100">
            <img src={src} alt="" className="w-full h-full object-cover" />
            {isNew && (
                <span className="absolute left-1 top-1 px-1.5 py-0.5 rounded bg-sky-500 text-white text-[10px] font-semibold">NEW</span>
            )}
            <button
                type="button"
                onClick={onRemove}
                aria-label="이미지 삭제"
                className="absolute right-1 top-1 w-6 h-6 rounded-full bg-black/60 text-white text-xs flex items-center justify-center hover:bg-black/80"
            >
                ✕
            </button>
        </div>
    )
}
