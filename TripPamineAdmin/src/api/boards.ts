import { apiFormRequest, apiRequest } from "./client"
import type {
    AdminBoardComment,
    AdminBoardReport,
    AdminReportedPost,
    BoardReportProcessRequest,
    BoardReportStatus,
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




// ===================== 운영자 댓글/답글 =====================

// POST /admin/boards/{postId}/comments - 운영자 댓글
export function createAdminComment(postId: number, content: string): Promise<AdminBoardComment> {
    return apiRequest<AdminBoardComment>(`/admin/boards/${postId}/comments`, { method: "POST", body: { content } })
}

// POST /admin/boards/comments/{commentId}/replies - 댓글에 운영자 답글
export function replyToComment(commentId: number, content: string): Promise<AdminBoardComment> {
    return apiRequest<AdminBoardComment>(`/admin/boards/comments/${commentId}/replies`, { method: "POST", body: { content } })
}

// PUT /admin/boards/comments/{commentId} - 본인이 쓴 운영자 댓글/답글 수정
export function updateAdminComment(commentId: number, content: string): Promise<AdminBoardComment> {
    return apiRequest<AdminBoardComment>(`/admin/boards/comments/${commentId}`, { method: "PUT", body: { content } })
}

// ===================== 신고 관리 =====================

// GET /admin/boards/reports - 신고된 게시글 목록 (status 비우면 전체)
export function getReportedPosts(
    status: BoardReportStatus | "",
    page: number,
    size = 20,
): Promise<BoardPage<AdminReportedPost>> {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    if (status) query.set("status", status)
    return apiRequest<BoardPage<AdminReportedPost>>(`/admin/boards/reports?${query.toString()}`)
}

// GET /admin/boards/reports/pending-count
export function getPendingReportCount(): Promise<{ pendingCount: number }> {
    return apiRequest<{ pendingCount: number }>("/admin/boards/reports/pending-count")
}

// GET /admin/boards/{postId}/reports - 게시글별 신고 내역
export function getPostReports(postId: number, page = 0, size = 50): Promise<BoardPage<AdminBoardReport>> {
    return apiRequest<BoardPage<AdminBoardReport>>(`/admin/boards/${postId}/reports?page=${page}&size=${size}`)
}

// PATCH /admin/boards/{postId}/reports - 대기 신고 일괄 처리
export function processPostReports(
    postId: number,
    request: BoardReportProcessRequest,
): Promise<{ processedCount: number }> {
    return apiRequest<{ processedCount: number }>(`/admin/boards/${postId}/reports`, { method: "PATCH", body: request })
}
