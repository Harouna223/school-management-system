import { describe, it, expect } from 'vitest'
import { resolveDashboardRole } from '../../pages/RoleDashboard'

describe('resolveDashboardRole', () => {
  it.each(['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE', 'SECRETAIRE'])(
    'associe %s au tableau de bord administratif',
    (role) => {
      expect(resolveDashboardRole([role])).toBe('ADMIN')
    },
  )

  it.each([
    ['PARENT', 'PARENT'],
    ['ENSEIGNANT', 'ENSEIGNANT'],
    ['ELEVE', 'ELEVE'],
    ['ETUDIANT', 'ETUDIANT'],
  ])('associe %s à son tableau de bord personnel', (role, expected) => {
    expect(resolveDashboardRole([role])).toBe(expected)
  })

  it('privilégie le rôle administratif pour un compte multi-rôles', () => {
    expect(resolveDashboardRole(['ENSEIGNANT', 'SUPER_ADMIN'])).toBe('ADMIN')
    expect(resolveDashboardRole(['PARENT', 'DIRECTEUR'])).toBe('ADMIN')
  })

  it('respecte l’ordre de priorité des rôles du menu latéral', () => {
    // ROLE_PRIORITY : ... ENSEIGNANT > PARENT > ETUDIANT > ELEVE
    expect(resolveDashboardRole(['ELEVE', 'PARENT'])).toBe('PARENT')
    expect(resolveDashboardRole(['PARENT', 'ENSEIGNANT'])).toBe('ENSEIGNANT')
    expect(resolveDashboardRole(['ELEVE', 'ETUDIANT'])).toBe('ETUDIANT')
  })

  it('ne renvoie aucun tableau de bord pour un compte sans rôle connu', () => {
    expect(resolveDashboardRole([])).toBe('NONE')
    expect(resolveDashboardRole(['ROLE_INCONNU'])).toBe('NONE')
  })
})
