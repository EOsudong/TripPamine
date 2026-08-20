// 회원가입 페이지. Login.jsx와 거의 동일한 구조(탭 전환 + 소셜 로그인 + 폼)이며,
// 탭에서 "로그인"을 누르면 /login 페이지로 이동합니다.
import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import SocialLoginButtons from "../components/SocialLoginButtons";
import { signupApi, checkEmailApi } from "../api/auth";

export default function Join() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: "",
    userName: "",
    phoneNumber: "",
    email: "",
    password: "",
    confirm: "",
  });

  // 이메일 중복 확인
  const [emailChecked, setEmailChecked] = useState(false);
  const [emailAvailable, setEmailAvailable] = useState<boolean | null>(null);

  // 이메일 중복 확인 함수 정의
  const handleCheckEmail = async () => {
    if (!form.email) {
      alert("이메일을 입력해주세요.");
      return;
    }
    try {
      const isAvailable = await checkEmailApi(form.email);
      if (isAvailable) {
        alert("사용 가능한 이메일입니다.");
        setEmailAvailable(true);
        setEmailChecked(true);
      } else {
        alert("이미 사용 중인 이메일입니다.");
        setEmailAvailable(false);
        setEmailChecked(true);
      }
    } catch (error) {
      console.error("이메일 중복 확인 실패:", error);
      alert("이메일 중복 확인 중 오류가 발생했습니다. 다시 시도해주세요.");
      setEmailAvailable(null);
      setEmailChecked(false);
    }
  };

  // 로딩 및 에러 메시지 상태 추가
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  // 회원가입 폼 제출 처리
  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setErrorMessage(""); // 이전 에러 메시지 초기화

    // 프론트엔드 1차 유효성 검가
    if (
      !form.name ||
      !form.email ||
      !form.password ||
      !form.confirm ||
      !form.userName ||
      !form.phoneNumber
    ) {
      setErrorMessage("모든 입력 항목을 채워주세요.");
      return;
    }

    if (form.password !== form.confirm) {
      setErrorMessage("비밀번호가 일치하지 않습니다.");
      return;
    }

    if (form.password.length < 8) {
      setErrorMessage("비밀번호는 8자 이상이어야 합니다.");
      return;
    }

    try {
      setLoading(true);

      // 회원가입 API 호출
      await signupApi({
        email: form.email,
        password: form.password,
        name: form.name,
        userName: form.userName,
        phoneNumber: form.phoneNumber,
      });
      alert("회원가입이 완료되었습니다. 로그인 페이지로 이동합니다.");
      // 회원가입 성공 시 로그인 페이지로 이동
      navigate("/login");
    } catch (error: any) {
      console.error("회원가입 실패:", error);
      setErrorMessage(
        error.response?.data?.message || "회원가입 중 오류가 발생했습니다.",
      );
      // 백엔드 예외 처리 메시지 출력 (예: 이미 존재하는 이메일)
      if (error.response && error.response.data?.message) {
        setErrorMessage(error.response.data.message);
      } else {
        setErrorMessage("회원가입 중 오류가 발생했습니다. 다시 시도해주세요.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm bg-white rounded-3xl shadow-2xl overflow-hidden">
        {/* 상단 그라데이션 헤더 */}
        <div className="bg-gradient-to-br from-sky-500 to-sky-600 px-6 pt-8 pb-10">
          <Link
            to="/"
            className="inline-flex w-10 h-10 rounded-2xl bg-white/20 items-center justify-center mb-3"
          >
            <span className="text-white text-lg">✈️</span>
          </Link>
          <h2 className="text-white font-bold text-xl">TripPamin</h2>
          <p className="text-sky-100 text-sm mt-0.5">
            AI와 함께 떠나는 국내 여행
          </p>
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

          {/* 에러 메시지 출력 영력 */}
          {errorMessage && (
            <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-xl text-xs text-red-600 text-center">
              {errorMessage}
            </div>
          )}

          {/* 회원가입 폼: 이름/이메일/비밀번호/비밀번호 확인 */}
          <form onSubmit={handleSubmit} className="space-y-3 mt-5">
            <FormInput
              label="닉네임"
              type="text"
              placeholder="닉네임"
              value={form.userName}
              onChange={(v) => setForm((f) => ({ ...f, userName: v }))}
            />
            <FormInput
              label="이메일"
              type="email"
              placeholder="example@email.com"
              value={form.email}
              onChange={(v) => {
                setForm((f) => ({ ...f, email: v }));
                setEmailChecked(false); // 이메일 입력 시 중복 확인 상태 초기화
                setEmailAvailable(null); // 이메일 입력 시 사용 가능 여부 초기화
              }}
              buttonText="중복확인"
              onButtonClick={handleCheckEmail}
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
            <FormInput
              label="이름"
              type="text"
              placeholder="홍길동"
              value={form.name}
              onChange={(v) => setForm((f) => ({ ...f, name: v }))}
            />
            <FormInput
              label="전화번호"
              type="text"
              placeholder="010-1234-5678"
              value={form.phoneNumber}
              onChange={(v) => setForm((f) => ({ ...f, phoneNumber: v }))}
            />
            {/* 버튼 비활성화 및 로딩 문구 처리 */}
            <SubmitBtn
              label={loading ? "가입 처리중..." : "회원가입"}
            />
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
  );
}

// "또는 이메일로" 구분선 (Login.jsx의 동일 컴포넌트와 같은 역할 — 각 페이지에 따로 정의되어 있음)
function Divider() {
  return (
    <div className="flex items-center gap-3">
      <div className="flex-1 h-px bg-slate-100" />
      <span className="text-xs text-slate-400">또는 이메일로</span>
      <div className="flex-1 h-px bg-slate-100" />
    </div>
  );
}

// 라벨 + input 공용 입력 필드
interface FormInputProps {
  label: string;
  type: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;

  buttonText?: string;
  onButtonClick?: () => void;
}

function FormInput({
  label,
  type,
  placeholder,
  value,
  onChange,
  buttonText,
  onButtonClick,
}: FormInputProps) {
  return (
    <div>
      <label className="text-xs font-semibold text-slate-600 block mb-1.5">
        {label}
      </label>
      <div className="flex gap-2">
        <input
          type={type}
          placeholder={placeholder}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="w-full px-4 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-sm text-slate-700 placeholder-slate-400 transition-colors"
        />
        {buttonText && onButtonClick && (
          <button
            type="button"
            onClick={onButtonClick}
            className="shrink-0 px-4 py-2.5 bg-sky-500 hover:bg-sky-600 text-white text-sm font-semibold rounded-xl transition-colors"
          >
            {buttonText}
          </button>
        )}
      </div>
    </div>
  );
}

function SubmitBtn({ label }: { label: string }) {
  return (
    <button
      type="submit"
      className="w-full py-3 bg-sky-500 hover:bg-sky-600 text-white font-bold text-sm rounded-xl transition-colors shadow-sm shadow-sky-200 mt-1"
    >
      {label}
    </button>
  );
}
