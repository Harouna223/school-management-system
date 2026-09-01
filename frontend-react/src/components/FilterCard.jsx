import { Box, Typography } from '@mui/material'
import { Tune } from '@mui/icons-material'

/**
 * Carte de filtres standardisée : conteneur premium pour les barres
 * de recherche/filtres des pages de listes.
 */
export default function FilterCard({ title = 'Filtres', children, actions }) {
  return (
    <Box className="filter-card animate-fade-in-up">
      <Box display="flex" alignItems="center" gap={1} mb={1.5}>
        <Box
          sx={{
            width: 28,
            height: 28,
            borderRadius: '9px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'primary.light',
            color: 'primary.main',
          }}
        >
          <Tune sx={{ fontSize: 16 }} />
        </Box>
        <Typography variant="subtitle2" fontWeight={700} sx={{ fontSize: 13 }}>
          {title}
        </Typography>
        <Box flexGrow={1} />
        {actions}
      </Box>
      {children}
    </Box>
  )
}