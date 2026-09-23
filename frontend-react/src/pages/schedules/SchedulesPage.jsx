import { useState, useEffect } from 'react'
import {
  Grid,
  TextField,
  MenuItem,
  Button,
  Card,
  Typography,
  Box,
  Tab,
  Tabs,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow
} from '@mui/material'
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { scheduleApi, classApi, subjectApi, teacherApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { DAYS_FR, DAY_KEYS } from '../../utils/format'

const TIMES = ['07:30', '08:00', '08:30', '09:30', '10:30', '11:30', '13:00', '14:00', '15:00', '16:00']

const empty = {
  dayOfWeek: 'MONDAY', startTime: '07:30', endTime: '08:30',
  classId: '', subjectId: '', teacherId: '', roomId: '',
}

/**
 * Emploi du temps : vues classe / enseignant / salle + détection de conflits.
 */
export default function SchedulesPage() {
  const { success, error: toastError } = useToast()
  const [view, setView] = useState('class')
  const [filterId, setFilterId] = useState('')
  const [classes, setClasses] = useState([])
  const [teachers, setTeachers] = useState([])
  const [rooms, setRooms] = useState([])
  const [subjects, setSubjects] = useState([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [schedule, setSchedule] = useState([])

  const loadRefs = async () => {
    const [c, t, r, s] = await Promise.all([
      classApi.all(), teacherApi.search({ page: 0, size: 500 }),
      classApi.rooms(), subjectApi.all(),
    ])
    setClasses(c.data.data)
    setTeachers(t.data.data.content)
    setRooms(r.data.data)
    setSubjects(s.data.data)
  }

  const loadSchedule = async () => {
    try {
      let res
      if (!filterId) res = await scheduleApi.all()
      else if (view === 'class') res = await scheduleApi.byClass(filterId)
      else if (view === 'teacher') res = await scheduleApi.byTeacher(filterId)
      else res = await scheduleApi.byRoom(filterId)
      setSchedule(res.data.data)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    loadRefs()
  }, [])

  useEffect(() => {
    loadSchedule()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, filterId])

  // Organisation du tableau : lignes = jours, colonnes = créneaux
  const grid = DAYS_FR.map((day, idx) => {
    const dayKey = Object.keys(DAY_KEYS).find((k) => DAY_KEYS[k] === idx)
    return {
      day,
      slots: TIMES.map((time) => {
        return schedule.find(
          (s) => s.dayOfWeek === dayKey && String(s.startTime).slice(0, 5) === time,
        )
      }),
    }
  })

  const handleSubmit = async (values) => {
    const payload = { ...values, classId: Number(values.classId), subjectId: Number(values.subjectId), teacherId: Number(values.teacherId), roomId: values.roomId ? Number(values.roomId) : null }
    try {
      if (editing) {
        await scheduleApi.update(editing.id, payload)
        success('Créneau modifié')
      } else {
        await scheduleApi.create(payload)
        success('Créneau créé (aucun conflit)')
      }
      setDialogOpen(false)
      loadSchedule()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async (slot) => {
    try {
      await scheduleApi.remove(slot.id)
      success('Créneau supprimé')
      loadSchedule()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Emploi du temps"
        subtitle="Génération avec détection automatique des conflits"
        actionLabel="Nouveau créneau"
        onAction={() => { setEditing(null); setDialogOpen(true) }}
      />

      <Grid container spacing={2} mb={3}>
        <Grid item xs={12} md={6}>
          <Tabs value={view} onChange={(_, v) => { setView(v); setFilterId('') }}>
            <Tab label="Par classe" value="class" />
            <Tab label="Par enseignant" value="teacher" />
            <Tab label="Par salle" value="room" />
          </Tabs>
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField
            select size="small" fullWidth
            label={view === 'class' ? 'Classe' : view === 'teacher' ? 'Enseignant' : 'Salle'}
            value={filterId}
            onChange={(e) => setFilterId(e.target.value)}
          >
            <MenuItem value="">Tous</MenuItem>
            {view === 'class' && classes.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
            {view === 'teacher' && teachers.map((t) => <MenuItem key={t.id} value={t.id}>{t.firstName} {t.lastName}</MenuItem>)}
            {view === 'room' && rooms.map((r) => <MenuItem key={r.id} value={r.id}>{r.name}</MenuItem>)}
          </TextField>
        </Grid>
      </Grid>

      {/* Grille */}
      <Card sx={{ borderRadius: '16px', overflow: 'auto', border: '1px solid', borderColor: 'divider' }}>
        <Table size="small" sx={{ minWidth: 900 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Jour</TableCell>
              {TIMES.map((t) => (
                <TableCell key={t} align="center" sx={{ fontWeight: 700 }}>{t}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {grid.map((row) => (
              <TableRow key={row.day} hover>
                <TableCell sx={{ fontWeight: 600 }}>{row.day}</TableCell>
                {row.slots.map((slot, i) => (
                  <TableCell key={i} sx={{ p: 0.8, verticalAlign: 'top' }}>
                    {slot ? (
                      <Box
                        sx={{
                          bgcolor: '#fce7f3',
                          color: '#9d174d',
                          borderRadius: '10px',
                          p: 1,
                          minHeight: 52,
                          border: '1px solid',
                          borderColor: '#f472b6',
                          transition: 'transform 160ms ease, box-shadow 160ms ease',
                          '&:hover': { transform: 'translateY(-2px)', boxShadow: '0 6px 14px rgba(236,72,153,0.35)' },
                        }}
                      >
                        <Box display="flex" alignItems="center" justifyContent="space-between" gap={0.5} mb={0.5}>
                          <Chip
                            size="small"
                            label={`${String(slot.startTime).slice(0, 5)} – ${String(slot.endTime).slice(0, 5)}`}
                            sx={{ fontSize: 9.5, height: 18, bgcolor: 'rgba(255,255,255,0.55)', color: '#9d174d', fontWeight: 700, fontFamily: 'monospace' }}
                          />
                          <Chip
                            size="small"
                            label="Suppr."
                            sx={{ fontSize: 10, height: 20, bgcolor: 'rgba(255,255,255,0.45)', color: '#9d174d', cursor: 'pointer' }}
                            onClick={() => handleDelete(slot)}
                          />
                        </Box>
                        <Typography fontWeight={700} fontSize={11.5} noWrap>{slot.subjectName}</Typography>
                        <Typography fontSize={10.5} noWrap>{slot.teacherName}</Typography>
                        <Typography fontSize={10.5} noWrap>{slot.className}{slot.roomName ? ` • ${slot.roomName}` : ''}</Typography>
                      </Box>
                    ) : null}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      {/* Dialog création */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <Formik
          initialValues={editing ? {
            dayOfWeek: editing.dayOfWeek, startTime: editing.startTime, endTime: editing.endTime,
            classId: String(editing.classId), subjectId: String(editing.subjectId),
            teacherId: String(editing.teacherId), roomId: editing.roomId ? String(editing.roomId) : '',
          } : empty}
          validationSchema={Yup.object({
            classId: Yup.string().required('La classe est requise'),
            subjectId: Yup.string().required('La matière est requise'),
            teacherId: Yup.string().required("L'enseignant est requis"),
          })}
          onSubmit={handleSubmit}
          enableReinitialize
        >
          {({ values, handleChange }) => (
            <Form>
              <DialogTitle>{editing ? 'Modifier le créneau' : 'Nouveau créneau'}</DialogTitle>
              <DialogContent>
                <Grid container spacing={2} mt={0.5}>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Jour" name="dayOfWeek" value={values.dayOfWeek} onChange={handleChange}>
                      {Object.keys(DAY_KEYS).map((k) => (
                        <MenuItem key={k} value={k}>{DAYS_FR[DAY_KEYS[k]]}</MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={6}>
                    <TextField select fullWidth label="Début" name="startTime" value={values.startTime} onChange={handleChange}>
                      {TIMES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={6}>
                    <TextField select fullWidth label="Fin" name="endTime" value={values.endTime} onChange={handleChange}>
                      {TIMES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Classe" name="classId" value={values.classId} onChange={handleChange}>
                      {classes.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Matière" name="subjectId" value={values.subjectId} onChange={handleChange}>
                      {subjects.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.name}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Enseignant" name="teacherId" value={values.teacherId} onChange={handleChange}>
                      {teachers.map((t) => <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Salle" name="roomId" value={values.roomId} onChange={handleChange}>
                      <MenuItem value="">Aucune</MenuItem>
                      {rooms.map((r) => <MenuItem key={r.id} value={String(r.id)}>{r.name}</MenuItem>)}
                    </TextField>
                  </Grid>
                </Grid>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
                <Button type="submit" variant="contained">{editing ? 'Enregistrer' : 'Créer'}</Button>
              </DialogActions>
            </Form>
          )}
        </Formik>
      </Dialog>
    </>
  )
}