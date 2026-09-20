import { Component } from 'react'
import { Box, Button, Card, CardContent, Stack, Typography, Alert } from '@mui/material'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import RefreshIcon from '@mui/icons-material/Refresh'
import DashboardIcon from '@mui/icons-material/Dashboard'

/**
 * Frontière d'erreur globale.
 *
 * Sans elle, toute exception levée pendant le rendu (par ex. un identifiant
 * utilisé mais non importé) démonte l'arbre React entier et laisse une **page
 * blanche muette** : l'utilisateur ne voit ni la cause ni un moyen de s'en
 * sortir. C'est exactement ce qui s'est produit sur `/my-children` le
 * 2026-09-19 (`useLocation` non importé).
 *
 * Ce composant transforme ce cas en message lisible + actions de reprise.
 * Il est volontairement sans dépendance à l'i18n : si la panne vient de la
 * couche de traduction, la frontière doit rester capable de s'afficher.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null, info: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    // Trace conservée en console pour le diagnostic (aucun backend de télémétrie ici).
    console.error('[ErrorBoundary] Exception de rendu :', error, info?.componentStack)
    this.setState({ info })
  }

  handleReload = () => {
    window.location.reload()
  }

  handleHome = () => {
    window.location.assign('/dashboard')
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children

    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          p: 3,
          bgcolor: 'background.default',
        }}
      >
        <Card sx={{ maxWidth: 620, width: '100%', borderRadius: '16px' }}>
          <CardContent sx={{ p: 3.5 }}>
            <Stack direction="row" spacing={1.5} alignItems="center" mb={2}>
              <ReportProblemOutlinedIcon color="error" fontSize="large" />
              <Typography variant="h6" fontWeight={700}>
                Une erreur est survenue
              </Typography>
            </Stack>

            <Typography variant="body2" color="text.secondary" mb={2}>
              Cette page n'a pas pu s'afficher. Vous pouvez recharger l'application ou
              revenir au tableau de bord.
            </Typography>

            <Alert severity="error" sx={{ mb: 2.5, fontFamily: 'monospace', fontSize: 12.5 }}>
              {error?.message || String(error)}
            </Alert>

            <Stack direction="row" spacing={1.5} flexWrap="wrap">
              <Button variant="contained" startIcon={<RefreshIcon />} onClick={this.handleReload}>
                Recharger
              </Button>
              <Button variant="outlined" startIcon={<DashboardIcon />} onClick={this.handleHome}>
                Tableau de bord
              </Button>
            </Stack>
          </CardContent>
        </Card>
      </Box>
    )
  }
}
