import { Box, Typography } from '@mui/material'
import Inbox from '@mui/icons-material/Inbox'
import { useI18n } from '../i18n/I18nContext'

/**
 * État vide premium : icône dans un halo, message et sous-message.
 */
export default function EmptyState({ message, icon: Icon = Inbox, hint }) {
  const { t } = useI18n()
  const text = message === undefined ? t('common.empty') : message
  return (
    <Box
      display="flex"
      flexDirection="column"
      alignItems="center"
      justifyContent="center"
      py={7}
      gap={1.5}
    >
      <Box
        sx={{
          width: 72,
          height: 72,
          borderRadius: '24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(59,130,246,0.08)' : 'primary.light'),
          color: 'primary.main',
          '&::before': {
            content: '""',
            position: 'absolute',
            inset: -10,
            borderRadius: '50%',
            bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(59,130,246,0.05)' : 'rgba(37,99,235,0.05)'),
          },
        }}
      >
        <Icon sx={{ fontSize: 34 }} />
      </Box>
      <Typography color="text.secondary" variant="body2" fontWeight={600}>
        {text}
      </Typography>
      {hint && (
        <Typography color="text.disabled" variant="caption" sx={{ fontSize: 12 }}>
          {hint}
        </Typography>
      )}
    </Box>
  )
}