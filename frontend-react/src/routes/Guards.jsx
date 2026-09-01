import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSelector } from 'react-redux'

/**
 * Garde de route : redirige vers /login si non authentifié.
 */
export function ProtectedRoute() {
  const isAuthenticated = useSelector((state) => state.auth.isAuthenticated)
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <Outlet />
}

/**
 * Garde de route : redirige vers /dashboard si déjà connecté.
 */
export function PublicOnlyRoute() {
  const isAuthenticated = useSelector((state) => state.auth.isAuthenticated)

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }
  return <Outlet />
}

/**
 * Garde de rôle : restreint l'accès à certains rôles.
 */
export function RoleRoute({ roles, children }) {
  const user = useSelector((state) => state.auth.user)
  const location = useLocation()

  const hasAccess =
    user?.roles?.includes('SUPER_ADMIN') ||
    (roles ?? []).some((r) => user?.roles?.includes(r))

  if (!hasAccess) {
    return <Navigate to="/dashboard" state={{ from: location }} replace />
  }
  return children ?? <Outlet />
}