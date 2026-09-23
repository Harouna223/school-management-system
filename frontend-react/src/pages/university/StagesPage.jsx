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
import { Add, Delete, Edit, Business, Event, MenuBook } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { stageApi, studentApi, teacherApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate } from '../../utils/format'

const STATUSES = ['EN_COURS', 'TERMINE', 'SOUTENU', 'ABANDONNE']

/**
 * Module stages universitaires.
 */
export default function StagesPage() {
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [students, setStudents] = useState([])
  const [teachers, setTeachers] = useState([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState({
    studentId: '', company: '', companyContact: '', subject: '',
    startDate: '', endDate: '', supervisorId: '', conventionRef: '',
    reportSubmitted: false, defenseDate: '', grade: '', evaluation: '', status: 'EN_COURS',
  })

  const load = () => {
    stageApi.list().then((r) => setRows(r.data.data || [])).catch(() => {})
  }

  useEffect(() => {
    studentApi.search({ page: 0, size: 500 }).then((r) => setStudents(r.data.data.content || [])).catch(() => {})
    teacherApi.search({ page: 0, size: 500 }).then((r) => setTeachers(r.data.data.content || [])).catch(() => {})
  }, [])

  useEffect(() => { load() }, [])

  const resetForm = () => setForm({
    studentId: '', company: '', companyContact: '', subject: '',
    startDate: '', endDate: '', supervisorId: '', conventionRef: '',
    reportSubmitted: false, defenseDate: '', grade: '', evaluation: '', status: 'EN_COURS',
  })

  const handleSave = async () => {
    if (!form.studentId || !form.company) {
      toastError('Étudiant et entreprise sont obligatoires')
      return
    }
    const payload = {
      student: { id: Number(form.studentId) },
      company: form.company,
      companyContact: form.companyContact || undefined,
      subject: form.subject || undefined,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
      supervisor: form.supervisorId ? { id: Number(form.supervisorId) } : undefined,
      conventionRef: form.conventionRef || undefined,
      reportSubmitted: form.reportSubmitted,
      defenseDate: form.defenseDate || undefined,
      grade: form.grade ? Number(form.grade) : undefined,
      evaluation: form.evaluation || undefined,
      status: form.status,
    }
    try {
      if (editing) await stageApi.update(editing.id, payload)
      else await stageApi.create(payload)
      success(editing ? 'Stage modifié' : 'Stage créé')
      setDialogOpen(false)
      setEditing(null)
      resetForm()
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async (id) => {
    try {
      await stageApi.delete(id)
      success('Stage supprimé')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const openEdit = (s) => {
    setEditing(s)
    setForm({
      studentId: String(s.student?.id || ''), company: s.company || '', companyContact: s.companyContact || '',
      subject: s.subject || '', startDate: s.startDate || '', endDate: s.endDate || '',
      supervisorId: s.supervisor ? String(s.supervisor.id) : '', conventionRef: s.conventionRef || '',
      reportSubmitted: s.reportSubmitted || false, defenseDate: s.defenseDate || '',
      grade: s.grade || '', evaluation: s.evaluation || '', status: s.status || 'EN_COURS',
    })
    setDialogOpen(true)
  }

  return (
    <>
      <PageHeader
        title="Stages universitaires"
        subtitle="Entreprises, conventions, encadrement, rapport et soutenance"
        actions={[{ label: 'Nouveau stage', icon: <Add />, onClick: () => { setEditing(null); resetForm(); setDialogOpen(true) } }]}
      />

      <Grid container spacing={2.5}>
        {rows.map((s) => (
          <Grid item xs={12} md={6} lg={4} key={s.id}>
            <Card sx={{ p: 2.5, borderRadius: '16px', border: '1px solid', borderColor: 'divider' }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Chip size="small" color={s.status === 'SOUTENU' ? 'success' : s.status === 'TERMINE' ? 'primary' : 'warning'} label={s.status} />
                <Typography variant="caption" color="text.secondary">Réf. convention {s.conventionRef || '—'}</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={1}>
                <Avatar sx={{ width: 30, height: 30, bgcolor: 'secondary.main' }}><Business sx={{ fontSize: 16 }} /></Avatar>
                <Typography variant="h6" fontWeight={700}>{s.company}</Typography>
              </Box>
              <Typography variant="caption" color="text.secondary">{s.subject}</Typography>

              <Box display="flex" alignItems="center" gap={1} mt={1.5}>
                <Avatar sx={{ width: 28, height: 28, bgcolor: 'primary.main' }}><MenuBook sx={{ fontSize: 15 }} /></Avatar>
                <Typography variant="body2" fontWeight={600}>{s.student?.firstName} {s.student?.lastName}</Typography>
                <Typography variant="caption" color="text.secondary">({s.student?.matricule})</Typography>
              </Box>

              <Box display="flex" alignItems="center" gap={1} mt={1}>
                <Event color="action" sx={{ fontSize: 18 }} />
                <Typography variant="body2">
                  {s.startDate ? formatDate(s.startDate) : '—'} → {s.endDate ? formatDate(s.endDate) : '—'}
                </Typography>
              </Box>

              <Box display="flex" gap={1} flexWrap="wrap" mt={1.5}>
                {s.supervisor && <Chip size="small" label={`Encadrant : ${s.supervisor.firstName} ${s.supervisor.lastName}`} variant="outlined" />}
                {s.reportSubmitted && <Chip size="small" color="success" label="Rapport déposé" />}
                {s.defenseDate && <Chip size="small" label={`Soutenance : ${formatDate(s.defenseDate)}`} variant="outlined" />}
                {s.grade != null && <Chip size="small" color="secondary" label={`Note : ${s.grade}/20`} />}
              </Box>

              {s.evaluation && (
                <Typography variant="body2" color="text.secondary" mt={1} sx={{ fontStyle: 'italic' }}>
                  {s.evaluation}
                </Typography>
              )}

              <Box display="flex" justifyContent="flex-end" gap={0.5} mt={1.5}>
                <IconButton size="small" color="primary" onClick={() => openEdit(s)} title="Modifier"><Edit fontSize="small" /></IconButton>
                <IconButton size="small" color="error" onClick={() => handleDelete(s.id)} title="Supprimer"><Delete fontSize="small" /></IconButton>
              </Box>
            </Card>
          </Grid>
        ))}
        {rows.length === 0 && (
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary" textAlign="center" py={4}>Aucun stage enregistré.</Typography>
          </Grid>
        )}
      </Grid>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editing ? 'Modifier le stage' : 'Nouveau stage'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={12}>
              <TextField select fullWidth size="small" label="Étudiant" value={form.studentId}
                onChange={(e) => setForm((f) => ({ ...f, studentId: e.target.value }))}>
                {students.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.firstName} {s.lastName} — {s.matricule}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Entreprise" value={form.company}
                onChange={(e) => setForm((f) => ({ ...f, company: e.target.value }))} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" label="Contact entreprise" value={form.companyContact}
                onChange={(e) => setForm((f) => ({ ...f, companyContact: e.target.value }))} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" label="Réf. convention" value={form.conventionRef}
                onChange={(e) => setForm((f) => ({ ...f, conventionRef: e.target.value }))} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Sujet du stage" value={form.subject}
                onChange={(e) => setForm((f) => ({ ...f, subject: e.target.value }))} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" type="date" label="Début" value={form.startDate}
                onChange={(e) => setForm((f) => ({ ...f, startDate: e.target.value }))} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" type="date" label="Fin" value={form.endDate}
                onChange={(e) => setForm((f) => ({ ...f, endDate: e.target.value }))} />
            </Grid>
            <Grid item xs={6}>
              <TextField select fullWidth size="small" label="Encadrant" value={form.supervisorId}
                onChange={(e) => setForm((f) => ({ ...f, supervisorId: e.target.value }))}>
                <MenuItem value="">Aucun</MenuItem>
                {teachers.map((t) => <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField select fullWidth size="small" label="Statut" value={form.status}
                onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}>
                {STATUSES.map((st) => <MenuItem key={st} value={st}>{st}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" type="date" label="Date soutenance" value={form.defenseDate}
                onChange={(e) => setForm((f) => ({ ...f, defenseDate: e.target.value }))} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" type="number" inputProps={{ min: 0, max: 20, step: 0.25 }} label="Note /20"
                value={form.grade} onChange={(e) => setForm((f) => ({ ...f, grade: e.target.value }))} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Évaluation" multiline minRows={2} value={form.evaluation}
                onChange={(e) => setForm((f) => ({ ...f, evaluation: e.target.value }))} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleSave}>{editing ? 'Enregistrer' : 'Créer'}</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}