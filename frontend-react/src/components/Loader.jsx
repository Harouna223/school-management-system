import { useSyncExternalStore } from 'react'
import { Backdrop, CircularProgress, Typography, Box } from '@mui/material'
import loadingService from '../services/loadingService'
import { useI18n } from '../i18n/I18nContext'

/**
 * Voile de chargement d'une PAGE (pas du layout) : à monter uniquement quand la
 * page n'a encore rien à afficher. Le layout global utilise une fine barre de
 * progression non bloquante.
 */
export default function Loader({ open, label }) {
  const { t } = useI18n()
  const pending = useSyncExternalStore(
    loadingService.subscribe,
    () => loadingService.isPending(),
    () => false,
  )
  const visible = open !== undefined ? open : pending
  const text = label === undefined ? t('common.loading') : label
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
        {text && (
          <Typography variant="body2" sx={{ color: 'background.paper', fontWeight: 600 }}>
            {text}
          </Typography>
        )}
      </Box>
    </Backdrop>
  )
}
