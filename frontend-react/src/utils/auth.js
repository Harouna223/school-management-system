/**
 * Accès sécurisé à l'utilisateur courant côté client.
 */
export function getCurrentUser() {
  try {
    return JSON.parse(localStorage.getItem('user'))
  } catch {
    return null
  }
}

export function hasRole(role) {
  const user = getCurrentUser()
  return user?.roles?.some((r) => r === role) ?? false
}

export function hasPermission(permission) {
  const user = getCurrentUser()
  return user?.permissions?.some((p) => p === permission) ?? false
}

/**
 * Ordre de priorité des rôles (le plus élevé d'abord).
 * Source unique de vérité : utilisée par `primaryRole()` (menu latéral) et par
 * `resolveDashboardRole()` (tableau de bord) pour qu'ils ne divergent jamais.
 * ETUDIANT est prioritaire sur ELEVE pour les comptes universitaires.
 */
export const ROLE_PRIORITY = [
  'SUPER_ADMIN',
  'DIRECTEUR',
  'COMPTABLE',
  'SECRETAIRE',
  'ENSEIGNANT',
  'PARENT',
  'ETUDIANT',
  'ELEVE',
]

/**
 * Rôle principal (le plus élevé) d'un utilisateur.
 */
export function primaryRole() {
  const user = getCurrentUser()
  if (!user?.roles) return 'ELEVE'
  return ROLE_PRIORITY.find((r) => user.roles.includes(r)) ?? 'ELEVE'
}