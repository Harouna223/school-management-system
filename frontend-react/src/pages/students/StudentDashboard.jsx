import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSelector } from 'react-redux'
import {
  Grid, Card, CardContent, CardActionArea, Typography, Box, Chip, Divider,
} from '@mui/material'
import {
  MenuBook as GradesIcon,
  FactCheck as AttendanceIcon,
  Payments as PaymentsIcon,
  EmojiEvents as RankIcon,
  TrendingUp as TrendIcon,
  CalendarMonth as ScheduleIcon,
  WarningAmber as WarningIcon,
  ArrowForward as ArrowIcon,
  School as SchoolIcon,
} from '@mui/icons-material'
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts'
import { useTheme } from '@mui/material/styles'
import StatCard from '../../components/StatCard'
import MiniMetric from '../../components/MiniMetric'
import PortalHero from '../../components/PortalHero'
import Loader from '../../components/Loader'
import StatusChip from '../../components/StatusChip'
import EmptyState from '../../components/EmptyState'
import { myApi } from '../../api/endpoints'
import { initials, formatDate, formatCurrency, DAYS_FR, DAY_KEYS } from '../../utils/format'

const TERM_LABEL = { T1: '1er Trimestre', T2: '2e Trimestre', T3: '3e Trimestre' }

/** Tri décroissant année puis trimestre (le plus récent d'abord). */
const byPeriodDesc = (a, b) =>
  (b.academicYear || '').localeCompare(a.academicYear || '') ||
  (b.term || '').localeCompare(a.term || '')

/**
 * Date locale au format AAAA-MM-JJ, comparable lexicographiquement.
 * Évite `new Date('2026-09-30')`, qui est interprété en UTC minuit et décale
 * la comparaison selon le fuseau.
 */
const localDay = (d = new Date()) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/**
 * Tableau de bord personnalisé de l'élève (rôle ELEVE).
 * Synthèse de la scolarité : moyenne, rang, assiduité, soldes et cours du jour.
 * Toutes les données proviennent de l'espace personnel (/my/**) : aucune donnée simulée.
 */
export default function StudentDashboard() {
  const navigate = useNavigate()
  const theme = useTheme()
  const user = useSelector((state) => state.auth.user)

  const [profile, setProfile] = useState(null)
  const [bulletins, setBulletins] = useState([])
  const [grades, setGrades] = useState([])
  const [attendances, setAttendances] = useState([])
  const [invoices, setInvoices] = useState([])
  const [schedule, setSchedule] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let mounted = true
    Promise.all([
      myApi.profile().catch(() => null),
      myApi.bulletins().catch(() => null),
      myApi.grades().catch(() => null),
      myApi.attendances().catch(() => null),
      myApi.invoices().catch(() => null),
      myApi.schedule().catch(() => null),
    ]).then(([p, b, g, a, i, s]) => {
      if (!mounted) return
      setProfile(p?.data?.data || null)
      setBulletins(b?.data?.data || [])
      setGrades(g?.data?.data || [])
      setAttendances(a?.data?.data || [])
      setInvoices(i?.data?.data || [])
      setSchedule(s?.data?.data || [])
      setLoading(false)
    })
    return () => { mounted = false }
  }, [])

  const latestBulletin = useMemo(() => [...bulletins].sort(byPeriodDesc)[0] || null, [bulletins])

  const absences = useMemo(() => attendances.filter((a) => a.status === 'ABSENT').length, [attendances])
  const lates = useMemo(() => attendances.filter((a) => a.status === 'LATE').length, [attendances])
  const justified = useMemo(
    () => attendances.filter((a) => a.status === 'ABSENT' && a.justification).length,
    [attendances],
  )

  const dueInvoices = useMemo(() => invoices.filter((i) => i.status !== 'PAID'), [invoices])
  const soldeDu = useMemo(
    () => dueInvoices.reduce((sum, i) => sum + Number(i.remainingAmount || 0), 0),
    [dueInvoices],
  )
  // Même règle que le backend (`InvoiceRepository.findOverdue`) : une facture est
  // en retard si son échéance est *strictement* antérieure à aujourd'hui.
  // Comparer `new Date(dueDate) < new Date()` la déclarait en retard dès 00:00
  // le jour de l'échéance, soit un jour trop tôt.
  const overdueInvoices = useMemo(
    () => dueInvoices.filter((i) => {
      const due = (i.dueDate || '').slice(0, 10)
      return due !== '' && due < localDay()
    }),
    [dueInvoices],
  )

  const progressionData = useMemo(
    () => [...bulletins]
      .sort((a, b) => byPeriodDesc(b, a))
      .map((b) => ({
        name: `${TERM_LABEL[b.term] || b.term} ${b.academicYear || ''}`.trim(),
        moyenne: b.average != null ? Number(b.average) : 0,
      })),
    [bulletins],
  )

  const todayIdx = (new Date().getDay() + 6) % 7
  const todaySchedule = useMemo(
    () => schedule.filter((s) => DAY_KEYS[s.dayOfWeek] === todayIdx),
    [schedule, todayIdx],
  )

  const recentGrades = useMemo(() => grades.slice(0, 6), [grades])

  const dark = theme.palette.mode === 'dark'
  const axisColor = dark ? '#64748b' : '#94a3b8'
  const gridColor = dark ? '#1e293b' : '#e2e8f0'
  const tooltipStyle = {
    borderRadius: 12,
    border: `1px solid ${dark ? '#334155' : '#e2e8f0'}`,
    boxShadow: dark ? '0 8px 24px rgba(0,0,0,0.5)' : '0 8px 24px rgba(15,23,42,0.1)',
    background: dark ? '#0f172a' : '#fff',
    color: dark ? '#e2e8f0' : '#1e293b',
    fontSize: 12.5,
  }

  const today = new Date().toLocaleDateString('fr-FR', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  })
  const firstName = (user?.firstName || 'élève').replace(/^./, (c) => c.toUpperCase())

  const goTo = (tab) => navigate('/my-school', { state: { tab } })

  if (loading) return <Loader />

  const pills = [
    { icon: <SchoolIcon sx={{ fontSize: 15 }} />, label: profile?.className || 'Classe non assignée' },
    { icon: null, label: `Matricule ${profile?.matricule || '—'}` },
    {
      icon: <TrendIcon sx={{ fontSize: 15 }} />,
      label: latestBulletin?.average != null ? `Moyenne ${Number(latestBulletin.average).toFixed(2)}/20` : 'Moyenne —',
    },
  ]

  return (
    <>
      <PortalHero
        title={`Bonjour, ${firstName} 👋`}
        subtitle={`${today} — voici la synthèse de votre scolarité.`}
        pills={pills}
        actions={[
          { label: 'Mes bulletins', icon: <GradesIcon sx={{ fontSize: 17 }} />, onClick: () => goTo(0) },
          { label: 'Mes notes', icon: <GradesIcon sx={{ fontSize: 17 }} />, onClick: () => goTo(1) },
          { label: 'Emploi du temps', icon: <ScheduleIcon sx={{ fontSize: 17 }} />, onClick: () => goTo(2) },
        ]}
      />

      {/* Indicateurs clés */}
      <Grid container spacing={3}>
        <Grid item xs={12} sm={6} md={3} className="stagger-1">
          <StatCard
            title="Moyenne générale"
            value={latestBulletin?.average != null ? Number(latestBulletin.average).toFixed(2) : '—'}
            suffix="/20"
            icon={TrendIcon}
            color="primary"
            subtitle={latestBulletin ? `${TERM_LABEL[latestBulletin.term] || latestBulletin.term} · ${latestBulletin.academicYear || ''}` : 'Aucun bulletin'}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-2">
          <StatCard
            title="Rang dans la classe"
            value={latestBulletin?.rank != null ? latestBulletin.rank : '—'}
            icon={RankIcon}
            color="secondary"
            subtitle={latestBulletin?.mention || '—'}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-3">
          <StatCard
            title="Absences"
            value={absences}
            icon={AttendanceIcon}
            color={absences > 0 ? 'danger' : 'success'}
            subtitle={`${justified} justifiée(s) · ${lates} retard(s)`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-4">
          <StatCard
            title="Solde à payer"
            value={formatCurrency(soldeDu)}
            icon={PaymentsIcon}
            color={soldeDu > 0 ? 'warning' : 'success'}
            subtitle={`${dueInvoices.length} facture(s) en attente`}
          />
        </Grid>
      </Grid>

      {/* Évolution + cours du jour */}
      <Grid container spacing={3} mt={0.5}>
        <Grid item xs={12} lg={7}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={2} flexWrap="wrap" gap={1}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>Évolution de ma moyenne</Typography>
                <Typography variant="caption" color="text.secondary">
                  Moyenne générale par trimestre (sur 20)
                </Typography>
              </Box>
              {latestBulletin?.classAverage != null && (
                <Chip
                  size="small"
                  variant="outlined"
                  color="info"
                  label={`Moyenne de la classe : ${Number(latestBulletin.classAverage).toFixed(2)}`}
                  sx={{ fontWeight: 700 }}
                />
              )}
            </Box>
            {progressionData.length === 0 ? (
              <EmptyState message="Aucun bulletin publié pour le moment." />
            ) : (
              <ResponsiveContainer width="100%" height={290}>
                <AreaChart data={progressionData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="studentAvgGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#2563eb" stopOpacity={0.28} />
                      <stop offset="95%" stopColor="#2563eb" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                  <XAxis dataKey="name" tick={{ fontSize: 11, fill: axisColor }} axisLine={false} tickLine={false} />
                  <YAxis domain={[0, 20]} tick={{ fontSize: 11.5, fill: axisColor }} axisLine={false} tickLine={false} width={36} />
                  <Tooltip
                    formatter={(v) => [`${Number(v).toFixed(2)} / 20`, 'Moyenne']}
                    contentStyle={tooltipStyle}
                    cursor={{ stroke: '#2563eb', strokeDasharray: '4 4' }}
                  />
                  <Area type="monotone" dataKey="moyenne" stroke="#2563eb" strokeWidth={2.5} fill="url(#studentAvgGrad)" />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" gap={1.2} mb={2}>
              <Box sx={{ width: 34, height: 34, borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'primary.light', color: 'primary.main' }}>
                <ScheduleIcon fontSize="small" />
              </Box>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Mes cours — {DAYS_FR[todayIdx]}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {todaySchedule.length} cours programmé(s)
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
                      <Typography variant="body2" fontWeight={700} noWrap>{s.subjectName}</Typography>
                      <Typography variant="caption" color="text.secondary" noWrap display="block">
                        {s.teacherName}{s.roomName ? ` · ${s.roomName}` : ''}
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
      </Grid>

      {/* Dernières notes + alertes */}
      <Grid container spacing={3} mt={0.5}>
        <Grid item xs={12} md={7}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={1.5}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Dernières notes</Typography>
              <Chip
                size="small"
                variant="outlined"
                label="Tout voir"
                onClick={() => goTo(1)}
                icon={<ArrowIcon sx={{ fontSize: 15 }} />}
                sx={{ fontWeight: 600 }}
              />
            </Box>
            {recentGrades.length === 0 ? (
              <EmptyState message="Aucune note saisie pour le moment." />
            ) : (
              <Box display="flex" flexDirection="column">
                {recentGrades.map((g, i) => (
                  <Box key={g.id}>
                    {i > 0 && <Divider sx={{ borderStyle: 'dashed' }} />}
                    <Box display="flex" alignItems="center" gap={1.5} py={1.2}>
                      <Box flex={1} minWidth={0}>
                        <Typography variant="body2" fontWeight={600} noWrap>{g.subjectName}</Typography>
                        <Typography variant="caption" color="text.secondary" noWrap display="block">
                          {[g.examName, g.term && (TERM_LABEL[g.term] || g.term)]
                            .filter(Boolean)
                            .join(' · ')}
                        </Typography>
                      </Box>
                      <Chip
                        size="small"
                        color={Number(g.value) >= 10 ? 'success' : 'error'}
                        variant="outlined"
                        label={`${g.value} / ${g.maxValue}`}
                        sx={{ fontWeight: 700 }}
                      />
                    </Box>
                  </Box>
                ))}
              </Box>
            )}
          </Card>
        </Grid>

        <Grid item xs={12} md={5}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }} mb={2}>
              Points de vigilance
            </Typography>
            <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: '1fr 1fr' }} gap={2}>
              <MiniMetric
                icon={<WarningIcon />}
                color={overdueInvoices.length > 0 ? 'error' : 'success'}
                label="Factures en retard"
                value={overdueInvoices.length}
                onClick={() => goTo(4)}
              />
              <MiniMetric
                icon={<PaymentsIcon />}
                color="warning"
                label="Reste à payer"
                value={formatCurrency(soldeDu)}
                onClick={() => goTo(4)}
              />
              <MiniMetric
                icon={<AttendanceIcon />}
                color={absences > 0 ? 'error' : 'success'}
                label="Absences"
                value={absences}
                onClick={() => goTo(3)}
              />
              <MiniMetric
                icon={<ScheduleIcon />}
                color="info"
                label="Retards"
                value={lates}
                onClick={() => goTo(3)}
              />
            </Box>

            {dueInvoices.length > 0 && (
              <>
                <Typography variant="subtitle2" fontWeight={700} mt={2.5} mb={1}>
                  Prochaines échéances
                </Typography>
                <Box display="flex" flexDirection="column" gap={1}>
                  {[...dueInvoices]
                    .sort((a, b) => (a.dueDate || '').localeCompare(b.dueDate || ''))
                    .slice(0, 3)
                    .map((inv) => (
                      <Box key={inv.id} display="flex" alignItems="center" gap={1}>
                        <Box flex={1} minWidth={0}>
                          <Typography variant="body2" fontWeight={600} noWrap>{inv.feeTypeName}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {formatDate(inv.dueDate)}
                          </Typography>
                        </Box>
                        <Typography variant="body2" fontWeight={700} color="warning.dark">
                          {formatCurrency(inv.remainingAmount)}
                        </Typography>
                      </Box>
                    ))}
                </Box>
              </>
            )}
          </Card>
        </Grid>
      </Grid>

      {/* Bandeau profil */}
      {profile && (
        <Card className="animate-fade-in-up" sx={{ mt: 3, borderRadius: '16px' }}>
          <CardActionArea onClick={() => navigate('/profile')}>
            <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
              <Box
                sx={{
                  width: 52, height: 52, borderRadius: '16px', display: 'flex', alignItems: 'center',
                  justifyContent: 'center', background: 'linear-gradient(135deg, #2563eb, #1e3a8a)',
                  color: '#fff', fontWeight: 800, fontSize: 18, flexShrink: 0,
                }}
              >
                {initials(profile.firstName, profile.lastName)}
              </Box>
              <Box flex={1} minWidth={200}>
                <Typography variant="subtitle1" fontWeight={700}>
                  {profile.firstName} {profile.lastName}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {profile.className || '—'} · Matricule {profile.matricule} · Inscrit le {formatDate(profile.enrollmentDate)}
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
