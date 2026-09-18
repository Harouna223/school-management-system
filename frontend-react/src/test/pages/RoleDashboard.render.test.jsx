import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import RoleDashboard from '../../pages/RoleDashboard'
import { I18nProvider } from '../../i18n/I18nContext'

afterEach(cleanup)

// Réponses simulées : toutes les API consommées par les tableaux de bord.
vi.mock('../../api/endpoints', () => {
  const ok = (data) => Promise.resolve({ data: { data } })
  return {
    dashboardApi: {
      stats: () => ok({}),
      universityStats: () => ok({}),
      universityReport: () => ok({}),
      auditLogs: () => ok({ content: [] }),
    },
    myApi: {
      profile: () => ok({ firstName: 'Awa', lastName: 'Diop', className: '6e A', matricule: 'M-001' }),
      schedule: () => ok([]),
      grades: () => ok([]),
      bulletins: () => ok([]),
      attendances: () => ok([]),
      invoices: () => ok([]),
      teacherProfile: () => ok({ firstName: 'Ibrahim', lastName: 'Traore', employeeNo: 'E-01' }),
      teacherSchedule: () => ok([]),
      teacherClasses: () => ok([]),
    },
    parentApi: {
      children: () => ok([{ id: 1, firstName: 'Awa', lastName: 'Diop', matricule: 'M-001' }]),
      childBulletins: () => ok([]),
      childAttendances: () => ok([]),
      childInvoices: () => ok([]),
    },
    teacherHoursApi: {
      myMonthly: () => ok([]),
      myTransactions: () => ok([]),
    },
  }
})

function renderDashboard(roles) {
  const store = configureStore({
    reducer: {
      auth: () => ({
        isAuthenticated: true,
        user: { firstName: 'Test', lastName: 'Utilisateur', roles },
      }),
    },
  })
  return render(
    <Provider store={store}>
      <I18nProvider>
        <MemoryRouter initialEntries={['/dashboard']}>
          <RoleDashboard />
        </MemoryRouter>
      </I18nProvider>
    </Provider>,
  )
}

describe('RoleDashboard — rendu par rôle', () => {
  it('affiche le tableau de bord administratif pour SUPER_ADMIN', async () => {
    renderDashboard(['SUPER_ADMIN'])
    expect(await screen.findByText('Cycle :')).toBeInTheDocument()
    expect(screen.queryByText(/Enfants suivis/)).not.toBeInTheDocument()
  })

  it('affiche le tableau de bord parent pour PARENT', async () => {
    renderDashboard(['PARENT'])
    expect(await screen.findByText('Enfants suivis')).toBeInTheDocument()
    expect(screen.getByText(/Ouvrir l'espace de Awa/)).toBeInTheDocument()
    expect(screen.queryByText('Cycle :')).not.toBeInTheDocument()
  })

  it('affiche le tableau de bord élève pour ELEVE', async () => {
    renderDashboard(['ELEVE'])
    expect(await screen.findByText('Moyenne générale')).toBeInTheDocument()
    expect(screen.getByText('Points de vigilance')).toBeInTheDocument()
    expect(screen.queryByText('Enfants suivis')).not.toBeInTheDocument()
  })

  it('affiche le tableau de bord enseignant pour ENSEIGNANT', async () => {
    renderDashboard(['ENSEIGNANT'])
    expect(await screen.findByText('Classes assignées')).toBeInTheDocument()
    expect(screen.getByText('Ma rémunération')).toBeInTheDocument()
    expect(screen.queryByText('Points de vigilance')).not.toBeInTheDocument()
  })

  it('respecte le rôle principal d’un compte multi-rôles', async () => {
    renderDashboard(['ELEVE', 'PARENT'])
    expect(await screen.findByText('Enfants suivis')).toBeInTheDocument()
    expect(screen.queryByText('Points de vigilance')).not.toBeInTheDocument()
  })
})
