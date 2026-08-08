// 카카오/네이버/구글 소셜 로그인 버튼 모음.
// Login.jsx와 Join.jsx에서 공통으로 사용하는 재사용 컴포넌트입니다.
// 지금은 버튼만 있고 실제 소셜 로그인(OAuth) 연동은 되어있지 않습니다 — onClick을 추가해서 붙이면 됩니다.
const providers = [
  { color: "#FEE500", textColor: "#181600", logo: "K", label: "카카오로 계속하기" },
  { color: "#03C75A", textColor: "white", logo: "N", label: "네이버로 계속하기" },
  { color: "#fff", textColor: "#3c4043", logo: "G", label: "Google로 계속하기", border: true },
]

export default function SocialLoginButtons() {
  return (
    <div className="space-y-2.5">
      {providers.map((p) => (
        <button
          key={p.logo}
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
