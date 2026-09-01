import { Chip } from '@mui/material'
import { humanize } from '../utils/format'

const palette = {
  ACTIVE: 'success',
  PAID: 'success',
  PRESENT: 'success',
  APPROVED: 'success',
  RETURNED: 'success',
  COMPLETED: 'success',
  ACTIVE_COLOR: 'success',
  LOGIN: 'info',
  LOGOUT: 'default',
  CREATE: 'success',
  UPDATE: 'info',
  DELETE: 'error',
  CONTRACT_UPDATE: 'info',
  CONTRACT_DELETE: 'error',
  ANNOUNCEMENT_DELETE: 'error',
  INACTIVE: 'default',
  PENDING: 'warning',
  PARTIAL: 'warning',
  LATE: 'warning',
  PLANNED: 'info',
  ONGOING: 'info',
  BORROWED: 'info',
  SUSPENDED: 'error',
  ABSENT: 'error',
  REJECTED: 'error',
  OVERDUE: 'error',
  UNPAID: 'error',
  DELIBERATED: 'secondary',
  GRADUATED: 'secondary',
  TRANSFERRED: 'info',
  RADIATED: 'error',
  JUSTIFIED: 'info',
  ON_LEAVE: 'warning',
}

const tones = {
  success: { bg: '#dcfce7', color: '#15803d' },
  warning: { bg: '#fef3c7', color: '#b45309' },
  info: { bg: '#dbeafe', color: '#1d4ed8' },
  error: { bg: '#fee2e2', color: '#b91c1c' },
  secondary: { bg: '#ede9fe', color: '#6d28d9' },
  default: { bg: '#f1f5f9', color: '#475569' },
}
const darkTones = {
  success: { bg: 'rgba(34,197,94,0.14)', color: '#4ade80' },
  warning: { bg: 'rgba(245,158,11,0.14)', color: '#fbbf24' },
  info: { bg: 'rgba(59,130,246,0.16)', color: '#60a5fa' },
  error: { bg: 'rgba(239,68,68,0.14)', color: '#f87171' },
  secondary: { bg: 'rgba(167,139,250,0.16)', color: '#a78bfa' },
  default: { bg: 'rgba(148,163,184,0.14)', color: '#94a3b8' },
}

/**
 * Badge « soft » coloré pour les statuts d'entités.
 */
export default function StatusChip({ status }) {
  if (!status) return null
  const color = palette[status] || 'default'
  const tone = (t) => (t.palette.mode === 'dark' ? darkTones : tones)[color] || tones.default
  return (
    <Chip
      size="small"
      label={humanize(status)}
      sx={(t) => ({
        bgcolor: tone(t).bg,
        color: tone(t).color,
        fontWeight: 700,
        fontSize: 11.5,
        px: 0.6,
        '& .MuiChip-label': { px: 1.1 },
      })}
    />
  )
}