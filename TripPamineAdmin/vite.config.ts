import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5174, // 관리자 페이지는 항상 이 포트로 고정 (유저 페이지랑 겹치지 않게)
    proxy: {
      // 백엔드(Spring Boot, 기본 8080)로 /admin 요청을 그대로 전달.
      // 이렇게 해두면 프론트에서 CORS 걱정 없이 상대경로("/admin/...")로 fetch 가능
      "/admin": "http://localhost:8080",
      // [게시판 추가] 게시글 첨부 이미지(<img src="/board-images/...">)도 백엔드에서 받아오기
      "/board-images": "http://localhost:8080",
      // 관리자 화면 주소(/mascots)는 프론트 라우트이므로 "/mascots" 전체를 프록시하면 안 된다 → .png만 정규식으로 매칭
      "^/mascots/.*\\.(png|jpe?g|webp|gif)$": "http://localhost:8080",
    },
  },
})