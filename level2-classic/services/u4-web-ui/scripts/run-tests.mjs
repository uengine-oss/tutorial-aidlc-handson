// 전체 시험을 unit -> timing -> proxy 순서로 순차 실행한다 (F3).
// 시간을 재는 시험끼리, 그리고 프록시 통합 시험과 동시에 돌지 않게 하려는 것이다.
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

// vitest의 exports가 vitest.mjs를 열어 두지 않으므로 프로젝트의 node_modules 경로로 직접 가리킨다.
const vitestBin = fileURLToPath(new URL('../node_modules/vitest/vitest.mjs', import.meta.url))
const projects = ['unit', 'timing', 'proxy']
const extraArgs = process.argv.slice(2)

const failed = []
for (const project of projects) {
  console.log(`\n=== vitest 프로젝트: ${project} ===`)
  const result = spawnSync(
    process.execPath,
    [vitestBin, 'run', '--project', project, ...extraArgs],
    { stdio: 'inherit' },
  )
  if (result.status !== 0) {
    failed.push(project)
  }
}

if (failed.length > 0) {
  console.error(`\n실패한 프로젝트: ${failed.join(', ')}`)
  process.exit(1)
}
console.log('\n모든 프로젝트 통과: ' + projects.join(' -> '))
