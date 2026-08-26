// 카카오/네이버/구글 소셜 로그인 버튼 모음.
// Login.jsx와 Join.jsx에서 공통으로 사용하는 재사용 컴포넌트입니다.
//
// Spring Security의 oauth2Login()이 "/oauth2/authorization/{registrationId}" 경로를
// 기본으로 처리해주므로(= SecurityConfig에서 이미 permitAll), 프론트는 브라우저를
// 그 주소로 이동시키기만 하면 카카오/구글/네이버 로그인 페이지로 넘어간다.
//
// [리팩터링] 예전에는 이 파일이 백엔드 주소를 "http://localhost:8080"으로 따로 선언해두고
// 있었다. 주석에는 "axios.ts의 baseURL과 동일하게 맞춤"이라고 적혀 있었지만 실제로는
// VITE_API_BASE_URL을 보지 않는 별개의 상수라, 환경변수를 바꾸면(폰 테스트용 터널 등)
// 다른 API는 다 정상인데 소셜 로그인 진입만 localhost로 튀어서 실패했다.
// 이제 axios.ts가 계산한 API_BASE_URL을 그대로 가져다 쓴다.
import { API_BASE_URL } from "../api/axios"

const providers = [
  { color: "#FEE500", textColor: "#181600", logo: "K", label: "카카오로 계속하기", registrationId: "kakao" },
  { color: "#03C75A", textColor: "white", logo: "N", label: "네이버로 계속하기", registrationId: "naver" },
  { color: "#fff", textColor: "#3c4043", logo: "G", label: "Google로 계속하기", border: true, registrationId: "google" },
]

function handleSocialLogin(registrationId: string) {
  window.location.href = `${API_BASE_URL}/oauth2/authorization/${registrationId}`
}

export default function SocialLoginButtons() {
  return (
      <div className="space-y-2.5">
        {providers.map((p) => (
            <button
                key={p.logo}
                type="button"
                onClick={() => handleSocialLogin(p.registrationId)}
                style={{ backgroundColor: p.color, color: p.textColor, border: p.border ? "1px solid #e2e8f0" : "none" }}
                className="w-full flex items-center justify-center gap-2.5 py-3 rounded-xl text-sm font-semibold transition-opacity hover:opacity-90"
            >
          <span className="w-5 h-5 rounded-full bg-black/10 flex items-center justify-center text-xs font-bold">
            {p.logo}
          </span>
              {p.label}
            </button>
        ))}
      </div>
  )
}
