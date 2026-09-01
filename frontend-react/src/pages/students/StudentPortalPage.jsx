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
  Dashboard as DashboardIcon,
  Notifications as NotificationsIcon,
  Event as EventIcon,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import StatusChip from '../../components/StatusChip'
import Loader from '../../components/Loader'
import EmptyState from '../../components/EmptyState'
import { myApi, examApi, communicationApi } from '../../api/endpoints'
import { downloadResponse } from '../../services/exportService'
import { useToast } from '../../hooks/useToast'
import { extractError } from '../../api/axios'
import { primaryRole } from '../../utils/auth'
import { initials, formatDate, formatCurrency, DAYS_FR, DAY_KEYS } from '../../utils/format'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts'

const TERM_LABEL = { T1: '1er Trimestre', T2: '2e Trimestre', T3: '3e Trimestre' }
const DECISION_COLORS = { ADMIS: 'success', AJOURNE: 'warning', REDOUBLE: 'error' }

/**
 * Espace élève : tableau de bord, résultats, emploi du temps, présences et paiements (rôle ELEVE).
 */
export default function StudentPortalPage() {
  const role = primaryRole()
  const { success, error: toastError } = useToast()

  const [profile, setProfile] = useState(null)
  const [schedule, setSchedule] = useState([])
  const [grades, setGrades] = useState([])
  const [bulletins, setBulletins] = useState([])
  const [attendances, setAttendances] = useState([])
  const [invoices, setInvoices] = useState([])
  const [notifications, setNotifications] = useState([])
  const [tab, setTab] = useState(0)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    myApi.profile().then((res) => setProfile(res.data.data || null)).catch(() => {})
    myApi.schedule().then((res) => setSchedule(res.data.data || [])).catch(() => {})
    myApi.grades().then((res) => setGrades(res.data.data || [])).catch(() => {})
    myApi.bulletins().then((res) => setBulletins(res.data.data || [])).catch(() => {})
    myApi.attendances().then((res) => setAttendances(res.data.data || [])).catch(() => {})
    myApi.invoices().then((res) => setInvoices(res.data.data || [])).catch(() => {})
    communicationApi.notifications({ page: 0, size: 5 }).then((res) => setNotifications(res.data.data?.content || res.data.data || [])).catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const absentCount = useMemo(
    () => attendances.filter((a) => a.status === 'ABSENT').length,
    [attendances]
  )
  const dueInvoices = useMemo(
    () => invoices.filter((i) => i.status !== 'PAID' && i.status !== 'PAYE'),
    [invoices]
  )
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

  const todayIdx = useMemo(() => {
    const now = new Date().getDay()
    return now === 0 ? 0 : now - 1
  }, [])
  const todaySchedule = useMemo(() => scheduleByDay[todayIdx] || [], [scheduleByDay, todayIdx])
  const recentGrades = useMemo(() => grades.slice(0, 5), [grades])
  const rank = lastBulletin?.rank
  const progressionData = useMemo(() => bulletins
    .slice()
    .sort((a, b) => (a.academicYear || '').localeCompare(b.academicYear || '') || a.term.localeCompare(b.term))
    .map((b) => ({ name: `${TERM_LABEL[b.term] || b.term} ${b.academicYear || ''}`, moyenne: b.average ?? 0 })),
  [bulletins])

  if (role !== 'ELEVE') {
    return <Navigate to="/dashboard" replace />
  }

  const handlePrint = () => {
    document.body.classList.add('printing')
    window.print()
    setTimeout(() => document.body.classList.remove('printing'), 500)
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

  const markRead = async (id) => {
    try {
      await communicationApi.markRead(id)
      setNotifications((n) => n.map((x) => (x.id === id ? { ...x, read: true } : x)))
    } catch { /* silencieux */ }
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
                <Box display="flex" gap={2} flexWrap="wrap">
                  <Box textAlign="center">
                    <Typography variant="h5" fontWeight={800} color={lastBulletin?.average >= 10 ? 'success.main' : 'error.main'}>
                      {lastBulletin?.average != null ? lastBulletin.average.toFixed(2) : '—'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">Moyenne / 20</Typography>
                  </Box>
                  <Box textAlign="center">
                    <Typography variant="h5" fontWeight={800}>{absentCount}</Typography>
                    <Typography variant="caption" color="text.secondary">Absences</Typography>
                  </Box>
                  <Box textAlign="center">
                    <Typography variant="h5" fontWeight={800}>{dueInvoices.length}</Typography>
                    <Typography variant="caption" color="text.secondary">Factures en attente</Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardContent>
              <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
                <Tab icon={<DashboardIcon />} iconPosition="start" label="Tableau de bord" />
                <Tab icon={<SchoolIcon />} iconPosition="start" label="Mes bulletins" />
                <Tab icon={<GradesIcon />} iconPosition="start" label="Mes notes" />
                <Tab icon={<ScheduleIcon />} iconPosition="start" label="Emploi du temps" />
                <Tab icon={<AttendanceIcon />} iconPosition="start" label="Présences" />
                <Tab icon={<PaymentsIcon />} iconPosition="start" label="Paiements" />
              </Tabs>

              {/* Tableau de bord */}
              {tab === 0 && (
                <Grid container spacing={2.5}>
                  <Grid item xs={12} sm={6} md={3}>
                    <Card variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
                      <Typography variant="h4" fontWeight={800} color={lastBulletin?.average >= 10 ? 'success.main' : 'error.main'}>
                        {lastBulletin?.average != null ? lastBulletin.average.toFixed(2) : '—'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">Moyenne / 20</Typography>
                    </Card>
                  </Grid>
                  <Grid item xs={12} sm={6} md={3}>
                    <Card variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
                      <Typography variant="h4" fontWeight={800}>{rank || '—'}</Typography>
                      <Typography variant="caption" color="text.secondary">Rang dans la classe</Typography>
                    </Card>
                  </Grid>
                  <Grid item xs={12} sm={6} md={3}>
                    <Card variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
                      <Typography variant="h4" fontWeight={800} color={absentCount > 0 ? 'error.main' : 'success.main'}>{absentCount}</Typography>
                      <Typography variant="caption" color="text.secondary">Absences</Typography>
                    </Card>
                  </Grid>
                  <Grid item xs={12} sm={6} md={3}>
                    <Card variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
                      <Typography variant="h4" fontWeight={800} color={dueInvoices.length > 0 ? 'warning.main' : 'success.main'}>{dueInvoices.length}</Typography>
                      <Typography variant="caption" color="text.secondary">Factures en attente</Typography>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={6}>
                    <Card variant="outlined">
                      <CardContent>
                        <Typography variant="subtitle1" fontWeight={700} mb={1.5}>Cours d'aujourd'hui ({todaySchedule.length})</Typography>
                        {todaySchedule.length === 0 ? (
                          <EmptyState message="Aucun cours aujourd'hui." />
                        ) : (
                          todaySchedule.map((s) => (
                            <Box key={s.id} display="flex" alignItems="center" gap={1.5} mb={1}>
                              <EventIcon color="primary" sx={{ fontSize: 20 }} />
                              <Box flex={1}>
                                <Typography variant="body2" fontWeight={600}>{s.subjectName}</Typography>
                                <Typography variant="caption" color="text.secondary">{s.teacherName} · {s.roomName}</Typography>
                              </Box>
                              <Chip size="small" label={`${s.startTime} – ${s.endTime}`} variant="outlined" />
                            </Box>
                          ))
                        )}
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={6}>
                    <Card variant="outlined">
                      <CardContent>
                        <Typography variant="subtitle1" fontWeight={700} mb={1.5}>Dernières notes</Typography>
                        {recentGrades.length === 0 ? (
                          <EmptyState message="Aucune note saisie pour le moment." />
                        ) : (
                          recentGrades.map((g) => (
                            <Box key={g.id} display="flex" alignItems="center" gap={1.5} mb={1}>
                              <Box flex={1}>
                                <Typography variant="body2" fontWeight={600}>{g.subjectName}</Typography>
                                <Typography variant="caption" color="text.secondary">{g.examName} · {TERM_LABEL[g.term] || g.term}</Typography>
                              </Box>
                              <Chip size="small" color={g.value >= 10 ? 'success' : 'error'} label={`${g.value}/${g.maxValue}`} />
                            </Box>
                          ))
                        )}
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12}>
                    <Card variant="outlined">
                      <CardContent>
                        <Typography variant="subtitle1" fontWeight={700} mb={1.5}>Évolution de ma moyenne</Typography>
                        {progressionData.length > 1 ? (
                          <ResponsiveContainer width="100%" height={220}>
                            <LineChart data={progressionData} margin={{ top: 8, right: 16, left: -8, bottom: 0 }}>
                              <CartesianGrid strokeDasharray="3 3" stroke="rgba(128,128,128,0.25)" />
                              <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                              <YAxis domain={[0, 20]} tick={{ fontSize: 11 }} />
                              <Tooltip />
                              <Legend />
                              <Line type="monotone" dataKey="moyenne" name="Moyenne /20" stroke="#2563eb" strokeWidth={2.5} dot={{ r: 4 }} />
                            </LineChart>
                          </ResponsiveContainer>
                        ) : (
                          <EmptyState message="Les bulletins affichés ici dès qu'il y en a au moins deux." />
                        )}
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12}>
                    <Card variant="outlined">
                      <CardContent>
                        <Typography variant="subtitle1" fontWeight={700} mb={1.5}>
                          <NotificationsIcon sx={{ fontSize: 18, verticalAlign: 'middle', mr: 0.5 }} /> Notifications récentes
                        </Typography>
                        {notifications.length === 0 ? (
                          <EmptyState message="Aucune notification pour le moment." />
                        ) : (
                          notifications.map((n) => (
                            <Box key={n.id} display="flex" alignItems="center" gap={1.5} mb={1}
                              sx={{ p: 1, borderRadius: 2, bgcolor: n.read ? 'transparent' : 'action.hover' }}>
                              <Box flex={1}>
                                <Typography variant="body2" fontWeight={n.read ? 400 : 700}>{n.title}</Typography>
                                <Typography variant="caption" color="text.secondary">{n.message}</Typography>
                              </Box>
                              {!n.read && <Button size="small" onClick={() => markRead(n.id)}>Marquer lu</Button>}
                            </Box>
                          ))
                        )}
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>
              )}

              {/* Bulletins */}
              {tab === 1 && (
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
              {tab === 2 && (
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
              {tab === 3 && (
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
              {tab === 4 && (
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
              {tab === 5 && (
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