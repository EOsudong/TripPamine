import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

// Docker Desktop 개발환경 전용 설정. 기존 vite.config.ts와 운영 설정은 변경하지 않는다.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // 기존 sockjs-client 호환성을 유지한다.
  define: { global: "globalThis" },
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    // Windows 파일 마운트에서 변경 이벤트가 유실되는 경우를 보완한다.
    watch: { usePolling: true, interval: 350 },
    proxy: {
      "/api/music": {
        target: process.env.LOCAL_BACKEND_URL ?? "http://backend:8080",
        changeOrigin: true,
      },
    },
  },
})
