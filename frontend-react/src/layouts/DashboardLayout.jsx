import { useState, useSyncExternalStore } from 'react'
import { Outlet } from 'react-router-dom'
import { Box, LinearProgress } from '@mui/material'
import Sidebar from '../components/Sidebar'
import Navbar from '../components/Navbar'
import loadingService from '../services/loadingService'

/**
 * Barre de progression non bloquante : signale les requêtes API en cours sans
 * intercepter les clics. L'ancien Loader plein écran apparaissait à chaque
 * requête et bloquait toute l'interface (formulaires, navigation, scroll).
 */
function TopProgressBar() {
  const pending = useSyncExternalStore(
    loadingService.subscribe,
    () => loadingService.isPending(),
    () => false,
  )
  if (!pending) return null
  return (
    <Box
      sx={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        height: 3,
        zIndex: (t) => t.zIndex.modal + 1,
        pointerEvents: 'none',
      }}
    >
      <LinearProgress sx={{ height: 3 }} />
    </Box>
  )
}

/**
 * Layout principal de l'application connectée (sidebar rétractable + navbar + contenu).
 */
export default function DashboardLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('sidebar-collapsed') === '1'
    } catch {
      return false
    }
  })

  const toggleCollapsed = (value) => {
    setCollapsed(value)
    try {
      localStorage.setItem('sidebar-collapsed', value ? '1' : '0')
    } catch {
      // silencieux
    }
  }

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Sidebar
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        collapsed={collapsed}
        onCollapseToggle={toggleCollapsed}
      />
      <Box component="main" sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Navbar
          onMenuClick={() => setMobileOpen(true)}
          collapsed={collapsed}
          onCollapseToggle={toggleCollapsed}
        />
        <Box sx={{ p: { xs: 2, md: 3.5 }, flexGrow: 1, maxWidth: 1600, width: '100%', mx: 'auto' }}>
          <Box className="page-fade" key={window.location.pathname}>
            <Outlet />
          </Box>
        </Box>
      </Box>
      <TopProgressBar />
    </Box>
  )
}