import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import DashboardLayout from '../layouts/DashboardLayout'
import AuthLayout from '../layouts/AuthLayout'
import { ProtectedRoute, PublicOnlyRoute, RoleRoute } from './Guards'
import ToastHost from '../components/ToastHost'
import Loader from '../components/Loader'

// Pages légères / critiques chargées immédiatement
import LoginPage from '../pages/auth/LoginPage'
import DashboardPage from '../pages/DashboardPage'
import NotFoundPage from '../pages/NotFoundPage'

// Pages lourdes chargées à la demande (lazy loading)
const StudentsPage = lazy(() => import('../pages/students/StudentsPage'))
const StudentFormPage = lazy(() => import('../pages/students/StudentFormPage'))
const StudentDocumentsPage = lazy(() => import('../pages/students/StudentDocumentsPage'))
const StudentDetailPage = lazy(() => import('../pages/students/StudentDetailPage'))
const TeachersPage = lazy(() => import('../pages/teachers/TeachersPage'))
const TeacherFormPage = lazy(() => import('../pages/teachers/TeacherFormPage'))
const ClassesPage = lazy(() => import('../pages/classes/ClassesPage'))
const LevelsPage = lazy(() => import('../pages/classes/LevelsPage'))
const SectionsPage = lazy(() => import('../pages/classes/SectionsPage'))
const RoomsPage = lazy(() => import('../pages/classes/RoomsPage'))
const SubjectsPage = lazy(() => import('../pages/subjects/SubjectsPage'))
const SchedulesPage = lazy(() => import('../pages/schedules/SchedulesPage'))
const AttendancesPage = lazy(() => import('../pages/attendances/AttendancesPage'))
const GradesPage = lazy(() => import('../pages/grades/GradesPage'))
const ExamsPage = lazy(() => import('../pages/exams/ExamsPage'))
const ExamTypesPage = lazy(() => import('../pages/exams/ExamTypesPage'))
const ConvocationsPage = lazy(() => import('../pages/convocations/ConvocationsPage'))
const UniversityExamsPage = lazy(() => import('../pages/university/UniversityExamsPage'))
const StagesPage = lazy(() => import('../pages/university/StagesPage'))
const CandidaturesPage = lazy(() => import('../pages/university/CandidaturesPage'))
const MemoiresPage = lazy(() => import('../pages/university/MemoiresPage'))
const AlumniPage = lazy(() => import('../pages/university/AlumniPage'))
const PaymentsPage = lazy(() => import('../pages/payments/PaymentsPage'))
const FeeTypesPage = lazy(() => import('../pages/payments/FeeTypesPage'))
const ExpensesPage = lazy(() => import('../pages/expenses/ExpensesPage'))
const LibraryPage = lazy(() => import('../pages/library/LibraryPage'))
const HrPage = lazy(() => import('../pages/hr/HrPage'))
const MessagesPage = lazy(() => import('../pages/messages/MessagesPage'))
const AnnouncementsPage = lazy(() => import('../pages/announcements/AnnouncementsPage'))
const UsersPage = lazy(() => import('../pages/users/UsersPage'))
const ParentPortalPage = lazy(() => import('../pages/parents/ParentPortalPage'))
const StudentPortalPage = lazy(() => import('../pages/students/StudentPortalPage'))
const TeacherPortalPage = lazy(() => import('../pages/teachers/TeacherPortalPage'))
const AuditPage = lazy(() => import('../pages/audit/AuditPage'))
const SettingsPage = lazy(() => import('../pages/settings/SettingsPage'))
const ReportsPage = lazy(() => import('../pages/reports/ReportsPage'))
const ProfilePage = lazy(() => import('../pages/ProfilePage'))
const LmdPage = lazy(() => import('../pages/lmd/LmdPage'))
const UniversityPortalPage = lazy(() => import('../pages/university/UniversityPortalPage'))

/**
 * Arbre de routage de l'application.
 */
export default function AppRoutes() {
  return (
    <BrowserRouter>
      <ToastHost />
      <Suspense fallback={<Loader />}>
        <Routes>
          {/* Pages publiques */}
          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<AuthLayout><LoginPage /></AuthLayout>} />
          </Route>

          {/* Zone authentifiée */}
          <Route element={<ProtectedRoute />}>
            <Route element={<DashboardLayout />}>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/profile" element={<ProfilePage />} />

              {/* Scolarité */}
              <Route path="/students" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'COMPTABLE', 'PARENT']}><StudentsPage /></RoleRoute>} />
              <Route path="/students/new" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE']}><StudentFormPage /></RoleRoute>} />
              <Route path="/students/:id/edit" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE']}><StudentFormPage /></RoleRoute>} />
              <Route path="/students/:id/documents" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'COMPTABLE', 'PARENT']}><StudentDocumentsPage /></RoleRoute>} />
              <Route path="/students/:id" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'COMPTABLE', 'PARENT']}><StudentDetailPage /></RoleRoute>} />
              <Route path="/teachers" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><TeachersPage /></RoleRoute>} />
              <Route path="/teachers/new" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><TeacherFormPage /></RoleRoute>} />
              <Route path="/teachers/:id/edit" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><TeacherFormPage /></RoleRoute>} />
              <Route path="/classes" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><ClassesPage /></RoleRoute>} />
              <Route path="/levels" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><LevelsPage /></RoleRoute>} />
              <Route path="/sections" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><SectionsPage /></RoleRoute>} />
              <Route path="/rooms" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><RoomsPage /></RoleRoute>} />
              <Route path="/subjects" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><SubjectsPage /></RoleRoute>} />

              {/* Pédagogie */}
              <Route path="/schedules" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT']}><SchedulesPage /></RoleRoute>} />
              <Route path="/attendances" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT']}><AttendancesPage /></RoleRoute>} />
              <Route path="/grades" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT']}><GradesPage /></RoleRoute>} />
              <Route path="/exams" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><ExamsPage /></RoleRoute>} />
              <Route path="/exam-types" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><ExamTypesPage /></RoleRoute>} />
              <Route path="/convocations" element={<RoleRoute roles={['DIRECTEUR', 'SECRETAIRE']}><ConvocationsPage /></RoleRoute>} />
              <Route path="/university-exams" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT']}><UniversityExamsPage /></RoleRoute>} />
              <Route path="/stages" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT']}><StagesPage /></RoleRoute>} />
              <Route path="/candidatures" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE']}><CandidaturesPage /></RoleRoute>} />
              <Route path="/memoires" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT']}><MemoiresPage /></RoleRoute>} />
              <Route path="/alumni" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE']}><AlumniPage /></RoleRoute>} />

              {/* Finance */}
              <Route path="/payments" element={<RoleRoute roles={['DIRECTEUR', 'COMPTABLE', 'SECRETAIRE']}><PaymentsPage /></RoleRoute>} />
              <Route path="/fee-types" element={<RoleRoute roles={['DIRECTEUR', 'COMPTABLE', 'SECRETAIRE']}><FeeTypesPage /></RoleRoute>} />
              <Route path="/expenses" element={<RoleRoute roles={['DIRECTEUR', 'COMPTABLE']}><ExpensesPage /></RoleRoute>} />

              {/* Services */}
              <Route path="/library" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE']}><LibraryPage /></RoleRoute>} />
              <Route path="/hr" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE']}><HrPage /></RoleRoute>} />
              <Route path="/lmd" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'ENSEIGNANT']}><LmdPage /></RoleRoute>} />
              <Route path="/reports" element={<RoleRoute roles={['SUPER_ADMIN', 'DIRECTEUR', 'SECRETAIRE', 'COMPTABLE', 'ENSEIGNANT']}><ReportsPage /></RoleRoute>} />
              <Route path="/messages" element={<MessagesPage />} />
              <Route path="/announcements" element={<AnnouncementsPage />} />
              <Route path="/my-children" element={<RoleRoute roles={['PARENT']}><ParentPortalPage /></RoleRoute>} />
              <Route path="/my-school" element={<RoleRoute roles={['ELEVE']}><StudentPortalPage /></RoleRoute>} />
              <Route path="/my-university" element={<RoleRoute roles={['ETUDIANT', 'ELEVE']}><UniversityPortalPage /></RoleRoute>} />
              <Route path="/my-teaching" element={<RoleRoute roles={['ENSEIGNANT']}><TeacherPortalPage /></RoleRoute>} />

              {/* Administration */}
              <Route path="/users" element={<RoleRoute roles={['DIRECTEUR']}><UsersPage /></RoleRoute>} />
              <Route path="/audit" element={<RoleRoute roles={['DIRECTEUR']}><AuditPage /></RoleRoute>} />
              <Route path="/settings" element={<RoleRoute roles={['SUPER_ADMIN']}><SettingsPage /></RoleRoute>} />
            </Route>
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}