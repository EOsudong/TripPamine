import { apiFormRequest, apiRequest } from "./client"
import type {
    AdminBoardComment,
    AdminBoardPostDetail,
    AdminBoardPostRequest,
    AdminBoardPostSummary,
    BoardCategory,
    BoardPage,
    BoardSearchType,
    BoardStatus,
} from "../types/api"

export interface AdminBoardListParams {
    category?: BoardCategory | ""
    status?: BoardStatus | ""
    searchType?: BoardSearchType
    keyword?: string
    page: number
    size?: number
}

// GET /admin/boards - 게시글 목록 (숨김/삭제 포함)
export function getBoardPosts(params: AdminBoardListParams): Promise<BoardPage<AdminBoardPostSummary>> {
    const query = new URLSearchParams()
    if (params.category) query.set("category", params.category)
    if (params.status) query.set("status", params.status)
    if (params.keyword) {
        query.set("keyword", params.keyword)
        query.set("searchType", params.searchType ?? "TITLE_CONTENT")
    }
    query.set("page", String(params.page))
    query.set("size", String(params.size ?? 20))
    return apiRequest<BoardPage<AdminBoardPostSummary>>(`/admin/boards?${query.toString()}`)
}

// GET /admin/boards/{postId} - 상세 (조회수 증가 없음)
export function getBoardPost(postId: number): Promise<AdminBoardPostDetail> {
    return apiRequest<AdminBoardPostDetail>(`/admin/boards/${postId}`)
}

function toFormData(request: AdminBoardPostRequest, images: File[]): FormData {
    const formData = new FormData()
    formData.append("request", new Blob([JSON.stringify(request)], { type: "application/json" }))
    images.forEach((file) => formData.append("images", file))
    return formData
}

// POST /admin/boards - 공지 등 관리자 글 작성
export function createBoardPost(request: AdminBoardPostRequest, images: File[]): Promise<AdminBoardPostDetail> {
    return apiFormRequest<AdminBoardPostDetail>("/admin/boards", toFormData(request, images), "POST")
}

// PUT /admin/boards/{postId} - 관리자 작성 글 수정
export function updateBoardPost(
    postId: number,
    request: AdminBoardPostRequest,
    images: File[],
): Promise<AdminBoardPostDetail> {
    return apiFormRequest<AdminBoardPostDetail>(`/admin/boards/${postId}`, toFormData(request, images), "PUT")
}

// PATCH /admin/boards/{postId}/status - 노출/숨김/삭제
export function changeBoardPostStatus(postId: number, status: BoardStatus): Promise<void> {
    return apiRequest<void>(`/admin/boards/${postId}/status`, { method: "PATCH", body: { status } })
}

// PATCH /admin/boards/{postId}/pin - 상단 고정/해제
export function changeBoardPostPinned(postId: number, pinned: boolean): Promise<void> {
    return apiRequest<void>(`/admin/boards/${postId}/pin`, { method: "PATCH", body: { pinned } })
}

// GET /admin/boards/{postId}/comments - 댓글 (숨김/삭제 포함)
export function getBoardComments(postId: number, page = 0, size = 50): Promise<BoardPage<AdminBoardComment>> {
    return apiRequest<BoardPage<AdminBoardComment>>(`/admin/boards/${postId}/comments?page=${page}&size=${size}`)
}

// PATCH /admin/boards/comments/{commentId}/status
export function changeBoardCommentStatus(commentId: number, status: BoardStatus): Promise<void> {
    return apiRequest<void>(`/admin/boards/comments/${commentId}/status`, { method: "PATCH", body: { status } })
}
