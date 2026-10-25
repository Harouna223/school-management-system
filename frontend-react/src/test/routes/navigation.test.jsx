import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup, waitFor, fireEvent } from '@testing-library/react'
import { configureStore } from '@reduxjs/toolkit'
import authReducer, { setCredentials } from '../../redux/slices/authSlice'
import themeReducer from '../../redux/slices/themeSlice'
import toastReducer from '../../redux/slices/toastSlice'
import App from '../../App'
import AppProviders from '../../AppProviders'

/*
 * Garde-fou de NAVIGATION INTERNE (SPA).
 *
 * Pourquoi ce fichier existe
 * --------------------------
 * `routes.smoke.test.jsx` monte chaque route par URL : il couvre le PREMIER
 * rendu. Il ne couvre jamais le second — celui qui suit un clic.
 *
 * Or, en production (2026-10-24), cliquer n'importe quel bouton du tableau de
 * bord affichait « Une erreur est survenue » : React levait l'erreur #185
 * (« Maximum update depth exceeded ») pendant la transition. Recharger la page
 * cible directement fonctionnait, ce qui a longtemps masqué le defaut : le
 * premier rendu etait sain, le second non.
 *
 * Ces tests franchissent donc la frontiere : ils montent `/dashboard`, puis
 * naviguent par CLIC dans le SPA — exactement le chemin de l'utilisateur.
 * L'assertion est simple : l'ErrorBoundary ne doit JAMAIS apparaitre.
 */

const benignList = () => {
  const list = []
  list.content = []
  list.items = []
  list.totalElements = 0
  list.totalPages = 0
  list.number = 0
  list.size = 0
  list.first = true
  list.last = true
  return list
}

vi.mock('../../api/endpoints', () => {
  const ok = (data) => Promise.resolve({ data: { data } })
  const benign = new Proxy({}, { get: () => () => Promise.resolve({ data: { data: benignList() } }) })
  const extend = (overrides) =>
    new Proxy(overrides, {
      get: (target, prop) => (prop in target ? target[prop] : benign[prop]),
    })

  const CLIENTS = [
    'authApi', 'dashboardApi', 'studentApi', 'teacherApi', 'classApi',
    'subjectApi', 'scheduleApi', 'attendanceApi', 'parentApi', 'myApi',
    'examApi', 'paymentApi', 'financeApi', 'settingsApi', 'academicYearApi',
    'backupApi', 'libraryApi', 'hrApi', 'teacherHoursApi',
    'stageApi', 'candidatureApi', 'memoireApi',
    'alumnusApi', 'searchApi', 'communicationApi', 'convocationApi', 'userApi',
  ]
  const mocked = {}
  for (const name of CLIENTS) mocked[name] = benign

  mocked.dashboardApi = extend({
    stats: () => ok({}),
    auditLogs: () => ok({ content: [] }),
  })
  mocked.studentApi = extend({ search: () => ok(benignList()) })
  mocked.examApi = extend({ byClass: () => ok([]), gradesByExam: () => ok([]) })
  mocked.classApi = extend({ all: () => ok([]) })
  mocked.academicYearApi = extend({ all: () => ok([]) })

  return mocked
})

afterEach(cleanup)

beforeEach(() => {
  localStorage.clear()
  window.history.pushState({}, '', '/')
})

/** Monte l'application REELLE connectee en SUPER_ADMIN sur /dashboard. */
function mountDashboard() {
  window.history.pushState({}, '', '/dashboard')
  const store = configureStore({
    reducer: { auth: authReducer, theme: themeReducer, toast: toastReducer },
  })
  store.dispatch(
    setCredentials({
      accessToken: 'jeton-de-test',
      refreshToken: 'refresh-de-test',
      user: { firstName: 'Test', lastName: 'Utilisateur', roles: ['SUPER_ADMIN'], permissions: [] },
    }),
  )
  return render(
    <AppProviders store={store}>
      <App />
    </AppProviders>,
  )
}

/**
 * Clique un bouton du tableau de bord puis verifie qu'aucun plantage n'apparait.
 *
 * Pourquoi un `waitFor` qui traque la BOUCLE et pas seulement l'ErrorBoundary :
 * l'erreur #185 nait d'une sequence de mises a jour declenchees en CASCADE, apres
 * la fin du clic. Une assertion synchrone juste apres `fireEvent` passerait donc
 * au vert alors que l'ecran d'erreur s'affiche quelques millisecondes plus tard.
 * On laisse a React le temps d'executer sa file, puis on exige l'absence de crash.
 */
async function clickAndExpectNoCrash(name) {
  const button = await screen.findByRole('button', { name })
  fireEvent.click(button)
  await waitFor(() => expect(screen.queryByText('Une erreur est survenue')).toBeNull(), { timeout: 4000 })
}

/** Le libelle affiche depend de la langue ; on cible le bouton par son icone/libelle traduit. */
function heroButton(label) {
  return screen.findByRole('button', { name: label })
}

describe('Navigation SPA — les boutons du tableau de bord n’interrompent pas l’application', () => {
  it('« Nouvel élève » ouvre /students/new', async () => {
    mountDashboard()
    await screen.findByText('Cycle :')
    await clickAndExpectNoCrash('Nouvel élève')
    expect(window.location.pathname).toBe('/students/new')
  })

  it('« Paiements & Reçus » ouvre /payments', async () => {
    mountDashboard()
    await screen.findByText('Cycle :')
    await clickAndExpectNoCrash('Paiements & Reçus')
    expect(window.location.pathname).toBe('/payments')
  })

  it('« Présences » ouvre /attendances', async () => {
    mountDashboard()
    await screen.findByText('Cycle :')
    await clickAndExpectNoCrash('Présences')
    expect(window.location.pathname).toBe('/attendances')
  })

  it('raccourci « Notes » ouvre /grades', async () => {
    mountDashboard()
    await screen.findByText('Cycle :')
    const button = await heroButton(/^(Notes|Grades)$/)
    fireEvent.click(button)
    await waitFor(() => expect(screen.queryByText('Une erreur est survenue')).toBeNull(), { timeout: 4000 })
    expect(window.location.pathname).toBe('/grades')
  })

  it('« Voir tout le journal » ouvre /audit', async () => {
    mountDashboard()
    await screen.findByText('Cycle :')
    await clickAndExpectNoCrash('Voir tout le journal')
    expect(window.location.pathname).toBe('/audit')
  })
})
