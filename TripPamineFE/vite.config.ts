import { defineConfig, loadEnv } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "")
  const tunnelHost = env.TUNNEL_HOST

  return {
    plugins: [react(), tailwindcss()],
    // sockjs-client(@stomp/stompjs 소켓 팩토리)가 브라우저에 없는 `global`을 참조하므로 매핑해준다.
    define: {
      global: "globalThis",
    },
    server: {
      host: true,
      hmr: tunnelHost
        ? { protocol: "wss", host: tunnelHost, clientPort: 443 }
        : undefined,
    },
  }
})
