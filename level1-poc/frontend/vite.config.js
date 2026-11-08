import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// 각 마이크로서비스로 프록시한다 (개발 서버 전용).
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api/recommend': 'http://localhost:8081',
      '/api/payments': 'http://localhost:8082',
      '/api/mock-pg': 'http://localhost:8082',
      '/api/reservations': 'http://localhost:8083'
    }
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.spec.js']
  }
})
