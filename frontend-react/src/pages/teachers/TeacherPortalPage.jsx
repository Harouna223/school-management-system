import { useEffect, useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  Grid, Card, CardContent, Typography, Box, Tabs, Tab, Chip, Button,
  Table, TableHead, TableRow, TableCell, TableBody,
  TextField, MenuItem, Dialog, DialogTitle, DialogContent, DialogActions, Avatar, IconButton,
} from '@mui/material'
import {
  CalendarMonth as ScheduleIcon,
  Group as ClassesIcon,
  EditNote as GradesIcon,
  Save as SaveIcon,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import StatusChip from '../../components/StatusChip'
import Loader from '../../components/Loader'
import EmptyState from '../../components/EmptyState'
import { useToast } from '../../hooks/useToast'
import { myApi, examApi, studentApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { primaryRole } from '../../utils/auth'
import { initials, formatGrade, DAYS_FR, DAY_KEYS } from '../../utils/format'

const TERM_LABEL = { T1: '1er Trimestre', T2: '2e Trimestre', T3: '3e Trimestre' }

/**
 * Espace enseignant : emploi du temps, classes, saisie des notes (rôle ENSEIGNANT).
 */
export default function TeacherPortalPage() {
  const role = primaryRole()
  const { success, error: toastError } = useToast()

  const [profile, setProfile] = useState(null)
  const [myClasses, setMyClasses] = useState([])
  const [schedule, setSchedule] = useState([])
  const [students, setStudents] = useState([])
  const [exams, setExams] = useState([])
  const [selectedClass, setSelectedClass] = useState('')
  const [selectedExam, setSelectedExam] = useState('')
  const [gradeRows, setGradeRows] = useState([])
  const [draftGrades, setDraftGrades] = useState({})
  const [tab, setTab] = useState(0)
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [currentStudent, setCurrentStudent] = useState(null)
  const [gradeValue, setGradeValue] = useState('')

  useEffect(() => {
    myApi.teacherProfile().then((res) => setProfile(res.data.data || null)).catch(() => {})
    myApi.teacherSchedule().then((res) => setSchedule(res.data.data || [])).catch(() => {})
    myApi.teacherClasses()
      .then((res) => setMyClasses(res.data.data || []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (tab === 1 && myClasses.length > 0 && !selectedClass) {
      setSelectedClass(String(myClasses[0].classId))
    }
  }, [tab, myClasses, selectedClass])

  useEffect(() => {
    if (!selectedClass) return
    studentApi.search({ classId: Number(selectedClass), size: 500 })
      .then((r) => setStudents(r.data.data.content || []))
      .catch(() => {})
    examApi.byClass(Number(selectedClass))
      .then((r) => setExams(r.data.data || []))
      .catch(() => {})
  }, [selectedClass])

  useEffect(() => {
    if (!selectedExam) {
      setGradeRows([])
      setDraftGrades({})
      return
    }
    examApi.gradesByExam(selectedExam)
      .then((r) => {
        setGradeRows(r.data.data || [])
        const drafts = {}
        ;(r.data.data || []).forEach((g) => { drafts[g.studentId] = String(g.value ?? '') })
        setDraftGrades(drafts)
      })
      .catch(() => {})
  }, [selectedExam])

  const saveInlineGrade = async (studentId, studentName) => {
    const raw = draftGrades[studentId]
    if (raw === undefined || raw === '') return
    const value = Number(raw)
    if (Number.isNaN(value) || value < 0 || value > 20) {
      toastError('La note doit être comprise entre 0 et 20')
      return
    }
    try {
      await examApi.saveGrade({
        studentId,
        examId: Number(selectedExam),
        value,
        appreciation: value >= 10 ? 'Acquis' : 'Non acquis',
      })
      success(`Note de ${studentName} enregistrée (${formatGrade(value)})`)
      examApi.gradesByExam(selectedExam)
        .then((r) => {
          setGradeRows(r.data.data || [])
          const drafts = {}
          ;(r.data.data || []).forEach((g) => { drafts[g.studentId] = String(g.value ?? '') })
          setDraftGrades(drafts)
        })
        .catch(() => {})
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const scheduleByDay = useMemo(() => {
    const map = {}
    DAYS_FR.forEach((_, i) => { map[i] = [] })
    schedule.forEach((s) => {
      const idx = DAY_KEYS[s.dayOfWeek]
      if (idx != null) map[idx].push(s)
    })
    return map
  }, [schedule])

  const selectedClassInfo = useMemo(
    () => myClasses.find((c) => String(c.classId) === String(selectedClass)),
    [myClasses, selectedClass]
  )
  const classExams = useMemo(
    () => exams.filter((e) => String(e.classId) === String(selectedClass)),
    [exams, selectedClass]
  )

  if (role !== 'ENSEIGNANT') {
    return <Navigate to="/dashboard" replace />
  }

  const openGradeDialog = (student) => {
    setCurrentStudent(student)
    setGradeValue('')
    setDialogOpen(true)
  }

  const handleSaveGrade = async () => {
    try {
      await examApi.saveGrade({
        studentId: currentStudent.studentId,
        examId: Number(selectedExam),
        value: Number(gradeValue),
        appreciation: gradeValue >= 10 ? 'Acquis' : 'Non acquis',
      })
      success(`Note ${formatGrade(gradeValue)} enregistrée`)
      setDialogOpen(false)
      const { data } = await examApi.gradesByExam(selectedExam)
      setGradeRows(data.data || [])
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Mon espace"
        subtitle="Votre emploi du temps, vos classes et la saisie de vos notes"
      />

      {loading ? (
        <Loader />
      ) : (
        <>
          {profile && (
            <Card sx={{ mb: 3 }}>
              <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                <Avatar src={profile.photo} sx={{ width: 56, height: 56 }}>
                  {initials(profile.firstName, profile.lastName)}
                </Avatar>
                <Box flex={1} minWidth={200}>
                  <Typography variant="h6">{profile.firstName} {profile.lastName}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {profile.employeeNo} · {profile.contractType} · {profile.email || profile.phone}
                  </Typography>
                </Box>
                <Box textAlign="center">
                  <Typography variant="h5" fontWeight={800}>{myClasses.length}</Typography>
                  <Typography variant="caption" color="text.secondary">Classes</Typography>
                </Box>
                <Box textAlign="center">
                  <Typography variant="h5" fontWeight={800}>{schedule.length}</Typography>
                  <Typography variant="caption" color="text.secondary">Cours / semaine</Typography>
                </Box>
                <StatusChip status={profile.status} />
              </CardContent>
            </Card>
          )}

          <Card>
            <CardContent>
              <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
                <Tab icon={<ScheduleIcon />} iconPosition="start" label="Mon emploi du temps" />
                <Tab icon={<ClassesIcon />} iconPosition="start" label="Mes classes" />
                <Tab icon={<GradesIcon />} iconPosition="start" label="Saisie des notes" />
              </Tabs>

              {/* Emploi du temps */}
              {tab === 0 && (
                schedule.length === 0 ? (
                  <EmptyState message="Aucun cours planifié pour vous." />
                ) : (
                  <Grid container spacing={2}>
                    {DAYS_FR.map((day, i) => (
                      <Grid item xs={12} sm={6} md={4} key={day}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" fontWeight={700} mb={1}>{day}</Typography>
                            {scheduleByDay[i].length === 0 ? (
                              <Typography variant="body2" color="text.secondary">—</Typography>
                            ) : (
                              scheduleByDay[i].map((s) => (
                                <Box key={s.id} mb={1}>
                                  <Typography variant="body2" fontWeight={600}>
                                    {s.startTime} – {s.endTime}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    {s.subjectName} · {s.className}
                                  </Typography>
                                  <Typography variant="caption" color="text.disabled">{s.roomName}</Typography>
                                </Box>
                              ))
                            )}
                          </CardContent>
                        </Card>
                      </Grid>
                    ))}
                  </Grid>
                )
              )}

              {/* Mes classes */}
              {tab === 1 && (
                myClasses.length === 0 ? (
                  <EmptyState message="Aucune classe ne vous est affectée." />
                ) : (
                  <>
                    <Box display="flex" gap={2} mb={2} flexWrap="wrap">
                      <TextField
                        select fullWidth size="small" label="Classe" value={selectedClass}
                        onChange={(e) => setSelectedClass(e.target.value)} sx={{ maxWidth: 360 }}
                      >
                        {[...new Map(myClasses.map((c) => [c.classId, c])).values()].map((c) => (
                          <MenuItem key={c.classId} value={c.classId}>{c.className}</MenuItem>
                        ))}
                      </TextField>
                      <Box display="flex" gap={1} flexWrap="wrap" alignItems="center">
                        {myClasses
                          .filter((c) => String(c.classId) === String(selectedClass))
                          .map((c) => (
                            <Chip key={c.assignmentId} size="small" variant="outlined" label={c.subjectName} />
                          ))}
                      </Box>
                    </Box>
                    {students.length === 0 ? (
                      <EmptyState message="Aucun élève dans cette classe." />
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Élève</TableCell>
                            <TableCell>Matricule</TableCell>
                            <TableCell>Statut</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {students.map((s) => (
                            <TableRow key={s.id}>
                              <TableCell>
                                <Box display="flex" alignItems="center" gap={1.5}>
                                  <Avatar src={s.photo} sx={{ width: 32, height: 32 }}>
                                    {initials(s.firstName, s.lastName)}
                                  </Avatar>
                                  <Typography fontWeight={600}>{s.firstName} {s.lastName}</Typography>
                                </Box>
                              </TableCell>
                              <TableCell>{s.matricule}</TableCell>
                              <TableCell><StatusChip status={s.status} /></TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </>
                )
              )}

              {/* Saisie des notes */}
              {tab === 2 && (
                <>
                  <Grid container spacing={2} mb={2}>
                    <Grid item xs={12} md={4}>
                      <TextField
                        select fullWidth size="small" label="Classe" value={selectedClass}
                        onChange={(e) => setSelectedClass(e.target.value)}
                      >
                        <MenuItem value="">Sélectionner</MenuItem>
                        {[...new Map(myClasses.map((c) => [c.classId, c])).values()].map((c) => (
                          <MenuItem key={c.classId} value={c.classId}>{c.className}</MenuItem>
                        ))}
                      </TextField>
                    </Grid>
                    <Grid item xs={12} md={5}>
                      <TextField
                        select fullWidth size="small" label="Évaluation" value={selectedExam}
                        onChange={(e) => setSelectedExam(e.target.value)}
                      >
                        <MenuItem value="">Sélectionner une évaluation</MenuItem>
                        {classExams.map((e) => (
                          <MenuItem key={e.id} value={e.id}>
                            {e.name} — {e.subjectName} ({TERM_LABEL[e.term] || e.term})
                          </MenuItem>
                        ))}
                      </TextField>
                    </Grid>
                  </Grid>
                  {selectedClassInfo && (
                    <Typography variant="body2" color="text.secondary" mb={1}>
                      Matières enseignées dans cette classe :{' '}
                      {myClasses.filter((c) => String(c.classId) === String(selectedClass)).map((c) => c.subjectName).join(', ')}
                    </Typography>
                  )}
                  {!selectedExam ? (
                    <EmptyState message="Sélectionnez une évaluation pour saisir les notes." />
                  ) : gradeRows.length === 0 && students.length === 0 ? (
                    <EmptyState message="Aucun élève dans cette classe." />
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Élève</TableCell>
                          <TableCell>Matricule</TableCell>
                          <TableCell>Note /20</TableCell>
                          <TableCell>Statut</TableCell>
                          <TableCell align="right">Action</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {students.map((s) => {
                          const g = gradeRows.find((r) => r.studentId === s.id)
                          const raw = draftGrades[s.id] ?? ''
                          return (
                            <TableRow key={s.id}>
                              <TableCell fontWeight={600}>{s.firstName} {s.lastName}</TableCell>
                              <TableCell>{s.matricule}</TableCell>
                              <TableCell>
                                <TextField
                                  size="small" type="number" inputProps={{ min: 0, max: 20, step: 0.25 }}
                                  placeholder={g ? String(g.value) : '—'}
                                  value={raw}
                                  onChange={(e) => setDraftGrades((d) => ({ ...d, [s.id]: e.target.value }))}
                                  sx={{ width: 90 }}
                                />
                              </TableCell>
                              <TableCell>
                                {g ? <Chip size="small" color={g.value >= 10 ? 'success' : 'error'} label={g.value >= 10 ? 'Acquis' : 'Non acquis'} /> : <Typography variant="caption" color="text.secondary">Non saisie</Typography>}
                              </TableCell>
                              <TableCell align="right">
                                <IconButton size="small" color="primary" disabled={raw === ''}
                                  onClick={() => saveInlineGrade(s.id, `${s.firstName} ${s.lastName}`)}
                                  title="Enregistrer la note">
                                  <SaveIcon fontSize="small" />
                                </IconButton>
                              </TableCell>
                            </TableRow>
                          )
                        })}
                      </TableBody>
                    </Table>
                  )}
                </>
              )}
            </CardContent>
          </Card>
        </>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Note de l'élève</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus fullWidth type="number" size="small" label="Note / 20" value={gradeValue}
            onChange={(e) => setGradeValue(e.target.value)} inputProps={{ min: 0, max: 20, step: 0.25 }}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" disabled={gradeValue === ''} onClick={handleSaveGrade}>Enregistrer</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}