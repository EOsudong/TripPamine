import api, { API_BASE_URL } from "./axios"

// 백엔드 board 도메인(BoardController, dto/*)과 필드명을 1:1로 맞춘 타입 및 API 함수.
//
// - 목록/상세/댓글 목록은 비로그인도 조회 가능 (로그인 상태면 liked/mine 값이 채워짐)
// - 글 작성/수정은 multipart/form-data:
//     request : JSON(Blob, application/json)
//     images  : 이미지 파일 0~5개
// - 첨부 이미지 URL은 "/board-images/xxx.jpg" 처럼 상대경로로 오므로 boardImageSrc()로 백엔드 주소를 붙여서 사용

export type BoardCategory = "NOTICE" | "REVIEW" | "QNA" | "FREE"
export type BoardStatus = "ACTIVE" | "HIDDEN" | "DELETED"
export type BoardWriterType = "USER" | "ADMIN"
export type BoardSearchType = "TITLE" | "CONTENT" | "TITLE_CONTENT" | "WRITER"
export type BoardReportReason = "SPAM" | "ABUSE" | "OBSCENE" | "PRIVACY" | "FALSE_INFO" | "ETC"

export const BOARD_REPORT_REASON_LABEL: Record<BoardReportReason, string> = {
    SPAM: "스팸/광고",
    ABUSE: "욕설/비방",
    OBSCENE: "음란/선정성",
    PRIVACY: "개인정보 노출",
    FALSE_INFO: "허위 정보",
    ETC: "기타",
}

export const BOARD_CATEGORY_LABEL: Record<BoardCategory, string> = {
    NOTICE: "공지사항",
    REVIEW: "여행후기",
    QNA: "질문",
    FREE: "자유",
}

// 회원이 글을 쓸 수 있는 카테고리 (NOTICE는 관리자 전용)
export const USER_WRITABLE_CATEGORIES: BoardCategory[] = ["REVIEW", "QNA", "FREE"]

export const BOARD_SEARCH_TYPE_LABEL: Record<BoardSearchType, string> = {
    TITLE_CONTENT: "제목+내용",
    TITLE: "제목",
    CONTENT: "내용",
    WRITER: "작성자",
}

// 백엔드 BoardImageStorage와 동일한 제한
export const BOARD_MAX_IMAGES = 5
export const BOARD_MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const BOARD_ALLOWED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"]

// BoardPageResponse<T>
export interface BoardPage<T> {
    content: T[]
    totalElements: number
    totalPages: number
    number: number // 현재 페이지 (0부터)
    size: number
    first: boolean
    last: boolean
}

// BoardPostSummaryResponse
export interface BoardPostSummary {
    postId: number
    category: BoardCategory
    categoryLabel: string
    title: string
    writerId: number | null
    writerName: string
    writerType: BoardWriterType
    viewCount: number
    likeCount: number
    commentCount: number
    pinned: boolean
    thumbnailUrl: string | null
    imageCount: number
    status: BoardStatus
    createDate: string
}

// BoardImageResponse
export interface BoardImage {
    imageId: number
    imageUrl: string
    originalName: string | null
}

// BoardPostDetailResponse
export interface BoardPostDetail {
    postId: number
    category: BoardCategory
    categoryLabel: string
    title: string
    content: string
    writerId: number | null
    writerName: string
    writerProfileImageUrl: string | null
    writerType: BoardWriterType
    viewCount: number
    likeCount: number
    commentCount: number
    pinned: boolean
    status: BoardStatus
    images: BoardImage[]
    liked: boolean
    mine: boolean
    reported: boolean // 내가 이미 신고한 글인지
    createDate: string
    updateDate: string | null
}

// BoardCommentResponse
// 원댓글은 replies에 답글(운영자 답글 등)을 담고, 답글은 replies가 빈 배열.
// 원댓글이 숨김/삭제됐는데 답글이 남아 있으면 status가 ACTIVE가 아니고 content가 안내 문구로 내려온다.
export interface BoardComment {
    commentId: number
    postId: number
    parentId: number | null
    content: string
    writerId: number | null
    writerName: string
    writerProfileImageUrl: string | null
    writerType: BoardWriterType
    mine: boolean
    status: BoardStatus
    createDate: string
    updateDate: string | null
    replies: BoardComment[]
}

// BoardReportRequest
export interface BoardReportRequest {
    reason: BoardReportReason
    detail?: string
}

// BoardLikeResponse
export interface BoardLikeResult {
    liked: boolean
    likeCount: number
}

// BoardPostRequest
export interface BoardPostRequest {
    category: BoardCategory
    title: string
    content: string
    keepImageIds?: number[] // 수정 시: 남겨둘 기존 이미지 ID
}

export interface BoardListParams {
    category?: BoardCategory
    searchType?: BoardSearchType
    keyword?: string
    page?: number
    size?: number
}

function toFormData(request: BoardPostRequest, images: File[]): FormData {
    const formData = new FormData()
    formData.append(
        "request",
        new Blob([JSON.stringify(request)], { type: "application/json" }),
    )
    images.forEach((file) => formData.append("images", file))
    return formData
}

// axios 인스턴스 기본 Content-Type이 application/json이라 그대로 두면 FormData가 JSON으로 바뀌어 버린다.
// multipart/form-data로 명시하면 브라우저가 boundary를 붙여서 올바르게 전송한다.
const MULTIPART = { headers: { "Content-Type": "multipart/form-data" } }

export const boardApi = {
    getPosts: async (params: BoardListParams): Promise<BoardPage<BoardPostSummary>> => {
        const response = await api.get<BoardPage<BoardPostSummary>>("/boards", {
            params: {
                category: params.category || undefined,
                searchType: params.keyword ? params.searchType : undefined,
                keyword: params.keyword || undefined,
                page: params.page ?? 0,
                size: params.size ?? 10,
            },
        })
        return response.data
    },

    getPost: async (postId: number): Promise<BoardPostDetail> => {
        const response = await api.get<BoardPostDetail>(`/boards/${postId}`)
        return response.data
    },

    // 수정 화면용 (본인 글만, 조회수 증가 없음)
    getPostForEdit: async (postId: number): Promise<BoardPostDetail> => {
        const response = await api.get<BoardPostDetail>(`/boards/${postId}/edit`)
        return response.data
    },

    createPost: async (request: BoardPostRequest, images: File[]): Promise<BoardPostDetail> => {
        const response = await api.post<BoardPostDetail>(
            "/boards",
            toFormData(request, images),
            MULTIPART,
        )
        return response.data
    },

    updatePost: async (
        postId: number,
        request: BoardPostRequest,
        images: File[],
    ): Promise<BoardPostDetail> => {
        const response = await api.put<BoardPostDetail>(
            `/boards/${postId}`,
            toFormData(request, images),
            MULTIPART,
        )
        return response.data
    },

    deletePost: async (postId: number): Promise<void> => {
        await api.delete(`/boards/${postId}`)
    },

    toggleLike: async (postId: number): Promise<BoardLikeResult> => {
        const response = await api.post<BoardLikeResult>(`/boards/${postId}/likes`)
        return response.data
    },

    // 게시글 신고 (회원당 1회, 사유 ETC는 detail 필수)
    reportPost: async (postId: number, request: BoardReportRequest): Promise<void> => {
        await api.post(`/boards/${postId}/reports`, request)
    },

    getComments: async (
        postId: number,
        page = 0,
        size = 20,
    ): Promise<BoardPage<BoardComment>> => {
        const response = await api.get<BoardPage<BoardComment>>(
            `/boards/${postId}/comments`,
            { params: { page, size } },
        )
        return response.data
    },

    createComment: async (postId: number, content: string): Promise<BoardComment> => {
        const response = await api.post<BoardComment>(`/boards/${postId}/comments`, { content })
        return response.data
    },

    updateComment: async (commentId: number, content: string): Promise<BoardComment> => {
        const response = await api.put<BoardComment>(`/boards/comments/${commentId}`, { content })
        return response.data
    },

    deleteComment: async (commentId: number): Promise<void> => {
        await api.delete(`/boards/comments/${commentId}`)
    },
}

// "/board-images/xxx.jpg" → "http://localhost:8080/board-images/xxx.jpg"
export function boardImageSrc(url: string | null | undefined): string {
    if (!url) return ""
    if (/^https?:\/\//.test(url)) return url
    return `${API_BASE_URL}${url}`
}

// 오늘 쓴 글은 "14:05", 그 외는 "2026.10.06"
export function formatBoardDate(value: string | null | undefined): string {
    if (!value) return ""
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value.slice(0, 10)
    const now = new Date()
    const pad = (n: number) => String(n).padStart(2, "0")
    if (date.toDateString() === now.toDateString()) {
        return `${pad(date.getHours())}:${pad(date.getMinutes())}`
    }
    return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())}`
}

export function formatBoardDateTime(value: string | null | undefined): string {
    if (!value) return ""
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const pad = (n: number) => String(n).padStart(2, "0")
    return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 백엔드 BoardExceptionHandler가 내려주는 { status, message }에서 메시지 꺼내기
export function extractBoardErrorMessage(err: unknown, fallback: string): string {
    const anyErr = err as { response?: { status?: number; data?: { message?: string } } }
    // 업로드 용량 초과는 Spring이 컨트롤러 도달 전에 413으로 막아서 message가 없을 수 있다
    if (anyErr?.response?.status === 413) return "첨부한 이미지 용량이 너무 커요. 장수나 크기를 줄여주세요."
    return anyErr?.response?.data?.message ?? fallback
}



