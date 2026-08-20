// 카카오/네이버/구글 소셜 로그인 버튼 모음.
// Login.jsx와 Join.jsx에서 공통으로 사용하는 재사용 컴포넌트입니다.
//
// 수정 내역 (2026-08-19): onClick이 비어있어서 버튼을 눌러도 아무 반응이 없었음.
// Spring Security의 oauth2Login()이 "/oauth2/authorization/{registrationId}" 경로를
// 기본으로 처리해주므로(= SecurityConfig에서 이미 permitAll), 프론트는 그냥
// 브라우저를 그 주소로 이동시키기만 하면 카카오/구글/네이버 로그인 페이지로 넘어간다.
const API_BASE_URL = "http://localhost:8080" // 백엔드 서버 주소 (axios.ts의 baseURL과 동일하게 맞춤)

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