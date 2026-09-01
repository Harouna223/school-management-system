import { useState } from 'react'
import {
  Grid, Card, CardContent, Typography, Box, Avatar, Button, Divider, Dialog,
  DialogTitle, DialogContent, DialogActions, TextField, IconButton,
} from '@mui/material'
import { LockReset, Key } from '@mui/icons-material'
import { useSelector } from 'react-redux'
import { formatDate, initials } from '../utils/format'
import PageHeader from '../components/PageHeader'
import StatusChip from '../components/StatusChip'
import { authApi } from '../api/endpoints'
import { useToast } from '../hooks/useToast'
import { extractError } from '../api/axios'

/**
 * Page de profil utilisateur.
 */
export default function ProfilePage() {
  const user = useSelector((state) => state.auth.user)
  const { success, error: toastError } = useToast()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [saving, setSaving] = useState(false)

  if (!user) return null

  const handleChangePassword = async () => {
    if (form.newPassword.length < 8 || !/[A-Za-z]/.test(form.newPassword) || !/[0-9]/.test(form.newPassword)) {
      toastError('Le mot de passe doit contenir au moins 8 caractères, des lettres et des chiffres')
      return
    }
    if (form.newPassword !== form.confirmPassword) {
      toastError('La confirmation ne correspond pas au nouveau mot de passe')
      return
    }
    setSaving(true)
    try {
      await authApi.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
      success('Mot de passe modifié avec succès')
      setOpen(false)
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader title="Mon profil" subtitle="Informations de votre compte" />

      <Grid container spacing={3} maxWidth="md">
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={3} flexWrap="wrap" justifyContent="space-between">
                <Box display="flex" alignItems="center" gap={3} flexWrap="wrap">
                  <Avatar
                    sx={{ width: 96, height: 96, fontSize: 40, bgcolor: 'primary.main' }}
                    src={user.avatar}
                  >
                    {initials(user.firstName, user.lastName)}
                  </Avatar>
                  <Box>
                    <Typography variant="h5" fontWeight={700}>
                      {user.firstName} {user.lastName}
                    </Typography>
                    <Typography color="text.secondary">@{user.username}</Typography>
                    <Box mt={1} display="flex" gap={1} flexWrap="wrap">
                      {user.roles?.map((role) => <StatusChip key={role} status={role} />)}
                    </Box>
                  </Box>
                </Box>
                <Button variant="outlined" startIcon={<LockReset />} onClick={() => setOpen(true)}>
                  Changer le mot de passe
                </Button>
              </Box>
              <Divider sx={{ my: 3 }} />
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Info label="Email" value={user.email} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Info label="Téléphone" value={user.phone || '—'} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Info label="Compte créé le" value={formatDate(user.createdAt)} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Info label="Statut" value={user.enabled ? 'Actif' : 'Désactivé'} />
                </Grid>
              </Grid>
              <Box mt={3}>
                <Typography variant="subtitle2" mb={1}>
                  Permissions ({user.permissions?.length ?? 0})
                </Typography>
                <Box display="flex" gap={1} flexWrap="wrap">
                  {user.permissions?.slice(0, 15).map((p) => (
                    <ChipSmall key={p} label={p} />
                  ))}
                  {(user.permissions?.length ?? 0) > 15 && (
                    <ChipSmall label={`+${user.permissions.length - 15}`} />
                  )}
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Dialog changement de mot de passe */}
      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle display="flex" alignItems="center" gap={1}>
          <IconButton size="small" color="primary" sx={{ bgcolor: 'primary.light', mr: 0.5 }}>
            <Key sx={{ fontSize: 18 }} />
          </IconButton>
          Changer le mot de passe
        </DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={2} mt={1}>
            <TextField
              fullWidth type="password" label="Mot de passe actuel"
              value={form.currentPassword}
              onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
            />
            <TextField
              fullWidth type="password" label="Nouveau mot de passe"
              helperText="8 caractères minimum, lettres et chiffres"
              value={form.newPassword}
              onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
            />
            <TextField
              fullWidth type="password" label="Confirmer le nouveau mot de passe"
              value={form.confirmPassword}
              onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setOpen(false)}>Annuler</Button>
          <Button variant="contained" disabled={saving} onClick={handleChangePassword}>
            {saving ? 'Enregistrement...' : 'Modifier'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

function Info({ label, value }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1">{value}</Typography>
    </Box>
  )
}

function ChipSmall({ label }) {
  return (
    <Box
      sx={{
        px: 1.2,
        py: 0.4,
        borderRadius: '20px',
        bgcolor: 'primary.light',
        color: 'primary.contrastText',
        fontSize: 11.5,
        fontWeight: 500,
      }}
    >
      {label}
    </Box>
  )
}