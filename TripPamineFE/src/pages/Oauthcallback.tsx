// 소셜 로그인(카카오/구글/네이버) 성공 후, 백엔드 OAuth2LoginSuccessHandler가
// 프론트 콜백 주소(app.oauth2.redirect-uri)로 ?accessToken=... 을 붙여 리다이렉트하면
// 이 페이지가 토큰을 꺼내 로그인 상태로 전환하고 홈으로 보낸다.
// 백엔드가 accessToken만 넘기고 userId는 주지 않으므로, 받은 토큰으로 /users/auth/me를
// 한 번 호출해서 userId를 채운 뒤 AuthContext에 넘긴다.
import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { api } from "../api/axios";
import { clearUserSession } from "../auth/session";

export default function OAuthCallback() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const { login } = useAuth();
    const hasRun = useRef(false);

    useEffect(() => {
        if (hasRun.current) return;
        hasRun.current = true;

        const handleOAuthLogin = async () => {
            const accessToken = searchParams.get("accessToken");

            if (!accessToken) {
                console.error("OAuth 콜백: accessToken이 없습니다.");
                navigate("/login", { replace: true });
                return;
            }

            // 이전 로그인의 잔여 토큰이 남아 있으면 axios 요청 인터셉터가 그 옛 토큰으로
            // Authorization 헤더를 덮어써버린다. 새 토큰으로만 검증되도록 먼저 비운다.
            clearUserSession();

            try {
                // 아직 userId를 몰라서 storeUserSession()을 호출할 수 없으므로,
                // localStorage에 토큰을 미리 써두는 대신 이 요청에만 헤더를 직접 실어 보낸다.
                // (토큰을 저장하는 책임은 auth/session.ts 한 곳에만 남겨둔다)
                const res = await api.get<{ userId: number }>("/users/auth/me", {
                    headers: { Authorization: `Bearer ${accessToken}` },
                });

                // login() 내부에서 storeUserSession(accessToken, userId)가 호출된다.
                login(accessToken, res.data.userId);
                navigate("/", { replace: true });
            } catch (err) {
                console.error("OAuth 콜백: 사용자 정보 조회 실패", err);
                clearUserSession();
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
