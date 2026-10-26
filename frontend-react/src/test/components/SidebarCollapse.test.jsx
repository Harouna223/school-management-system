import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup, waitFor, fireEvent } from '@testing-library/react'
import { configureStore } from '@reduxjs/toolkit'
import authReducer, { setCredentials } from '../../redux/slices/authSlice'
import themeReducer from '../../redux/slices/themeSlice'
import toastReducer from '../../redux/slices/toastSlice'
import App from '../../App'
import AppProviders from '../../AppProviders'

/*
 * Garde-fou du MENU LATERAL : deplier puis replier un groupe.
 *
 * Pourquoi ce fichier existe
 * --------------------------
 * Le 2026-10-24, tout clic de navigation traversant le menu lateral faisait
 * apparaitre « Une erreur est survenue » (React #185, « Maximum update depth
 * exceeded »). La cause etait le `<Collapse timeout="auto" unmountOnExit>` de
 * Sidebar.jsx : avec `timeout="auto"`, MUI transmet `timeout: null` a
 * react-transition-group et, sur la transition `UNMOUNTED -> entered`,
 * `getDerivedStateFromProps` et `componentDidUpdate` se renvoyaient
 * indefiniment l'etat EXITED / ENTERING.
 *
 * Un rechargement direct de l'URL ne declenchait PAS la boucle (le groupe etait
 * deja ouvert au montage). Les tests de route page-par-page de
 * `routes.smoke.test.jsx` ne la voyaient donc pas. Il fallait interagir avec le
 * MENU : c'est ce que fait ce fichier.
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
    new Proxy(overrides, { get: (target, prop) => (prop in target ? target[prop] : benign[prop]) })

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
  // Les cycles actifs conditionnent l'affichage des rubriques `requires: 'school'`.
  mocked.settingsApi = extend({
    cycles: () => ok(['JARDIN', 'PRIMAIRE', 'COLLEGE', 'LYCEE', 'UNIVERSITE']),
  })
  return mocked
})

afterEach(cleanup)

beforeEach(() => {
  localStorage.clear()
  window.history.pushState({}, '', '/dashboard')
})

function mountDashboard() {
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

describe('Menu latéral — déplier puis replier un groupe ne casse pas l’application', () => {
  it('le groupe « Administration » s’ouvre et se referme', async () => {
    mountDashboard()
    await screen.findByText('Cycle :')

    // Le groupe est identifie par son en-tete (ListItemButton) dans le menu.
    const nav = document.querySelector('nav')
    const headers = [...nav.querySelectorAll('.MuiListItemButton-root')]
    const adminHeader = headers.find((el) => /Administration/i.test(el.textContent || ''))
    expect(adminHeader, 'en-tête « Administration » introuvable dans le menu').toBeTruthy()

    /*
     * On mesure la CONVERGENCE de la transition, pas la simple presence du DOM.
     *
     * Depuis le correctif, le sous-menu n'est plus demonte (`unmountOnExit`
     * retire) : ses liens existent donc en permanence, meme replies. Les
     * chercher ne prouverait rien. MUI pose en revanche une classe d'etat sur le
     * conteneur : `MuiCollapse-entered` quand la transition a abouti,
     * `MuiCollapse-hidden` quand le groupe est replie. C'est exactement ce que
     * la boucle infinie empechait d'atteindre.
     */
    const collapse = () => nav.querySelector('a[href="/students"]')?.closest('.MuiCollapse-root')

    await waitFor(() => expect(collapse()).toBeTruthy(), { timeout: 4000 })
    expect(collapse().className, 'groupe replié au départ').toMatch(/MuiCollapse-hidden/)

    fireEvent.click(adminHeader)
    await waitFor(() => expect(collapse().className).toMatch(/MuiCollapse-entered/), { timeout: 4000 })
    await waitFor(() => expect(screen.queryByText('Une erreur est survenue')).toBeNull(), { timeout: 4000 })

    // Puis on replie : c'est la transition inverse, historiquement aussi fragile.
    fireEvent.click(adminHeader)
    await waitFor(() => expect(collapse().className).toMatch(/MuiCollapse-hidden/), { timeout: 4000 })
    await waitFor(() => expect(screen.queryByText('Une erreur est survenue')).toBeNull(), { timeout: 4000 })
  })

  it('un groupe replié s’ouvre réellement (transition aboutie)', async () => {
    mountDashboard()
    await screen.findByText('Cycle :')
    const nav = document.querySelector('nav')
    const headers = [...nav.querySelectorAll('.MuiListItemButton-root')]
    const services = headers.find((el) => /Services/i.test(el.textContent || ''))
    expect(services, 'en-tête « Services » introuvable').toBeTruthy()

    // Le lien existe deja (contenu garde monte) : c'est son etat qui doit changer.
    // On cible le Collapse du groupe via le lien, pas le premier du menu.
    const collapse = () => nav.querySelector('a[href="/library"]')?.closest('.MuiCollapse-root')
    expect(collapse()).toBeTruthy()
    expect(collapse().className, 'groupe replié au départ').toMatch(/MuiCollapse-hidden/)

    fireEvent.click(services)
    await waitFor(() => expect(collapse().className).toMatch(/MuiCollapse-entered/), { timeout: 4000 })
    await waitFor(() => expect(screen.queryByText('Une erreur est survenue')).toBeNull(), { timeout: 4000 })
  })
})
