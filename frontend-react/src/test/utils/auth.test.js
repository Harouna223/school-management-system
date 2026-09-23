import { describe, it, expect } from 'vitest'
import { primaryRole, hasPermission } from '../../utils/auth'

const mockUser = (roles, permissions) => ({
  roles: roles || [],
  permissions: permissions || [],
})

describe('primaryRole', () => {
  it('retourne le rôle le plus élevé', () => {
    const user = mockUser(['ELEVE', 'ETUDIANT'])
    localStorage.setItem('user', JSON.stringify(user))
    expect(primaryRole()).toBe('ETUDIANT')
  })

  it('retourne SUPER_ADMIN pour un admin', () => {
    const user = mockUser(['SUPER_ADMIN', 'DIRECTEUR'])
    localStorage.setItem('user', JSON.stringify(user))
    expect(primaryRole()).toBe('SUPER_ADMIN')
  })
})

describe('hasPermission', () => {
  it('retourne true si la permission existe', () => {
    const user = mockUser(['ELEVE'], ['STUDENT_READ'])
    localStorage.setItem('user', JSON.stringify(user))
    expect(hasPermission('STUDENT_READ')).toBe(true)
  })

  it('retourne false si la permission manque', () => {
    const user = mockUser(['ELEVE'], ['STUDENT_READ'])
    localStorage.setItem('user', JSON.stringify(user))
    expect(hasPermission('PAYMENT_WRITE')).toBe(false)
  })
})