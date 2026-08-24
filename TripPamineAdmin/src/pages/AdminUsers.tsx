// 회원 목록을 페이징으로 보여주고, 각 회원을 정지/정지해제할 수 있는 화면.
// 백엔드 GET /admin/users, PATCH /admin/users/{userId}/suspend,
// PATCH /admin/users/{userId}/unsuspend 세 API를 사용.
import { useEffect, useState } from "react"
import AdminLayout from "../components/AdminLayout"
import { getUserList, suspendUser, unsuspendUser } from "../api/users"
import { ApiError } from "../api/client"
import type { AdminUser } from "../types/api"

const statusStyle: Record<string, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-600",
  WITHDRAW: "bg-slate-100 text-slate-500",
  SUSPENDED: "bg-red-50 text-red-600",
  SLEEP: "bg-amber-50 text-amber-600",
}

const statusLabel: Record<string, string> = {
  ACTIVE: "활동중",
  WITHDRAW: "탈퇴",
  SUSPENDED: "정지됨",
  SLEEP: "휴면",
}

export default function AdminUsers() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [suspendTarget, setSuspendTarget] = useState<AdminUser | null>(null) // 정지 모달을 띄울 대상 회원
  const [unsuspendingId, setUnsuspendingId] = useState<number | null>(null) // 지금 정지해제 처리 중인 회원 (버튼 중복 클릭 방지용)

  async function loadUsers(targetPage: number) {
    setLoading(true)
    setError(null)
    try {
      const result = await getUserList(targetPage)
      setUsers(result.content)
      setTotalPages(result.totalPages)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "회원 목록을 불러오지 못했습니다.")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadUsers(page)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page])

  // 정지 해제 처리 - 사유 입력이 필요 없어서 모달 없이 바로 API 호출
  async function handleUnsuspend(userId: number) {
    setUnsuspendingId(userId)
    setError(null)
    try {
      await unsuspendUser(userId)
      await loadUsers(page) // 최신 상태로 목록 다시 불러오기 -> 버튼이 "정지"로 다시 바뀜
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "정지 해제에 실패했습니다.")
    } finally {
      setUnsuspendingId(null)
    }
  }

  return (
      <AdminLayout>
        <h1 className="text-xl font-bold text-slate-800 mb-1">회원 관리</h1>
        <p className="text-sm text-slate-500 mb-6">전체 회원 목록입니다. 문제가 있는 회원은 정지 처리할 수 있어요.</p>

        {error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-4 py-2.5 mb-4">{error}</p>
        )}

        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
            <tr className="bg-slate-50 border-b border-slate-200 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
              <th className="px-5 py-3">이름</th>
              <th className="px-5 py-3">닉네임</th>
              <th className="px-5 py-3">이메일</th>
              <th className="px-5 py-3">상태</th>
              <th className="px-5 py-3">가입일</th>
              <th className="px-5 py-3 text-right">관리</th>
            </tr>
            </thead>
            <tbody>
            {loading ? (
                <tr>
                  <td colSpan={6} className="px-5 py-10 text-center text-slate-400">
                    불러오는 중...
                  </td>
                </tr>
            ) : users.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-5 py-10 text-center text-slate-400">
                    회원이 없습니다.
                  </td>
                </tr>
            ) : (
                users.map((user) => (
                    <tr key={user.userId} className="border-b border-slate-100 last:border-0">
                      <td className="px-5 py-3.5 text-slate-700">{user.name ?? "-"}</td>
                      <td className="px-5 py-3.5 text-slate-700">{user.userName}</td>
                      <td className="px-5 py-3.5 text-slate-500">{user.email}</td>
                      <td className="px-5 py-3.5">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusStyle[user.status]}`}>
                      {statusLabel[user.status]}
                    </span>
                        {user.status === "SUSPENDED" && user.suspendReason && (
                            <p className="text-xs text-slate-400 mt-1">사유: {user.suspendReason}</p>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-slate-500">
                        {user.createDate ? user.createDate.slice(0, 10) : "-"}
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        {/* 정지 상태인 회원은 "정지해제" 버튼을, 그 외는 "정지" 버튼을 보여줌 */}
                        {user.status === "SUSPENDED" ? (
                            <button
                                onClick={() => handleUnsuspend(user.userId)}
                                disabled={unsuspendingId === user.userId}
                                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-emerald-600 hover:bg-emerald-50 disabled:text-slate-300 disabled:hover:bg-transparent transition-colors"
                            >
                              {unsuspendingId === user.userId ? "처리 중..." : "정지해제"}
                            </button>
                        ) : (
                            <button
                                onClick={() => setSuspendTarget(user)}
                                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 transition-colors"
                            >
                              정지
                            </button>
                        )}
                      </td>
                    </tr>
                ))
            )}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 mt-5">
              <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-3 py-1.5 rounded-lg text-sm text-slate-500 hover:bg-slate-100 disabled:opacity-30 transition-colors"
              >
                이전
              </button>
              <span className="text-sm text-slate-500">
            {page + 1} / {totalPages}
          </span>
              <button
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-3 py-1.5 rounded-lg text-sm text-slate-500 hover:bg-slate-100 disabled:opacity-30 transition-colors"
              >
                다음
              </button>
            </div>
        )}

        {/* 정지 사유 입력 모달 */}
        {suspendTarget && (
            <SuspendModal
                user={suspendTarget}
                onClose={() => setSuspendTarget(null)}
                onSuccess={() => {
                  setSuspendTarget(null)
                  loadUsers(page)
                }}
            />
        )}
      </AdminLayout>
  )
}

interface SuspendModalProps {
  user: AdminUser
  onClose: () => void
  onSuccess: () => void
}

// 정지 사유를 입력받아 PATCH /admin/users/{userId}/suspend를 호출하는 작은 모달
function SuspendModal({ user, onClose, onSuccess }: SuspendModalProps) {
  const [reason, setReason] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    if (!reason.trim()) {
      setError("정지 사유를 입력해주세요.")
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await suspendUser(user.userId, { reason })
      onSuccess()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "정지 처리에 실패했습니다.")
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <div className="fixed inset-0 bg-slate-900/50 flex items-center justify-center p-4 z-50">
        <div className="bg-white rounded-2xl p-6 w-full max-w-sm">
          <h2 className="font-bold text-slate-800 mb-1">회원 정지</h2>
          <p className="text-sm text-slate-500 mb-4">
            <span className="font-semibold text-slate-700">{user.userName}</span>님을 정지 처리합니다.
          </p>

          <label className="text-xs font-semibold text-slate-600 block mb-1.5">정지 사유</label>
          <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              placeholder="예) 부적절한 게시물 반복 등록"
              className="w-full px-3.5 py-2.5 rounded-lg border-2 border-slate-200 focus:border-red-400 outline-none text-sm text-slate-700 placeholder-slate-400 resize-none transition-colors"
          />

          {error && <p className="text-xs text-red-500 mt-2">{error}</p>}

          <div className="flex gap-2 mt-5">
            <button
                onClick={onClose}
                className="flex-1 py-2.5 rounded-lg border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 transition-colors"
            >
              취소
            </button>
            <button
                onClick={handleConfirm}
                disabled={submitting}
                className="flex-1 py-2.5 rounded-lg bg-red-500 hover:bg-red-600 disabled:bg-slate-300 text-white text-sm font-semibold transition-colors"
            >
              {submitting ? "처리 중..." : "정지하기"}
            </button>
          </div>
        </div>
      </div>
  )
}