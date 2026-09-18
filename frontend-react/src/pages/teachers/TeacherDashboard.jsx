import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSelector } from 'react-redux'
import {
  Grid, Card, CardContent, CardActionArea, Typography, Box, Chip, Divider, Avatar,
} from '@mui/material'
import {
  Class as ClassesIcon,
  MenuBook as SubjectsIcon,
  AccessTime as HoursIcon,
  Paid as SalaryIcon,
  CalendarMonth as ScheduleIcon,
  EditNote as GradesIcon,
  ReceiptLong as ReceiptIcon,
  ArrowForward as ArrowIcon,
  CheckCircleOutline as PaidIcon,
} from '@mui/icons-material'
import StatCard from '../../components/StatCard'
import MiniMetric from '../../components/MiniMetric'
import PortalHero from '../../components/PortalHero'
import Loader from '../../components/Loader'
import StatusChip from '../../components/StatusChip'
import EmptyState from '../../components/EmptyState'
import { myApi, teacherHoursApi } from '../../api/endpoints'
import { initials, formatCurrency, DAYS_FR, DAY_KEYS } from '../../utils/format'

const MONTH_LABEL = (value) => {
  if (!value) return '—'
  const d = new Date(`${value}-01T00:00:00`)
  return Number.isNaN(d.getTime())
    ? value
    : d.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' })
}

const METHOD_LABEL = {
  CASH: 'Espèces',
  MOBILE_MONEY: 'Mobile Money',
  BANK_TRANSFER: 'Virement bancaire',
  CHECK: 'Chèque',
  CARD: 'Carte',
}

/**
 * Tableau de bord personnalisé de l'enseignant (rôle ENSEIGNANT).
 * Synthèse : classes et matières, charge horaire du mois, rémunération, cours du jour.
 * Les données de paie proviennent de /teacher-hours/my/** (espace enseignant).
 */
export default function TeacherDashboard() {
  const navigate = useNavigate()
  const user = useSelector((state) => state.auth.user)

  const payMonth = useMemo(() => new Date().toISOString().slice(0, 7), [])

  const [profile, setProfile] = useState(null)
  const [myClasses, setMyClasses] = useState([])
  const [schedule, setSchedule] = useState([])
  const [payRow, setPayRow] = useState(null)
  const [myTxs, setMyTxs] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let mounted = true
    Promise.all([
      myApi.teacherProfile().catch(() => null),
      myApi.teacherClasses().catch(() => null),
      myApi.teacherSchedule().catch(() => null),
      // La paie horaire est optionnelle : son absence ne doit pas casser le tableau de bord.
      teacherHoursApi.myMonthly({ month: `${payMonth}-01` }).catch(() => null),
      teacherHoursApi.myTransactions().catch(() => null),
    ]).then(([p, c, s, rows, txs]) => {
      if (!mounted) return
      setProfile(p?.data?.data || null)
      setMyClasses(c?.data?.data || [])
      setSchedule(s?.data?.data || [])
      setPayRow((rows?.data?.data || [])[0] || null)
      setMyTxs(txs?.data?.data || [])
      setLoading(false)
    })
    return () => { mounted = false }
  }, [payMonth])

  const distinctClasses = useMemo(() => {
    const map = new Map()
    myClasses.forEach((c) => { if (!map.has(c.classId)) map.set(c.classId, c) })
    return [...map.values()]
  }, [myClasses])

  const distinctSubjects = useMemo(
    () => [...new Set(myClasses.map((c) => c.subjectName).filter(Boolean))],
    [myClasses],
  )

  const todayIdx = (new Date().getDay() + 6) % 7
  const todaySchedule = useMemo(
    () => schedule.filter((s) => DAY_KEYS[s.dayOfWeek] === todayIdx),
    [schedule, todayIdx],
  )

  const weeklyHours = useMemo(() => schedule.reduce((sum, s) => {
    if (!s.startTime || !s.endTime) return sum
    const [sh, sm] = s.startTime.split(':').map(Number)
    const [eh, em] = s.endTime.split(':').map(Number)
    const diff = (eh * 60 + em) - (sh * 60 + sm)
    return sum + (diff > 0 ? diff / 60 : 0)
  }, 0), [schedule])

  const recentTxs = useMemo(
    () => [...myTxs].sort((a, b) => (b.paymentDate || '').localeCompare(a.paymentDate || '')).slice(0, 5),
    [myTxs],
  )

  const today = new Date().toLocaleDateString('fr-FR', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  })
  const firstName = (user?.firstName || 'professeur').replace(/^./, (c) => c.toUpperCase())

  const goTo = (tab) => navigate('/my-teaching', { state: { tab } })

  if (loading) return <Loader />

  const pills = [
    { icon: <ClassesIcon sx={{ fontSize: 15 }} />, label: `${distinctClasses.length} classe(s)` },
    { icon: <SubjectsIcon sx={{ fontSize: 15 }} />, label: `${distinctSubjects.length} matière(s)` },
    { icon: <HoursIcon sx={{ fontSize: 15 }} />, label: `${weeklyHours.toFixed(1)} h / semaine` },
  ]

  return (
    <>
      <PortalHero
        title={`Bonjour, ${firstName} 👋`}
        subtitle={`${today} — votre journée et votre rémunération.`}
        pills={pills}
        actions={[
          { label: 'Saisir des notes', icon: <GradesIcon sx={{ fontSize: 17 }} />, onClick: () => goTo(2) },
          { label: 'Mes classes', icon: <ClassesIcon sx={{ fontSize: 17 }} />, onClick: () => goTo(1) },
          { label: 'Ma paie', icon: <SalaryIcon sx={{ fontSize: 17 }} />, onClick: () => goTo(3) },
        ]}
      />

      {/* Indicateurs clés */}
      <Grid container spacing={3}>
        <Grid item xs={12} sm={6} md={3} className="stagger-1">
          <StatCard
            title="Classes assignées"
            value={distinctClasses.length}
            icon={ClassesIcon}
            color="primary"
            subtitle={`${myClasses.length} affectation(s) matière`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-2">
          <StatCard
            title="Cours aujourd'hui"
            value={todaySchedule.length}
            icon={ScheduleIcon}
            color="info"
            subtitle={`${weeklyHours.toFixed(1)} h de cours par semaine`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-3">
          <StatCard
            title={`Heures — ${MONTH_LABEL(payMonth)}`}
            value={payRow ? Number(payRow.totalHours).toFixed(1) : '—'}
            suffix="h"
            icon={HoursIcon}
            color="secondary"
            subtitle={payRow?.hourlyRate != null ? `Tarif ${formatCurrency(payRow.hourlyRate)} / h` : 'Aucune heure saisie'}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-4">
          <StatCard
            title="Reste à percevoir"
            value={payRow ? formatCurrency(payRow.remainingAmount) : formatCurrency(0)}
            icon={SalaryIcon}
            color={payRow && Number(payRow.remainingAmount) > 0 ? 'warning' : 'success'}
            subtitle={payRow ? `Salaire dû : ${formatCurrency(payRow.totalAmount)}` : 'Aucun calcul disponible'}
          />
        </Grid>
      </Grid>

      {/* Cours du jour + synthèse de paie */}
      <Grid container spacing={3} mt={0.5}>
        <Grid item xs={12} lg={7}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" gap={1.2} mb={2}>
              <Box sx={{ width: 34, height: 34, borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'primary.light', color: 'primary.main' }}>
                <ScheduleIcon fontSize="small" />
              </Box>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>Mes cours — {DAYS_FR[todayIdx]}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {todaySchedule.length} séance(s) programmée(s)
                </Typography>
              </Box>
            </Box>
            {todaySchedule.length === 0 ? (
              <EmptyState message="Aucun cours aujourd'hui." />
            ) : (
              <Box display="flex" flexDirection="column" gap={1.2}>
                {todaySchedule.map((s) => (
                  <Box
                    key={s.id}
                    display="flex"
                    alignItems="center"
                    gap={1.5}
                    p={1.4}
                    borderRadius="12px"
                    border="1px solid"
                    borderColor="divider"
                  >
                    <Box
                      sx={{
                        minWidth: 62, textAlign: 'center', borderRadius: '10px', py: 0.6,
                        bgcolor: 'primary.light', color: 'primary.dark', fontWeight: 800, fontSize: 12,
                      }}
                    >
                      {s.startTime?.slice(0, 5)}
                    </Box>
                    <Box minWidth={0} flex={1}>
                      <Typography variant="body2" fontWeight={700} noWrap>
                        {s.subjectName} · {s.className}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" noWrap display="block">
                        {s.roomName || 'Salle non précisée'}
                      </Typography>
                    </Box>
                    <Typography variant="caption" color="text.disabled">
                      {s.startTime?.slice(0, 5)}–{s.endTime?.slice(0, 5)}
                    </Typography>
                  </Box>
                ))}
              </Box>
            )}
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" gap={1.2} mb={2}>
              <Box sx={{ width: 34, height: 34, borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'success.light', color: 'success.main' }}>
                <SalaryIcon fontSize="small" />
              </Box>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>Ma rémunération</Typography>
                <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'capitalize' }}>
                  {MONTH_LABEL(payMonth)}
                </Typography>
              </Box>
            </Box>

            {!payRow ? (
              <EmptyState message="Aucune heure enseignée enregistrée pour ce mois." />
            ) : (
              <>
                <Box
                  display="flex"
                  alignItems="center"
                  gap={1.5}
                  p={1.6}
                  borderRadius="14px"
                  border="1px solid"
                  borderColor="divider"
                  mb={2}
                >
                  <Box flex={1}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Salaire dû
                    </Typography>
                    <Typography variant="h5" fontWeight={800}>{formatCurrency(payRow.totalAmount)}</Typography>
                  </Box>
                  <Divider orientation="vertical" flexItem />
                  <Box flex={1}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Déjà perçu
                    </Typography>
                    <Typography variant="h5" fontWeight={800}>{formatCurrency(payRow.amountPaid)}</Typography>
                  </Box>
                </Box>

                <Box display="grid" gridTemplateColumns="1fr 1fr" gap={1.4}>
                  <MiniMetric icon={<HoursIcon />} color="secondary" label="Heures" value={`${Number(payRow.totalHours).toFixed(1)} h`} />
                  <MiniMetric icon={<PaidIcon />} color="primary" label="Tarif horaire" value={formatCurrency(payRow.hourlyRate)} />
                </Box>

                <Box display="flex" alignItems="center" justifyContent="space-between" mt={2}>
                  <Typography variant="body2" color="text.secondary">Statut du mois</Typography>
                  <Chip
                    size="small"
                    color={payRow.status === 'PAID' ? 'success' : payRow.status === 'PARTIAL' ? 'warning' : 'default'}
                    label={payRow.status === 'PAID' ? 'Payé' : payRow.status === 'PARTIAL' ? 'Partiel' : 'Non payé'}
                  />
                </Box>
                {Number(payRow.remainingAmount) > 0 && (
                  <Typography variant="caption" color="text.secondary" display="block" mt={0.5}>
                    Reste à percevoir : <strong>{formatCurrency(payRow.remainingAmount)}</strong>
                  </Typography>
                )}
              </>
            )}
          </Card>
        </Grid>
      </Grid>

      {/* Mes classes + derniers paiements */}
      <Grid container spacing={3} mt={0.5}>
        <Grid item xs={12} md={6}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Mes classes</Typography>
              <Chip
                size="small"
                variant="outlined"
                label="Détails"
                icon={<ArrowIcon sx={{ fontSize: 15 }} />}
                onClick={() => goTo(1)}
                sx={{ fontWeight: 600 }}
              />
            </Box>
            {distinctClasses.length === 0 ? (
              <EmptyState message="Aucune classe ne vous est affectée." />
            ) : (
              <Box display="flex" flexDirection="column">
                {distinctClasses.map((c, i) => {
                  const subjects = myClasses.filter((x) => String(x.classId) === String(c.classId))
                  return (
                    <Box key={c.classId}>
                      {i > 0 && <Divider sx={{ borderStyle: 'dashed' }} />}
                      <Box display="flex" alignItems="center" gap={1.5} py={1.3}>
                        <Avatar sx={{ width: 36, height: 36, bgcolor: 'primary.main' }}>
                          <ClassesIcon sx={{ fontSize: 18 }} />
                        </Avatar>
                        <Box flex={1} minWidth={0}>
                          <Typography variant="body2" fontWeight={700} noWrap>{c.className}</Typography>
                          <Box display="flex" gap={0.6} flexWrap="wrap" mt={0.5}>
                            {subjects.map((s) => (
                              <Chip key={s.assignmentId} size="small" variant="outlined" label={s.subjectName} sx={{ height: 20, fontSize: 10.5 }} />
                            ))}
                          </Box>
                        </Box>
                      </Box>
                    </Box>
                  )
                })}
              </Box>
            )}
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Derniers paiements reçus</Typography>
              <Chip
                size="small"
                variant="outlined"
                label="Ma paie"
                icon={<ArrowIcon sx={{ fontSize: 15 }} />}
                onClick={() => goTo(3)}
                sx={{ fontWeight: 600 }}
              />
            </Box>
            {recentTxs.length === 0 ? (
              <EmptyState message="Aucun paiement enregistré." />
            ) : (
              <Box display="flex" flexDirection="column">
                {recentTxs.map((t, i) => (
                  <Box key={t.id}>
                    {i > 0 && <Divider sx={{ borderStyle: 'dashed' }} />}
                    <Box display="flex" alignItems="center" gap={1.5} py={1.3}>
                      <Box
                        sx={{
                          width: 36, height: 36, borderRadius: '12px', display: 'flex',
                          alignItems: 'center', justifyContent: 'center',
                          bgcolor: 'success.light', color: 'success.dark', flexShrink: 0,
                        }}
                      >
                        <ReceiptIcon sx={{ fontSize: 18 }} />
                      </Box>
                      <Box flex={1} minWidth={0}>
                        <Typography variant="body2" fontWeight={700} noWrap>
                          {formatCurrency(t.amount)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" noWrap display="block">
                          {t.receiptNo} · {METHOD_LABEL[t.method] || t.method} · {(t.paymentDate || '').slice(0, 10)}
                        </Typography>
                      </Box>
                      <Typography variant="caption" color="text.disabled">
                        {MONTH_LABEL((t.monthDate || '').slice(0, 7))}
                      </Typography>
                    </Box>
                  </Box>
                ))}
              </Box>
            )}
          </Card>
        </Grid>
      </Grid>

      {/* Bandeau profil */}
      {profile && (
        <Card className="animate-fade-in-up" sx={{ mt: 3, borderRadius: '16px' }}>
          <CardActionArea onClick={() => navigate('/profile')}>
            <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
              <Avatar src={profile.photo} sx={{ width: 52, height: 52, bgcolor: 'primary.main' }}>
                {initials(profile.firstName, profile.lastName)}
              </Avatar>
              <Box flex={1} minWidth={200}>
                <Typography variant="subtitle1" fontWeight={700}>
                  {profile.firstName} {profile.lastName}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {profile.employeeNo || '—'} · {profile.contractType || '—'} · {profile.email || profile.phone || '—'}
                </Typography>
              </Box>
              <StatusChip status={profile.status} />
              <ArrowIcon sx={{ color: 'text.disabled' }} />
            </CardContent>
          </CardActionArea>
        </Card>
      )}
    </>
  )
}
