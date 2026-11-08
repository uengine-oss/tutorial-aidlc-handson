// 금지 API를 ESLint 규칙이 실제로 막는지 확인한다 (F6). 소스 검색 시험과 함께 이중으로 막는다.
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { ESLint } from 'eslint'
import { PROJECT_ROOT } from './support/scan.js'

const eslint = new ESLint({
  cwd: PROJECT_ROOT,
  overrideConfigFile: path.join(PROJECT_ROOT, 'eslint.config.js'),
})

async function lint(filePath, code) {
  const [result] = await eslint.lintText(code, { filePath: path.join(PROJECT_ROOT, filePath) })
  return result.messages
    .filter((message) => message.severity === 2)
    .map((message) => message.ruleId)
}

describe('ESLint 금지 규칙 (F6)', () => {
  it('v-html을 오류로 막는다', async () => {
    const rules = await lint(
      'src/components/Bad.vue',
      '<script setup>\nconst html = "x"\n</script>\n<template><div v-html="html" /></template>\n',
    )
    expect(rules).toContain('vue/no-v-html')
  })

  it.each([
    ['innerHTML 대입', 'export const f = (el, v) => { el.innerHTML = v }\n'],
    ['outerHTML 대입', 'export const f = (el, v) => { el.outerHTML = v }\n'],
    [
      'insertAdjacentHTML 호출',
      "export const f = (el, v) => el.insertAdjacentHTML('beforeend', v)\n",
    ],
    ['document.write', 'export const f = (v) => document.write(v)\n'],
    ['document.writeln', 'export const f = (v) => document.writeln(v)\n'],
    ['객체 키 innerHTML', 'export const props = { innerHTML: "x" }\n'],
    ['객체 키 domProps', 'export const props = { domProps: { a: 1 } }\n'],
  ])('%s를 막는다', async (_name, code) => {
    const rules = await lint('src/lib/bad.js', code)
    expect(
      rules.some((rule) => rule === 'no-restricted-properties' || rule === 'no-restricted-syntax'),
    ).toBe(true)
  })

  it('eval과 new Function을 막는다', async () => {
    expect(await lint('src/lib/bad.js', 'export const f = (c) => eval(c)\n')).toContain('no-eval')
    expect(await lint('src/lib/bad.js', 'export const f = (c) => new Function(c)\n')).toContain(
      'no-new-func',
    )
  })

  it('템플릿의 :innerHTML 바인딩을 막는다', async () => {
    const rules = await lint(
      'src/components/Bad.vue',
      '<script setup>\nconst html = "x"\n</script>\n<template><div :innerHTML="html" /></template>\n',
    )
    expect(rules).toContain('vue/no-restricted-v-bind')
  })

  it('금지 API가 없는 코드는 오류가 없다', async () => {
    const rules = await lint(
      'src/lib/good.js',
      'export const f = (el, v) => {\n  el.textContent = v\n}\n',
    )
    expect(rules).toEqual([])
  })
})
