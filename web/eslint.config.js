import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import jsxA11y from 'eslint-plugin-jsx-a11y'

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'playwright-report', 'test-results'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      'jsx-a11y': jsxA11y,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['**/*.test.{ts,tsx}'],
    languageOptions: { globals: globals.vitest },
  },
  {
    files: ['src/app/AuthContext.tsx'],
    rules: { 'react-refresh/only-export-components': ['warn', { allowExportNames: ['useAuth'] }] },
  },
  {
    files: ['src/app/PreferencesContext.tsx'],
    rules: { 'react-refresh/only-export-components': ['warn', { allowExportNames: ['usePreferences'] }] },
  },
  {
    files: ['src/components/LessonCard.tsx'],
    rules: { 'react-refresh/only-export-components': ['warn', { allowExportNames: ['formatDuration'] }] },
  },
)
