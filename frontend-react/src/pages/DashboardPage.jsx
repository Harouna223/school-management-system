import { useEffect, useState } from 'react'
import {
  Grid, Card, CardContent, Typography, Box, Chip, Divider,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Avatar,
  Skeleton, Button,
} from '@mui/material'
import {
  People,
  School,
  Class,
  MenuBook,
  AccountTree,
  Payments,
  AccountBalanceWallet,
  FactCheck,
  LocalLibrary,
  CurrencyExchange,
  ReceiptLong,
  EventBusy,
  AutoStories,
  BeachAccess,
  History,
  Groups,
  PersonAddAlt1,
  ArrowForward,
  Schedule,
  TrendingUp,
  AccessTime,
  Save,
  HowToReg,
  NotificationsActive,
} from '@mui/icons-material'
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  PieChart,
  Pie,
  Cell,
  Legend,
  BarChart,
  Bar,
} from 'recharts'
import { useTheme } from '@mui/material/styles'
import StatCard from '../components/StatCard'
import MiniMetric from '../components/MiniMetric'
import { useFetch } from '../hooks/useFetch'
import { dashboardApi } from '../api/endpoints'
import { formatCurrency } from '../utils/format'
import { useSelector } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import { useI18n } from '../i18n/I18nContext'

const GENDER_COLORS = ['#2563eb', '#ec4899']
const CLASS_COLORS = ['#2563eb', '#7c3aed', '#0284c7', '#d97706', '#16a34a', '#dc2626', '#6d28d9', '#0e7490']

/**
 * Tableau de bord premium : bannière d'accueil, KPIs, graphiques et activités.
 * Toutes les données proviennent de /dashboard/stats (aucune donnée simulée).
 */
export default function DashboardPage() {
  const theme = useTheme()
  const navigate = useNavigate()
  const { t, lang } = useI18n()
  const { data: recentActivity, loading: loadingActivity } = useFetch(() => dashboardApi.auditLogs({ page: 0, size: 8 }))
  const user = useSelector((state) => state.auth.user)

  // Actualisation automatique toutes les 5 minutes
  const [refreshKey, setRefreshKey] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setRefreshKey((k) => k + 1), 300000)
    return () => clearInterval(id)
  }, [])

  // Filtre de cycle : Tous / Jardin / Primaire / Collège / Lycée / Université
  const [cycle, setCycle] = useState('Tous')
  const { data: cycleStats, loading: cycleLoading } = useFetch(
    () => cycle === 'Université'
      ? dashboardApi.universityStats()
      : dashboardApi.stats(cycle === 'Tous' ? undefined : cycle),
    [cycle, refreshKey],
  )
  const stats = cycle === 'Université' ? null : (cycleStats ?? null)
  const univ = cycle === 'Université' ? cycleStats : null
  const { data: univReportData } = useFetch(() => (cycle === 'Université' ? dashboardApi.universityReport() : Promise.resolve({ data: { data: null } })), [cycle, refreshKey])
  const univReport = cycle === 'Université' ? univReportData : null

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

  const cards = [
    { title: t('dashboard.students'), value: stats?.activeStudents ?? 0, icon: People, color: 'primary' },
    { title: t('dashboard.teachers'), value: stats?.totalTeachers ?? 0, icon: School, color: 'secondary' },
    { title: t('dashboard.classes'), value: stats?.totalClasses ?? 0, icon: Class, color: 'info' },
    { title: t('dashboard.subjects'), value: stats?.totalSubjects ?? 0, icon: MenuBook, color: 'warning' },
    { title: t('dashboard.monthlyRevenue'), value: formatCurrency(stats?.monthlyRevenue), icon: Payments, color: 'success' },
    { title: t('dashboard.monthlyExpenses'), value: formatCurrency(stats?.monthlyExpenses), icon: AccountBalanceWallet, color: 'danger' },
    { title: t('dashboard.presentToday'), value: stats?.todayPresent ?? 0, icon: FactCheck, color: 'info' },
    { title: t('dashboard.books'), value: stats?.totalBooks ?? 0, icon: LocalLibrary, color: 'secondary' },
  ]

  const today = new Date().toLocaleDateString(lang === 'en' ? 'en-US' : 'fr-FR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })

  const firstName = user?.firstName || t('login.welcome')
  const firstNameClean = firstName.replace(/^./, (c) => c.toUpperCase())

  return (
    <>
      {/* Bannière d'accueil premium */}
      <Box className="hero-banner animate-fade-in" mb={3.5} p={{ xs: 3, md: 4 }}>
        <Box position="relative" zIndex={1}>
          <Box display="flex" alignItems="center" flexWrap="wrap" justifyContent="space-between" gap={2}>
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.02em', color: '#fff', fontSize: { xs: 22, md: 27 } }}>
                {t('dashboard.welcome', { name: firstNameClean })}
              </Typography>
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.78)', textTransform: 'capitalize', mt: 0.6 }}>
                {t('dashboard.overview', { date: today })}
              </Typography>
            </Box>
            <Box display="flex" gap={1.2} flexWrap="wrap">
              <Button
                variant="contained"
                size="small"
                startIcon={<PersonAddAlt1 sx={{ fontSize: 18 }} />}
                onClick={() => navigate('/students/new')}
                sx={{
                  bgcolor: 'rgba(255,255,255,0.16)',
                  backdropFilter: 'blur(8px)',
                  color: '#fff',
                  border: '1px solid rgba(255,255,255,0.25)',
                  borderRadius: '10px',
                  '&:hover': { bgcolor: 'rgba(255,255,255,0.26)' },
                }}
              >
                {t('dashboard.newStudent')}
              </Button>
              <Button
                variant="contained"
                size="small"
                startIcon={<Payments sx={{ fontSize: 18 }} />}
                onClick={() => navigate('/payments')}
                sx={{
                  bgcolor: 'rgba(255,255,255,0.16)',
                  backdropFilter: 'blur(8px)',
                  color: '#fff',
                  border: '1px solid rgba(255,255,255,0.25)',
                  borderRadius: '10px',
                  '&:hover': { bgcolor: 'rgba(255,255,255,0.26)' },
                }}
>
                {t('nav.payments')}
              </Button>
              <Button
                variant="contained"
                size="small"
                startIcon={<HowToReg sx={{ fontSize: 18 }} />}
                onClick={() => navigate('/attendances')}
                sx={{
                  bgcolor: 'rgba(255,255,255,0.16)',
                  backdropFilter: 'blur(8px)',
                  color: '#fff',
                  border: '1px solid rgba(255,255,255,0.25)',
                  borderRadius: '10px',
                  '&:hover': { bgcolor: 'rgba(255,255,255,0.26)' },
                }}
              >
                {t('nav.attendances')}
              </Button>
              <Button
                variant="contained"
                size="small"
                startIcon={<Save sx={{ fontSize: 18 }} />}
                onClick={() => navigate('/grades')}
                sx={{
                  bgcolor: 'rgba(255,255,255,0.16)',
                  backdropFilter: 'blur(8px)',
                  color: '#fff',
                  border: '1px solid rgba(255,255,255,0.25)',
                  borderRadius: '10px',
                  '&:hover': { bgcolor: 'rgba(255,255,255,0.26)' },
                }}
              >
                Notes
              </Button>
            </Box>
          </Box>
          <Box mt={2.5} display="flex" gap={1.5} flexWrap="wrap">
            <QuickPill icon={<Groups />} label={t('dashboard.studentsEnrolled', { count: stats?.totalStudents ?? 0 })} />
            <QuickPill icon={<Schedule />} label={t('dashboard.lateToday', { count: stats?.totalLate ?? 0 })} />
            <QuickPill icon={<TrendingUp />} label={t('dashboard.studentsPerClass', { count: stats?.studentsPerClassAvg ?? 0 })} />
          </Box>
        </Box>
      </Box>

      {/* Sélecteur de cycle */}
      <Box display="flex" alignItems="center" gap={1} flexWrap="wrap" mb={2.5}>
        <Typography variant="subtitle2" fontWeight={700} mr={0.5}>Cycle :</Typography>
        {['Tous', 'Jardin', 'Primaire', 'Collège', 'Lycée', 'Université'].map((c) => (
          <Chip key={c} label={c} clickable color={cycle === c ? 'primary' : 'default'}
            onClick={() => setCycle(c)} sx={{ fontWeight: 600 }} />
        ))}
      </Box>

      {/* Cartes KPIs */}
      {cycle === 'Université' ? (
        <Grid container spacing={3}>
          {[
            { title: 'Inscriptions LMD', value: univ?.totalEnrollments ?? 0, icon: MenuBook, color: 'primary' },
            { title: 'Étudiants actifs', value: univ?.activeStudents ?? 0, icon: People, color: 'success' },
            { title: 'Filières', value: univ?.totalFields ?? 0, icon: AccountTree, color: 'info' },
            { title: 'Programmes', value: univ?.totalPrograms ?? 0, icon: School, color: 'secondary' },
            { title: 'UE', value: univ?.totalUes ?? 0, icon: AutoStories, color: 'warning' },
            { title: 'Taux de réussite', value: `${univ?.successRate ?? 0}%`, icon: TrendingUp, color: 'success' },
            { title: 'Admis', value: univ?.admisCount ?? 0, icon: FactCheck, color: 'primary' },
            { title: 'Ajournés / Redoublants', value: univ?.ajournesCount ?? 0, icon: EventBusy, color: 'error' },
          ].map((card, i) => (
            <Grid item xs={12} sm={6} md={3} key={card.title} className={`stagger-${(i % 8) + 1}`}>
              <StatCard {...card} loading={!univ} />
            </Grid>
          ))}
        </Grid>
      ) : (
      <Grid container spacing={3}>
        {cards.map((card, i) => (
          <Grid item xs={12} sm={6} md={3} key={card.title} className={`stagger-${(i % 8) + 1}`}>
            <StatCard {...card} loading={cycleLoading} />
          </Grid>
        ))}
      </Grid>
      )}

      {/* Rapport universitaire (détaillé) */}
      {cycle === 'Université' && univReport && (
        <Grid container spacing={3} mt={0.5}>
          <Grid item xs={12} md={6}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Effectifs par filière</Typography>
              <Box display="flex" flexDirection="column" gap={1}>
                {(univReport.byField || []).map((f) => (
                  <Box key={f.fieldName} display="flex" alignItems="center" gap={1}>
                    <Typography variant="body2" flex={1}>{f.fieldName}</Typography>
                    <Chip size="small" label={`${f.count} étudiant(s)`} />
                  </Box>
                ))}
                {(univReport.byField || []).length === 0 && <Typography variant="body2" color="text.secondary">Aucune donnée.</Typography>}
              </Box>
            </Card>
          </Grid>
          <Grid item xs={12} md={6}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Répartition par niveau</Typography>
              <Box display="flex" flexDirection="column" gap={1}>
                {(univReport.byLevel || []).map((l) => (
                  <Box key={l.level} display="flex" alignItems="center" gap={1}>
                    <Typography variant="body2" flex={1}>{l.level}</Typography>
                    <Chip size="small" label={`${l.count} étudiant(s)`} />
                  </Box>
                ))}
                {(univReport.byLevel || []).length === 0 && <Typography variant="body2" color="text.secondary">Aucune donnée.</Typography>}
              </Box>
            </Card>
          </Grid>
          <Grid item xs={12} md={6}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Réussite par filière</Typography>
              <Box display="flex" flexDirection="column" gap={1}>
                {(univReport.successByField || []).map((s) => (
                  <Box key={s.fieldName} display="flex" alignItems="center" gap={1}>
                    <Typography variant="body2" flex={1}>{s.fieldName}</Typography>
                    <Chip size="small" color="success" label={`${s.admis} admis`} />
                    <Chip size="small" color="error" label={`${s.ajournes} ajourné(s)`} />
                    <Chip size="small" variant="outlined" label={`${s.successRate ?? 0}%`} />
                  </Box>
                ))}
                {(univReport.successByField || []).length === 0 && <Typography variant="body2" color="text.secondary">Aucune délibération.</Typography>}
              </Box>
            </Card>
          </Grid>
          <Grid item xs={12} md={6}>
            <Card sx={{ p: 2.5, borderRadius: '16px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Synthèse</Typography>
              <Box display="flex" flexDirection="column" gap={1.5}>
                <Box display="flex" alignItems="center" gap={1}><Typography variant="body2" flex={1}>Total inscriptions</Typography><Chip size="small" label={univReport.totalEnrollments} /></Box>
                <Box display="flex" alignItems="center" gap={1}><Typography variant="body2" flex={1}>Étudiants actifs</Typography><Chip size="small" color="success" label={univReport.totalActive} /></Box>
                <Box display="flex" alignItems="center" gap={1}><Typography variant="body2" flex={1}>Délibérations</Typography><Chip size="small" label={univReport.totalDeliberations} /></Box>
                <Box display="flex" alignItems="center" gap={1}><Typography variant="body2" flex={1}>Avec dettes</Typography><Chip size="small" color="warning" label={univReport.withDebts} /></Box>
                <Box display="flex" alignItems="center" gap={1}><Typography variant="body2" flex={1}>Examens planifiés</Typography><Chip size="small" label={univReport.totalExams ?? 0} /></Box>
                <Box display="flex" alignItems="center" gap={1}><Typography variant="body2" flex={1}>Stages</Typography><Chip size="small" label={univReport.totalStages ?? 0} /></Box>
                <Box display="flex" alignItems="center" gap={1}><Typography variant="body2" flex={1}>Convocations</Typography><Chip size="small" label={univReport.totalConvocations ?? 0} /></Box>
              </Box>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Graphiques */}
      <Grid container spacing={3} mt={0.5}>
        <Grid item xs={12} lg={7}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={2} flexWrap="wrap" gap={1}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  {t('dashboard.revenue6m')}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {t('dashboard.revenueSub')}
                </Typography>
              </Box>
              {cycleLoading ? (
                <Skeleton width={130} height={28} />
              ) : (
                <Chip
                  icon={<CurrencyExchange sx={{ fontSize: 16 }} />}
                  label={formatCurrency(stats?.monthlyRevenue)}
                  color="success"
                  variant="outlined"
                  size="small"
                  sx={{ fontWeight: 700 }}
                />
              )}
            </Box>
            {cycleLoading ? (
              <Skeleton variant="rounded" height={310} sx={{ borderRadius: '12px' }} />
            ) : (
              <ResponsiveContainer width="100%" height={310}>
                <AreaChart data={stats?.revenueByMonth ?? []} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="revenueGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#2563eb" stopOpacity={0.28} />
                      <stop offset="95%" stopColor="#2563eb" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                  <XAxis dataKey="label" tick={{ fontSize: 11.5, fill: axisColor }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11.5, fill: axisColor }} axisLine={false} tickLine={false} width={52} />
                  <Tooltip
                    formatter={(v) => [formatCurrency(v), t('dashboard.revenue')]}
                    contentStyle={tooltipStyle}
                    cursor={{ stroke: '#2563eb', strokeDasharray: '4 4' }}
                  />
                  <Area type="monotone" dataKey="value" stroke="#2563eb" strokeWidth={2.5} fill="url(#revenueGrad)" />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Grid>

        <Grid item xs={12} md={6} lg={5}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }} mb={0.5}>
              {t('dashboard.studentsByGender')}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {t('dashboard.distribution')}
            </Typography>
            {cycleLoading ? (
              <Skeleton variant="rounded" height={270} sx={{ borderRadius: '12px', mt: 1 }} />
            ) : (
              <ResponsiveContainer width="100%" height={270}>
                <PieChart>
                  <Pie
                    data={stats?.studentsByGender ?? []}
                    dataKey="value"
                    nameKey="label"
                    cx="50%"
                    cy="50%"
                    innerRadius={52}
                    outerRadius={85}
                    paddingAngle={3}
                    strokeWidth={0}
                    label={(e) => (e.value > 0 ? `${e.label} (${e.value})` : null)}
                    labelLine={false}
                    fontSize={11}
                  >
                    {(stats?.studentsByGender ?? []).map((_, i) => (
                      <Cell key={i} fill={GENDER_COLORS[i % GENDER_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={tooltipStyle} />
                  <Legend wrapperStyle={{ fontSize: 12.5 }} />
                </PieChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={2} flexWrap="wrap" gap={1}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  {t('dashboard.studentsByClass')}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {t('dashboard.studentsByClassSub')}
                </Typography>
              </Box>
            </Box>
            {cycleLoading ? (
              <Skeleton variant="rounded" height={230} sx={{ borderRadius: '12px' }} />
            ) : (
              <ResponsiveContainer width="100%" height={230}>
                <BarChart data={stats?.studentsByClass ?? []} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                  <XAxis dataKey="label" tick={{ fontSize: 11, fill: axisColor }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11.5, fill: axisColor }} axisLine={false} tickLine={false} width={36} allowDecimals={false} />
                  <Tooltip contentStyle={tooltipStyle} cursor={{ fill: dark ? 'rgba(59,130,246,0.08)' : 'rgba(37,99,235,0.06)' }} />
                  <Bar dataKey="value" name={t('dashboard.students')} radius={[6, 6, 0, 0]} maxBarSize={44}>
                    {(stats?.studentsByClass ?? []).map((_, i) => (
                      <Cell key={i} fill={CLASS_COLORS[i % CLASS_COLORS.length]} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Grid>
      </Grid>

      {/* Indicateurs secondaires */}
      <Grid container spacing={3} mt={0.5}>
        <Grid item xs={12} md={6}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: 2.5, height: '100%' }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }} mb={2}>
              {t('dashboard.financialIndicators')}
            </Typography>
            <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: '1fr 1fr' }} gap={2}>
              <MiniMetric
                icon={<ReceiptLong />}
                color="error"
                label={t('dashboard.unpaidInvoices')}
                value={stats?.unpaidInvoices ?? 0}
                loading={cycleLoading}
              />
              <MiniMetric
                icon={<Payments />}
                color="success"
                label={t('dashboard.paymentsRecorded')}
                value={stats?.totalPaymentsCount ?? 0}
                loading={cycleLoading}
              />
              <MiniMetric
                icon={<CurrencyExchange />}
                color="primary"
                label={t('dashboard.totalRevenue')}
                value={formatCurrency(stats?.totalRevenue)}
                loading={cycleLoading}
              />
              <MiniMetric
                icon={<AccountBalanceWallet />}
                color="warning"
                label={t('dashboard.totalExpenses')}
                value={formatCurrency(stats?.totalExpenses)}
                loading={cycleLoading}
              />
            </Box>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card className="chart-card animate-fade-in-up" sx={{ p: 2.5, height: '100%' }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }} mb={2}>
              {t('dashboard.schoolLife')}
            </Typography>
            <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: '1fr 1fr' }} gap={2}>
              <MiniMetric
                icon={<FactCheck />}
                color="success"
                label={t('dashboard.presentToday')}
                value={`${stats?.todayPresent ?? 0} ${t('dashboard.present').toLowerCase()}(s)`}
                loading={cycleLoading}
              />
              <MiniMetric
                icon={<EventBusy />}
                color="error"
                label={t('dashboard.absentToday')}
                value={stats?.todayAbsent ?? 0}
                loading={cycleLoading}
              />
              <MiniMetric
                icon={<AutoStories />}
                color="info"
                label={t('dashboard.borrowedBooks')}
                value={stats?.borrowedBooks ?? 0}
                loading={cycleLoading}
              />
              <MiniMetric
                icon={<BeachAccess />}
                color="warning"
                label={t('dashboard.pendingLeaves')}
                value={stats?.pendingLeaves ?? 0}
                loading={cycleLoading}
              />
            </Box>
          </Card>
        </Grid>
      </Grid>

      {/* Activités récentes */}
      <Grid container spacing={3} mt={0.5}>
        <Grid item xs={12}>
          <Card className="chart-card animate-fade-in-up" sx={{ overflow: 'hidden' }}>
            <Box p={2.5} pb={1.5} display="flex" alignItems="center" gap={1.5}>
              <Box sx={{ width: 36, height: 36, borderRadius: '14px', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'primary.light', color: 'primary.main' }}>
                <History fontSize="small" />
              </Box>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>{t('dashboard.recentActivity')}</Typography>
                <Typography variant="caption" color="text.secondary">{t('dashboard.recentActivitySub')}</Typography>
              </Box>
            </Box>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('dashboard.user')}</TableCell>
                    <TableCell>{t('dashboard.action')}</TableCell>
                    <TableCell>{t('dashboard.module')}</TableCell>
                    <TableCell>{t('dashboard.details')}</TableCell>
                    <TableCell>{t('dashboard.date')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loadingActivity && (
                    Array.from({ length: 4 }).map((_, i) => (
                      <TableRow key={`sk-${i}`}>
                        <TableCell colSpan={5}><Skeleton height={28} /></TableCell>
                      </TableRow>
                    ))
                  )}
                  {!loadingActivity && (recentActivity?.content ?? []).map((a) => (
                    <TableRow key={a.id} hover>
                      <TableCell>
                        <Box display="flex" alignItems="center" gap={1.2}>
                          <Avatar sx={{ width: 30, height: 30, bgcolor: 'primary.main', fontSize: 13 }}>
                            {(a.username || '?')[0].toUpperCase()}
                          </Avatar>
                          <Typography variant="body2" fontWeight={600}>{a.username}</Typography>
                        </Box>
                      </TableCell>
                      <TableCell><Chip size="small" label={humanizeAction(a.action)} color="default" variant="outlined" /></TableCell>
                      <TableCell><Typography variant="body2" color="text.secondary">{a.entity}</Typography></TableCell>
                      <TableCell>
                        <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: 380 }}>
                          {a.details}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Box display="flex" alignItems="center" gap={0.6}>
                          <AccessTime sx={{ fontSize: 13, color: 'text.disabled' }} />
                          <Typography variant="body2" color="text.secondary" sx={{ fontSize: 12.5 }}>
                            {new Date(a.createdAt).toLocaleString(lang === 'en' ? 'en-US' : 'fr-FR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                          </Typography>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                  {!loadingActivity && (recentActivity?.content ?? []).length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ py: 5 }} color="text.secondary">
                        {t('dashboard.noActivity')}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <Box p={2} pt={1.5} display="flex" justifyContent="flex-end">
              <Button
                size="small"
                endIcon={<ArrowForward sx={{ fontSize: 16 }} />}
                onClick={() => navigate('/audit')}
              >
                {t('dashboard.viewAllLog')}
              </Button>
            </Box>
          </Card>
        </Grid>
      </Grid>
    </>
  )
}

function humanizeAction(action) {
  if (!action) return '—'
  return action.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}

function QuickPill({ icon, label }) {
  return (
    <Box
      display="flex"
      alignItems="center"
      gap={0.8}
      px={1.3}
      py={0.6}
      borderRadius="999px"
      sx={{
        bgcolor: 'rgba(255,255,255,0.14)',
        backdropFilter: 'blur(8px)',
        border: '1px solid rgba(255,255,255,0.2)',
        color: '#fff',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', color: 'rgba(255,255,255,0.85)' }}>{icon}</Box>
      <Typography variant="caption" sx={{ fontWeight: 600, fontSize: 12 }}>{label}</Typography>
    </Box>
  )
}
