// 관리자 로그인 페이지. 일반 사용자용 회원가입/소셜로그인 없이, 아이디+비밀번호만 받음
// (관리자는 SUPER 관리자가 만들어주는 방식이라 셀프 가입이 없음).
import { useState } from "react"
import type { FormEvent } from "react"
import { useNavigate } from "react-router-dom"
import { ApiError, setAdminToken } from "../api/client"
import { adminLogin } from "../api/admin"

export default function AdminLogin() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ adminLoginId: "", password: "" })
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const response = await adminLogin(form)
      setAdminToken(response.accessToken)
      navigate("/")
    } catch (err) {
      // 백엔드의 IllegalArgumentException 메시지(아이디 없음/비밀번호 불일치/정지 계정 등)를 그대로 보여줌
      setError(err instanceof ApiError ? err.message : "로그인에 실패했습니다.")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-white tracking-tight">
            TripPamin <span className="text-indigo-400">Admin</span>
          </h1>
          <p className="text-slate-500 text-sm mt-1">관리자 전용 페이지입니다</p>
        </div>

        <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1.5">관리자 아이디</label>
            <input
              type="text"
              value={form.adminLoginId}
              onChange={(e) => setForm((f) => ({ ...f, adminLoginId: e.target.value }))}
              className="w-full px-3.5 py-2.5 rounded-lg bg-slate-800 border border-slate-700 focus:border-indigo-500 outline-none text-sm text-slate-100 placeholder-slate-500 transition-colors"
              placeholder="admin01"
              autoComplete="username"
            />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1.5">비밀번호</label>
            <input
              type="password"
              value={form.password}
              onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
              className="w-full px-3.5 py-2.5 rounded-lg bg-slate-800 border border-slate-700 focus:border-indigo-500 outline-none text-sm text-slate-100 placeholder-slate-500 transition-colors"
              placeholder="비밀번호 입력"
              autoComplete="current-password"
            />
          </div>

          {error && (
            <p className="text-sm text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-400 disabled:bg-slate-700 disabled:text-slate-400 text-white font-semibold text-sm transition-colors"
          >
            {loading ? "로그인 중..." : "로그인"}
          </button>
        </form>
      </div>
    </div>
  )
}
