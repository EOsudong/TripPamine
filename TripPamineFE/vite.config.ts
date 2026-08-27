import { defineConfig, loadEnv } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "")
  const tunnelHost = env.TUNNEL_HOST

  return {
    plugins: [react(), tailwindcss()],
    server: {
      host: true,
      hmr: tunnelHost
        ? { protocol: "wss", host: tunnelHost, clientPort: 443 }
        : undefined,
    },
  }
})
