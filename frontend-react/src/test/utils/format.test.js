import { describe, it, expect } from 'vitest'
import { formatCurrency, formatDate, formatGrade, initials, DAYS_FR, DAY_KEYS } from '../../utils/format'

describe('formatCurrency', () => {
  it('formate un montant avec FCFA par défaut', () => {
    // Intl.NumberFormat('fr-FR') utilise un espace insécable étroit (\u202F)
    expect(formatCurrency(1500)).toMatch(/1\s*500 FCFA/)
  })

  it('renvoie 0 FCFA pour null/undefined', () => {
    expect(formatCurrency(null)).toBe('0 FCFA')
    expect(formatCurrency(undefined)).toBe('0 FCFA')
  })

  it('utilise une devise personnalisée', () => {
    expect(formatCurrency(500, '€')).toMatch(/500\s*€/)
  })
})

describe('formatDate', () => {
  it('renvoie un tiret pour une valeur vide', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate('')).toBe('—')
  })

  it('formate une date ISO en fr-FR', () => {
    expect(formatDate('2026-09-01')).toMatch(/sept\./)
  })
})

describe('formatGrade', () => {
  it('formate une note sur 20', () => {
    expect(formatGrade(15)).toBe('15.00/20')
  })

  it('renvoie un tiret pour null', () => {
    expect(formatGrade(null)).toBe('—')
  })
})

describe('initials', () => {
  it('construit les initiales', () => {
    expect(initials('Koffi', 'Konan')).toBe('KK')
    expect(initials('Awa', 'Diallo')).toBe('AD')
  })
})

describe('DAYS_FR / DAY_KEYS', () => {
  it('contient les 7 jours', () => {
    expect(DAYS_FR).toHaveLength(7)
    expect(DAYS_FR[0]).toBe('Lundi')
  })

  it('mappe les jours de la semaine', () => {
    expect(DAY_KEYS.MONDAY).toBe(0)
    expect(DAY_KEYS.FRIDAY).toBe(4)
    expect(DAY_KEYS.SATURDAY).toBe(5)
  })
})
