import { Box, Typography, Button } from '@mui/material'

/**
 * Bannière d'accueil des tableaux de bord personnels (élève, parent, enseignant).
 * Reprend l'identité visuelle de la bannière du tableau de bord administrateur.
 *
 * @param title      Titre principal (ex. « Bonjour, Awa »)
 * @param subtitle   Ligne de contexte (ex. « mercredi 17 septembre 2026 — votre journée »)
 * @param pills      Puces d'information : [{ icon, label }]
 * @param actions    Boutons d'action rapide : [{ label, icon, onClick }]
 */
export default function PortalHero({ title, subtitle, pills = [], actions = [] }) {
  return (
    <Box className="hero-banner animate-fade-in" mb={3.5} p={{ xs: 2.6, md: 3.6 }}>
      <Box position="relative" zIndex={1}>
        <Box display="flex" alignItems="center" flexWrap="wrap" justifyContent="space-between" gap={2}>
          <Box minWidth={0}>
            <Typography
              variant="h4"
              sx={{ fontWeight: 800, letterSpacing: '-0.02em', color: '#fff', fontSize: { xs: 21, md: 26 } }}
            >
              {title}
            </Typography>
            {subtitle && (
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.78)', mt: 0.6 }}>
                {subtitle}
              </Typography>
            )}
          </Box>
          {actions.length > 0 && (
            <Box display="flex" gap={1.2} flexWrap="wrap">
              {actions.map((action) => (
                <Button
                  key={action.label}
                  variant="contained"
                  size="small"
                  startIcon={action.icon}
                  onClick={action.onClick}
                  sx={{
                    bgcolor: 'rgba(255,255,255,0.16)',
                    backdropFilter: 'blur(8px)',
                    color: '#fff',
                    border: '1px solid rgba(255,255,255,0.25)',
                    borderRadius: '10px',
                    boxShadow: 'none',
                    '&:hover': { bgcolor: 'rgba(255,255,255,0.26)', boxShadow: 'none' },
                  }}
                >
                  {action.label}
                </Button>
              ))}
            </Box>
          )}
        </Box>
        {pills.length > 0 && (
          <Box mt={2.4} display="flex" gap={1.4} flexWrap="wrap">
            {pills.map((pill) => (
              <Box
                key={pill.label}
                display="flex"
                alignItems="center"
                gap={0.8}
                px={1.3}
                py={0.6}
                borderRadius="999px"
                sx={{
                  bgcolor: 'rgba(255,255,255,0.14)',
                  backdropFilter: 'blur(8px)',
                  border: '1px solid rgba(255,255,255,0.2)',
                  color: '#fff',
                }}
              >
                {pill.icon && (
                  <Box sx={{ display: 'flex', alignItems: 'center', color: 'rgba(255,255,255,0.85)' }}>
                    {pill.icon}
                  </Box>
                )}
                <Typography variant="caption" sx={{ fontWeight: 600, fontSize: 12 }}>
                  {pill.label}
                </Typography>
              </Box>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  )
}
