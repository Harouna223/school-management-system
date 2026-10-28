import { useCallback, useEffect, useState } from 'react'
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
import Add from '@mui/icons-material/Add'
import Delete from '@mui/icons-material/Delete'
import Person from '@mui/icons-material/Person'
import School from '@mui/icons-material/School'
import PersonAdd from '@mui/icons-material/PersonAdd'
import PageHeader from '../../components/PageHeader'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { candidatureApi, classApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
const STATUSES = ['EN_ATTENTE', 'RECUE', 'ACCEPTEE', 'LISTE_ATTENTE', 'REFUSEE', 'INSCRITE']
const STATUS_COLORS = {
  EN_ATTENTE: 'warning', RECUE: 'info', ACCEPTEE: 'success',
  LISTE_ATTENTE: 'warning', REFUSEE: 'error', INSCRITE: 'success',
}
const GENDERS = [
  { value: 'MALE', label: 'Masculin' },
  { value: 'FEMALE', label: 'Féminin' },
]
const CYCLES = [
  { value: 'JARDIN', label: 'Maternelle' },
  { value: 'PRIMAIRE', label: 'Primaire' },
  { value: 'COLLEGE', label: 'Collège' },
  { value: 'LYCEE', label: 'Lycée' },
  { value: 'UNIVERSITE', label: 'Université' },
]
const EMPTY_ENROLL = { gender: '', classId: '', educationCycle: '' }

/**
 * Module admission universitaire : candidatures, dossiers, sélection et
 * inscription (transformation du dossier en élève).
 */
export default function CandidaturesPage() {
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [statusFilter, setStatusFilter] = useState('')
  const [classes, setClasses] = useState([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [toDelete, setToDelete] = useState(null)
  const [enrollTarget, setEnrollTarget] = useState(null)
  const [enrollForm, setEnrollForm] = useState(EMPTY_ENROLL)
  const [enrollResult, setEnrollResult] = useState(null)
  const [form, setForm] = useState({
    firstName: '', lastName: '', birthDate: '', email: '', phone: '',
    fieldName: '', level: 'L1', notes: '',
  })

  const load = useCallback(async () => {
    try {
      const { data } = await candidatureApi.list({
        status: statusFilter || undefined, page, size,
      })
      setRows(data.data.content || [])
      setTotal(data.data.totalElements || 0)
    } catch (err) {
      toastError(extractError(err))
    }
  }, [statusFilter, page, size, toastError])

  useEffect(() => { load() }, [load])

  useEffect(() => {
    // Classes d'accueil possibles : facultatives à l'inscription.
    classApi.all().then((r) => setClasses(r.data.data || [])).catch(() => {})
  }, [])

  const handleCreate = async () => {
    if (!form.firstName || !form.lastName || !form.fieldName) {
      toastError('Prénom, nom et filière sont obligatoires')
      return
    }
    try {
      await candidatureApi.create({
        firstName: form.firstName, lastName: form.lastName,
        birthDate: form.birthDate || undefined, email: form.email || undefined, phone: form.phone || undefined,
        fieldName: form.fieldName, level: form.level, notes: form.notes || undefined,
      })
      success('Candidature déposée')
      setDialogOpen(false)
      setForm({ firstName: '', lastName: '', birthDate: '', email: '', phone: '', fieldName: '', level: 'L1', notes: '' })
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

  const openEnroll = (c) => {
    setEnrollTarget(c)
    setEnrollForm(EMPTY_ENROLL)
  }

  const handleEnroll = async () => {
    if (!enrollForm.gender) {
      toastError('Le genre est obligatoire')
      return
    }
    try {
      const { data } = await candidatureApi.enroll(enrollTarget.id, {
        gender: enrollForm.gender,
        classId: enrollForm.classId ? Number(enrollForm.classId) : undefined,
        educationCycle: enrollForm.educationCycle || undefined,
      })
      setEnrollResult(data.data)
      success('Candidature inscrite')
      setEnrollTarget(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await candidatureApi.delete(toDelete.id)
      success('Candidature supprimée')
      setToDelete(null)
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
                <Typography variant="body2">{c.fieldName} — {c.level || ''}</Typography>
              </Box>
              {c.notes && <Typography variant="body2" color="text.secondary" mt={1} sx={{ fontStyle: 'italic' }}>{c.notes}</Typography>}
              {c.matricule && (
                <Chip size="small" sx={{ mt: 1 }} color="primary" variant="outlined" label={`Élève ${c.matricule}`} />
              )}

              <Box display="flex" gap={0.5} mt={1.5} flexWrap="wrap">
                {c.status === 'ACCEPTEE' && (
                  <Button size="small" variant="contained" startIcon={<PersonAdd />} onClick={() => openEnroll(c)}>Inscrire</Button>
                )}
                {c.status !== 'INSCRITE' && (
                  <>
                    <Button size="small" color="success" variant="outlined" onClick={() => handleStatus(c.id, 'ACCEPTEE')}>Accepter</Button>
                    <Button size="small" color="warning" variant="outlined" onClick={() => handleStatus(c.id, 'LISTE_ATTENTE')}>Liste d'attente</Button>
                    <Button size="small" color="error" variant="outlined" onClick={() => handleStatus(c.id, 'REFUSEE')}>Refuser</Button>
                    <IconButton size="small" color="error" onClick={() => setToDelete(c)}><Delete fontSize="small" /></IconButton>
                  </>
                )}
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
            <Grid item xs={12}><TextField fullWidth size="small" label="Filière" value={form.fieldName} onChange={(e) => setForm((f) => ({ ...f, fieldName: e.target.value }))} /></Grid>
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

      <Dialog open={Boolean(enrollTarget)} onClose={() => setEnrollTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Inscrire {enrollTarget?.firstName} {enrollTarget?.lastName}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" mb={2}>
            L&apos;élève est créé à partir du dossier : prénom, nom, naissance et coordonnées sont repris.
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField select fullWidth size="small" label="Genre" value={enrollForm.gender}
                onChange={(e) => setEnrollForm((f) => ({ ...f, gender: e.target.value }))}>
                {GENDERS.map((g) => <MenuItem key={g.value} value={g.value}>{g.label}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField select fullWidth size="small" label="Classe d'accueil" value={enrollForm.classId}
                onChange={(e) => setEnrollForm((f) => ({ ...f, classId: e.target.value }))}>
                <MenuItem value="">Aucune</MenuItem>
                {classes.map((cl) => <MenuItem key={cl.id} value={String(cl.id)}>{cl.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField select fullWidth size="small" label="Cycle" value={enrollForm.educationCycle}
                onChange={(e) => setEnrollForm((f) => ({ ...f, educationCycle: e.target.value }))}
                helperText="Déduit de la classe si celle-ci est renseignée.">
                <MenuItem value="">Non précisé</MenuItem>
                {CYCLES.map((cy) => <MenuItem key={cy.value} value={cy.value}>{cy.label}</MenuItem>)}
              </TextField>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEnrollTarget(null)}>Annuler</Button>
          <Button variant="contained" onClick={handleEnroll}>Inscrire</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(enrollResult)} onClose={() => setEnrollResult(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Inscription effectuée</DialogTitle>
        <DialogContent>
          <Typography variant="body2" mb={2}>
            {enrollTarget?.firstName} {enrollTarget?.lastName} est inscrit(e) comme élève.
          </Typography>
          <Box sx={{ p: 1.5, borderRadius: '12px', bgcolor: 'action.hover' }}>
            <Typography variant="caption" color="text.secondary" display="block">Matricule</Typography>
            <Typography variant="body2" fontWeight={700}>{enrollResult?.student?.matricule}</Typography>
            {enrollResult?.student?.accountUsername && (
              <>
                <Typography variant="caption" color="text.secondary" display="block" mt={1}>Identifiant</Typography>
                <Typography variant="body2" fontWeight={700}>{enrollResult.student.accountUsername}</Typography>
              </>
            )}
            {enrollResult?.student?.generatedPassword && (
              <>
                <Typography variant="caption" color="text.secondary" display="block" mt={1}>Mot de passe</Typography>
                <Typography variant="body2" fontWeight={700}>{enrollResult.student.generatedPassword}</Typography>
              </>
            )}
          </Box>
          {enrollResult?.student?.generatedPassword && (
            <Typography variant="caption" color="warning.main" display="block" mt={1.5}>
              Ce mot de passe n&apos;est affiché qu&apos;une fois : transmettez-le maintenant.
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={() => setEnrollResult(null)}>Fermer</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer la candidature"
        message={`Supprimer la candidature de ${toDelete?.firstName} ${toDelete?.lastName} (${toDelete?.reference}) ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}
