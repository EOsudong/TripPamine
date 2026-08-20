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
    },
  },
})
