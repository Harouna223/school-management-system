import { useState, useEffect } from 'react'
import {
  Grid,
  TextField,
  MenuItem,
  Button,
  Card,
  Typography,
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tabs,
  Tab
} from '@mui/material'
import { Check, Close, Schedule, WhatsApp as WhatsAppIcon } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { attendanceApi, classApi, studentApi, teacherApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

const STATUSES = ['PRESENT', 'ABSENT', 'LATE']

/**
 * Pointage des présences : élèves (classe + date) et enseignants (date).
 */
export default function AttendancesPage() {
  const { success, error: toastError } = useToast()
  const [tab, setTab] = useState('students')

  // --- Élèves ---
  const [classes, setClasses] = useState([])
  const [classId, setClassId] = useState('')
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [students, setStudents] = useState([])
  const [entries, setEntries] = useState({})
  const [savedRecords, setSavedRecords] = useState([])
  const [justifyOpen, setJustifyOpen] = useState(null)
  const [justification, setJustification] = useState('')
  const [waAlerts, setWaAlerts] = useState([])

  // --- Enseignants ---
  const [teachers, setTeachers] = useState([])
  const [teacherEntries, setTeacherEntries] = useState({})
  const [teacherDate, setTeacherDate] = useState(new Date().toISOString().slice(0, 10))

  useEffect(() => {
    classApi.all().then((r) => setClasses(r.data.data)).catch(() => {})
  }, [])

  const loadStudents = async () => {
    if (!classId) return
    try {
      const { data } = await studentApi.search({ classId, page: 0, size: 200 })
      const list = data.data.content
      setStudents(list)
      const initial = {}
      list.forEach((s) => { initial[s.id] = 'PRESENT' })
      setEntries(initial)
      const rec = await attendanceApi.byClassAndDate(classId, date)
      if (rec.data.data.length > 0) {
        const loaded = {}
        rec.data.data.forEach((a) => { loaded[a.studentId] = a.status })
        setEntries(loaded)
      }
      setSavedRecords(rec.data.data)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    if (tab === 'students') loadStudents()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [classId, date, tab])

  const handleSave = async () => {
    if (!classId) return
    try {
      const res = await attendanceApi.record({
        classId: Number(classId),
        date,
        entries: students.map((s) => ({ studentId: s.id, status: entries[s.id] })),
      })
      success('Présences enregistrées')
      const alerts = res.data.data?.alerts || []
      setWaAlerts(alerts)
      alerts.forEach((alert, i) => {
        setTimeout(() => window.open(alert.waLink, '_blank'), i * 800)
      })
      loadStudents()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleJustify = async () => {
    if (!justifyOpen?.id) return
    try {
      await attendanceApi.justify(justifyOpen.id, justification)
      success('Absence justifiée')
      setJustifyOpen(null)
      setJustification('')
      loadStudents()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  // --- Enseignants ---
  const loadTeachers = async () => {
    try {
      const { data } = await teacherApi.search({ page: 0, size: 500 })
      const list = data.data.content
      setTeachers(list)
      const initial = {}
      list.forEach((t) => { initial[t.id] = 'PRESENT' })
      setTeacherEntries(initial)
      const rec = await attendanceApi.teachersByDate(teacherDate)
      if (rec.data.data.length > 0) {
        const loaded = {}
        rec.data.data.forEach((a) => { loaded[a.teacherId] = a.status })
        setTeacherEntries(loaded)
      }
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    if (tab === 'teachers') loadTeachers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [teacherDate, tab])

  const handleSaveTeachers = async () => {
    try {
      await attendanceApi.recordTeachers({
        date: teacherDate,
        entries: teachers.map((t) => ({ teacherId: t.id, status: teacherEntries[t.id] })),
      })
      success('Présences des enseignants enregistrées')
      loadTeachers()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader title="Présences" subtitle="Pointage quotidien des élèves et des enseignants" />

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 3 }}>
        <Tab label="Élèves" value="students" />
        <Tab label="Enseignants" value="teachers" />
      </Tabs>

      {/* ---- Onglet Élèves ---- */}
      {tab === 'students' && (
        <>
          <FilterCard mb={3}>
            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <TextField select fullWidth label="Classe" value={classId} onChange={(e) => setClassId(e.target.value)}>
                  <MenuItem value="">Sélectionner</MenuItem>
                  {classes.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12} md={3}>
                <TextField fullWidth type="date" label="Date" value={date} onChange={(e) => setDate(e.target.value)} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid item xs={12} md={3}>
                <Button variant="contained" onClick={loadStudents} disabled={!classId}>Charger la classe</Button>
              </Grid>
              <Grid item xs={12} md={2} display="flex" justifyContent="flex-end">
                <Button variant="contained" color="success" onClick={handleSave} disabled={!classId}>
                  Enregistrer
                </Button>
              </Grid>
            </Grid>
          </FilterCard>

          {waAlerts.length > 0 && (
            <Card sx={{ mb: 2, p: 2, borderRadius: '14px', border: '1px solid', borderColor: '#a7f3d0', bgcolor: '#f0fdf4' }}>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <WhatsAppIcon color="success" />
                <Typography variant="subtitle2" fontWeight={700} color="#065f46">
                  {waAlerts.length} parent(s) informé(s) — WhatsApp va s'ouvrir avec le message pré-rempli
                </Typography>
              </Box>
              <Box display="flex" flexWrap="wrap" gap={1}>
                {waAlerts.map((alert, i) => (
                  <Button key={i} size="small" variant="outlined" color="success"
                    startIcon={<WhatsAppIcon />} onClick={() => window.open(alert.waLink, '_blank')}>
                    {alert.phone}
                  </Button>
                ))}
              </Box>
            </Card>
          )}

          <Paper>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Élève</TableCell>
                    <TableCell>Matricule</TableCell>
                    <TableCell align="center">Présent</TableCell>
                    <TableCell align="center">Absent</TableCell>
                    <TableCell align="center">Retard</TableCell>
                    <TableCell>Justification</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {students.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: 4 }} color="text.secondary">
                        Sélectionnez une classe pour pointer les présences
                      </TableCell>
                    </TableRow>
                  )}
                  {students.map((s) => {
                    const status = entries[s.id] || 'PRESENT'
                    return (
                      <TableRow key={s.id}>
                        <TableCell fontWeight={600}>{s.firstName} {s.lastName}</TableCell>
                        <TableCell>{s.matricule}</TableCell>
                        {STATUSES.map((st) => (
                          <TableCell key={st} align="center">
                            <Chip
                              icon={st === 'PRESENT' ? <Check /> : st === 'ABSENT' ? <Close /> : <Schedule />}
                              label={st === 'PRESENT' ? 'P' : st === 'ABSENT' ? 'A' : 'R'}
                              color={status === st ? (st === 'PRESENT' ? 'success' : st === 'ABSENT' ? 'error' : 'warning') : 'default'}
                              variant={status === st ? 'filled' : 'outlined'}
                              onClick={() => setEntries({ ...entries, [s.id]: st })}
                              sx={{ cursor: 'pointer' }}
                            />
                          </TableCell>
                        ))}
                        <TableCell>
                          {status === 'ABSENT' || status === 'LATE' ? (() => {
                            const record = savedRecords.find((r) => r.studentId === s.id)
                            return (
                              <Button size="small" disabled={!record?.id}
                                title={record?.id ? '' : 'Enregistrez d\'abord les présences'}
                                onClick={() => setJustifyOpen({
                                  id: record?.id,
                                  student: s,
                                })}>
                                Justifier
                              </Button>
                            )
                          })() : (
                            <Typography variant="body2" color="text.secondary">—</Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}

      {/* ---- Onglet Enseignants ---- */}
      {tab === 'teachers' && (
        <>
          <FilterCard mb={3}>
            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <TextField fullWidth type="date" label="Date" value={teacherDate}
                  onChange={(e) => setTeacherDate(e.target.value)} InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid item xs={12} md={3}>
                <Button variant="contained" onClick={loadTeachers}>Charger les enseignants</Button>
              </Grid>
              <Grid item xs={12} md={3} />
              <Grid item xs={12} md={2} display="flex" justifyContent="flex-end">
                <Button variant="contained" color="success" onClick={handleSaveTeachers}>
                  Enregistrer
                </Button>
              </Grid>
            </Grid>
          </FilterCard>

          <Paper>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Enseignant</TableCell>
                    <TableCell>Matricule</TableCell>
                    <TableCell align="center">Présent</TableCell>
                    <TableCell align="center">Absent</TableCell>
                    <TableCell align="center">Retard</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {teachers.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ py: 4 }} color="text.secondary">
                        Aucun enseignant enregistré
                      </TableCell>
                    </TableRow>
                  )}
                  {teachers.map((t) => {
                    const status = teacherEntries[t.id] || 'PRESENT'
                    return (
                      <TableRow key={t.id}>
                        <TableCell fontWeight={600}>{t.firstName} {t.lastName}</TableCell>
                        <TableCell>{t.employeeNo}</TableCell>
                        {STATUSES.map((st) => (
                          <TableCell key={st} align="center">
                            <Chip
                              icon={st === 'PRESENT' ? <Check /> : st === 'ABSENT' ? <Close /> : <Schedule />}
                              label={st === 'PRESENT' ? 'P' : st === 'ABSENT' ? 'A' : 'R'}
                              color={status === st ? (st === 'PRESENT' ? 'success' : st === 'ABSENT' ? 'error' : 'warning') : 'default'}
                              variant={status === st ? 'filled' : 'outlined'}
                              onClick={() => setTeacherEntries({ ...teacherEntries, [t.id]: st })}
                              sx={{ cursor: 'pointer' }}
                            />
                          </TableCell>
                        ))}
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}

      <Dialog open={Boolean(justifyOpen)} onClose={() => setJustifyOpen(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Justifier l'absence de {justifyOpen?.student?.firstName}</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth multiline rows={3} sx={{ mt: 1 }}
            label="Motif de justification"
            value={justification}
            onChange={(e) => setJustification(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setJustifyOpen(null)}>Annuler</Button>
          <Button variant="contained" onClick={handleJustify} disabled={!justification.trim()}>Justifier</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}