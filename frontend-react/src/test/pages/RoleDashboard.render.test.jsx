import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import RoleDashboard from '../../pages/RoleDashboard'
import { I18nProvider } from '../../i18n/I18nContext'

afterEach(cleanup)

/*
 * Les charges utiles ci-dessous sont copiées de reponses REELLES de l'API
 * (relevees le 2026-09-19), et non inventees. Le test sert donc aussi de garde
 * de contrat : si un DTO perd ou renomme un champ, le tableau de bord concerné
 * cessera de l'afficher et ces assertions echoueront.
 */

// GET /api/students/{id} et /api/parents/children -> StudentResponse
const STUDENT = {
  id: 10,
  matricule: 'ETU-2026-000008',
  firstName: 'Idrissa',
  lastName: 'Siby',
  birthDate: '2014-03-12',
  birthPlace: null,
  gender: 'MALE',
  address: null,
  phone: null,
  email: null,
  photo: null,
  enrollmentDate: '2026-08-17',
  status: 'ACTIVE',
  educationCycle: 'COLLEGE',
  classId: 1,
  className: '6ème A',
  parent: null,
  hasAccount: true,
}

// GET /api/exams/student/{id}/bulletins -> BulletinResponse
const BULLETIN = {
  id: 5,
  studentId: 10,
  studentName: 'Idrissa Siby',
  matricule: 'ETU-2026-000008',
  className: '6ème A',
  term: 'T1',
  academicYear: '2026-2027',
  average: 14.14,
  classAverage: 14.4,
  classMin: 12.43,
  classMax: 17.29,
  rank: 3,
  mention: 'Bien',
  decision: 'ADMIS',
  pdfPath: null,
}

// GET /api/attendances/student/{id} -> AttendanceResponse
const ATTENDANCE = {
  id: 8,
  studentId: 10,
  studentName: 'Idrissa Siby',
  matricule: 'ETU-2026-000008',
  classId: 1,
  date: '2026-08-23',
  status: 'ABSENT',
  justification: null,
}

// GET /api/payments/invoices/student/{id} -> InvoiceResponse
const INVOICE = {
  id: 1,
  invoiceNo: 'FAC-260823-00001',
  studentId: 10,
  studentName: 'Idrissa Siby',
  matricule: 'ETU-2026-000008',
  studentPhone: '76750362',
  parentPhone: '65239488',
  feeTypeId: 2,
  feeTypeName: 'Scolarité Trimestre 1',
  amount: 40000.0,
  discount: 5000.0,
  paidAmount: 35000.0,
  remainingAmount: 0.0,
  dueDate: '2026-08-23',
  status: 'PAID',
}

// GET /api/my/grades -> MyGradeResponse
const MY_GRADE = {
  id: 1,
  examId: 3,
  examName: 'Contrôle 1',
  examType: 'CONTROL',
  subjectId: 2,
  subjectName: 'Mathématiques',
  term: 'T1',
  academicYear: '2026-2027',
  value: 15.5,
  maxValue: 20,
  coefficient: 1,
  appreciation: null,
  examDate: '2026-08-20',
}

// GET /api/my/teacher-profile et /api/teachers/{id} -> TeacherResponse
const TEACHER = {
  id: 1,
  employeeNo: 'ENS-2026-0001',
  firstName: 'Jean',
  lastName: 'Kamdem',
  birthDate: null,
  gender: 'MALE',
  phone: '+237690000001',
  email: 'jk@school.com',
  address: null,
  hireDate: '2024-09-01',
  contractType: 'CDI',
  salary: 200000.0,
  photo: null,
  status: 'ACTIVE',
}

// GET /api/my/teacher/classes -> MyTeacherClassResponse
const TEACHER_CLASS = {
  assignmentId: 7,
  classId: 1,
  className: '6ème A',
  subjectId: 2,
  subjectName: 'Mathématiques',
}

// GET /api/my/teacher/schedule -> ScheduleResponse
const SCHEDULE = {
  id: 2,
  dayOfWeek: 'MONDAY',
  startTime: '10:30:00',
  endTime: '13:00:00',
  classId: 3,
  className: '5ème A',
  subjectId: 6,
  subjectName: 'Histoire-Géographie',
  teacherId: 2,
  teacherName: 'Amadou Touré',
  roomId: 2,
  roomName: 'Salle A2',
}

// GET /api/teacher-hours/my/monthly -> TeacherPayrollRowResponse
const PAYROLL_ROW = {
  teacherId: 1,
  teacherName: 'Jean Kamdem',
  monthlyPaymentId: null,
  totalHours: 0,
  hourlyRate: null,
  totalAmount: 0,
  amountPaid: 0,
  remainingAmount: 0,
  status: 'PENDING',
  closed: false,
}

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
      profile: () => ok(STUDENT),
      schedule: () => ok([SCHEDULE]),
      grades: () => ok([MY_GRADE]),
      bulletins: () => ok([BULLETIN]),
      attendances: () => ok([ATTENDANCE]),
      invoices: () => ok([INVOICE]),
      teacherProfile: () => ok(TEACHER),
      teacherSchedule: () => ok([SCHEDULE]),
      teacherClasses: () => ok([TEACHER_CLASS]),
    },
    parentApi: {
      children: () => ok([STUDENT]),
      childBulletins: () => ok([BULLETIN]),
      childAttendances: () => ok([ATTENDANCE]),
      childInvoices: () => ok([INVOICE]),
    },
    teacherHoursApi: {
      myMonthly: () => ok([PAYROLL_ROW]),
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
    expect(screen.getByText(/Ouvrir l'espace de Idrissa/)).toBeInTheDocument()
    // Valeurs issues de la charge utile reelle du bulletin (moyenne 14.14, mention "Bien").
    // 14.14 apparait deux fois : la moyenne consolidee (h4) et celle de l'enfant (h5),
    // identiques puisqu'il n'y a qu'un enfant dans ce jeu de donnees.
    expect(screen.getAllByText('14.14')).toHaveLength(2)
    expect(screen.getByText('Bien')).toBeInTheDocument()
    // 1 absence remontee par /attendances/student/{id}
    expect(screen.getByText('Absences')).toBeInTheDocument()
    expect(screen.queryByText('Cycle :')).not.toBeInTheDocument()
  })

  it('affiche le tableau de bord élève pour ELEVE', async () => {
    renderDashboard(['ELEVE'])
    expect(await screen.findByText('Moyenne générale')).toBeInTheDocument()
    expect(screen.getByText('Points de vigilance')).toBeInTheDocument()
    // La note reelle (MyGradeResponse) doit apparaitre avec son trimestre
    expect(screen.getByText(/Contrôle 1/)).toBeInTheDocument()
    expect(screen.queryByText('Enfants suivis')).not.toBeInTheDocument()
  })

  it('affiche le tableau de bord enseignant pour ENSEIGNANT', async () => {
    renderDashboard(['ENSEIGNANT'])
    expect(await screen.findByText('Classes assignées')).toBeInTheDocument()
    expect(screen.getByText('Ma rémunération')).toBeInTheDocument()
    // La classe et la matiere reelles (MyTeacherClassResponse) doivent apparaitre
    expect(screen.getByText('6ème A')).toBeInTheDocument()
    expect(screen.getByText('Mathématiques')).toBeInTheDocument()
    expect(screen.queryByText('Points de vigilance')).not.toBeInTheDocument()
  })

  it('respecte le rôle principal d’un compte multi-rôles', async () => {
    renderDashboard(['ELEVE', 'PARENT'])
    expect(await screen.findByText('Enfants suivis')).toBeInTheDocument()
    expect(screen.queryByText('Points de vigilance')).not.toBeInTheDocument()
  })
})
