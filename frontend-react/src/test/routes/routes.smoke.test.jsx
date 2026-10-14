import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup, waitFor } from '@testing-library/react'
import { configureStore } from '@reduxjs/toolkit'
import authReducer, { setCredentials } from '../../redux/slices/authSlice'
import themeReducer from '../../redux/slices/themeSlice'
import toastReducer from '../../redux/slices/toastSlice'
import App from '../../App'
import AppProviders from '../../AppProviders'

/*
 * Garde-fou de CÂBLAGE : on monte la VRAIE application (`App` → `AppRoutes`).
 *
 * Pourquoi ce test existe
 * -----------------------
 * `RoleDashboard.render.test.jsx` monte le tableau de bord isolement, avec un
 * `MemoryRouter` et sans `DashboardLayout`. `Portals.render.test.jsx` fait de
 * meme pour les portails. Aucun test ne montait donc l'arbre reel :
 * BrowserRouter + Guards + DashboardLayout + Suspense + imports `lazy`.
 *
 * C'est pourtant la couche qui a produit l'ecran blanc du 2026-09-19 (une route
 * pointant vers un module absent, et `useLocation` non importe) : une page qui
 * plante au rendu ne se voit qu'en montant le routeur reel, sous ses vrais
 * fournisseurs de contexte. Ce fichier ferme cette breche.
 *
 * Les charges utiles sont copiees de reponses REELLES de l'API (relevees le
 * 2026-09-19) : elles sont deja eprouvees par RoleDashboard.render.test.jsx.
 */

const STUDENT = {
  id: 10, matricule: 'ETU-2026-000008', firstName: 'Idrissa', lastName: 'Siby',
  enrollmentDate: '2026-08-17', status: 'ACTIVE', educationCycle: 'COLLEGE',
  classId: 1, className: '6ème A', photo: null, hasAccount: true,
}

const BULLETIN = {
  id: 5, studentId: 10, studentName: 'Idrissa Siby', matricule: 'ETU-2026-000008',
  className: '6ème A', term: 'T1', academicYear: '2026-2027', average: 14.14,
  classAverage: 14.4, classMin: 12.43, classMax: 17.29, rank: 3,
  mention: 'Bien', decision: 'ADMIS', pdfPath: null,
}

const ATTENDANCE = {
  id: 8, studentId: 10, studentName: 'Idrissa Siby', matricule: 'ETU-2026-000008',
  classId: 1, date: '2026-08-23', status: 'ABSENT', justification: null,
}

const INVOICE = {
  id: 1, invoiceNo: 'FAC-260823-00001', studentId: 10, studentName: 'Idrissa Siby',
  matricule: 'ETU-2026-000008', feeTypeId: 2, feeTypeName: 'Scolarité Trimestre 1',
  amount: 40000.0, discount: 5000.0, paidAmount: 35000.0, remainingAmount: 0.0,
  dueDate: '2026-08-23', status: 'PAID',
}

const MY_GRADE = {
  id: 1, examId: 3, examName: 'Contrôle 1', examType: 'CONTROL', subjectId: 2,
  subjectName: 'Mathématiques', term: 'T1', academicYear: '2026-2027',
  value: 15.5, maxValue: 20, coefficient: 1, appreciation: null, examDate: '2026-08-20',
}

const TEACHER = {
  id: 1, employeeNo: 'ENS-2026-0001', firstName: 'Jean', lastName: 'Kamdem',
  phone: '+237690000001', email: 'jk@school.com', hireDate: '2024-09-01',
  contractType: 'CDI', salary: 200000.0, status: 'ACTIVE',
}

const TEACHER_CLASS = {
  assignmentId: 7, classId: 1, className: '6ème A', subjectId: 2, subjectName: 'Mathématiques',
}

const SCHEDULE = {
  id: 2, dayOfWeek: 'MONDAY', startTime: '10:30:00', endTime: '13:00:00',
  classId: 3, className: '5ème A', subjectId: 6,
  subjectName: 'Histoire-Géographie', teacherId: 2, teacherName: 'Amadou Touré',
  roomId: 2, roomName: 'Salle A2',
}

const PAYROLL_ROW = {
  teacherId: 1, teacherName: 'Jean Kamdem', monthlyPaymentId: null, totalHours: 0,
  hourlyRate: null, totalAmount: 0, amountPaid: 0, remainingAmount: 0,
  status: 'PENDING', closed: false,
}

vi.mock('../../api/endpoints', () => {
  const ok = (data) => Promise.resolve({ data: { data } })

  // Liste benigne : se comporte comme un tableau ET comme une page paginee,
  // pour ne pas faire echouer un ecran qui lirait `.content` ou `.items`.
  const emptyList = () => {
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
  const benign = new Proxy(
    {},
    { get: () => () => Promise.resolve({ data: { data: emptyList() } }) },
  )

  /**
   * Complete une surcharge : toute methode NON listee retombe sur le repli benin.
   *
   * Sans ce repli, `mocked.examApi = { byClass }` SUPPRIME les autres methodes du
   * client : la page levait « examApi.bulletinsByStudent is not a function » et le
   * garde-fou de routes signalait un plantage qui n'existait pas en production.
   * Un client surcharge doit rester complet, jamais tronque.
   */
  const extend = (overrides) =>
    new Proxy(overrides, {
      get: (target, prop) => (prop in target ? target[prop] : benign[prop]),
    })

  // TOUS les clients exportes par api/endpoints doivent exister : `Sidebar`
  // appelle `settingsApi.cycles()` et `Navbar` appelle `communicationApi`, qui
  // sont montes par le vrai DashboardLayout. Un client manquant ferait lever
  // « No export is defined on the mock » au rendu.
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

  // Clients dont les tableaux de bord ont besoin, avec des charges utiles
  // copiees de reponses REELLES de l'API.
  mocked.dashboardApi = extend({
    stats: () => ok({}),
    auditLogs: () => ok({ content: [] }),
  })
  mocked.myApi = extend({
    profile: () => ok(STUDENT),
    schedule: () => ok([SCHEDULE]),
    grades: () => ok([MY_GRADE]),
    bulletins: () => ok([BULLETIN]),
    attendances: () => ok([ATTENDANCE]),
    invoices: () => ok([INVOICE]),
    teacherProfile: () => ok(TEACHER),
    teacherSchedule: () => ok([SCHEDULE]),
    teacherClasses: () => ok([TEACHER_CLASS]),
  })
  mocked.parentApi = extend({
    children: () => ok([STUDENT]),
    childBulletins: () => ok([BULLETIN]),
    childAttendances: () => ok([ATTENDANCE]),
    childGrades: () => ok([MY_GRADE]),
    childTimeline: () => ok([]),
    childInvoices: () => ok([INVOICE]),
  })
  mocked.teacherHoursApi = extend({
    myMonthly: () => ok([PAYROLL_ROW]),
    myTransactions: () => ok([]),
  })
  mocked.examApi = extend({ byClass: () => ok([]), gradesByExam: () => ok([]) })
  /*
   * `studentApi.search` doit renvoyer une liste PAGINEE (`.content`), pas un
   * tableau nu : StudentsPage, PaymentsPage et LibraryPage font tous
   * `setX(data.data.content)`. Avec `ok([])`, `.content` valait `undefined`,
   * l'etat devenait `undefined` et la page levait « Cannot read properties of
   * undefined (reading 'map') » — un faux plantage, verifie sain en navigateur.
   * `get` doit renvoyer un ELEVE complet : StudentDocumentsPage lit
   * `student.lastName.toUpperCase()`.
   */
  mocked.studentApi = extend({
    search: () => ok(emptyList()),
    get: () => ok(STUDENT),
  })

  return mocked
})

afterEach(cleanup)

beforeEach(() => {
  localStorage.clear()
  window.history.pushState({}, '', '/')
})

/** Monte l'application REELLE sur `path`, avec ou sans session. */
function mountApp(path, { roles = null } = {}) {
  window.history.pushState({}, '', path)

  const store = configureStore({
    reducer: { auth: authReducer, theme: themeReducer, toast: toastReducer },
  })

  if (roles) {
    // Passe par le vrai reducer : il alimente aussi localStorage, dont depend
    // `primaryRole()` (garde interne des portails).
    store.dispatch(
      setCredentials({
        accessToken: 'jeton-de-test',
        refreshToken: 'refresh-de-test',
        user: { firstName: 'Test', lastName: 'Utilisateur', roles, permissions: [] },
      }),
    )
  }

  return render(
    // Meme pile de fournisseurs qu'en production (Redux + theme), pour que le
    // test monte exactement l'arborescence reelle — voir AppProviders.jsx.
    <AppProviders store={store}>
      <App />
    </AppProviders>,
  )
}

/** La frontiere d'erreur remplace TOUT l'arbre : son titre signale un plantage. */
function expectNoRenderCrash() {
  expect(screen.queryByText('Une erreur est survenue')).toBeNull()
}

describe('Câblage réel — le tableau de bord sous DashboardLayout', () => {
  it('SUPER_ADMIN : rendu complet, sans plantage', async () => {
    mountApp('/dashboard', { roles: ['SUPER_ADMIN'] })
    expect(await screen.findByText('Cycle :')).toBeInTheDocument()
    expectNoRenderCrash()
  })

  it('PARENT : rendu complet, sans plantage', async () => {
    mountApp('/dashboard', { roles: ['PARENT'] })
    expect(await screen.findByText('Enfants suivis')).toBeInTheDocument()
    expectNoRenderCrash()
  })

  it('ELEVE : rendu complet, sans plantage', async () => {
    mountApp('/dashboard', { roles: ['ELEVE'] })
    expect(await screen.findByText('Moyenne générale')).toBeInTheDocument()
    expectNoRenderCrash()
  })

  it('ENSEIGNANT : rendu complet, sans plantage', async () => {
    mountApp('/dashboard', { roles: ['ENSEIGNANT'] })
    expect(await screen.findByText('Classes assignées')).toBeInTheDocument()
    expectNoRenderCrash()
  })
})

describe('Câblage réel — les gardes de route', () => {
  it('redirige un visiteur non authentifié vers /login', async () => {
    mountApp('/dashboard')
    await waitFor(() => expect(window.location.pathname).toBe('/login'))
    expectNoRenderCrash()
  })

  it('affiche la page 404 pour une route inconnue', async () => {
    mountApp('/route-qui-nexiste-pas', { roles: ['SUPER_ADMIN'] })
    expect(await screen.findByText('Page introuvable')).toBeInTheDocument()
    expectNoRenderCrash()
  })
})

/*
 * Toutes les routes d'administration, une par une.
 *
 * Pourquoi ce garde existe
 * ------------------------
 * Le bloc precedent ne monte que `/dashboard`. Les ~35 autres pages ne sont donc
 * jamais rendues par la CI — et une page qui plante AU RENDU passe inapercue.
 *
 * Cas reel (2026-10-13) : `/settings` etait inaccessible en production. Cause :
 * `SettingsPage` listait `loadYears` dans le tableau de dependances d'un
 * `useEffect` ecrit PLUS HAUT que la declaration `const loadYears = useCallback(...)`.
 * Ce tableau est evalue pendant le rendu : la variable etait encore dans sa zone
 * morte temporelle (TDZ) et la page levait
 * « Cannot access 'loadYears' before initialization ». L'ErrorBoundary affichait
 * « Une erreur est survenue ». Aucun test ne montait cette route, donc la CI
 * restait verte et le bug n'apparaissait qu'en navigateur.
 *
 * Les routes de portail (`/my-children`, `/my-school`, `/my-teaching`) sont
 * volontairement exclues : SUPER_ADMIN y est refuse par la garde interne du
 * portail (comportement attendu), elles sont couvertes par Portals.render.test.jsx.
 */
const ADMIN_ROUTES = [
  '/dashboard',
  '/profile',
  '/students',
  '/students/new',
  '/students/10',
  '/students/10/edit',
  '/students/10/documents',
  '/teachers',
  '/teachers/new',
  '/teachers/1/edit',
  '/classes',
  '/levels',
  '/sections',
  '/rooms',
  '/subjects',
  '/schedules',
  '/attendances',
  '/grades',
  '/exams',
  '/exam-types',
  '/convocations',
  '/stages',
  '/candidatures',
  '/memoires',
  '/alumni',
  '/payments',
  '/fee-types',
  '/expenses',
  '/teacher-payroll',
  '/library',
  '/hr',
  '/reports',
  '/messages',
  '/announcements',
  '/users',
  '/audit',
  '/settings',
]

describe('Câblage réel — chaque route d’administration se rend sans plantage', () => {
  it.each(ADMIN_ROUTES)('%s', async (path) => {
    mountApp(path, { roles: ['SUPER_ADMIN'] })

    /*
     * Le <Suspense> de routes/index.jsx est place AU-DESSUS du DashboardLayout :
     * tant que la page paresseuse n'est pas resolue, TOUT l'arbre (layout compris)
     * est remplace par le fallback. On attend donc que le layout soit monte — ou
     * que la frontiere d'erreur apparaisse, ce qui signifie un plantage.
     */
    await waitFor(
      () => {
        if (screen.queryByText('Une erreur est survenue')) return // plantage : on sort
        expect(document.querySelector('.page-fade')).not.toBeNull()
      },
      { timeout: 10000 },
    )

    // La frontiere d'erreur remplace TOUT l'arbre : sa presence = plantage au rendu.
    // On lit le message qu'elle affiche (un `Alert`) pour que l'echec nomme la cause
    // au lieu de dire seulement « ca a plante ».
    const alerte = screen.queryByText('Une erreur est survenue')
      ? screen.queryByRole('alert')?.textContent?.trim()
      : null
    expect(alerte, 'la route doit se rendre sans planter').toBeNull()
  })
})
