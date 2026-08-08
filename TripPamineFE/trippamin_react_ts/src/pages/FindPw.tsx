// 비밀번호 찾기 페이지. FindId.jsx와 동일한 "폼 → 결과 안내" 패턴입니다.
import { useState } from "react"
import type { FormEvent } from "react"
import { Link } from "react-router-dom"

export default function FindPw() {
  const [email, setEmail] = useState("")
  const [submitted, setSubmitted] = useState(false) // 제출 완료 여부 — true가 되면 결과 안내 화면으로 전환

  // 더미 로직: 실제로 메일을 발송하지 않고 바로 "완료" 상태로 전환만 함
  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    // TODO: 실제 비밀번호 재설정 API 연동
    setSubmitted(true)
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm bg-white rounded-3xl shadow-2xl overflow-hidden p-6">
        <Link to="/login" className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-sky-500 mb-5 transition-colors">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          돌아가기
        </Link>

        <h3 className="text-lg font-bold text-slate-800 mb-1">비밀번호 찾기</h3>
        <p className="text-sm text-slate-500 mb-5">가입한 이메일 주소로 비밀번호 재설정 링크를 발송합니다.</p>

        {/* submitted가 true면 결과 안내, false면 이메일 입력 폼을 보여줌 */}
        {submitted ? (
          <div className="rounded-2xl bg-sky-50 p-4 text-sm text-slate-700 leading-relaxed">
            {email || "입력하신 이메일"}로 비밀번호 재설정 링크를 보내드렸어요. (데모)
            <Link to="/login" className="block mt-3 text-sky-500 font-semibold">
              로그인하러 가기 →
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1.5">이메일</label>
              <input
                type="email"
                placeholder="example@email.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-4 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors"
              />
            </div>
            <button
              type="submit"
              className="w-full py-3 bg-sky-500 hover:bg-sky-600 text-white font-bold text-sm rounded-xl transition-colors shadow-sm shadow-sky-200 mt-1"
            >
              재설정 링크 발송
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
