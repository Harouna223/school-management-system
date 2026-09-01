import { useEffect, useState } from 'react'
import {
  Grid, Card, CardContent, Typography, Box, Chip, Button, TextField, MenuItem,
  Dialog, DialogTitle, DialogContent, DialogActions, IconButton, Avatar,
} from '@mui/material'
import { Add, Delete, Edit, MenuBook, Event, Group } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { memoireApi, studentApi, teacherApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate } from '../../utils/format'

const STATUSES = ['EN_REDACTION', 'SOUMIS', 'SOUTENU', 'VALIDEE', 'REFUSE']
const STATUS_COLORS = {
  EN_REDACTION: 'warning', SOUMIS: 'info', SOUTENU: 'primary',
  VALIDEE: 'success', REFUSE: 'error',
}

/**
 * Module mémoires et soutenances universitaires.
 */
export default function MemoiresPage() {
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [students, setStudents] = useState([])
  const [teachers, setTeachers] = useState([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState({
    studentId: '', subject: '', directorId: '', defenseDate: '', jury: '',
    defenseLocation: '', grade: '', decision: '', status: 'EN_REDACTION',
  })

  const load = () => {
    memoireApi.list().then((r) => setRows(r.data.data || [])).catch(() => {})
  }

  useEffect(() => {
    studentApi.search({ page: 0, size: 500 }).then((r) => setStudents(r.data.data.content || [])).catch(() => {})
    teacherApi.search({ page: 0, size: 500 }).then((r) => setTeachers(r.data.data.content || [])).catch(() => {})
  }, [])

  useEffect(() => { load() }, [])

  const resetForm = () => setForm({
    studentId: '', subject: '', directorId: '', defenseDate: '', jury: '',
    defenseLocation: '', grade: '', decision: '', status: 'EN_REDACTION',
  })

  const handleSave = async () => {
    if (!form.studentId || !form.subject) {
      toastError('Étudiant et sujet sont obligatoires')
      return
    }
    const payload = {
      student: { id: Number(form.studentId) },
      subject: form.subject,
      director: form.directorId ? { id: Number(form.directorId) } : undefined,
      defenseDate: form.defenseDate || undefined,
      jury: form.jury || undefined,
      defenseLocation: form.defenseLocation || undefined,
      grade: form.grade ? Number(form.grade) : undefined,
      decision: form.decision || undefined,
      status: form.status,
    }
    try {
      if (editing) await memoireApi.update(editing.id, payload)
      else await memoireApi.create(payload)
      success(editing ? 'Mémoire modifié' : 'Mémoire créé')
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
      await memoireApi.delete(id)
      success('Mémoire supprimé')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const openEdit = (m) => {
    setEditing(m)
    setForm({
      studentId: String(m.student?.id || ''), subject: m.subject || '',
      directorId: m.director ? String(m.director.id) : '', defenseDate: m.defenseDate || '',
      jury: m.jury || '', defenseLocation: m.defenseLocation || '',
      grade: m.grade || '', decision: m.decision || '', status: m.status || 'EN_REDACTION',
    })
    setDialogOpen(true)
  }

  return (
    <>
      <PageHeader
        title="Mémoires et soutenances"
        subtitle="Sujets, directeurs, jurys, notes et décisions"
        actions={[{ label: 'Nouveau mémoire', icon: <Add />, onClick: () => { setEditing(null); resetForm(); setDialogOpen(true) } }]}
      />

      <Grid container spacing={2.5}>
        {rows.map((m) => (
          <Grid item xs={12} md={6} lg={4} key={m.id}>
            <Card sx={{ p: 2.5, borderRadius: '16px', border: '1px solid', borderColor: 'divider' }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Chip size="small" color={STATUS_COLORS[m.status] || 'default'} label={m.status} />
                {m.grade != null && <Chip size="small" color="secondary" label={`Note : ${m.grade}/20`} />}
              </Box>
              <Box display="flex" alignItems="center" gap={1}>
                <Avatar sx={{ bgcolor: 'secondary.main' }}><MenuBook /></Avatar>
                <Typography variant="h6" fontWeight={700}>{m.subject}</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={1} mt={1.5}>
                <Avatar sx={{ width: 28, height: 28, bgcolor: 'primary.main' }}><Group sx={{ fontSize: 15 }} /></Avatar>
                <Typography variant="body2" fontWeight={600}>{m.student?.firstName} {m.student?.lastName}</Typography>
              </Box>
              {m.director && <Typography variant="caption" color="text.secondary" display="block" mt={0.5}>Directeur : {m.director.firstName} {m.director.lastName}</Typography>}
              {m.defenseDate && <Typography variant="body2" mt={1}><Event sx={{ fontSize: 15, verticalAlign: 'middle', mr: 0.5 }} />Soutenance : {formatDate(m.defenseDate)}</Typography>}
              {m.jury && <Typography variant="caption" color="text.secondary" display="block" mt={0.5}>Jury : {m.jury}</Typography>}
              {m.decision && <Typography variant="body2" color="text.secondary" mt={1} sx={{ fontStyle: 'italic' }}>Décision : {m.decision}</Typography>}

              <Box display="flex" justifyContent="flex-end" gap={0.5} mt={1.5}>
                <IconButton size="small" color="primary" onClick={() => openEdit(m)}><Edit fontSize="small" /></IconButton>
                <IconButton size="small" color="error" onClick={() => handleDelete(m.id)}><Delete fontSize="small" /></IconButton>
              </Box>
            </Card>
          </Grid>
        ))}
        {rows.length === 0 && (
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary" textAlign="center" py={4}>Aucun mémoire enregistré.</Typography>
          </Grid>
        )}
      </Grid>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editing ? 'Modifier le mémoire' : 'Nouveau mémoire'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={12}><TextField select fullWidth size="small" label="Étudiant" value={form.studentId} onChange={(e) => setForm((f) => ({ ...f, studentId: e.target.value }))}>
              {students.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.firstName} {s.lastName} — {s.matricule}</MenuItem>)}
            </TextField></Grid>
            <Grid item xs={12}><TextField fullWidth size="small" label="Sujet" value={form.subject} onChange={(e) => setForm((f) => ({ ...f, subject: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField select fullWidth size="small" label="Directeur" value={form.directorId} onChange={(e) => setForm((f) => ({ ...f, directorId: e.target.value }))}>
              <MenuItem value="">Aucun</MenuItem>
              {teachers.map((t) => <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>)}
            </TextField></Grid>
            <Grid item xs={6}><TextField select fullWidth size="small" label="Statut" value={form.status} onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}>
              {STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </TextField></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" type="date" label="Soutenance" value={form.defenseDate} onChange={(e) => setForm((f) => ({ ...f, defenseDate: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Lieu" value={form.defenseLocation} onChange={(e) => setForm((f) => ({ ...f, defenseLocation: e.target.value }))} /></Grid>
            <Grid item xs={12}><TextField fullWidth size="small" label="Jury" value={form.jury} onChange={(e) => setForm((f) => ({ ...f, jury: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" type="number" inputProps={{ min: 0, max: 20, step: 0.25 }} label="Note /20" value={form.grade} onChange={(e) => setForm((f) => ({ ...f, grade: e.target.value }))} /></Grid>
            <Grid item xs={6}><TextField fullWidth size="small" label="Décision" value={form.decision} onChange={(e) => setForm((f) => ({ ...f, decision: e.target.value }))} /></Grid>
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