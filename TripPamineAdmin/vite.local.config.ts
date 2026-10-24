import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

// 관리자 컨테이너의 localhost는 백엔드가 아니라 자신이므로 서비스 DNS를 사용한다.
const backendUrl = process.env.LOCAL_BACKEND_URL ?? "http://backend:8080"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: true,
    port: 5174,
    strictPort: true,
    watch: { usePolling: true, interval: 350 },
    proxy: {
      "/admin": backendUrl,
      "/board-images": backendUrl,
      "^/mascots/.*\.(png|jpe?g|webp|gif)$": backendUrl,
    },
  },
})
