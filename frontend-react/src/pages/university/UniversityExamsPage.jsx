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
  IconButton
} from '@mui/material'
import { Add, Delete, Event, MeetingRoom, Person } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { universityExamApi, lmdApi, teacherApi, classApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate } from '../../utils/format'

/**
 * Calendrier des examens universitaires.
 */
export default function UniversityExamsPage() {
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [ues, setUes] = useState([])
  const [ecs, setEcs] = useState([])
  const [rooms, setRooms] = useState([])
  const [teachers, setTeachers] = useState([])
  const [filterDate, setFilterDate] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [form, setForm] = useState({
    courseUnit: '', room: '', supervisor: '', date: '', startTime: '08:00', endTime: '10:00',
    session: 1, groupName: '', semester: 'S1', notes: '',
  })

  const load = () => {
    universityExamApi.list({ date: filterDate || undefined })
      .then((r) => setRows(r.data.data || []))
      .catch(() => {})
  }

  useEffect(() => {
    lmdApi.ues('', 'S1').then((r) => setUes(r.data.data || [])).catch(() => {})
    classApi.rooms().then((r) => setRooms(r.data.data || [])).catch(() => {})
    teacherApi.search({ page: 0, size: 500 }).then((r) => setTeachers(r.data.data.content || [])).catch(() => {})
  }, [])

  useEffect(() => { load() }, [filterDate])

  const loadEcsForUe = async (ueId) => {
    if (!ueId) { setEcs([]); return }
    lmdApi.ecs(ueId).then((r) => setEcs(r.data.data || [])).catch(() => {})
  }

  const handleCreate = async () => {
    if (!form.courseUnit || !form.date) {
      toastError('EC et date sont obligatoires')
      return
    }
    try {
      await universityExamApi.create({
        courseUnit: { id: Number(form.courseUnit) },
        room: form.room ? { id: Number(form.room) } : undefined,
        supervisor: form.supervisor ? { id: Number(form.supervisor) } : undefined,
        date: form.date,
        startTime: `${form.startTime}:00`,
        endTime: `${form.endTime}:00`,
        session: Number(form.session),
        groupName: form.groupName || undefined,
        semester: form.semester,
        notes: form.notes || undefined,
      })
      success('Examen planifié')
      setDialogOpen(false)
      resetForm()
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async (id) => {
    try {
      await universityExamApi.delete(id)
      success('Examen supprimé')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const resetForm = () => setForm({
    courseUnit: '', room: '', supervisor: '', date: '', startTime: '08:00', endTime: '10:00',
    session: 1, groupName: '', semester: 'S1', notes: '',
  })

  return (
    <>
      <PageHeader
        title="Calendrier des examens"
        subtitle="Planification des épreuves universitaires — salles, surveillants, sessions"
        actions={[{ label: 'Planifier un examen', icon: <Add />, onClick: () => setDialogOpen(true) }]}
      />

      <Card sx={{ p: 2, borderRadius: '16px', mb: 3 }}>
        <TextField size="small" type="date" label="Filtrer par date" value={filterDate}
          onChange={(e) => setFilterDate(e.target.value)} sx={{ minWidth: 200 }} />
      </Card>

      <Grid container spacing={2.5}>
        {rows.map((ex) => (
          <Grid item xs={12} md={6} lg={4} key={ex.id}>
            <Card sx={{ p: 2.5, borderRadius: '16px', border: '1px solid', borderColor: 'divider' }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Chip size="small" color="primary" label={`Session ${ex.session}`} />
                <Typography variant="caption" color="text.secondary">{ex.semester || ''}</Typography>
              </Box>
              <Typography variant="h6" fontWeight={700}>{ex.courseUnit?.name || '—'}</Typography>
              <Typography variant="caption" color="text.secondary">{ex.courseUnit?.code}</Typography>

              <Box display="flex" alignItems="center" gap={1} mt={1.5}>
                <Event color="action" sx={{ fontSize: 18 }} />
                <Typography variant="body2">{formatDate(ex.date)} — {ex.startTime} → {ex.endTime}</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                <MeetingRoom color="action" sx={{ fontSize: 18 }} />
                <Typography variant="body2">{ex.room?.name || 'Salle non affectée'}</Typography>
              </Box>
              {ex.supervisor && (
                <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                  <Person color="action" sx={{ fontSize: 18 }} />
                  <Typography variant="body2">Surveillant : {ex.supervisor.firstName} {ex.supervisor.lastName}</Typography>
                </Box>
              )}
              {ex.groupName && <Chip size="small" sx={{ mt: 1 }} label={`Groupe ${ex.groupName}`} variant="outlined" />}

              <Box display="flex" justifyContent="flex-end" mt={1.5}>
                <IconButton size="small" color="error" onClick={() => handleDelete(ex.id)} title="Supprimer">
                  <Delete fontSize="small" />
                </IconButton>
              </Box>
            </Card>
          </Grid>
        ))}
        {rows.length === 0 && (
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary" textAlign="center" py={4}>
              Aucun examen planifié{filterDate ? ' pour cette date' : ''}.
            </Typography>
          </Grid>
        )}
      </Grid>

      {/* Dialogue planification */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Planifier un examen</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={12}>
              <TextField select fullWidth size="small" label="UE"
                onChange={(e) => loadEcsForUe(e.target.value)}>
                <MenuItem value="">Sélectionner…</MenuItem>
                {ues.map((u) => <MenuItem key={u.id} value={String(u.id)}>{u.code} — {u.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField select fullWidth size="small" label="EC" value={form.courseUnit}
                onChange={(e) => setForm((f) => ({ ...f, courseUnit: e.target.value }))}>
                <MenuItem value="">Sélectionner…</MenuItem>
                {ecs.map((ec) => <MenuItem key={ec.id} value={String(ec.id)}>{ec.code} — {ec.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField select fullWidth size="small" label="Salle" value={form.room}
                onChange={(e) => setForm((f) => ({ ...f, room: e.target.value }))}>
                <MenuItem value="">Aucune</MenuItem>
                {rooms.map((r) => <MenuItem key={r.id} value={String(r.id)}>{r.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField select fullWidth size="small" label="Surveillant" value={form.supervisor}
                onChange={(e) => setForm((f) => ({ ...f, supervisor: e.target.value }))}>
                <MenuItem value="">Aucun</MenuItem>
                {teachers.map((t) => <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={4}>
              <TextField fullWidth size="small" type="date" label="Date" value={form.date}
                onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))} />
            </Grid>
            <Grid item xs={4}>
              <TextField fullWidth size="small" type="time" label="Début" value={form.startTime}
                onChange={(e) => setForm((f) => ({ ...f, startTime: e.target.value }))} />
            </Grid>
            <Grid item xs={4}>
              <TextField fullWidth size="small" type="time" label="Fin" value={form.endTime}
                onChange={(e) => setForm((f) => ({ ...f, endTime: e.target.value }))} />
            </Grid>
            <Grid item xs={4}>
              <TextField select fullWidth size="small" label="Session" value={form.session}
                onChange={(e) => setForm((f) => ({ ...f, session: e.target.value }))}>
                <MenuItem value={1}>Session 1</MenuItem>
                <MenuItem value={2}>Session 2 (rattrapage)</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={4}>
              <TextField fullWidth size="small" label="Semestre" value={form.semester}
                onChange={(e) => setForm((f) => ({ ...f, semester: e.target.value }))} />
            </Grid>
            <Grid item xs={4}>
              <TextField fullWidth size="small" label="Groupe" value={form.groupName}
                onChange={(e) => setForm((f) => ({ ...f, groupName: e.target.value }))} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Notes" multiline minRows={2} value={form.notes}
                onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleCreate}>Planifier</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}