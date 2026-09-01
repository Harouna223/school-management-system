import { useSyncExternalStore } from 'react'
import { Backdrop, CircularProgress, Typography, Box } from '@mui/material'
import loadingService from '../services/loadingService'

/**
 * Loader plein écran (global) : s'affiche automatiquement tant que des requêtes
 * API sont en cours (service de chargement global) ou via la prop `open`.
 */
export default function Loader({ open, label = 'Chargement…' }) {
  const pending = useSyncExternalStore(
    loadingService.subscribe,
    () => loadingService.isPending(),
    () => false,
  )
  const visible = open !== undefined ? open : pending
  return (
    <Backdrop
      open={visible}
      sx={{
        color: 'primary.main',
        zIndex: (theme) => theme.zIndex.drawer + 9999,
        background: 'rgba(15, 23, 42, 0.35)',
        backdropFilter: 'blur(2px)',
      }}
    >
      <Box display="flex" flexDirection="column" alignItems="center" gap={2}>
        <CircularProgress color="inherit" />
        {label && (
          <Typography variant="body2" sx={{ color: 'background.paper', fontWeight: 600 }}>
            {label}
          </Typography>
        )}
      </Box>
    </Backdrop>
  )
}