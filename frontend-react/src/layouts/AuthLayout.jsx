import { Box, Paper, Typography } from '@mui/material'
import { School, FactCheck, Payments, BarChart } from '@mui/icons-material'
import logo from '../assets/logo.svg'

const FEATURES = [
  {
    icon: <School sx={{ fontSize: 20 }} />,
    title: 'Gestion scolaire complète',
    text: 'Élèves, enseignants, classes, notes et bulletins au même endroit.',
  },
  {
    icon: <FactCheck sx={{ fontSize: 20 }} />,
    title: 'Présences & emplois du temps',
    text: 'Suivez les absences et planifiez les cours en toute simplicité.',
  },
  {
    icon: <Payments sx={{ fontSize: 20 }} />,
    title: 'Finance & paiements',
    text: 'Factures, encaissements, reçus PDF et suivi des impayés.',
  },
  {
    icon: <BarChart sx={{ fontSize: 20 }} />,
    title: 'Rapports & statistiques',
    text: 'Tableaux de bord et indicateurs en temps réel.',
  },
]

/**
 * Layout d'authentification premium : panneau de marque à gauche,
 * formulaire à droite. Responsive (panneau masqué sur mobile).
 */
export default function AuthLayout({ children }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', bgcolor: 'background.default' }}>
      {/* Panneau de marque */}
      <Box
        sx={{
          display: { xs: 'none', lg: 'flex' },
          width: '44%',
          minWidth: 480,
          flexDirection: 'column',
          justifyContent: 'space-between',
          p: 6,
          position: 'relative',
          overflow: 'hidden',
          background: 'linear-gradient(145deg, #1e3a8a 0%, #2563eb 60%, #4f46e5 100%)',
          color: '#fff',
        }}
      >
        {/* Halos décoratifs */}
        <Box
          sx={{
            position: 'absolute',
            width: 520,
            height: 520,
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(255,255,255,0.12) 0%, transparent 65%)',
            top: -160,
            right: -140,
            pointerEvents: 'none',
          }}
        />
        <Box
          sx={{
            position: 'absolute',
            width: 460,
            height: 460,
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(255,255,255,0.08) 0%, transparent 65%)',
            bottom: -160,
            left: -120,
            pointerEvents: 'none',
          }}
        />
        <Box
          sx={{
            position: 'absolute',
            width: 300,
            height: 300,
            borderRadius: '50%',
            border: '1px solid rgba(255,255,255,0.1)',
            bottom: '25%',
            right: '12%',
            pointerEvents: 'none',
          }}
        />

        {/* Logo */}
        <Box display="flex" alignItems="center" gap={1.6} position="relative">
          <Box
            sx={{
              width: 48,
              height: 48,
              borderRadius: '15px',
              overflow: 'hidden',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'rgba(255,255,255,0.16)',
              backdropFilter: 'blur(8px)',
              border: '1px solid rgba(255,255,255,0.2)',
            }}
          >
            <img src={logo} alt="Logo SMS" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          </Box>
          <Box>
            <Typography variant="h6" fontWeight={800} lineHeight={1.1} sx={{ fontSize: 19, letterSpacing: '-0.01em' }}>
              SMS
            </Typography>
            <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.75)', fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.09em' }}>
              School Management System
            </Typography>
          </Box>
        </Box>

        {/* Slogan */}
        <Box position="relative" my={4}>
          <Typography variant="h3" sx={{ fontWeight: 800, letterSpacing: '-0.02em', lineHeight: 1.25, fontSize: { lg: 30 } }}>
            La gestion scolaire,
            <br />
            simple et moderne.
          </Typography>
          <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.8)', mt: 2, maxWidth: 400, lineHeight: 1.7 }}>
            Une plateforme unique pour piloter votre établissement : scolarité,
            pédagogie, finance et communication — dans une interface élégante et rapide.
          </Typography>
        </Box>

        {/* Points forts */}
        <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2} position="relative">
          {FEATURES.map((f) => (
            <Box
              key={f.title}
              display="flex"
              gap={1.2}
              p={1.8}
              borderRadius="14px"
              sx={{ bgcolor: 'rgba(255,255,255,0.08)', backdropFilter: 'blur(6px)', border: '1px solid rgba(255,255,255,0.12)' }}
            >
              <Box sx={{ color: 'rgba(255,255,255,0.9)', mt: 0.2, flexShrink: 0 }}>{f.icon}</Box>
              <Box>
                <Typography variant="body2" fontWeight={700} sx={{ fontSize: 13.5 }}>
                  {f.title}
                </Typography>
                <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.7)', display: 'block', mt: 0.3, lineHeight: 1.5 }}>
                  {f.text}
                </Typography>
              </Box>
            </Box>
          ))}
        </Box>

        <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.5)', position: 'relative', mt: 3 }}>
          © {new Date().getFullYear()} SMS — School Management System
        </Typography>
      </Box>

      {/* Panneau formulaire */}
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          p: { xs: 2.5, sm: 4 },
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Halo subtil sur mobile uniquement */}
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            background:
              'radial-gradient(700px 320px at 50% -10%, rgba(37, 99, 235, 0.08) 0%, transparent 60%)',
            display: { xs: 'block', lg: 'none' },
            pointerEvents: 'none',
          }}
        />
        <Paper
          elevation={0}
          sx={{
            width: '100%',
            maxWidth: 440,
            p: { xs: 3.5, sm: 5 },
            borderRadius: '22px',
            bgcolor: 'background.paper',
            position: 'relative',
            boxShadow: (t) =>
              t.palette.mode === 'dark'
                ? '0 24px 64px rgba(0, 0, 0, 0.45)'
                : '0 24px 64px rgba(15, 23, 42, 0.12)',
            border: '1px solid',
            borderColor: 'divider',
          }}
          className="animate-fade-in-up"
        >
          {/* Logo compact (mobile) */}
          <Box display={{ xs: 'flex', lg: 'none' }} alignItems="center" justifyContent="center" mb={2.5} gap={1.2}>
            <Box
              sx={{
                width: 44,
                height: 44,
                borderRadius: '14px',
                background: 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 6px 16px rgba(37, 99, 235, 0.35)',
                overflow: 'hidden',
              }}
            >
              <img src={logo} alt="Logo SMS" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            </Box>
            <Box>
              <Typography variant="subtitle1" fontWeight={800} lineHeight={1.1} sx={{ fontSize: 16 }}>
                SMS
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.08em' }}>
                School Management
              </Typography>
            </Box>
          </Box>

          <Box textAlign="center" mb={3}>
            <Typography variant="h5" fontWeight={800} letterSpacing="-0.02em">
              School Manager
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Plateforme de gestion scolaire
            </Typography>
          </Box>
          {children}
        </Paper>
      </Box>
    </Box>
  )
}