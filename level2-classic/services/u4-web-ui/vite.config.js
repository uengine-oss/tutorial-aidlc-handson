import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { DEFAULT_PORT, assertLoopbackHost, createProxyConfig, resolveHost } from './vite.proxy.js'

/**
 * 호스트 검사는 설정 파일 안의 환경 변수 읽기가 아니라 Vite가 설정을 해석한 뒤의 값에 건다.
 * CLI의 --host가 이 파일의 값을 덮어써도 해석 뒤 값이 검사된다 (NFR6.6).
 */
const loopbackGuard = {
  name: 'u4-loopback-guard',
  configResolved(config) {
    assertLoopbackHost(config.server.host, process.env)
  },
}

export default defineConfig({
  plugins: [vue(), loopbackGuard],
  server: {
    host: resolveHost(process.env),
    port: DEFAULT_PORT,
    // 포트가 사용 중이면 조용히 다른 포트로 올라가지 않고 실패한다.
    strictPort: true,
    proxy: createProxyConfig(process.env),
  },
})
