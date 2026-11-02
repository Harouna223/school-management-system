import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from '../../pages/DashboardPage'
import { I18nProvider } from '../../i18n/I18nContext'

afterEach(cleanup)

/*
 * Charge utile copiée du DTO réel `DashboardResponse` côté backend, avec les
 * libellés que `DashboardService` produit vraiment : « Garçons » / « Filles »
 * pour le genre, mois abrégés pour les recettes, nom de classe pour la
 * répartition. Le test sert donc aussi de garde de contrat : si un champ est
 * renommé, l'indicateur correspondant cesse de s'afficher et l'assertion tombe.
 *
 * C'est la page la plus chargée en graphiques de l'application, et la seule qui
 * ne rendait aucun test — c'est là que la montée de recharts 2 -> 3 n'était
 * vérifiée que par la compilation.
 */
const STATS = {
  totalStudents: 1284,
  activeStudents: 1250,
  totalTeachers: 48,
  totalClasses: 32,
  totalSubjects: 19,
  studentsPerClassAvg: 39.06,
  unpaidInvoices: 7,
  totalPaymentsCount: 431,
  monthlyRevenue: 4250000,
  monthlyExpenses: 1875000,
  totalRevenue: 38200000,
  totalExpenses: 15400000,
  todayPresent: 1198,
  todayAbsent: 52,
  totalLate: 14,
  totalBooks: 2140,
  borrowedBooks: 96,
  pendingLeaves: 3,
  revenueByMonth: [
    { label: 'juin', value: 3100000 },
    { label: 'juil.', value: 2875000 },
    { label: 'août', value: 1980000 },
    { label: 'sept.', value: 4620000 },
    { label: 'oct.', value: 3980000 },
    { label: 'nov.', value: 4250000 },
  ],
  studentsByClass: [
    { label: '6ème A', value: 42 },
    { label: '6ème B', value: 39 },
    { label: '5ème A', value: 41 },
    { label: '4ème A', value: 38 },
  ],
  studentsByGender: [
    { label: 'Garçons', value: 660 },
    { label: 'Filles', value: 590 },
  ],
  averageBySubject: [],
}

// `vi.hoisted` : la fabrique de `vi.mock` est hissée au-dessus des imports,
// elle ne peut donc pas refermer sur une variable déclarée plus bas.
const { statsMock } = vi.hoisted(() => ({ statsMock: vi.fn() }))

vi.mock('../../api/endpoints', () => ({
  dashboardApi: {
    stats: (...args) => statsMock(...args),
    auditLogs: () => Promise.resolve({ data: { data: { content: [] } } }),
  },
}))

function renderDashboard() {
  const store = configureStore({
    reducer: {
      auth: () => ({
        isAuthenticated: true,
        user: { firstName: 'Aminata', lastName: 'Diallo', roles: ['ADMIN'] },
      }),
    },
  })
  return render(
    <Provider store={store}>
      <I18nProvider>
        <MemoryRouter initialEntries={['/dashboard']}>
          <DashboardPage />
        </MemoryRouter>
      </I18nProvider>
    </Provider>,
  )
}

beforeEach(() => {
  statsMock.mockReset()
  statsMock.mockResolvedValue({ data: { data: STATS } })
})

describe('DashboardPage – indicateurs', () => {
  it('affiche les valeurs renvoyées par /dashboard/stats', async () => {
    renderDashboard()

    // 1250 = activeStudents, 2140 = totalBooks : valeurs distinctives, absentes
    // du reste de l'écran (dates, libellés), donc sans ambiguïté de sélection.
    expect(await screen.findByText('1250')).toBeInTheDocument()
    expect(screen.getByText('2140')).toBeInTheDocument()
  })
})

describe('DashboardPage – graphiques Recharts', () => {
  it('monte les trois graphiques avec les données reçues', async () => {
    const { container } = renderDashboard()
    await screen.findByText('1250')

    // Recharts ne rend son SVG qu'une fois le conteneur mesuré : c'est le
    // polyfill de src/test/setup.js qui fournit une taille sous jsdom.
    await waitFor(() => {
      expect(container.querySelectorAll('.recharts-responsive-container')).toHaveLength(3)
    })

    expect(container.querySelector('.recharts-area')).not.toBeNull()
    expect(container.querySelector('.recharts-pie')).not.toBeNull()
    expect(container.querySelector('.recharts-bar')).not.toBeNull()
  })

  it('rend la légende du camembert avec les libellés du serveur', async () => {
    renderDashboard()

    // Passe par le `label` sous forme de fonction du <Pie> : c'est précisément
    // la prop dont la signature a changé en recharts 3.
    expect(await screen.findByText('Garçons')).toBeInTheDocument()
    expect(screen.getByText('Filles')).toBeInTheDocument()
  })
})

describe('DashboardPage – filtre de cycle', () => {
  it('ne transmet aucun cycle au premier chargement', async () => {
    renderDashboard()

    await waitFor(() => expect(statsMock).toHaveBeenCalled())
    expect(statsMock.mock.calls[0][0]).toBeUndefined()
  })

  it("transmet la constante de l'énumération, pas le libellé affiché", async () => {
    renderDashboard()
    await screen.findByText('1250')

    await userEvent.click(screen.getByText('Collège'))

    await waitFor(() => expect(statsMock).toHaveBeenCalledWith('COLLEGE'))

    // Garde de non-régression : Spring convertit `cycle` en énumération et
    // rejette toute valeur inconnue. Envoyer « Collège » renvoyait un 400 et
    // laissait tous les indicateurs vides, sans message pour l'utilisateur.
    expect(statsMock).not.toHaveBeenCalledWith('Collège')
  })
})
