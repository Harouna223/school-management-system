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
  Avatar,
  TablePagination
} from '@mui/material'
import { Add, Delete, Person, School } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { candidatureApi, lmdApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
const STATUSES = ['EN_ATTENTE', 'RECUE', 'ACCEPTEE', 'LISTE_ATTENTE', 'REFUSEE']
const STATUS_COLORS = {
  EN_ATTENTE: 'warning', RECUE: 'info', ACCEPTEE: 'success',
  LISTE_ATTENTE: 'warning', REFUSEE: 'error',
}

/**
 * Module admission universitaire : candidatures, dossiers, sélection.
 */
export default function CandidaturesPage() {
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [fields, setFields] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [statusFilter, setStatusFilter] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [form, setForm] = useState({
    firstName: '', lastName: '', birthDate: '', email: '', phone: '',
    fieldId: '', level: 'L1', notes: '',
  })

  const load = async () => {
    try {
      const { data } = await candidatureApi.list({
        status: statusFilter || undefined, page, size,
      })
      setRows(data.data.content || [])
      setTotal(data.data.totalElements || 0)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    lmdApi.fields().then((r) => setFields(r.data.data || [])).catch(() => {})
  }, [])

  useEffect(() => { load() }, [page, size, statusFilter])

  const handleCreate = async () => {
    if (!form.firstName || !form.lastName || !form.fieldId) {
      toastError('Prénom, nom et filière sont obligatoires')
      return
    }
    try {
      await candidatureApi.create({
        firstName: form.firstName, lastName: form.lastName,
        birthDate: form.birthDate || undefined, email: form.email || undefined, phone: form.phone || undefined,
        field: { id: Number(form.fieldId) }, level: form.level, notes: form.notes || undefined,
      })
      success('Candidature déposée')
      setDialogOpen(false)
      setForm({ firstName: '', lastName: '', birthDate: '', email: '', phone: '', fieldId: '', level: 'L1', notes: '' })
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleStatus = async (id, status) => {
    try {
      await candidatureApi.updateStatus(id, status)
      success('Statut mis à jour')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async (id) => {
    try {
      await candidatureApi.delete(id)
      success('Candidature supprimée')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Admission universitaire"
        subtitle="Candidatures, dossiers et sélection"
        actions={[{ label: 'Nouvelle candidature', icon: <Add />, onClick: () => setDialogOpen(true) }]}
      />

      <Card sx={{ p: 2, borderRadius: '16px', mb: 3 }}>
        <TextField select size="small" label="Filtrer par statut" value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }} sx={{ minWidth: 220 }}>
          <MenuItem value="">Tous</MenuItem>
          {STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
      </Card>

      <Grid container spacing={2.5}>
        {rows.map((c) => (
          <Grid item xs={12} md={6} lg={4} key={c.id}>
            <Card sx={{ p: 2.5, borderRadius: '16px', border: '1px solid', borderColor: 'divider' }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Chip size="small" color={STATUS_COLORS[c.status] || 'default'} label={c.status} />
                <Typography variant="caption" color="text.secondary">Réf. {c.reference}</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={1.5}>
                <Avatar sx={{ bgcolor: 'primary.main' }}><Person /></Avatar>
                <Box>
                  <Typography variant="subtitle1" fontWeight={700}>{c.firstName} {c.lastName}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {c.email || '—'} · {c.phone || '—'}
                  </Typography>
                </Box>
              </Box>
              <Box display="flex" alignItems="center" gap={1} mt={1.5}>
                <School color="action" sx={{ fontSize: 18 }} />
                <Typography variant="body2">{c.field?.name} — {c.level || ''}</Typography>
              </Box>
              {c.notes && <Typography variant="body2" color="text.secondary" mt={1} sx={{ fontStyle: 'italic' }}>{c.notes}</Typography>}

              <Box display="flex" gap={0.5} mt={1.5} flexWrap="wrap">
                <Button size="small" color="success" variant="outlined" onClick={() => handleStatus(c.id, 'ACCEPTEE')}>Accepter</Button>
                <Button size="small" color="warning" variant="outlined" onClick={() => handleStatus(c.id, 'LISTE_ATTENTE')}>Liste d'attente</Button>
                <Button size="small" color="error" variant="outlined" onClick={() => handleStatus(c.id, 'REFUSEE')}>Refuser</Button>
                <IconButton size="small" color="error" onClick={() => handleDelete(c.id)}><Delete fontSize="small" /></IconButton>
              </Box>
            </Card>
          </Grid>
        ))}
        {rows.length === 0 && (
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary" textAlign="center" py={4}>
              Aucune candidature{statusFilter ? ` au statut ${statusFilter}` : ''}.
            </Typography>
          </Grid>
        )}
      </Grid>

      <TablePagination
        component="div"
        count={total}
        page={page}
        rowsPerPage={size}
        rowsPerPageOptions={[5, 10, 25]}
        onPageChange={(_, p) => setPage(p)}
        onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
      />

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Nouvelle candidature</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={6}><TextField fullWidth size="small" label="Prénom" value={form.firstName} onChange={(e) => setForm((f) => ({ ...f, firstName: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Nom" value={form.lastName} onChange={(e) => setForm((f) => ({ ...f, lastName: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" type="date" label="Naissance" value={form.birthDate} onChange={(e) => setForm((f) => ({ ...f, birthDate: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Niveau" value={form.level} onChange={(e) => setForm((f) => ({ ...f, level: e.target.value }))} /></Grid>
            <Grid item xs={12}><TextField select fullWidth size="small" label="Filière" value={form.fieldId} onChange={(e) => setForm((f) => ({ ...f, fieldId: e.target.value }))}>
              {fields.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name}</MenuItem>)}
            </TextField></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Email" value={form.email} onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Téléphone" value={form.phone} onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))} /></Grid>
            <Grid item xs={12}><TextField fullWidth size="small" label="Notes" multiline minRows={2} value={form.notes} onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleCreate}>Déposer</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}