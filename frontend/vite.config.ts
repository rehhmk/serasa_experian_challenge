/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// Backend has no CORS/security config at all (verified against the Java source) —
// this proxy is the entire reason a separate Vite dev server never needs one.
// See frontend/README.md for how to point it at a non-default backend port.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/setupTests.ts'],
    },
  }
})
