// 회원가입 페이지. Login.jsx와 거의 동일한 구조(탭 전환 + 소셜 로그인 + 폼)이며,
// 탭에서 "로그인"을 누르면 /login 페이지로 이동합니다.
import { useState } from "react"
import type { FormEvent } from "react"
import { Link, useNavigate } from "react-router-dom"
import SocialLoginButtons from "../components/SocialLoginButtons"

export default function Join() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: "", email: "", password: "", confirm: "" })

  // 회원가입 폼 제출 처리 (더미 로직 — 실제 서버 저장 없이 로그인 페이지로 이동만 시킴)
  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    // TODO: 실제 회원가입 API 연동
    navigate("/login")
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm bg-white rounded-3xl shadow-2xl overflow-hidden">
        {/* 상단 그라데이션 헤더 */}
        <div className="bg-gradient-to-br from-sky-500 to-sky-600 px-6 pt-8 pb-10">
          <Link to="/" className="inline-flex w-10 h-10 rounded-2xl bg-white/20 items-center justify-center mb-3">
            <span className="text-white text-lg">✈️</span>
          </Link>
          <h2 className="text-white font-bold text-xl">TripPamin</h2>
          <p className="text-sky-100 text-sm mt-0.5">AI와 함께 떠나는 국내 여행</p>
        </div>

        {/* 탭 전환 UI: "회원가입"이 현재 페이지(비활성 버튼), "로그인"은 /login으로 이동하는 Link */}
        <div className="mx-6 -mt-5 bg-white rounded-2xl shadow-lg flex p-1 gap-1 mb-6">
          <Link
            to="/login"
            className="flex-1 py-2.5 rounded-xl text-sm font-semibold text-slate-500 hover:text-slate-700 text-center transition-colors"
          >
            로그인
          </Link>
          <button className="flex-1 py-2.5 rounded-xl text-sm font-semibold bg-sky-500 text-white shadow-sm">
            회원가입
          </button>
        </div>

        <div className="px-6 pb-6">
          {/* 소셜 로그인 버튼 */}
          <div className="mb-5">
            <SocialLoginButtons />
          </div>

          <Divider />

          {/* 회원가입 폼: 이름/이메일/비밀번호/비밀번호 확인 */}
          <form onSubmit={handleSubmit} className="space-y-3 mt-5">
            <FormInput
              label="이름"
              type="text"
              placeholder="홍길동"
              value={form.name}
              onChange={(v) => setForm((f) => ({ ...f, name: v }))}
            />
            <FormInput
              label="이메일"
              type="email"
              placeholder="example@email.com"
              value={form.email}
              onChange={(v) => setForm((f) => ({ ...f, email: v }))}
            />
            <FormInput
              label="비밀번호"
              type="password"
              placeholder="8자 이상"
              value={form.password}
              onChange={(v) => setForm((f) => ({ ...f, password: v }))}
            />
            <FormInput
              label="비밀번호 확인"
              type="password"
              placeholder="비밀번호 재입력"
              value={form.confirm}
              onChange={(v) => setForm((f) => ({ ...f, confirm: v }))}
            />
            <SubmitBtn label="회원가입" />
            <p className="text-center text-xs text-slate-400 pt-1">
              이미 계정이 있으신가요?{" "}
              <Link to="/login" className="text-sky-500 font-semibold">
                로그인
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  )
}

// "또는 이메일로" 구분선 (Login.jsx의 동일 컴포넌트와 같은 역할 — 각 페이지에 따로 정의되어 있음)
function Divider() {
  return (
    <div className="flex items-center gap-3">
      <div className="flex-1 h-px bg-slate-100" />
      <span className="text-xs text-slate-400">또는 이메일로</span>
      <div className="flex-1 h-px bg-slate-100" />
    </div>
  )
}

// 라벨 + input 공용 입력 필드
interface FormInputProps {
  label: string
  type: string
  placeholder: string
  value: string
  onChange: (value: string) => void
}

function FormInput({ label, type, placeholder, value, onChange }: FormInputProps) {
  return (
    <div>
      <label className="text-xs font-semibold text-slate-600 block mb-1.5">{label}</label>
      <input
        type={type}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full px-4 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors"
      />
    </div>
  )
}

function SubmitBtn({ label }: { label: string }) {
  return (
    <button
      type="submit"
      className="w-full py-3 bg-sky-500 hover:bg-sky-600 text-white font-bold text-sm rounded-xl transition-colors shadow-sm shadow-sky-200 mt-1"
    >
      {label}
    </button>
  )
}
