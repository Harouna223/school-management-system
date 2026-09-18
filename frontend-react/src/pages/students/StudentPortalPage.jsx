import { useEffect, useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  Grid, Card, CardContent, Typography, Box, Tabs, Tab, Chip, Button,
  Table, TableHead, TableRow, TableCell, TableBody, Divider,
} from '@mui/material'
import {
  School as SchoolIcon,
  MenuBook as GradesIcon,
  CalendarMonth as ScheduleIcon,
  PictureAsPdf as PdfIcon,
  FactCheck as AttendanceIcon,
  Payments as PaymentsIcon,
  ReceiptLong as ReceiptIcon,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import StatusChip from '../../components/StatusChip'
import Loader from '../../components/Loader'
import EmptyState from '../../components/EmptyState'
import { myApi, examApi } from '../../api/endpoints'
import { downloadResponse } from '../../services/exportService'
import { useToast } from '../../hooks/useToast'
import { extractError } from '../../api/axios'
import { primaryRole } from '../../utils/auth'
import { initials, formatDate, formatCurrency, DAYS_FR, DAY_KEYS } from '../../utils/format'

const TERM_LABEL = { T1: '1er Trimestre', T2: '2e Trimestre', T3: '3e Trimestre' }
const DECISION_COLORS = { ADMIS: 'success', AJOURNE: 'warning', REDOUBLE: 'error' }

/**
 * Espace élève : tableau de bord, résultats, emploi du temps, présences et paiements (rôle ELEVE).
 */
export default function StudentPortalPage() {
  const role = primaryRole()
  const location = useLocation()
  const { success, error: toastError } = useToast()

  const [profile, setProfile] = useState(null)
  const [schedule, setSchedule] = useState([])
  const [grades, setGrades] = useState([])
  const [bulletins, setBulletins] = useState([])
  const [attendances, setAttendances] = useState([])
  const [invoices, setInvoices] = useState([])
  // Onglet ciblé par le tableau de bord (navigation depuis /dashboard).
  const [tab, setTab] = useState(location.state?.tab ?? 0)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (location.state?.tab != null) setTab(location.state.tab)
  }, [location.state])

  useEffect(() => {
    myApi.profile().then((res) => setProfile(res.data.data || null)).catch(() => {})
    myApi.schedule().then((res) => setSchedule(res.data.data || [])).catch(() => {})
    myApi.grades().then((res) => setGrades(res.data.data || [])).catch(() => {})
    myApi.bulletins().then((res) => setBulletins(res.data.data || [])).catch(() => {})
    myApi.attendances().then((res) => setAttendances(res.data.data || [])).catch(() => {})
    myApi.invoices()
      .then((res) => setInvoices(res.data.data || []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const lastBulletin = bulletins[0]
  const scheduleByDay = useMemo(() => {
    const map = {}
    DAYS_FR.forEach((_, i) => { map[i] = [] })
    schedule.forEach((s) => {
      const idx = DAY_KEYS[s.dayOfWeek]
      if (idx != null) map[idx].push(s)
    })
    return map
  }, [schedule])

  if (role !== 'ELEVE') {
    return <Navigate to="/dashboard" replace />
  }

  const downloadBulletin = async (b) => {
    try {
      const res = await examApi.bulletinPdf(b.id)
      await downloadResponse(res, `bulletin-${profile?.matricule || 'eleve'}-${b.term}.pdf`)
      success('Bulletin téléchargé')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Mon espace"
        subtitle="Vos résultats, emploi du temps, présences et paiements"
      />

      {loading ? (
        <Loader />
      ) : (
        <>
          {/* Bandeau profil */}
          {profile && (
            <Card sx={{ mb: 3 }}>
              <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                <Box
                  sx={{
                    width: 56, height: 56, borderRadius: 3, display: 'flex', alignItems: 'center',
                    justifyContent: 'center', background: 'linear-gradient(135deg, #2563eb, #1e3a8a)',
                    color: '#fff', fontWeight: 800, fontSize: 20, flexShrink: 0,
                  }}
                >
                  {profile.photo ? null : initials(profile.firstName, profile.lastName)}
                </Box>
                <Box flex={1} minWidth={200}>
                  <Typography variant="h6">{profile.firstName} {profile.lastName}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {profile.className} · Matricule {profile.matricule}
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardContent>
              <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
                <Tab icon={<SchoolIcon />} iconPosition="start" label="Mes bulletins" />
                <Tab icon={<GradesIcon />} iconPosition="start" label="Mes notes" />
                <Tab icon={<ScheduleIcon />} iconPosition="start" label="Emploi du temps" />
                <Tab icon={<AttendanceIcon />} iconPosition="start" label="Présences" />
                <Tab icon={<PaymentsIcon />} iconPosition="start" label="Paiements" />
              </Tabs>

              {/* Bulletins */}
              {tab === 0 && (
                bulletins.length === 0 ? (
                  <EmptyState message="Aucun bulletin disponible pour le moment." />
                ) : (
                  <Grid container spacing={2}>
                    {bulletins.map((b) => (
                      <Grid item xs={12} sm={6} key={b.id}>
                        <Card variant="outlined">
                          <CardContent>
                            <Typography variant="subtitle2" color="primary" fontWeight={700}>
                              {TERM_LABEL[b.term] || b.term} · {b.academicYear}
                            </Typography>
                            <Box my={1}>
                              <Typography variant="h4" fontWeight={800}>
                                {b.average != null ? b.average.toFixed(2) : '—'}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">Moyenne / 20</Typography>
                            </Box>
                            <Box display="flex" gap={1} flexWrap="wrap">
                              <Chip size="small" label={`Rang : ${b.rank || '—'}`} variant="outlined" />
                              <Chip size="small" color={b.average >= 10 ? 'success' : 'error'} label={b.mention} variant="outlined" />
                              {b.decision && (
                                <Chip size="small" color={DECISION_COLORS[b.decision] || 'default'} label={b.decision} />
                              )}
                            </Box>
                            <Button size="small" sx={{ mt: 2 }} startIcon={<PdfIcon />} onClick={() => downloadBulletin(b)}>
                              Télécharger le PDF
                            </Button>
                          </CardContent>
                        </Card>
                      </Grid>
                    ))}
                  </Grid>
                )
              )}

              {/* Notes */}
              {tab === 1 && (
                grades.length === 0 ? (
                  <EmptyState message="Aucune note n'a encore été saisie." />
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Matière</TableCell>
                        <TableCell>Évaluation</TableCell>
                        <TableCell>Trimestre</TableCell>
                        <TableCell>Note</TableCell>
                        <TableCell>Coef.</TableCell>
                        <TableCell>Appréciation</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {grades.map((g) => (
                        <TableRow key={g.id}>
                          <TableCell fontWeight={600}>{g.subjectName}</TableCell>
                          <TableCell>{g.examName}</TableCell>
                          <TableCell>{TERM_LABEL[g.term] || g.term}</TableCell>
                          <TableCell fontWeight={600}>{g.value} / {g.maxValue}</TableCell>
                          <TableCell>{g.coefficient ?? '—'}</TableCell>
                          <TableCell>{g.appreciation || '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )
              )}

              {/* Emploi du temps */}
              {tab === 2 && (
                schedule.length === 0 ? (
                  <EmptyState message="Aucun cours planifié pour votre classe." />
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
                                    {s.subjectName} · {s.teacherName}
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

              {/* Présences */}
              {tab === 3 && (
                attendances.length === 0 ? (
                  <EmptyState message="Aucune présence enregistrée." />
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Date</TableCell>
                        <TableCell>Statut</TableCell>
                        <TableCell>Justification</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {attendances.slice(0, 60).map((a) => (
                        <TableRow key={a.id}>
                          <TableCell>{formatDate(a.date)}</TableCell>
                          <TableCell><StatusChip status={a.status} /></TableCell>
                          <TableCell>{a.justification || '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )
              )}

              {/* Paiements */}
              {tab === 4 && (
                invoices.length === 0 ? (
                  <EmptyState message="Aucune facture émise pour vous." />
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>N°</TableCell>
                        <TableCell>Type de frais</TableCell>
                        <TableCell>Montant</TableCell>
                        <TableCell>Payé</TableCell>
                        <TableCell>Reste</TableCell>
                        <TableCell>Échéance</TableCell>
                        <TableCell>Statut</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {invoices.map((i) => (
                        <TableRow key={i.id}>
                          <TableCell>{i.invoiceNo}</TableCell>
                          <TableCell>{i.feeTypeName}</TableCell>
                          <TableCell>{formatCurrency(i.amount)}</TableCell>
                          <TableCell>{formatCurrency(i.paidAmount)}</TableCell>
                          <TableCell fontWeight={600}>{formatCurrency(i.remainingAmount)}</TableCell>
                          <TableCell>{formatDate(i.dueDate)}</TableCell>
                          <TableCell><StatusChip status={i.status} /></TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )
              )}
            </CardContent>
          </Card>
        </>
      )}

      <Divider sx={{ my: 3 }} className="print-area-hidden" />
      <Box className="print-area" display="none" id="student-portal-print">
        {lastBulletin && (
          <>
            <Typography variant="h6" textAlign="center" fontWeight={800}>
              School Management System
            </Typography>
            <Typography variant="body2" textAlign="center" color="text.secondary" mb={2}>
              Bulletin · {TERM_LABEL[lastBulletin.term] || lastBulletin.term} · {lastBulletin.academicYear}
            </Typography>
            <Box display="flex" justifyContent="space-between" mb={2}>
              <Typography variant="body2"><strong>Élève :</strong> {lastBulletin.studentName}</Typography>
              <Typography variant="body2"><strong>Matricule :</strong> {lastBulletin.matricule}</Typography>
            </Box>
            <Box display="flex" justifyContent="space-between" mb={2}>
              <Typography variant="body2"><strong>Classe :</strong> {lastBulletin.className}</Typography>
              <Typography variant="body2"><strong>Mention :</strong> {lastBulletin.mention}</Typography>
            </Box>
            <Typography variant="h5" fontWeight={800} color="primary" mb={2}>
              Moyenne : {lastBulletin.average != null ? lastBulletin.average.toFixed(2) : '—'}/20 · Rang : {lastBulletin.rank || '—'}
            </Typography>
            <Box display="flex" justifyContent="space-between" mt={4}>
              <Typography variant="caption">Signature de l'administration</Typography>
              <Typography variant="caption">Signature de l'élève</Typography>
            </Box>
          </>
        )}
      </Box>
    </>
  )
}