import { describe, expect, it } from 'vitest'
import {
  FORBIDDEN_API_PATTERNS,
  SECRET_PATTERNS,
  listScannedFiles,
  readText,
  scanText,
} from './support/scan.js'

const labelsOf = (hits) => hits.map((hit) => hit.label)

describe('소스 검색 - 패턴의 자체 확인 (F6, F7)', () => {
  it.each([
    ['v-html', '<p v-html="message"></p>', 'v-html'],
    ['innerHTML', 'element.innerHTML = value', 'innerHTML'],
    ['outerHTML', 'element.outerHTML = value', 'outerHTML'],
    ['insertAdjacentHTML', "el.insertAdjacentHTML('beforeend', x)", 'insertAdjacentHTML'],
    ['document.write', 'document.write(x)', 'document.write'],
    ['document.writeln', 'document .  writeln (x)', 'document.write'],
    ['eval', 'const r = eval(code)', 'eval'],
    ['new Function', 'const f = new Function("return 1")', 'new Function'],
    ['domProps', "h('div', { domProps: { a: 1 } })", 'domProps'],
  ])('금지 API %s를 잡는다', (_name, text, label) => {
    expect(labelsOf(scanText(text, FORBIDDEN_API_PATTERNS))).toContain(label)
  })

  it('평범한 코드는 잡지 않는다', () => {
    const clean = [
      '<p>{{ message }}</p>',
      'const evaluation = 1',
      'element.textContent = value',
      'document.createElement("p")',
      'const writer = 1',
    ].join('\n')
    expect(scanText(clean, FORBIDDEN_API_PATTERNS)).toEqual([])
  })

  it.each([
    ['api_key 대입', "const apiKey = 'abcd1234efgh'", true],
    ['api-key 속성', 'api-key: "abcd1234efgh"', true],
    ['secret', "secret = 'supersecretvalue'", true],
    ['token', 'token: "abcdefgh12345"', true],
    ['password', "password='hunter2hunter2'", true],
    ['개인 키', '-----BEGIN RSA PRIVATE KEY-----', true],
    ['AWS 액세스 키', 'AKIAABCDEFGHIJKLMNOP', true],
    ['짧은 값은 허용', "token = 'abc'", false],
    ['값이 없는 이름', 'const token = readToken()', false],
    ['환경 변수 읽기', 'const secret = process.env.SECRET', false],
  ])('비밀 값 패턴: %s', (_name, text, expected) => {
    expect(scanText(text, SECRET_PATTERNS).length > 0).toBe(expected)
  })
})

describe('소스 검색 - 대상 파일', () => {
  const files = listScannedFiles()
  const names = files.map((file) => file.relative)

  it('앱 소스, 설정, index.html이 검색 대상이다 (시험 소스는 제외)', () => {
    expect(names).toEqual(
      expect.arrayContaining([
        'src/App.vue',
        'src/main.js',
        'src/api/api.js',
        'src/composables/useApp.js',
        'src/components/ParkingCard.vue',
        'vite.config.js',
        'vite.proxy.js',
        'index.html',
      ]),
    )
    expect(names.filter((name) => name.endsWith('.test.js'))).toEqual([])
    expect(names.filter((name) => name.startsWith('test/'))).toEqual([])
    expect(files.length).toBeGreaterThanOrEqual(20)
  })
})

describe('소스 검색 - 실제 소스', () => {
  const files = listScannedFiles()

  it('금지 API가 0건이다 (NFR6.1)', () => {
    const found = files.flatMap((file) =>
      scanText(readText(file.absolute), FORBIDDEN_API_PATTERNS).map(
        (hit) => `${file.relative}: ${hit.label}`,
      ),
    )
    expect(found).toEqual([])
  })

  it('비밀 값 패턴이 0건이다 (NFR6.2)', () => {
    const found = files.flatMap((file) =>
      scanText(readText(file.absolute), SECRET_PATTERNS).map(
        (hit) => `${file.relative}: ${hit.label}`,
      ),
    )
    expect(found).toEqual([])
  })

  it('서비스 코드를 직접 import하지 않는다 (NFR4.1)', () => {
    const found = files
      .filter((file) => /\.(js|vue)$/.test(file.relative))
      .filter((file) =>
        /from\s+['"][^'"]*(services\/|u[123]-)[^'"]*['"]/.test(readText(file.absolute)),
      )
      .map((file) => file.relative)
    expect(found).toEqual([])
  })

  it('세션 ID 쿼리(sessionId=)는 구독 URL을 만드는 api.js 한 곳에만 있다 (NFR6.4)', () => {
    const holders = files
      .filter((file) => /sessionId=/.test(readText(file.absolute)))
      .map((file) => file.relative)
    expect(holders).toEqual(['src/api/api.js'])
  })
})
