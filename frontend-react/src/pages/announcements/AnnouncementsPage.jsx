import { useState, useEffect } from 'react'
import {
  Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions,
  Card, CardContent, Typography, Box, Chip, IconButton, FormControlLabel, Switch,
} from '@mui/material'
import { PushPin, DeleteOutline, Campaign } from '@mui/icons-material'
import { useSelector } from 'react-redux'
import PageHeader from '../../components/PageHeader'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { communicationApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDateTime } from '../../utils/format'

const ROLES = ['SUPER_ADMIN', 'DIRECTEUR', 'COMPTABLE', 'SECRETAIRE', 'ENSEIGNANT', 'PARENT', 'ELEVE']

const ROLE_LABELS = {
  SUPER_ADMIN: 'Super Admin',
  DIRECTEUR: 'Directeur',
  COMPTABLE: 'Comptable',
  SECRETAIRE: 'Secrétaire',
  ENSEIGNANT: 'Enseignant',
  PARENT: 'Parent',
  ELEVE: 'Élève',
}

/**
 * Annonces administratives : publication, diffusion et consultation.
 */
export default function AnnouncementsPage() {
  const { success, error: toastError } = useToast()
  const user = useSelector((state) => state.auth.user)
  const isAdmin = user?.roles?.some((r) => r === 'SUPER_ADMIN' || r === 'DIRECTEUR')

  const [rows, setRows] = useState([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [form, setForm] = useState({ title: '', content: '', targetRole: '', pinned: false })
  const [toDelete, setToDelete] = useState(null)

  const load = async () => {
    try {
      const { data } = isAdmin ? await communicationApi.allAnnouncements() : await communicationApi.announcements()
      setRows(data.data)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const publish = async () => {
    try {
      await communicationApi.createAnnouncement({
        title: form.title,
        content: form.content,
        targetRole: form.targetRole || null,
        pinned: form.pinned,
      })
      success('Annonce publiée et diffusée')
      setDialogOpen(false)
      setForm({ title: '', content: '', targetRole: '', pinned: false })
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const remove = async () => {
    try {
      await communicationApi.deleteAnnouncement(toDelete.id)
      success('Annonce supprimée')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Annonces administratives"
        subtitle="Communication institutionnelle diffusée à tout le personnel"
        actionLabel={isAdmin ? 'Publier une annonce' : undefined}
        onAction={() => setDialogOpen(true)}
      />

      <Grid container spacing={3}>
        {rows.length === 0 && (
          <Grid item xs={12}>
            <Card>
              <CardContent sx={{ py: 6, textAlign: 'center' }} color="text.secondary">
                <Typography variant="body2" color="text.secondary">
                  Aucune annonce publiée
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        )}
        {rows.map((a) => (
          <Grid item xs={12} md={6} key={a.id} className="animate-fade-in-up">
            <Card sx={{ height: '100%', border: a.pinned ? '1px solid' : undefined, borderColor: 'primary.main' }}>
              <CardContent>
                <Box display="flex" alignItems="flex-start" justifyContent="space-between" gap={1}>
                  <Box display="flex" alignItems="center" gap={1} minWidth={0}>
                    {a.pinned && (
                      <Box sx={{ width: 34, height: 34, borderRadius: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'primary.light', color: 'primary.main', flexShrink: 0 }}>
                        <PushPin sx={{ fontSize: 18 }} />
                      </Box>
                    )}
                    <Typography variant="h6" sx={{ fontWeight: 700 }} noWrap>
                      {a.title}
                    </Typography>
                  </Box>
                  {isAdmin && (
                    <IconButton size="small" color="error" onClick={() => setToDelete(a)}>
                      <DeleteOutline fontSize="small" />
                    </IconButton>
                  )}
                </Box>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5, whiteSpace: 'pre-wrap' }}>
                  {a.content}
                </Typography>
                <Box display="flex" alignItems="center" gap={1.5} mt={2}>
                  <Chip
                    size="small"
                    icon={<Campaign sx={{ fontSize: 14 }} />}
                    label={a.targetRole ? ROLE_LABELS[a.targetRole] || a.targetRole : 'Tout le monde'}
                    color={a.targetRole ? 'secondary' : 'primary'}
                    variant="outlined"
                  />
                  <Typography variant="caption" color="text.secondary">
                    {a.createdByName} • {formatDateTime(a.createdAt)}
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Publier une annonce</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField fullWidth label="Titre" value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth multiline rows={4} label="Contenu"
                value={form.content}
                onChange={(e) => setForm({ ...form, content: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField select fullWidth label="Destinataires" value={form.targetRole}
                onChange={(e) => setForm({ ...form, targetRole: e.target.value })}>
                <MenuItem value="">Tout le monde</MenuItem>
                {ROLES.map((r) => <MenuItem key={r} value={r}>{ROLE_LABELS[r]}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6} display="flex" alignItems="center">
              <FormControlLabel
                control={<Switch checked={form.pinned} onChange={(e) => setForm({ ...form, pinned: e.target.checked })} />}
                label="Épingler en haut"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={publish} disabled={!form.title.trim() || !form.content.trim()}>
            Publier et diffuser
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer l'annonce"
        message={`Supprimer « ${toDelete?.title} » ?`}
        onConfirm={remove}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}