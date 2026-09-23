import { useEffect, useState } from 'react'
import {
  Grid,
  Card,
  Typography,
  Box,
  Chip,
  Button,
  TextField,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Avatar
} from '@mui/material'
import { Add, Delete, Person, School, Work } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { alumnusApi, lmdApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

/**
 * Module diplômés (alumni).
 */
export default function AlumniPage() {
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [fields, setFields] = useState([])
  const [fieldFilter, setFieldFilter] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [form, setForm] = useState({
    firstName: '', lastName: '', fieldId: '', diploma: '', academicYear: '',
    email: '', phone: '', currentJob: '', company: '', notes: '',
  })

  const load = () => {
    alumnusApi.list(fieldFilter || undefined).then((r) => setRows(r.data.data || [])).catch(() => {})
  }

  useEffect(() => {
    lmdApi.fields().then((r) => setFields(r.data.data || [])).catch(() => {})
  }, [])

  useEffect(() => { load() }, [fieldFilter])

  const handleCreate = async () => {
    if (!form.firstName || !form.lastName) {
      toastError('Prénom et nom sont obligatoires')
      return
    }
    try {
      await alumnusApi.create({
        firstName: form.firstName, lastName: form.lastName,
        field: form.fieldId ? { id: Number(form.fieldId) } : undefined,
        diploma: form.diploma || undefined, academicYear: form.academicYear || undefined,
        email: form.email || undefined, phone: form.phone || undefined,
        currentJob: form.currentJob || undefined, company: form.company || undefined, notes: form.notes || undefined,
      })
      success('Diplômé ajouté')
      setDialogOpen(false)
      setForm({ firstName: '', lastName: '', fieldId: '', diploma: '', academicYear: '', email: '', phone: '', currentJob: '', company: '', notes: '' })
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async (id) => {
    try {
      await alumnusApi.delete(id)
      success('Diplômé supprimé')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Diplômés (Alumni)"
        subtitle="Anciens étudiants, diplômes et parcours professionnel"
        actions={[{ label: 'Ajouter un diplômé', icon: <Add />, onClick: () => setDialogOpen(true) }]}
      />

      <Card sx={{ p: 2, borderRadius: '16px', mb: 3 }}>
        <TextField select size="small" label="Filtrer par filière" value={fieldFilter}
          onChange={(e) => setFieldFilter(e.target.value)} sx={{ minWidth: 240 }}>
          <MenuItem value="">Toutes</MenuItem>
          {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
        </TextField>
      </Card>

      <Grid container spacing={2.5}>
        {rows.map((a) => (
          <Grid item xs={12} md={6} lg={4} key={a.id}>
            <Card sx={{ p: 2.5, borderRadius: '16px', border: '1px solid', borderColor: 'divider' }}>
              <Box display="flex" alignItems="center" gap={1.5}>
                <Avatar sx={{ bgcolor: 'primary.main' }}><Person /></Avatar>
                <Box flex={1}>
                  <Typography variant="subtitle1" fontWeight={700}>{a.firstName} {a.lastName}</Typography>
                  <Typography variant="caption" color="text.secondary">{a.academicYear || '—'}</Typography>
                </Box>
                <IconButton size="small" color="error" onClick={() => handleDelete(a.id)}><Delete fontSize="small" /></IconButton>
              </Box>
              <Box display="flex" alignItems="center" gap={1} mt={1}>
                <School color="action" sx={{ fontSize: 18 }} />
                <Typography variant="body2">{a.field?.name || '—'}</Typography>
              </Box>
              {a.diploma && <Chip size="small" sx={{ mt: 1 }} label={`Diplôme : ${a.diploma}`} variant="outlined" />}
              {(a.currentJob || a.company) && (
                <Box display="flex" alignItems="center" gap={1} mt={1}>
                  <Work color="action" sx={{ fontSize: 18 }} />
                  <Typography variant="body2">{a.currentJob || '—'} {a.company ? `· ${a.company}` : ''}</Typography>
                </Box>
              )}
              {a.email && <Typography variant="caption" color="text.secondary" display="block" mt={1}>{a.email}</Typography>}
            </Card>
          </Grid>
        ))}
        {rows.length === 0 && (
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary" textAlign="center" py={4}>Aucun diplômé enregistré.</Typography>
          </Grid>
        )}
      </Grid>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Ajouter un diplômé</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={6}><TextField fullWidth size="small" label="Prénom" value={form.firstName} onChange={(e) => setForm((f) => ({ ...f, firstName: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Nom" value={form.lastName} onChange={(e) => setForm((f) => ({ ...f, lastName: e.target.value }))} /></Grid>
            <Grid item xs={12}><TextField select fullWidth size="small" label="Filière" value={form.fieldId} onChange={(e) => setForm((f) => ({ ...f, fieldId: e.target.value }))}>
              <MenuItem value="">Aucune</MenuItem>
              {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
            </TextField></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Diplôme" value={form.diploma} onChange={(e) => setForm((f) => ({ ...f, diploma: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Année" value={form.academicYear} onChange={(e) => setForm((f) => ({ ...f, academicYear: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Email" value={form.email} onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Téléphone" value={form.phone} onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Emploi actuel" value={form.currentJob} onChange={(e) => setForm((f) => ({ ...f, currentJob: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Entreprise" value={form.company} onChange={(e) => setForm((f) => ({ ...f, company: e.target.value }))} /></Grid>
            <Grid item xs={12}><TextField fullWidth size="small" label="Notes" multiline minRows={2} value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleCreate}>Ajouter</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}