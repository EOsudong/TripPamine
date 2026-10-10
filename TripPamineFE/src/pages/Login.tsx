// 로그인 페이지.
// - 상단 탭("로그인"/"회원가입")은 실제 탭 전환이 아니라, "회원가입"을 누르면 /join 페이지로 이동하는 링크입니다.
// - 소셜 로그인 버튼은 SocialLoginButtons 컴포넌트를 그대로 재사용 (Join.jsx와 공통)
import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import SocialLoginButtons from "../components/SocialLoginButtons";
import AuthShell from "../components/AuthShell";
import { loginApi } from "../api/auth";
import { useAuth } from "../context/AuthContext";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: "", password: "" });
  const { login } = useAuth();

  // ProtectedRoute(예: 마이페이지)에서 로그인 없이 접근했다가 이리로 넘어온 경우,
  // location.state.from에 원래 가려던 위치가 담겨 있음. 없으면 기본값으로 홈("/")
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname || "/";

  // 로그인 폼 제출 처리.
  // 지금은 실제 서버 인증 없이 바로 홈으로 이동만 시키는 더미 로직입니다.
  // 나중에 실제 로그인 API를 연동할 때 이 부분을 fetch 호출로 교체하면 됩니다.
  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.email || !form.password) {
      alert("이메일과 비밀번호를 모두 입력해주세요.");
      return;
    }

    try {
      // 로그인 API 호출
      const response = await loginApi({
        email: form.email,
        password: form.password,
      });

      // AuthContext의 login 함수 호출하여 로그인 상태 업데이트
      login(response.accessToken, response.userId);

      // JWT 로컬 스토리지 저장
      localStorage.setItem("accessToken", response.accessToken);

      // 사용자 정보 저장
      localStorage.setItem(
          "user",
          JSON.stringify({
            userId: response.userId,
            tokenType: response.tokenType,
            accessToken: response.accessToken,
          }),
      );

      // 콘솔 확인용
      console.log(
          "사용자 정보:",
          JSON.parse(localStorage.getItem("user") || "{}"),
      );

      alert("로그인 성공!");
      navigate(from, { replace: true }); // 로그인 성공 시 원래 가려던 페이지(없으면 홈)로 이동
    } catch (error: any) {
      console.error("로그인 실패:", error);

      if (error.response?.status === 401) {
        alert("이메일 또는 비밀번호가 올바르지 않습니다.");
      } else if (error.response?.data?.message) {
        alert(error.response.data.message);
      } else {
        alert("로그인 중 오류가 발생했습니다. 다시 시도해주세요.");
      }
    }
  }

  return (
      <AuthShell title="다시 만나서 반가워요" subtitle="AI 매니저가 당신의 하루를 이어서 준비하고 있어요">
          {/* 탭 전환 UI: "로그인"은 현재 페이지라 그냥 버튼(비활성), "회원가입"은 /join으로 이동하는 Link */}
          <div className="mb-6 flex gap-1 rounded-2xl bg-slate-100/80 p-1">
            <button className="flex-1 rounded-xl bg-white py-2.5 text-sm font-bold text-slate-950 shadow-sm">
              로그인
            </button>
            <Link
                to="/join"
                className="flex-1 rounded-xl py-2.5 text-center text-sm font-semibold text-slate-500 transition-colors hover:text-slate-800"
            >
              회원가입
            </Link>
          </div>

          <div>
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
                <Link
                    to="/find-id"
                    className="hover:text-sky-500 transition-colors"
                >
                  아이디 찾기
                </Link>
                <span>·</span>
                <Link
                    to="/find-pw"
                    className="hover:text-sky-500 transition-colors"
                >
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
      </AuthShell>
  );
}

// "또는 이메일로" 구분선 (소셜 로그인과 이메일 로그인 폼 사이 시각적 구분용)
function Divider() {
  return (
      <div className="flex items-center gap-3">
        <div className="flex-1 h-px bg-slate-100" />
        <span className="text-xs text-slate-400">또는 이메일로</span>
        <div className="flex-1 h-px bg-slate-100" />
      </div>
  );
}

// 라벨 + input을 묶은 공용 입력 필드 (Login/Join/FindId/FindPw 페이지에서 각자 정의해서 사용)
interface FormInputProps {
  label: string;
  type: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
}

function FormInput({
                     label,
                     type,
                     placeholder,
                     value,
                     onChange,
                   }: FormInputProps) {
  return (
      <div>
        <label className="text-xs font-semibold text-slate-600 block mb-1.5">
          {label}
        </label>
        <input
            type={type}
            placeholder={placeholder}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            className="w-full px-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:border-sky-400 focus:ring-4 focus:ring-sky-100 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors"
        />
      </div>
  );
}

function SubmitBtn({ label }: { label: string }) {
  return (
      <button
          type="submit"
          className="w-full py-3 tp-gradient-bg text-white font-bold text-sm rounded-xl transition hover:brightness-110 shadow-[0_10px_24px_-10px_rgba(124,108,255,0.9)] mt-1"
      >
        {label}
      </button>
  );
}
