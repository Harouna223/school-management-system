import { lazy } from 'react'
import { Navigate } from 'react-router-dom'
import { useSelector } from 'react-redux'
import { ROLE_PRIORITY } from '../utils/auth'
import DashboardPage from './DashboardPage'

// Tableaux de bord personnalisés, chargés à la demande (un seul est monté par session).
const StudentDashboard = lazy(() => import('./students/StudentDashboard'))
const ParentDashboard = lazy(() => import('./parents/ParentDashboard'))
const TeacherDashboard = lazy(() => import('./teachers/TeacherDashboard'))

/** Rôles ayant accès au tableau de bord administratif (direction + staff). */
export const ADMIN_ROLES = ['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE', 'SECRETAIRE']

/**
 * Détermine le tableau de bord à afficher pour un ensemble de rôles.
 * S'appuie sur `ROLE_PRIORITY` (même ordre que le menu latéral) : un compte
 * multi-rôles voit ainsi le tableau de bord correspondant à son rôle principal.
 *
 * @param {string[]} roles Rôles de l'utilisateur connecté
 * @returns {'ADMIN'|'ENSEIGNANT'|'PARENT'|'ETUDIANT'|'ELEVE'|'NONE'}
 */
export function resolveDashboardRole(roles = []) {
  const primary = ROLE_PRIORITY.find((role) => roles.includes(role))
  if (!primary) return 'NONE'
  return ADMIN_ROLES.includes(primary) ? 'ADMIN' : primary
}

/**
 * Point d'entrée `/dashboard` : affiche le tableau de bord correspondant au rôle.
 *  - administration (direction + staff) → tableau de bord global de l'établissement
 *  - parent   → synthèse multi-enfants
 *  - élève    → synthèse de la scolarité
 *  - enseignant → classes, charge horaire et rémunération
 *  - étudiant → redirigé vers son espace universitaire (pas de tableau de bord dédié)
 */
export default function RoleDashboard() {
  const user = useSelector((state) => state.auth.user)
  const roles = user?.roles ?? []

  switch (resolveDashboardRole(roles)) {
    case 'ADMIN':
      return <DashboardPage />
    case 'PARENT':
      return <ParentDashboard />
    case 'ENSEIGNANT':
      return <TeacherDashboard />
    case 'ELEVE':
      return <StudentDashboard />
    case 'ETUDIANT':
      return <Navigate to="/my-university" replace />
    default:
      return <Navigate to="/profile" replace />
  }
}
