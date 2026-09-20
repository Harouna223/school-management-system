import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs'
import { join, resolve, relative } from 'node:path'

/*
 * Garde statique : détecte les hooks UTILISÉS mais NON IMPORTÉS.
 *
 * Pourquoi ce test existe
 * -----------------------
 * `npm run lint` ne peut pas s'exécuter dans ce dépôt : ESLint n'est ni installé
 * ni configuré, et il n'existe aucun fichier `.eslintrc*` / `eslint.config.*`.
 * Il n'y a donc AUCUNE analyse statique : ni `vite build` ni la CI ne signalent
 * un identifiant non déclaré. Le 2026-09-19, `useLocation()` utilisé sans import
 * dans `ParentPortalPage` et `StudentPortalPage` a ainsi pu être livré, et
 * l'application affichait un écran BLANC (ReferenceError au rendu, aucun
 * ErrorBoundary dans l'arbre).
 *
 * Ce test comble ce trou sans ajouter de dépendance : il lit les sources et
 * vérifie que chaque hook appelé est bien importé ou déclaré localement.
 */

/**
 * Racine du projet frontend.
 * `import.meta.url` n'est pas une URL `file:` sous Vitest (transform Vite), donc
 * `fileURLToPath` échoue : on remonte depuis le cwd jusqu'au dossier contenant
 * `src/test`, ce qui fonctionne aussi si Vitest est lancé depuis la racine du dépôt.
 */
function findProjectRoot() {
  let dir = process.cwd()
  for (let i = 0; i < 5; i += 1) {
    if (existsSync(join(dir, 'src', 'test'))) return dir
    const parent = resolve(dir, '..')
    if (parent === dir) break
    dir = parent
  }
  throw new Error(`Racine frontend introuvable depuis ${process.cwd()}`)
}

const SRC = join(findProjectRoot(), 'src')

// Hooks React / React Router / Redux + hooks maison du projet.
const HOOKS = [
  // React
  'useState', 'useEffect', 'useContext', 'useReducer', 'useCallback', 'useMemo',
  'useRef', 'useImperativeHandle', 'useLayoutEffect', 'useDebugValue',
  'useTransition', 'useDeferredValue', 'useId', 'useSyncExternalStore',
  // React Router
  'useNavigate', 'useLocation', 'useParams', 'useSearchParams', 'useRouteError',
  'useOutletContext', 'useHref', 'useResolvedPath',
  // Redux
  'useSelector', 'useDispatch', 'useStore',
  // Projet
  'useToast', 'useI18n', 'useSidebar', 'useFetch',
]

/** Retire commentaires et littéraux de chaîne pour éviter les faux positifs. */
function stripNoise(code) {
  return code
    .replace(/\/\*[\s\S]*?\*\//g, ' ')        // commentaires bloc
    .replace(/\/\/[^\n]*/g, ' ')               // commentaires ligne
    .replace(/'(?:[^'\\\n]|\\.)*'/g, "''")     // chaînes '...'
    .replace(/"(?:[^"\\\n]|\\.)*"/g, '""')     // chaînes "..."
    .replace(/`(?:[^`\\]|\\.)*`/g, '``')       // gabarits `...`
}

function collectSourceFiles(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === 'dist' || entry === 'dist-verify') continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) collectSourceFiles(full, acc)
    else if (/\.(js|jsx)$/.test(entry)) acc.push(full)
  }
  return acc
}

describe('Analyse statique — hooks importés', () => {
  const files = collectSourceFiles(SRC)

  it('scanne bien les sources', () => {
    expect(files.length).toBeGreaterThan(50)
  })

  it('aucun hook utilisé sans être importé ni déclaré', () => {
    const problems = []

    for (const file of files) {
      const raw = readFileSync(file, 'utf8')
      const code = stripNoise(raw)
      const rel = relative(SRC, file).replace(/\\/g, '/')

      for (const hook of HOOKS) {
        const used = new RegExp(`\\b${hook}\\s*\\(`).test(code)
        if (!used) continue

        // Déclaré localement (function / const / let / var) ou importé ?
        const declaredLocally = new RegExp(
          `\\b(?:function|const|let|var|class)\\s+${hook}\\b`,
        ).test(code)
        const imported = new RegExp(
          `import[^;]*\\b${hook}\\b[^;]*from`,
        ).test(code)

        if (!declaredLocally && !imported) problems.push(`${rel} → ${hook}`)
      }
    }

    expect(problems).toEqual([])
  })
})
