import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs'
import { join, resolve, relative, dirname } from 'node:path'

/*
 * Garde statique : les imports DYNAMIQUES et la table de routage.
 *
 * Pourquoi ce test existe
 * -----------------------
 * Le 2026-09-19, `routes/index.jsx` (fichier SUIVI) importait
 * `pages/payroll/TeacherPayrollPage` — un module qui n'etait alors PAS commite.
 * Le meme schema s'est reproduit cote backend (`8c9b8ba` appelait
 * `ReportService.teacherPaymentReceiptPdf`, jamais commite).
 *
 * Un import dynamique est resolu a la construction du graphe : si la cible
 * manque, l'echec survient tard et son message est noye dans la sortie de
 * Rollup. Ce test le detecte immediatement et NOMME le fichier fautif.
 *
 * Il verifie aussi la coherence de la table de routage : deux `path` identiques
 * se masquent silencieusement (le dernier gagne) et une page devient
 * inaccessible sans qu'aucun outil ne le signale.
 */

/** Racine du projet frontend (voir hooks-imports.test.js pour le detail). */
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

const ROOT = findProjectRoot()
const SRC = join(ROOT, 'src')

/**
 * Retire les COMMENTAIRES uniquement.
 *
 * Attention : ne jamais retirer les litteraux de chaine ici. Le specifieur
 * cherche est lui-meme une chaine (`import('../pages/x')`) : le remplacer par
 * `''` detruirait precisement l'information a verifier et le test passerait
 * toujours, y compris sur un import casse.
 */
function stripComments(code) {
  return code
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/[^\n]*/g, ' ')
}

function collectSourceFiles(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry.startsWith('dist')) continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) collectSourceFiles(full, acc)
    else if (/\.(js|jsx)$/.test(entry)) acc.push(full)
  }
  return acc
}

/**
 * Resout un specifieur relatif comme le fait Vite : le fichier exact, puis les
 * extensions usuelles, puis l'index du dossier.
 */
const EXTENSIONS = ['', '.jsx', '.js', '.tsx', '.ts']
const INDEXES = ['index.jsx', 'index.js', 'index.tsx', 'index.ts']

function resolveModule(fromFile, spec) {
  const base = resolve(dirname(fromFile), spec)
  const candidates = [
    ...EXTENSIONS.map((ext) => base + ext),
    ...INDEXES.map((name) => join(base, name)),
  ]
  return candidates.find((c) => existsSync(c) && statSync(c).isFile())
}

const FILES = collectSourceFiles(SRC)

describe('Analyse statique — imports dynamiques', () => {
  it('scanne bien les sources', () => {
    expect(FILES.length).toBeGreaterThan(50)
  })

  it('chaque import() pointe vers un module existant', () => {
    const problems = []

    for (const file of FILES) {
      const code = stripComments(readFileSync(file, 'utf8'))
      const rel = relative(SRC, file).replace(/\\/g, '/')

      // import('...') / import("...")  — un argument non litteral (gabarit ou
      // variable) n'est pas analysable ici, mais il est signale pour revue.
      const literal = /import\s*\(\s*(['"])([^'"]+)\1\s*\)/g
      let match
      while ((match = literal.exec(code)) !== null) {
        const spec = match[2]
        if (!spec.startsWith('.')) continue // paquet npm : hors perimetre
        if (!resolveModule(file, spec)) problems.push(`${rel} → import('${spec}') introuvable`)
      }

      const dynamicNonLiteral = /import\s*\(\s*[^'")]/.test(code)
      if (dynamicNonLiteral) problems.push(`${rel} → import() a argument non litteral`)
    }

    expect(problems).toEqual([])
  })
})

describe('Analyse statique — table de routage', () => {
  const ROUTES = join(SRC, 'routes', 'index.jsx')

  function declaredPaths() {
    const code = stripComments(readFileSync(ROUTES, 'utf8'))
    const paths = []
    const re = /path="([^"]*)"/g
    let m
    while ((m = re.exec(code)) !== null) paths.push(m[1])
    return paths
  }

  it('declare des routes', () => {
    expect(declaredPaths().length).toBeGreaterThan(20)
  })

  it('aucun path en double (une route en masquerait une autre)', () => {
    const paths = declaredPaths().filter((p) => p !== '*')
    const seen = new Map()
    const duplicates = []
    for (const p of paths) {
      if (seen.has(p)) duplicates.push(p)
      seen.set(p, true)
    }
    expect(duplicates).toEqual([])
  })

  it('chaque path est absolu ou un joker', () => {
    const invalid = declaredPaths().filter((p) => p !== '*' && !p.startsWith('/'))
    expect(invalid).toEqual([])
  })

  it('les espaces personnels sont tous declares', () => {
    const paths = declaredPaths()
    // Ces quatre routes sont celles des portails : une suppression accidentelle
    // les rendrait inaccessibles (redirection vers /dashboard).
    for (const p of ['/my-children', '/my-school', '/my-teaching', '/my-university']) {
      expect(paths).toContain(p)
    }
  })
})
