import { Box, Typography, Skeleton } from '@mui/material'

/**
 * Tuile métrique compacte (icône teintée + libellé + valeur).
 * Utilisée par les tableaux de bord pour les indicateurs secondaires.
 *
 * @param icon     Composant d'icône MUI
 * @param color    Palette MUI : primary | secondary | success | warning | error | info
 * @param label    Libellé en majuscules
 * @param value    Valeur affichée
 * @param loading  Affiche un squelette à la place de la valeur
 * @param onClick  Rend la tuile cliquable (curseur + effet hover renforcé)
 */
export default function MiniMetric({ icon, color = 'primary', label, value, loading, onClick }) {
  return (
    <Box
      onClick={onClick}
      sx={(theme) => ({
        p: 2,
        borderRadius: '14px',
        border: '1px solid',
        borderColor: 'divider',
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        bgcolor: theme.palette.mode === 'dark' ? 'rgba(148,163,184,0.05)' : '#f8fafc',
        transition: 'background 160ms ease, transform 160ms ease',
        cursor: onClick ? 'pointer' : 'default',
        '&:hover': { bgcolor: 'action.hover', transform: 'translateY(-2px)' },
      })}
    >
      <Box
        sx={{
          width: 40,
          height: 40,
          borderRadius: '14px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: `${color}.light`,
          color: `${color}.dark`,
          flexShrink: 0,
        }}
      >
        {icon}
      </Box>
      <Box minWidth={0}>
        <Typography
          variant="caption"
          color="text.secondary"
          display="block"
          sx={{ fontSize: 11.5, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}
        >
          {label}
        </Typography>
        {loading ? (
          <Skeleton width={80} height={24} sx={{ mt: 0.3 }} />
        ) : (
          <Typography variant="h6" fontWeight={800} noWrap>
            {value}
          </Typography>
        )}
      </Box>
    </Box>
  )
}
