import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import ParentPortalPage from '../../pages/parents/ParentPortalPage'
import StudentPortalPage from '../../pages/students/StudentPortalPage'
import TeacherPortalPage from '../../pages/teachers/TeacherPortalPage'
import UniversityPortalPage from '../../pages/university/UniversityPortalPage'
import { I18nProvider } from '../../i18n/I18nContext'

/*
 * Garde-fou de rendu des ESPACES PERSONNELS (/my-children, /my-school,
 * /my-teaching, /my-university).
 *
 * Ces quatre pages n'etaient couvertes par aucun test : seul le tableau de bord
 * (/dashboard) etait rendu. Un identifiant manquant dans leurs imports (par ex.
 * `useLocation` utilise mais non importe) provoquait donc un ReferenceError au
 * rendu — donc un ecran blanc, l'application n'ayant aucun ErrorBoundary.
 * Ce fichier monte reellement les quatre pages pour fermer cette breche.
 */

afterEach(cleanup)

// Charges utiles reelles (relevees le 2026-09-19) — voir RoleDashboard.render.test.jsx.
const STUDENT = {
  id: 10,
  matricule: 'ETU-2026-000008',
  firstName: 'Idrissa',
  lastName: 'Siby',
  enrollmentDate: '2026-08-17',
  status: 'ACTIVE',
  educationCycle: 'COLLEGE',
  classId: 1,
  className: '6ème A',
  photo: null,
}

const BULLETIN = {
  id: 5,
  studentId: 10,
  studentName: 'Idrissa Siby',
  matricule: 'ETU-2026-000008',
  className: '6ème A',
  term: 'T1',
  academicYear: '2026-2027',
  average: 14.14,
  rank: 3,
  mention: 'Bien',
  decision: 'ADMIS',
}

const ATTENDANCE = {
  id: 8,
  studentId: 10,
  date: '2026-08-23',
  status: 'ABSENT',
  justification: null,
}

const INVOICE = {
  id: 1,
  invoiceNo: 'FAC-260823-00001',
  feeTypeName: 'Scolarité Trimestre 1',
  amount: 40000.0,
  paidAmount: 35000.0,
  dueDate: '2026-08-23',
  status: 'PAID',
}

const MY_GRADE = {
  id: 1,
  examId: 3,
  examName: 'Contrôle 1',
  subjectId: 2,
  subjectName: 'Mathématiques',
  term: 'T1',
  academicYear: '2026-2027',
  value: 15.5,
  maxValue: 20,
  appreciation: null,
  examDate: '2026-08-20',
}

const SCHEDULE = {
  id: 2,
  dayOfWeek: 'MONDAY',
  startTime: '10:30:00',
  endTime: '13:00:00',
  classId: 1,
  className: '6ème A',
  subjectId: 2,
  subjectName: 'Mathématiques',
  teacherId: 1,
  teacherName: 'Jean Kamdem',
  roomId: 2,
  roomName: 'Salle A2',
}

const TEACHER = {
  id: 1,
  employeeNo: 'ENS-2026-0001',
  firstName: 'Jean',
  lastName: 'Kamdem',
  phone: '+237690000001',
  email: 'jk@school.com',
  hireDate: '2024-09-01',
  contractType: 'CDI',
  salary: 200000.0,
  status: 'ACTIVE',
}

const TEACHER_CLASS = {
  assignmentId: 7,
  classId: 1,
  className: '6ème A',
  subjectId: 2,
  subjectName: 'Mathématiques',
}

vi.mock('../../api/endpoints', () => {
  const ok = (data) => Promise.resolve({ data: { data } })
  return {
    parentApi: {
      children: () => ok([STUDENT]),
      childBulletins: () => ok([BULLETIN]),
      childAttendances: () => ok([ATTENDANCE]),
      childGrades: () => ok([MY_GRADE]),
      childTimeline: () => ok([]),
      childInvoices: () => ok([INVOICE]),
      childUniversityEnrollments: () => ok([]),
    },
    myApi: {
      profile: () => ok(STUDENT),
      schedule: () => ok([SCHEDULE]),
      grades: () => ok([MY_GRADE]),
      bulletins: () => ok([BULLETIN]),
      attendances: () => ok([ATTENDANCE]),
      invoices: () => ok([INVOICE]),
      teacherProfile: () => ok(TEACHER),
      teacherSchedule: () => ok([SCHEDULE]),
      teacherClasses: () => ok([TEACHER_CLASS]),
      university: () => ok([]),
      universityHistory: () => ok([]),
    },
    examApi: {
      byClass: () => ok([]),
      gradesByExam: () => ok([]),
    },
    studentApi: {
      search: () => ok([]),
    },
  }
})

function renderPortal(ui, roles) {
  // `primaryRole()` lit localStorage : c'est la source utilisee par les portails.
  localStorage.setItem('user', JSON.stringify({ roles, permissions: [] }))
  const store = configureStore({
    reducer: {
      auth: () => ({ isAuthenticated: true, user: { firstName: 'Test', lastName: 'Utilisateur', roles } }),
    },
  })
  return render(
    <Provider store={store}>
      <I18nProvider>
        <MemoryRouter initialEntries={['/my-children']}>{ui}</MemoryRouter>
      </I18nProvider>
    </Provider>,
  )
}

beforeEach(() => localStorage.clear())

describe('Espaces personnels — rendu', () => {
  it('rend /my-children (espace parent)', async () => {
    renderPortal(<ParentPortalPage />, ['PARENT'])
    expect(await screen.findByText('Mes enfants')).toBeInTheDocument()
    // L'enfant remonte par /api/parents/children apparait DEUX fois : une fois dans
    // la liste laterale, une fois dans l'en-tete de l'enfant selectionne (le premier
    // enfant est selectionne par defaut). Les deux doivent etre rendus.
    expect(await screen.findAllByText('Idrissa Siby')).toHaveLength(2)
    expect(screen.getByText('Bulletins')).toBeInTheDocument()
  })

  it('rend /my-school (espace élève)', async () => {
    renderPortal(<StudentPortalPage />, ['ELEVE'])
    expect(await screen.findByText('Mon espace')).toBeInTheDocument()
  })

  it('rend /my-teaching (espace enseignant)', async () => {
    renderPortal(<TeacherPortalPage />, ['ENSEIGNANT'])
    expect(await screen.findByText('Mon espace')).toBeInTheDocument()
  })

  it('rend /my-university (espace université)', async () => {
    renderPortal(<UniversityPortalPage />, ['ETUDIANT'])
    expect(await screen.findByText('Mon espace universitaire')).toBeInTheDocument()
  })

  it('redirige un rôle non autorisé hors de /my-children', async () => {
    // Un ELEVE n'a rien a faire dans l'espace parent : la page renvoie /dashboard.
    renderPortal(<ParentPortalPage />, ['ELEVE'])
    expect(screen.queryByText('Mes enfants')).not.toBeInTheDocument()
  })
})
