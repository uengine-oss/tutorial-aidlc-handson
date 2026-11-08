import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// 프로젝트 3개: unit(병렬, jsdom), timing(순차, jsdom), proxy(순차, node).
// 시간을 재는 시험은 계측이 없는 `npm run test`에서만 판정한다. test:coverage는 unit만 계측한다.
export default defineConfig({
  plugins: [vue()],
  test: {
    // 시간 시험의 중앙값·p99·최댓값 출력이 통과한 시험에서도 남도록 콘솔 출력을 숨기지 않는다.
    silent: false,
    coverage: {
      provider: 'v8',
      include: ['src/**', 'vite.proxy.js'],
      exclude: ['src/main.js', 'src/**/*.test.js', 'test/**', 'vite.config.js'],
      reporter: ['text', 'json-summary'],
      // 팀 하한(classic 범위): 라인 80%. 낮추지 않는다.
      thresholds: { lines: 80 },
    },
    projects: [
      {
        extends: true,
        test: {
          name: 'unit',
          environment: 'jsdom',
          include: ['src/**/*.test.js', 'test/**/*.test.js'],
          exclude: ['test/**/*.timing.test.js', 'test/**/*.proxy.test.js', 'node_modules/**'],
        },
      },
      {
        extends: true,
        test: {
          name: 'timing',
          environment: 'jsdom',
          include: ['test/**/*.timing.test.js'],
          fileParallelism: false,
          testTimeout: 60000,
        },
      },
      {
        extends: true,
        test: {
          name: 'proxy',
          environment: 'node',
          include: ['test/**/*.proxy.test.js'],
          fileParallelism: false,
          testTimeout: 60000,
          hookTimeout: 60000,
        },
      },
    ],
  },
})
