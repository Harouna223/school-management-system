import { Box, Typography, Button } from '@mui/material'
import { Link } from 'react-router-dom'
import { Home } from '@mui/icons-material'

export default function NotFoundPage() {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 2,
      }}
    >
      <Typography variant="h1" sx={{ fontWeight: 800, color: 'primary.main' }}>
        404
      </Typography>
      <Typography variant="h6">Page introuvable</Typography>
      <Button component={Link} to="/dashboard" startIcon={<Home />} variant="contained">
        Retour au tableau de bord
      </Button>
    </Box>
  )
}