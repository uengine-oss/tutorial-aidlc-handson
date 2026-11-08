import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import prettier from 'eslint-config-prettier'

// 서버가 준 문자열을 HTML로 해석하는 길을 막는다 (F6). 시험 소스는 금지 문자열을 다뤄야 하므로 제외한다.
const forbiddenHtmlApi = '/^(innerHTML|outerHTML|insertAdjacentHTML)$/'

export default [
  { ignores: ['node_modules/**', 'dist/**', 'coverage/**'] },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser },
    },
    rules: {
      'vue/no-v-html': 'error',
      'no-eval': 'error',
      'no-new-func': 'error',
      'no-implied-eval': 'error',
    },
  },
  {
    files: ['src/App.vue'],
    rules: { 'vue/multi-word-component-names': 'off' },
  },
  {
    files: ['src/**/*.{js,vue}'],
    ignores: ['src/**/*.test.js'],
    rules: {
      'no-restricted-properties': [
        'error',
        { property: 'innerHTML', message: '서버 문자열은 {{ }}로만 그린다.' },
        { property: 'outerHTML', message: '서버 문자열은 {{ }}로만 그린다.' },
        { property: 'insertAdjacentHTML', message: '서버 문자열은 {{ }}로만 그린다.' },
        { object: 'document', property: 'write', message: 'document.write 금지' },
        { object: 'document', property: 'writeln', message: 'document.writeln 금지' },
      ],
      'no-restricted-syntax': [
        'error',
        {
          selector: `Identifier[name=${forbiddenHtmlApi}]`,
          message: 'HTML을 직접 넣는 API는 쓰지 않는다.',
        },
        {
          selector: `Literal[value=${forbiddenHtmlApi}]`,
          message: 'HTML을 직접 넣는 API는 쓰지 않는다.',
        },
        {
          selector: "Property[key.name='domProps']",
          message: 'domProps로 HTML을 넘기지 않는다.',
        },
      ],
      'vue/no-restricted-v-bind': [
        'error',
        '/^inner-?html$/i',
        '/^outer-?html$/i',
        '/^dom-?props$/i',
      ],
    },
  },
  {
    files: ['*.js', '*.mjs', 'scripts/**/*.mjs', 'test/**/*.js', 'src/**/*.test.js'],
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
  },
  prettier,
]
