import { describe, it, expect, beforeEach } from 'vitest'
import themeReducer, { toggleTheme } from '../../redux/slices/themeSlice'

describe('themeSlice', () => {
  beforeEach(() => localStorage.clear())

  it('initialise avec le mode par défaut light', () => {
    const state = themeReducer(undefined, { type: '@@INIT' })
    expect(state.mode).toBe('light')
  })

  it('bascule de light à dark', () => {
    const state = themeReducer({ mode: 'light' }, toggleTheme())
    expect(state.mode).toBe('dark')
    expect(localStorage.getItem('themeMode')).toBe('dark')
  })

  it('bascule de dark à light', () => {
    localStorage.setItem('themeMode', 'dark')
    const state = themeReducer({ mode: 'dark' }, toggleTheme())
    expect(state.mode).toBe('light')
    expect(localStorage.getItem('themeMode')).toBe('light')
  })
})
