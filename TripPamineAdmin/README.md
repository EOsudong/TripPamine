# trippamine-admin

TripPamine 관리자 콘솔 — 사용자용 `trippamin` 프론트와 완전히 분리된 별도 React 프로젝트입니다.

## 실행 방법

```bash
npm install
npm run dev
```

백엔드(Spring Boot)가 `localhost:8080`에서 실행 중이어야 합니다. `vite.config.ts`의 proxy 설정으로
`/admin` 요청이 자동으로 `localhost:8080`으로 전달됩니다. 백엔드 포트가 다르면 `vite.config.ts`의
`server.proxy` 값을 바꿔주세요.

## 구현된 기능

- 관리자 로그인 (`POST /admin/auth/login`)
- 로그인 보호 라우트 (토큰 없으면 `/login`으로 리다이렉트)
- 회원 목록 조회 (`GET /admin/users`, 페이징)
- 회원 강제 정지 (`PATCH /admin/users/{userId}/suspend`, 사유 입력 모달)

## 아직 없는 기능

- 퀘스트 관리
- SUPER/STAFF 권한별 화면 제한 (지금은 로그인만 하면 모든 화면 접근 가능)

## 폴더 구조

```
src/
├── api/           API 호출 함수 (client.ts가 공통 fetch 래퍼)
├── components/    AdminLayout(사이드바), ProtectedRoute(로그인 가드)
├── pages/         AdminLogin, AdminDashboard, AdminUsers
├── router/        Router.tsx
└── types/         백엔드 DTO와 매칭되는 타입 정의 (api.ts)
```
