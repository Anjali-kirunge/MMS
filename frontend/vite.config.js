import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The SPA talks to the Spring Boot API. In development the Vite dev server
// proxies /api to the backend so the browser never deals with CORS; set
// VITE_API_BASE_URL for a deployed build that serves the API from another host.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
