import { useState, useEffect } from 'react'
import {
  Grid, TextField, MenuItem, Button, Card, Typography, Box, Chip,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Dialog, DialogTitle, DialogContent, DialogActions, Avatar, IconButton,
} from '@mui/material'
import { PictureAsPdf, Assignment, Leaderboard, EmojiEvents, Gavel, Save } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { examApi, classApi, studentApi, academicYearApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { downloadResponse } from '../../services/exportService'
import { formatGrade, toGradeOver20, initials } from '../../utils/format'

const RANK_COLORS = ['#f59e0b', '#94a3b8', '#b45309']

const DECISION_COLORS = {
  ADMIS: 'success',
  AJOURNE: 'warning',
  REDOUBLE: 'error',
}

function decisionLabel(decision) {
  return { ADMIS: 'Admis', AJOURNE: 'Ajourné', REDOUBLE: 'Redouble' }[decision] || '—'
}

/**
 * Tonus d'une note, exprimée sur son barème réel.
 * Le barème est ramené sur 20 avant comparaison : une note de 8/10 est un
 * « Acquis » (16/20) et non un « Insuffisant ».
 */
function gradeTone(value, maxValue = 20) {
  const sur20 = toGradeOver20(value, maxValue)
  if (sur20 == null) return { color: 'default', label: '—' }
  if (sur20 >= 14) return { color: 'primary', label: 'Excellent' }
  if (sur20 >= 10) return { color: 'success', label: 'Acquis' }
  if (sur20 >= 8) return { color: 'warning', label: 'Insuffisant' }
  return { color: 'error', label: 'Non acquis' }
}

/**
 * Notes : saisie par évaluation, moyennes, classement, bulletins.
 */
export default function GradesPage() {
  const { success, error: toastError } = useToast()
  const [classes, setClasses] = useState([])
  const [classId, setClassId] = useState('')
  const [term, setTerm] = useState('T1')
  const [exams, setExams] = useState([])
  const [selectedExam, setSelectedExam] = useState('')
  const [gradeRows, setGradeRows] = useState([])
  const [draftGrades, setDraftGrades] = useState({})
  const [ranking, setRanking] = useState([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [currentStudent, setCurrentStudent] = useState(null)
  const [gradeValue, setGradeValue] = useState('')
  const [classStudents, setClassStudents] = useState([])
  const [deliberations, setDeliberations] = useState([])
  const [deliberating, setDeliberating] = useState(false)
  // Année scolaire : les moyennes, classements et bulletins sont désormais
  // bornés à une année précise (Term seul mélangeait toutes les années).
  const [years, setYears] = useState([])
  const [yearId, setYearId] = useState('')

  useEffect(() => {
    classApi.all().then((r) => setClasses(r.data.data)).catch(() => {})
    academicYearApi.all()
      .then((r) => {
        const list = r.data.data || []
        setYears(list)
        const current = list.find((y) => y.current)
        if (current) setYearId(String(current.id))
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!classId) return
    const year = yearId ? Number(yearId) : undefined
    examApi.byClass(classId, year).then((r) => setExams(r.data.data || [])).catch(() => {})
    examApi.ranking(classId, term, year).then((r) => setRanking(r.data.data || [])).catch(() => {})
    studentApi.search({ classId, page: 0, size: 100 })
      .then((r) => setClassStudents(r.data.data.content || []))
      .catch(() => {})
    setSelectedExam('')
    setDialogOpen(false)
    setGradeRows([])
    setDraftGrades({})
    // Les délibérations affichées appartiennent à un couple (classe, trimestre,
    // année) : sans cette remise à zéro, les décisions du T1 restaient affichées
    // en T2 et le bouton « Procès-verbal » restait actif pour le mauvais PV.
    setDeliberations([])
  }, [classId, term, yearId])

  const loadGrades = async () => {
    if (!selectedExam) return
    try {
      const { data } = await examApi.gradesByExam(selectedExam)
      setGradeRows(data.data)
      const drafts = {}
      ;(data.data || []).forEach((g) => { drafts[g.studentId] = String(g.value ?? '') })
      setDraftGrades(drafts)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const gradeByStudent = (studentId) => gradeRows.find((g) => g.studentId === studentId)

  /** Barème de l'évaluation : celui déjà utilisé pour une note, sinon 20. */
  const baremeEvaluation = () => {
    const existante = gradeRows.find((g) => g.maxValue != null)
    return existante ? Number(existante.maxValue) : 20
  }

  const saveInlineGrade = async (student) => {
    const raw = draftGrades[student.id]
    if (raw === undefined || raw === '') return
    const value = Number(raw)
    const bareme = baremeEvaluation()
    // Bornes calculées sur le BARÈME de l'évaluation (une note /100 est légitime).
    if (!Number.isFinite(value) || value < 0 || value > bareme) {
      toastError(`La note doit être comprise entre 0 et ${bareme}`)
      return
    }
    try {
      await examApi.saveGrade({
        studentId: student.id,
        examId: Number(selectedExam),
        value,
        maxValue: bareme,
        appreciation: toGradeOver20(value, bareme) >= 10 ? 'Acquis' : 'Non acquis',
      })
      success(`Note de ${student.firstName} ${student.lastName} enregistrée (${formatGrade(value, bareme)})`)
      loadGrades()
      examApi.ranking(classId, term, yearId ? Number(yearId) : undefined).then((r) => setRanking(r.data.data || [])).catch(() => {})
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    loadGrades()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedExam])

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
      setGradeValue('')
      loadGrades()
      examApi.ranking(classId, term, yearId ? Number(yearId) : undefined).then((r) => setRanking(r.data.data || [])).catch(() => {})
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const generateBulletins = async () => {
    if (!classId) return
    try {
      await examApi.generateBulletins(classId, term, yearId ? Number(yearId) : undefined)
      success(`Bulletins du ${term} générés pour la classe`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const runDeliberation = async () => {
    if (!classId) return
    setDeliberating(true)
    try {
      const { data } = await examApi.deliberate(classId, term, yearId ? Number(yearId) : undefined)
      setDeliberations((data.data || []).slice()
        .sort((a, b) => (a.rank ?? Number.MAX_SAFE_INTEGER) - (b.rank ?? Number.MAX_SAFE_INTEGER)))
      success(`Délibération ${term} effectuée (${data.data.length} bulletins)`)
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setDeliberating(false)
    }
  }

  const downloadPv = async () => {
    if (!classId) return
    try {
      const res = await examApi.deliberationPv(classId, term, yearId ? Number(yearId) : undefined)
      await downloadResponse(res, `pv-deliberation-${term}.pdf`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const downloadBulletin = async (bulletin) => {
    try {
      const res = await examApi.bulletinPdf(bulletin.id)
      await downloadResponse(res, `bulletin-${bulletin.matricule}-${bulletin.term}.pdf`)
      success(`Bulletin de ${bulletin.studentName} téléchargé`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const downloadAllBulletins = async () => {
    if (!classId) return
    try {
      const res = await examApi.classBulletinsPdf(classId, term, yearId ? Number(yearId) : undefined)
      await downloadResponse(res, `bulletins-${term}-classe.pdf`)
      success('Tous les bulletins téléchargés dans un seul PDF')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader title="Notes & Bulletins" subtitle="Saisie des notes, moyennes et classements" />

      <FilterCard mb={3}>
        <Grid container spacing={2} alignItems="flex-end">
          <Grid item xs={12} md={3}>
            <TextField select fullWidth size="small" label="Classe" value={classId} onChange={(e) => setClassId(e.target.value)}>
              <MenuItem value="">Sélectionner</MenuItem>
              {classes.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField select fullWidth size="small" label="Trimestre" value={term} onChange={(e) => setTerm(e.target.value)}>
              <MenuItem value="T1">Trimestre 1</MenuItem>
              <MenuItem value="T2">Trimestre 2</MenuItem>
              <MenuItem value="T3">Trimestre 3</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12} md={2}>
            <TextField select fullWidth size="small" label="Année scolaire" value={yearId}
              onChange={(e) => setYearId(e.target.value)}
              helperText={years.length === 0 ? 'Aucune année configurée' : undefined}>
              <MenuItem value="">Année courante</MenuItem>
              {years.map((y) => (
                <MenuItem key={y.id} value={String(y.id)}>{y.label}{y.current ? ' (courante)' : ''}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField select fullWidth size="small" label="Évaluation" value={selectedExam} onChange={(e) => setSelectedExam(e.target.value)}>
              <MenuItem value="">Sélectionner une évaluation</MenuItem>
              {exams.map((e) => (
                <MenuItem key={e.id} value={e.id}>{e.name} — {e.subjectName}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={6} md={3}>
            <Button
              variant="contained"
              onClick={generateBulletins}
              disabled={!classId}
              startIcon={<PictureAsPdf />}
              fullWidth
            >
              Bulletins {term}
            </Button>
          </Grid>
          <Grid item xs={6} md={2}>
            <Button
              variant="outlined"
              onClick={downloadAllBulletins}
              disabled={!classId}
              fullWidth
            >
              Tous les PDF
            </Button>
          </Grid>
        </Grid>
      </FilterCard>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={7}>
          <Card sx={{ overflow: 'hidden' }} className="animate-fade-in-up">
            <Box p={2.5} pb={1.5} display="flex" alignItems="center" gap={1.5}>
              <Box sx={{ width: 36, height: 36, borderRadius: 2.5, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'primary.light', color: 'primary.main' }}>
                <Assignment fontSize="small" />
              </Box>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>Saisie des notes</Typography>
                <Typography variant="caption" color="text.secondary">Note /20 et appréciation par élève</Typography>
              </Box>
            </Box>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Élève</TableCell>
                    <TableCell>Matricule</TableCell>
                    <TableCell align="center">Note /20</TableCell>
                    <TableCell align="center">Statut</TableCell>
                    <TableCell align="center">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {classStudents.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ py: 6 }} color="text.secondary">
                        {selectedExam
                          ? 'Aucun élève inscrit dans cette classe'
                          : 'Sélectionnez une évaluation pour charger les élèves'}
                      </TableCell>
                    </TableRow>
                  )}
                  {classStudents.map((s) => {
                    const existing = gradeByStudent(s.id)
                    const raw = draftGrades[s.id] ?? ''
                    const value = raw === '' ? null : Number(raw)
                    const tone = gradeTone(value)
                    return (
                      <TableRow key={s.id}>
                        <TableCell>
                          <Box display="flex" alignItems="center" gap={1.2}>
                            <Avatar sx={{ width: 30, height: 30, bgcolor: 'primary.main' }}>
                              {initials(s.firstName, s.lastName)}
                            </Avatar>
                            <Typography variant="body2" fontWeight={600}>{s.firstName} {s.lastName}</Typography>
                          </Box>
                        </TableCell>
                        <TableCell sx={{ color: 'text.secondary' }}>{s.matricule}</TableCell>
                        <TableCell align="center">
                          <TextField
                            size="small"
                            type="number"
                            inputProps={{ min: 0, max: 20, step: 0.25 }}
                            placeholder={existing ? existing.value : '—'}
                            value={raw}
                            onChange={(e) => setDraftGrades((d) => ({ ...d, [s.id]: e.target.value }))}
                            disabled={!selectedExam}
                            sx={{ width: 90 }}
                          />
                        </TableCell>
                        <TableCell align="center">
                          {existing && (
                            <Chip label={formatGrade(existing.value)} color={gradeTone(existing.value).color} size="small" sx={{ fontWeight: 700 }} />
                          )}
                          {!existing && raw === '' && (
                            <Typography variant="caption" color="text.secondary">Non saisie</Typography>
                          )}
                          {!existing && raw !== '' && (
                            <Chip label={tone.label} color={tone.color} size="small" variant="outlined" sx={{ fontWeight: 600 }} />
                          )}
                        </TableCell>
                        <TableCell align="center">
                          <IconButton
                            size="small"
                            color="primary"
                            disabled={!selectedExam || raw === ''}
                            onClick={() => saveInlineGrade(s)}
                            title="Enregistrer la note"
                          >
                            <Save fontSize="small" />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Card sx={{ overflow: 'hidden', height: '100%' }} className="animate-fade-in-up">
            <Box p={2.5} pb={1.5} display="flex" alignItems="center" gap={1.5}>
              <Box sx={{ width: 36, height: 36, borderRadius: 2.5, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'warning.light', color: 'warning.dark' }}>
                <Leaderboard fontSize="small" />
              </Box>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>Classement de la classe</Typography>
                <Typography variant="caption" color="text.secondary">Moyennes du {term}</Typography>
              </Box>
            </Box>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Rang</TableCell>
                    <TableCell>Élève</TableCell>
                    <TableCell align="center">Moyenne</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {ranking.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={3} align="center" sx={{ py: 6 }} color="text.secondary">
                        Aucune moyenne calculée
                      </TableCell>
                    </TableRow>
                  )}
                  {ranking.map((r, i) => (
                    <TableRow key={r.studentId}>
                      <TableCell>
                        {i < 3 ? (
                          <Chip
                            icon={<EmojiEvents sx={{ fontSize: 14 }} />}
                            label={`#${i + 1}`}
                            size="small"
                            sx={{
                              fontWeight: 800,
                              bgcolor: `${RANK_COLORS[i]}22`,
                              color: RANK_COLORS[i],
                              border: '1px solid',
                              borderColor: RANK_COLORS[i],
                            }}
                          />
                        ) : (
                          <Chip label={`#${i + 1}`} size="small" variant="outlined" sx={{ fontWeight: 600 }} />
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>
                          {r.firstName} {r.lastName}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Typography variant="body2" fontWeight={800} color="primary.main">
                          {formatGrade(r.average)}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>
        </Grid>
      </Grid>

      {/* Délibération & procès-verbal */}
      <Card sx={{ overflow: 'hidden', mt: 3 }} className="animate-fade-in-up">
        <Box p={2.5} pb={1.5} display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1.5}>
          <Box display="flex" alignItems="center" gap={1.5}>
            <Box sx={{ width: 36, height: 36, borderRadius: 2.5, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'error.light', color: 'error.main' }}>
              <Gavel fontSize="small" />
            </Box>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Délibération</Typography>
              <Typography variant="caption" color="text.secondary">
                Décisions d'admission / ajournement / redoublement pour le {term}
              </Typography>
            </Box>
          </Box>
          <Box display="flex" gap={1.5} flexWrap="wrap">
            <Button
              variant="contained"
              color="error"
              onClick={runDeliberation}
              disabled={!classId || deliberating}
              startIcon={<Gavel />}
            >
              {deliberating ? 'Délibération en cours…' : `Lancer la délibération ${term}`}
            </Button>
            <Button
              variant="outlined"
              color="error"
              onClick={downloadPv}
              disabled={!classId || deliberations.length === 0}
              startIcon={<PictureAsPdf />}
            >
              Procès-verbal (PDF)
            </Button>
          </Box>
        </Box>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Rang</TableCell>
                <TableCell>Matricule</TableCell>
                <TableCell>Élève</TableCell>
                <TableCell align="center">Moyenne</TableCell>
                <TableCell>Mention</TableCell>
                <TableCell align="center">Décision</TableCell>
                <TableCell align="center">Bulletin</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {deliberations.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 5 }} color="text.secondary">
                    Lancez la délibération pour obtenir les décisions du jury
                  </TableCell>
                </TableRow>
              )}
              {deliberations.map((b) => (
                <TableRow key={b.id}>
                  <TableCell>
                    <Chip label={`#${b.rank ?? '-'}`} size="small" variant="outlined" sx={{ fontWeight: 600 }} />
                  </TableCell>
                  <TableCell sx={{ color: 'text.secondary' }}>{b.matricule}</TableCell>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>{b.studentName}</Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Typography variant="body2" fontWeight={800} color="primary.main">
                      {formatGrade(b.average)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">{b.mention || '—'}</Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={decisionLabel(b.decision)}
                      color={DECISION_COLORS[b.decision] || 'default'}
                      size="small"
                      sx={{ fontWeight: 700 }}
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Button size="small" variant="outlined" startIcon={<PictureAsPdf />} onClick={() => downloadBulletin(b)}>
                      PDF
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Dialog saisie de note */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Saisir une note</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Élève" value={currentStudent?.studentId || ''}
                onChange={(e) => {
                  const sid = Number(e.target.value)
                  const existing = gradeRows.find((g) => g.studentId === sid)
                  setCurrentStudent({ studentId: sid })
                  setGradeValue(existing ? String(existing.value) : '')
                }}>
                {classStudents.length === 0 && <MenuItem value="">Aucun élève inscrit</MenuItem>}
                {classStudents.map((s) => (
                  <MenuItem key={s.id} value={s.id}>
                    {s.firstName} {s.lastName} ({s.matricule})
                    {gradeRows.some((g) => g.studentId === s.id) ? ' — noté' : ''}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth type="number" label="Note /20" inputProps={{ min: 0, max: 20, step: 0.25 }}
                value={gradeValue} onChange={(e) => setGradeValue(e.target.value)}
                helperText={gradeValue >= 10 ? 'Note validée (≥ 10/20)' : 'Note insuffisante (< 10/20)'}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleSaveGrade} disabled={!currentStudent || !gradeValue}>
            Enregistrer
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}