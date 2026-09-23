import { Provider } from 'react-redux'
import { ThemeProvider, CssBaseline } from '@mui/material'
import { store as defaultStore } from './redux/store'
import { ThemeContextProvider, useThemeContext } from './context/ThemeContext'
import { lightTheme, darkTheme } from './styles/theme'

/**
 * Applique le thème MUI à partir du contexte thème.
 * Doit être rendu SOUS `ThemeContextProvider`.
 */
function ThemedApp({ children }) {
  const { mode } = useThemeContext()
  return (
    <ThemeProvider theme={mode === 'dark' ? darkTheme : lightTheme}>
      <CssBaseline />
      {children}
    </ThemeProvider>
  )
}

/**
 * Empile TOUS les fournisseurs de contexte de l'application :
 * Redux → thème (clair/sombre) → thème MUI.
 *
 * Pourquoi ce composant existe
 * ----------------------------
 * `App` ne contient PAS ces fournisseurs : `Navbar` appelle `useThemeContext()`,
 * qui lève une exception s'il est rendu hors de `ThemeContextProvider`. Un test
 * qui monte `<App />` seul échoue donc pour une raison qui n'existe pas en
 * production — et, à l'inverse, oublier un fournisseur ici produirait un écran
 * blanc que les tests ne verraient pas.
 *
 * En partageant cette pile entre `main.jsx` et les tests de câblage, le test
 * monte exactement l'arborescence de la production.
 *
 * @param {object} props
 * @param {import('react').ReactNode} props.children Arbre applicatif (généralement `<App />`)
 * @param {object} [props.store] Store Redux à utiliser (par défaut celui de l'application)
 */
export default function AppProviders({ children, store = defaultStore }) {
  return (
    <Provider store={store}>
      <ThemeContextProvider>
        <ThemedApp>{children}</ThemedApp>
      </ThemeContextProvider>
    </Provider>
  )
}
