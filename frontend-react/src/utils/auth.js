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
 * Rôle principal (le plus élevé) d'un utilisateur.
 * ETUDIANT est prioritaire sur ELEVE pour les comptes universitaires.
 */
export function primaryRole() {
  const user = getCurrentUser()
  const priority = [
    'SUPER_ADMIN',
    'DIRECTEUR',
    'COMPTABLE',
    'SECRETAIRE',
    'ENSEIGNANT',
    'PARENT',
    'ETUDIANT',
    'ELEVE',
  ]
  if (!user?.roles) return 'ELEVE'
  return priority.find((r) => user.roles.includes(r)) ?? 'ELEVE'
}