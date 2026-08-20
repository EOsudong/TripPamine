// 소셜 로그인(카카오/구글/네이버) 성공 후 백엔드가
// http://localhost:5173/oauth/callback?accessToken=... 로 리다이렉트하면
// 이 페이지가 accessToken을 꺼내서 로그인 상태로 전환하고 홈으로 보낸다.
// 백엔드가 accessToken만 쿼리로 넘겨주고 userId는 안 주기 때문에,
// 받은 토큰으로 /users/auth/me 를 한 번 호출해서 userId를 채워 넣는다.
import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { api } from "../api/axios";

export default function OAuthCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuth();
  const hasRun = useRef(false);

  useEffect(() => {
    if (hasRun.current) return;
    hasRun.current = true;

    const handleOAuthLogin = async () => {
      // 1. 쿼리 파라미터에서 accessToken 추출 (백엔드 파라미터명에 맞춰 "accessToken")
      const accessToken =
        searchParams.get("accessToken") || searchParams.get("accessToken");

      if (!accessToken) {
        console.error("OAuth 콜백: accessToken이 없습니다.");
        navigate("/login", { replace: true });
        return;
      }

      try {
        // 2. axios 인터셉터를 위해 미리 저장
        localStorage.setItem("accessToken", accessToken);

        // 3. 내 정보 조회 API 호출
        const res = await api.get<{ userId: number }>("/users/auth/me");

        // 콘솔 확인용
        console.log(
          "사용자 정보:",
          JSON.parse(localStorage.getItem("user") || "{}"),
        );

        // 4. AuthContext 상태 업데이트 후 홈으로 이동
        alert("로그인 성공! 홈으로 이동합니다.");
        login(accessToken, res.data.userId);
        navigate("/", { replace: true });
      } catch (err) {
        console.error("OAuth 콜백: 사용자 정보 조회 실패", err);
        localStorage.removeItem("accessToken");
        navigate("/login", { replace: true });
      }
    };

    handleOAuthLogin();
  }, [searchParams, login, navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      <p className="text-slate-500 text-sm">로그인 처리 중입니다...</p>
    </div>
  );
}
