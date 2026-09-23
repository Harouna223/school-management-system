import { Card, Typography, Box, Skeleton } from '@mui/material'
import { TrendingUp, TrendingDown } from '@mui/icons-material'

const colorMap = {
  primary: { grad: 'linear-gradient(135deg, #3b82f6, #2563eb)', accent: '#2563eb' },
  secondary: { grad: 'linear-gradient(135deg, #a78bfa, #7c3aed)', accent: '#7c3aed' },
  success: { grad: 'linear-gradient(135deg, #34d399, #16a34a)', accent: '#16a34a' },
  warning: { grad: 'linear-gradient(135deg, #fbbf24, #d97706)', accent: '#d97706' },
  danger: { grad: 'linear-gradient(135deg, #f87171, #dc2626)', accent: '#dc2626' },
  info: { grad: 'linear-gradient(135deg, #38bdf8, #0284c7)', accent: '#0284c7' },
}

/**
 * Carte statistique premium : barre d'accent colorée, icône teintée,
 * valeur, tendance et effet hover.
 */
export default function StatCard({ title, value, icon: Icon, color = 'primary', trend, loading, suffix, subtitle }) {
  const pickPalette = () => colorMap[color] || colorMap.primary
  return (
    <Card
      className="stat-card"
      sx={{
        height: '100%',
        p: 2.5,
        pt: 3,
        borderRadius: '16px',
        overflow: 'hidden',
        position: 'relative',
      }}
    >
      {/* Barre d'accent supérieure */}
      <Box
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 4,
          background: pickPalette().grad,
          opacity: 0.9,
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          top: -40,
          right: -40,
          width: 130,
          height: 130,
          borderRadius: '50%',
          background: (t) =>
            t.palette.mode === 'dark' ? 'rgba(37, 99, 235, 0.06)' : 'rgba(37, 99, 235, 0.05)',
        }}
      />
      <Box display="flex" alignItems="flex-start" justifyContent="space-between" position="relative">
        <Box minWidth={0}>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ fontSize: 11.5, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}
          >
            {title}
          </Typography>
          {loading ? (
            <Skeleton width={90} height={42} sx={{ mt: 0.5 }} />
          ) : (
            <Typography variant="h4" sx={{ fontWeight: 800, mt: 0.3, fontSize: { xs: 24, md: 28 } }}>
              {value}
              {suffix && (
                <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 0.5 }}>
                  {suffix}
                </Typography>
              )}
            </Typography>
          )}
          {subtitle && (
            <Typography variant="caption" color="text.disabled" sx={{ fontSize: 11, display: 'block', mt: 0.3 }}>
              {subtitle}
            </Typography>
          )}
          {trend !== undefined && trend !== null && (
            <Box display="flex" alignItems="center" gap={0.6} mt={0.8}>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.4,
                  borderRadius: 1.5,
                  px: 0.8,
                  py: 0.3,
                  bgcolor: trend >= 0 ? 'success.light' : 'error.light',
                  color: trend >= 0 ? 'success.dark' : 'error.dark',
                }}
              >
                {trend >= 0 ? <TrendingUp sx={{ fontSize: 13 }} /> : <TrendingDown sx={{ fontSize: 13 }} />}
                <Typography variant="caption" fontWeight={700} sx={{ fontSize: 11.5 }}>
                  {Math.abs(trend)}%
                </Typography>
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ fontSize: 11.5 }}>
                ce mois
              </Typography>
            </Box>
          )}
        </Box>
        {Icon && (
          <Box
            sx={{
              width: 48,
              height: 48,
              borderRadius: '14px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: pickPalette().grad,
              color: '#fff',
              flexShrink: 0,
              boxShadow: '0 6px 16px rgba(15, 23, 42, 0.14)',
            }}
          >
            <Icon sx={{ fontSize: 24 }} />
          </Box>
        )}
      </Box>
    </Card>
  )
}