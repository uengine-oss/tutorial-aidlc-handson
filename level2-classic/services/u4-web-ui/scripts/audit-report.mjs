// 보고 전용: 높음 이상 취약점을 출력하고 종료 코드는 항상 0이다 (NFR6.7).
// 레지스트리에 닿지 못하면 "audit 불가"를 출력한다.
import { spawnSync } from 'node:child_process'

const NETWORK_PATTERN =
  /ENOTFOUND|EAI_AGAIN|ECONNREFUSED|ECONNRESET|ETIMEDOUT|EHOSTUNREACH|FETCH_ERROR|audit endpoint|network/i

const result = spawnSync('npm', ['audit', '--audit-level=high'], { encoding: 'utf8' })
const output = `${result.stdout ?? ''}${result.stderr ?? ''}`

console.log('=== npm audit --audit-level=high (보고 전용) ===')
if (result.error || (result.status !== 0 && NETWORK_PATTERN.test(output))) {
  console.log('audit 불가: 레지스트리에 닿지 못해 취약점을 확인하지 못했습니다.')
  console.log(output.trim())
} else if (result.status === 0) {
  console.log('높음·치명 취약점 없음.')
  console.log(output.trim())
} else {
  console.log(
    '높음 또는 치명 취약점이 있습니다. 목록을 code-summary.md와 Unit 체크포인트 보고에 기록하세요.',
  )
  console.log(output.trim())
}
process.exit(0)
