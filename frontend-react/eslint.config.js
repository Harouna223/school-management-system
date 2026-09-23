import js from '@eslint/js'
import globals from 'globals'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'

/**
 * Configuration ESLint (config plate, ESLint 9).
 * Objectif : analyse statique réelle (identifiants non importés, règles des hooks,
 * code mort) — voir `npm run lint`.
 */
export default [
  {
    ignores: [
      'dist/**',
      // Sorties de build de vérification (`--outDir dist-verify`, `dist-check`…).
      // Le caractère générique évite qu'un répertoire de vérification laissé sur
      // le disque fasse échouer le lint sur du code généré. Aligné sur .gitignore.
      'dist-*/**',
      'node_modules/**',
      'public/**',
      'coverage/**',
      'vite.config.js.timestamp-*.mjs',
    ],
  },
  js.configs.recommended,
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.es2021 },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    plugins: { react, 'react-hooks': reactHooks },
    rules: {
      ...react.configs.flat.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      // Transform JSX automatique (Vite) : React n'a pas besoin d'être importé.
      'react/react-in-jsx-scope': 'off',
      'react/jsx-uses-react': 'off',
      // Composants non typés : les prop-types ne sont pas utilisés dans ce projet.
      'react/prop-types': 'off',
      // Textes français : les apostrophes sont légitimes dans le JSX.
      'react/no-unescaped-entities': 'off',
      // Détecte les composants utilisés sans import ni déclaration (« écran blanc »).
      'react/jsx-no-undef': 'error',
      'react/jsx-uses-vars': 'error',
      'no-unused-vars': [
        'error',
        { args: 'after-used', ignoreRestSiblings: true, varsIgnorePattern: '^_' },
      ],
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
    },
  },
  {
    // Fichiers de configuration exécutés par Node.
    files: ['vite.config.js', 'postcss.config.js', 'tailwind.config.js', 'eslint.config.js'],
    languageOptions: { globals: { ...globals.node } },
  },
  {
    // Tests : globals Vitest.
    files: ['src/test/**/*.{js,jsx}'],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
        describe: 'readonly',
        it: 'readonly',
        test: 'readonly',
        expect: 'readonly',
        vi: 'readonly',
        beforeAll: 'readonly',
        beforeEach: 'readonly',
        afterAll: 'readonly',
        afterEach: 'readonly',
      },
    },
  },
]
