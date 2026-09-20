import { useEffect } from 'react'
import { useDispatch } from 'react-redux'
import AppRoutes from './routes/index.jsx'
import ErrorBoundary from './components/ErrorBoundary'
import { clearCredentials } from './redux/slices/authSlice'
import { I18nProvider } from './i18n/I18nContext'

export default function App() {
  const dispatch = useDispatch()

  useEffect(() => {
    const handleForcedLogout = () => dispatch(clearCredentials())
    window.addEventListener('auth:logout', handleForcedLogout)
    return () => window.removeEventListener('auth:logout', handleForcedLogout)
  }, [dispatch])

  return (
    <I18nProvider>
      {/* Frontière d'erreur : évite l'écran blanc muet si une page plante au rendu. */}
      <ErrorBoundary>
        <AppRoutes />
      </ErrorBoundary>
    </I18nProvider>
  )
}