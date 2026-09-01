import { createContext, useContext, useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { toggleTheme as toggleThemeAction } from '../redux/slices/themeSlice'

/**
 * Contexte thème : expose le mode clair/sombre et sa bascule.
 */
const ThemeContext = createContext(null)

export function ThemeContextProvider({ children }) {
  const dispatch = useDispatch()
  const mode = useSelector((state) => state.theme.mode)

  useEffect(() => {
    if (mode === 'dark') {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }, [mode])

  const toggleMode = () => dispatch(toggleThemeAction())

  return (
    <ThemeContext.Provider value={{ mode, toggleMode }}>
      {children}
    </ThemeContext.Provider>
  )
}

export const useThemeContext = () => {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useThemeContext doit être utilisé dans ThemeContextProvider')
  return ctx
}