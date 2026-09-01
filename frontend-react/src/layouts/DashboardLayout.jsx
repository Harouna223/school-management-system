import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Box } from '@mui/material'
import Sidebar from '../components/Sidebar'
import Navbar from '../components/Navbar'
import Loader from '../components/Loader'

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
      <Loader />
    </Box>
  )
}