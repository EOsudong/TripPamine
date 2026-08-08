// 아이디 찾기 페이지.
// submitted 상태로 "입력 폼"과 "결과 안내 메시지" 화면을 전환합니다 (Hero.jsx의 폼↔결과 패턴과 동일한 방식).
import { useState } from "react"
import type { FormEvent } from "react"
import { Link } from "react-router-dom"

export default function FindId() {
  const [form, setForm] = useState({ name: "", phone: "" })
  const [submitted, setSubmitted] = useState(false) // 제출 완료 여부 — true가 되면 결과 안내 화면으로 전환

  // 더미 로직: 실제로 서버에 조회하지 않고 바로 "완료" 상태로 전환만 함
  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    // TODO: 실제 아이디 찾기 API 연동
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

        <h3 className="text-lg font-bold text-slate-800 mb-1">아이디 찾기</h3>
        <p className="text-sm text-slate-500 mb-5">가입 시 입력한 이름과 휴대폰 번호로 아이디를 찾아드립니다.</p>

        {/* submitted가 true면 결과 안내, false면 입력 폼을 보여줌 */}
        {submitted ? (
          <div className="rounded-2xl bg-sky-50 p-4 text-sm text-slate-700 leading-relaxed">
            입력하신 정보로 가입된 아이디를 확인했습니다. (데모) 가입하신 이메일로 아이디 안내를 보내드렸어요.
            <Link to="/login" className="block mt-3 text-sky-500 font-semibold">
              로그인하러 가기 →
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <FormInput
              label="이름"
              type="text"
              placeholder="홍길동"
              value={form.name}
              onChange={(v) => setForm((f) => ({ ...f, name: v }))}
            />
            <FormInput
              label="휴대폰 번호"
              type="tel"
              placeholder="010-0000-0000"
              value={form.phone}
              onChange={(v) => setForm((f) => ({ ...f, phone: v }))}
            />
            <button
              type="submit"
              className="w-full py-3 bg-sky-500 hover:bg-sky-600 text-white font-bold text-sm rounded-xl transition-colors shadow-sm shadow-sky-200 mt-1"
            >
              아이디 찾기
            </button>
          </form>
        )}
      </div>
    </div>
  )
}

interface FormInputProps {
  label: string
  type: string
  placeholder: string
  value: string
  onChange: (value: string) => void
}

// 라벨 + input 공용 입력 필드
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
