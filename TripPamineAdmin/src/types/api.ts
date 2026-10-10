// 백엔드 DTO(Admin*Dto.java, AdminUser*Dto.java)와 필드명을 정확히 맞춘 타입 모음.
// 백엔드에서 DTO 필드가 바뀌면 이 파일도 같이 바꿔야 합니다.

// AdminLoginRequestDto
export interface AdminLoginRequest {
  adminLoginId: string
  password: string
}

// AdminLoginResponseDto
export interface AdminLoginResponse {
  adminLoginId: string
  accessToken: string
  tokenType: string
}

// AdminResponseDto
export type AdminRole = "SUPER" | "STAFF"
export type AdminStatus = "ACTIVE" | "SUSPENDED"

export interface AdminProfile {
  adminId: number
  adminLoginId: string
  adminName: string
  role: AdminRole
  status: AdminStatus
  email: string | null
  lastLoginDate: string | null
  createDate: string | null
}

// AdminUserResponseDto
export type UserGrade = "BRONZE" | "SILVER" | "GOLD" | "PLATINUM"
export type UserStatus = "ACTIVE" | "WITHDRAW" | "SUSPENDED" | "SLEEP"

export interface AdminUser {
  userId: number
  email: string
  name: string | null
  userName: string
  phoneNumber: string | null
  grade: UserGrade
  status: UserStatus
  suspendReason: string | null
  createDate: string | null
}

// Spring Data의 Page<T> 응답 구조 (getUserList가 Page<AdminUserResponseDto>를 반환)
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number // 현재 페이지 (0부터 시작)
  size: number
}

// UserSuspendRequestDto
export interface UserSuspendRequest {
  reason: string
}

// ===================== 퀘스트 관리 =====================
// Quest.entity.QuestType과 1:1
export type QuestType = "LOCATION" | "DISCOVERY" | "PHOTO" | "ACTION" | "CHAIN"

// QuestResponse (도메인 quest/dto/response/QuestResponse.java)
// [RPG 확장] 레벨/EXP/챕터/난이도 관련 필드 추가.
export interface AdminQuest {
  questId: number
  questName: string
  description: string | null
  targetLat: number
  targetLng: number
  rewardPoint: number
  rewardExp: number
  clearRadius: number
  questType: QuestType
  difficulty: number
  imageUrl: string | null
  thumbnailUrl: string | null
  chapterId: number | null
  chapterName: string | null
  requiredLevel: number
  isActive: boolean
  // 관리자 조회에서는 항상 false (로그인한 "일반 사용자" 기준 잠금 여부만 의미가 있음)
  locked: boolean
}

// QuestRequest (도메인 quest/dto/request/QuestRequest.java) - 등록/수정 공용
export interface QuestUpsertRequest {
  questName: string
  description?: string
  targetLat: number
  targetLng: number
  rewardPoint?: number
  rewardExp?: number
  clearRadius?: number // 비우면 서버가 기본값 100m로 채움
  questType?: QuestType // 비우면 서버가 기본값 LOCATION으로 채움
  difficulty?: number // 1~3 권장. 비우면 서버가 기본값 1로 채움
  imageUrl?: string
  thumbnailUrl?: string
  chapterId?: number // 비우면 미분류
  requiredLevel?: number // 비우면 서버가 기본값 1로 채움
  isActive?: boolean // 비우면 서버가 기본값 true(노출)로 채움
}

// ===================== 퀘스트 챕터 관리 =====================
// QuestChapterResponse (도메인 quest/dto/response/QuestChapterResponse.java)
export interface AdminQuestChapter {
  chapterId: number
  chapterName: string
  description: string | null
  region: string | null
  orderNo: number
  requiredLevel: number
  imageUrl: string | null
  locked: boolean
}

// QuestChapterRequest (도메인 quest/dto/request/QuestChapterRequest.java) - 등록/수정 공용
export interface QuestChapterUpsertRequest {
  chapterName: string
  description?: string
  region?: string
  orderNo?: number
  requiredLevel?: number
  imageUrl?: string
}

// ===================== 칭호 관리 =====================
// Title.entity.TitleConditionType과 1:1
export type TitleConditionType = "FIRST_QUEST_CLEAR" | "QUEST_COUNT" | "LEVEL_REACHED"

// TitleResponse (도메인 quest/dto/response/TitleResponse.java)
// 관리자 조회에서는 특정 유저 기준 보유/장착 개념이 없으므로 earned/equipped는 항상 false,
// earnedAt은 항상 null로 내려온다(= AdminQuest.locked가 관리자 조회에서 항상 false인 것과 동일한 패턴).
export interface AdminTitle {
  titleId: number
  titleName: string
  description: string | null
  iconUrl: string | null
  requiredConditionType: TitleConditionType
  requiredConditionValue: number | null
  earned: boolean
  earnedAt: string | null
  equipped: boolean
}

// TitleRequest (도메인 quest/dto/request/TitleRequest.java) - 등록/수정 공용
export interface TitleUpsertRequest {
  titleName: string
  description?: string
  iconUrl?: string
  requiredConditionType: TitleConditionType
  // FIRST_QUEST_CLEAR에서는 사용하지 않음(비워도 됨)
  requiredConditionValue?: number
}

// ===================== 연쇄 퀘스트 단계 관리 =====================
// QuestStepResponse (도메인 quest/dto/response/QuestStepResponse.java)
// 관리자 조회에서는 특정 유저 기준 클리어 여부 개념이 없으므로 cleared는 항상 false
// (AdminQuest.locked, AdminTitle.earned과 동일한 패턴).
export interface AdminQuestStep {
  stepId: number
  questId: number
  stepOrder: number
  stepName: string
  description: string | null
  targetLat: number
  targetLng: number
  clearRadius: number
  cleared: boolean
}

// QuestStepRequest (도메인 quest/dto/request/QuestStepRequest.java) - 등록/수정 공용
export interface QuestStepUpsertRequest {
  // 미입력 시: 등록은 서버가 자동 배정(현재 단계 수 + 1), 수정은 기존 값 유지
  stepOrder?: number
  stepName: string
  description?: string
  targetLat: number
  targetLng: number
  clearRadius?: number // 비우면 서버가 기본값 100m로 채움
}


// ===================== 게시판 관리 =====================
// 백엔드 domain/board/dto/* 와 필드명 동일
export type BoardCategory = "NOTICE" | "REVIEW" | "QNA" | "FREE"
export type BoardStatus = "ACTIVE" | "HIDDEN" | "DELETED"
export type BoardWriterType = "USER" | "ADMIN"
export type BoardSearchType = "TITLE" | "CONTENT" | "TITLE_CONTENT" | "WRITER"

// BoardPageResponse<T> - Page<T>와 같은 필드 + first/last
export interface BoardPage<T> extends Page<T> {
  first: boolean
  last: boolean
}

// BoardPostSummaryResponse
export interface AdminBoardPostSummary {
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
  reportCount?: number // 누적 신고 수 (관리자 응답에만 포함)
  status: BoardStatus
  createDate: string
}

export interface AdminBoardImage {
  imageId: number
  imageUrl: string
  originalName: string | null
}

// BoardPostDetailResponse
export interface AdminBoardPostDetail extends Omit<AdminBoardPostSummary, "thumbnailUrl" | "imageCount"> {
  content: string
  writerProfileImageUrl: string | null
  images: AdminBoardImage[]
  liked: boolean
  mine: boolean
  updateDate: string | null
}

// BoardCommentResponse
// 원댓글은 replies에 답글을 담는다. mine = 현재 로그인한 관리자가 쓴 운영자 댓글인지
export interface AdminBoardComment {
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
  replies: AdminBoardComment[]
}

// ===================== 게시글 신고 =====================
export type BoardReportReason = "SPAM" | "ABUSE" | "OBSCENE" | "PRIVACY" | "FALSE_INFO" | "ETC"
export type BoardReportStatus = "PENDING" | "ACCEPTED" | "REJECTED"
export type BoardReportPostAction = "KEEP" | "HIDE" | "DELETE" | "ACTIVATE"

// AdminReportedPostResponse - 신고된 게시글 (게시글 단위 집계)
export interface AdminReportedPost {
  post: AdminBoardPostSummary
  reportCount: number
  pendingCount: number
  lastReportedAt: string
  reasonCounts: Partial<Record<BoardReportReason, number>>
}

// AdminBoardReportResponse - 개별 신고
export interface AdminBoardReport {
  reportId: number
  postId: number
  reporterId: number | null
  reporterName: string
  reason: BoardReportReason
  reasonLabel: string
  detail: string | null
  status: BoardReportStatus
  handledByName: string | null
  handledAt: string | null
  handleMemo: string | null
  createDate: string
}

// BoardReportProcessRequest
export interface BoardReportProcessRequest {
  result: "ACCEPTED" | "REJECTED"
  postAction: BoardReportPostAction
  memo?: string
}

// AdminBoardPostRequest
export interface AdminBoardPostRequest {
  category: BoardCategory
  title: string
  content: string
  pinned: boolean
  keepImageIds?: number[]
}



