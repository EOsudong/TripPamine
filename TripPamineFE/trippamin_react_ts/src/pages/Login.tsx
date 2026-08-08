// 로그인 페이지.
// - 상단 탭("로그인"/"회원가입")은 실제 탭 전환이 아니라, "회원가입"을 누르면 /join 페이지로 이동하는 링크입니다.
// - 소셜 로그인 버튼은 SocialLoginButtons 컴포넌트를 그대로 재사용 (Join.jsx와 공통)
import { useState } from "react"
import type { FormEvent } from "react"
import { Link, useNavigate } from "react-router-dom"
import SocialLoginButtons from "../components/SocialLoginButtons"

export default function Login() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: "", password: "" })

  // 로그인 폼 제출 처리.
  // 지금은 실제 서버 인증 없이 바로 홈으로 이동만 시키는 더미 로직입니다.
  // 나중에 실제 로그인 API를 연동할 때 이 부분을 fetch 호출로 교체하면 됩니다.
  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    // TODO: 실제 로그인 API 연동
    navigate("/")
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm bg-white rounded-3xl shadow-2xl overflow-hidden">
        {/* 상단 그라데이션 헤더: 로고 + 서비스 소개 문구 */}
        <div className="bg-gradient-to-br from-sky-500 to-sky-600 px-6 pt-8 pb-10">
          <Link to="/" className="inline-flex w-10 h-10 rounded-2xl bg-white/20 items-center justify-center mb-3">
            <span className="text-white text-lg">✈️</span>
          </Link>
          <h2 className="text-white font-bold text-xl">TripPamin</h2>
          <p className="text-sky-100 text-sm mt-0.5">AI와 함께 떠나는 국내 여행</p>
        </div>

        {/* 탭 전환 UI: "로그인"은 현재 페이지라 그냥 버튼(비활성), "회원가입"은 /join으로 이동하는 Link */}
        <div className="mx-6 -mt-5 bg-white rounded-2xl shadow-lg flex p-1 gap-1 mb-6">
          <button className="flex-1 py-2.5 rounded-xl text-sm font-semibold bg-sky-500 text-white shadow-sm">
            로그인
          </button>
          <Link
            to="/join"
            className="flex-1 py-2.5 rounded-xl text-sm font-semibold text-slate-500 hover:text-slate-700 text-center transition-colors"
          >
            회원가입
          </Link>
        </div>

        <div className="px-6 pb-6">
          {/* 소셜 로그인 버튼 (카카오/네이버/구글) */}
          <div className="mb-5">
            <SocialLoginButtons />
          </div>

          <Divider />

          {/* 이메일/비밀번호 로그인 폼 */}
          <form onSubmit={handleSubmit} className="space-y-3 mt-5">
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
              placeholder="비밀번호 입력"
              value={form.password}
              onChange={(v) => setForm((f) => ({ ...f, password: v }))}
            />
            <div className="flex justify-end gap-3 text-xs text-slate-400 pt-0.5">
              <Link to="/find-id" className="hover:text-sky-500 transition-colors">
                아이디 찾기
              </Link>
              <span>·</span>
              <Link to="/find-pw" className="hover:text-sky-500 transition-colors">
                비밀번호 찾기
              </Link>
            </div>
            <SubmitBtn label="로그인" />
            <p className="text-center text-xs text-slate-400">
              아직 회원이 아니신가요?{" "}
              <Link to="/join" className="text-sky-500 font-semibold">
                회원가입
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  )
}

// "또는 이메일로" 구분선 (소셜 로그인과 이메일 로그인 폼 사이 시각적 구분용)
function Divider() {
  return (
    <div className="flex items-center gap-3">
      <div className="flex-1 h-px bg-slate-100" />
      <span className="text-xs text-slate-400">또는 이메일로</span>
      <div className="flex-1 h-px bg-slate-100" />
    </div>
  )
}

// 라벨 + input을 묶은 공용 입력 필드 (Login/Join/FindId/FindPw 페이지에서 각자 정의해서 사용)
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
