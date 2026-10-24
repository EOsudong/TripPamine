import { defineConfig, type ProxyOptions } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

// Docker 컨테이너에서 기존 Windows Spring Boot에 접근한다.
const backendUrl = process.env.LOCAL_BACKEND_URL ?? "http://host.docker.internal:8080"

const backendRoots = [
  "api", "users", "auth", "oauth2", "admin", "accounts", "accountbook",
  "boards", "board-images", "bookmarks", "finance", "recommendations",
  "travel-plans", "mystery-tours", "quests", "mascots", "weather",
  "tours", "tour", "nego", "ai", "spawns", "diaries", "ws",
  "best-shot", "mosaic", "multi-mosaic", "mock-bank", "assets",
]

const backendProxy: ProxyOptions = {
  target: backendUrl,
  changeOrigin: true,
  ws: true,
  bypass(req) {
    // React 화면 이동과 백엔드 API 요청을 구분한다.
    const htmlNavigation = req.method === "GET" && req.headers.accept?.includes("text/html")
    if (htmlNavigation && !req.url?.startsWith("/oauth2/authorization/")) {
      return req.url
    }
    return undefined
  },
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: { global: "globalThis" },
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    watch: { usePolling: true, interval: 350 },
    proxy: {
      [`^/(${backendRoots.join("|")})(?:/|\\?|$)`]: backendProxy,
    },
  },
})