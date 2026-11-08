// 소스 검색 시험 도구: 금지 API(F6)와 비밀 값 패턴(F7)을 찾는다.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

// F6: Vue 3 형태의 금지 API
export const FORBIDDEN_API_PATTERNS = [
  { label: 'v-html', pattern: /v-html/ },
  { label: 'innerHTML', pattern: /\binnerHTML\b/ },
  { label: 'outerHTML', pattern: /\bouterHTML\b/ },
  { label: 'insertAdjacentHTML', pattern: /\binsertAdjacentHTML\b/ },
  { label: 'document.write', pattern: /\bdocument\s*\.\s*write(ln)?\s*\(/ },
  { label: 'eval', pattern: /(^|[^\w.])eval\s*\(/ },
  { label: 'new Function', pattern: /\bnew\s+Function\b/ },
  { label: 'domProps', pattern: /\bdomProps\b/ },
]

// F7: 비밀 값 패턴
export const SECRET_PATTERNS = [
  {
    label: '키·토큰·비밀번호 대입',
    pattern: /(api[_-]?key|secret|token|password)\s*[:=]\s*['"][^'"]{8,}['"]/i,
  },
  { label: '개인 키', pattern: /-----BEGIN [A-Z ]*PRIVATE KEY-----/ },
  { label: 'AWS 액세스 키', pattern: /AKIA[0-9A-Z]{16}/ },
]

/** 텍스트에서 패턴에 걸리는 곳을 모두 돌려준다. */
export function scanText(text, patterns) {
  return patterns.filter(({ pattern }) => pattern.test(text)).map(({ label }) => ({ label }))
}

export function readText(filePath) {
  return fs.readFileSync(filePath, 'utf8')
}

function walk(directory) {
  const entries = fs.readdirSync(directory, { withFileTypes: true })
  return entries.flatMap((entry) => {
    const full = path.join(directory, entry.name)
    return entry.isDirectory() ? walk(full) : [full]
  })
}

/**
 * 검색 대상: src/** (시험 소스 제외), vite.config.js, vite.proxy.js, index.html, .env*.
 * 시험 소스는 금지 문자열을 다뤄야 하므로 제외한다.
 */
export function listScannedFiles() {
  const sourceFiles = walk(path.join(PROJECT_ROOT, 'src')).filter(
    (file) => !/\.test\.js$/.test(file),
  )
  const topLevel = fs
    .readdirSync(PROJECT_ROOT)
    .filter(
      (name) =>
        ['vite.config.js', 'vite.proxy.js', 'index.html'].includes(name) || name.startsWith('.env'),
    )
    .map((name) => path.join(PROJECT_ROOT, name))
  return [...sourceFiles, ...topLevel].map((absolute) => ({
    absolute,
    relative: path.relative(PROJECT_ROOT, absolute).split(path.sep).join('/'),
  }))
}
