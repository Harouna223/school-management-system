import { Box, Typography, Button, Chip, Divider } from '@mui/material'
import { Add } from '@mui/icons-material'

/**
 * En-tête de page premium : titre, sous-titre, badge optionnel,
 * action principale et actions secondaires.
 */
export default function PageHeader({ title, subtitle, actionLabel, onAction, actionIcon, badge, actions = [] }) {
  return (
    <Box
      display="flex"
      alignItems="flex-end"
      justifyContent="space-between"
      flexWrap="wrap"
      gap={2}
      mb={3.5}
      className="animate-fade-in"
    >
      <Box>
        <Box display="flex" alignItems="center" gap={1.5}>
          <Typography variant="h4" className="page-title">
            {title}
          </Typography>
          {badge && <Chip label={badge} size="small" color="primary" variant="outlined" sx={{ fontWeight: 600 }} />}
        </Box>
        {subtitle && (
          <Typography variant="body2" className="page-subtitle" sx={{ mt: 0.8 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {(actionLabel || actions.length > 0) && (
        <Box display="flex" alignItems="center" gap={1.2} flexWrap="wrap">
          {actions.map((a, i) => (
            <Button
              key={i}
              variant={a.variant || 'outlined'}
              color={a.color || 'inherit'}
              startIcon={a.icon}
              onClick={a.onClick}
              disabled={a.disabled}
              size={a.size || 'medium'}
            >
              {a.label}
            </Button>
          ))}
          {actionLabel && (
            <Button
              variant="contained"
              startIcon={actionIcon || <Add />}
              onClick={onAction}
            >
              {actionLabel}
            </Button>
          )}
        </Box>
      )}
    </Box>
  )
}