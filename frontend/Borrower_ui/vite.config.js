import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/** Browser calls stay same-origin in local development; Vite forwards /api to Borrower :8082. */
export default defineConfig({
  plugins: [react()],
  server: { port: 5174, strictPort: false, proxy: { '/api': { target: 'http://localhost:8082', changeOrigin: true } } },
})
