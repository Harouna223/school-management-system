import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSelector } from 'react-redux'
import {
  Grid, Card, CardContent, CardActionArea, Typography, Box, Chip, Avatar,
  Button, Divider,
} from '@mui/material'
import {
  FamilyRestroom as ChildrenIcon,
  TrendingUp as TrendIcon,
  FactCheck as AttendanceIcon,
  Payments as PaymentsIcon,
  MenuBook as GradesIcon,
  EmojiEvents as RankIcon,
  ArrowForward as ArrowIcon,
  School as SchoolIcon,
} from '@mui/icons-material'
import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Cell,
} from 'recharts'
import { useTheme } from '@mui/material/styles'
import StatCard from '../../components/StatCard'
import MiniMetric from '../../components/MiniMetric'
import PortalHero from '../../components/PortalHero'
import Loader from '../../components/Loader'
import EmptyState from '../../components/EmptyState'
import { parentApi } from '../../api/endpoints'
import { initials, formatDate, formatCurrency } from '../../utils/format'

const TERM_LABEL = { T1: '1er Trimestre', T2: '2e Trimestre', T3: '3e Trimestre' }
const CYCLE_LABELS = {
  JARDIN: 'Jardin', PRIMAIRE: 'Primaire', COLLEGE: 'Collège',
  LYCEE: 'Lycée', UNIVERSITE: 'Université',
}
const CHILD_COLORS = ['#2563eb', '#7c3aed', '#0284c7', '#d97706', '#16a34a', '#dc2626']

/** Tri décroissant année puis trimestre (le plus récent d'abord). */
const byPeriodDesc = (a, b) =>
  (b.academicYear || '').localeCompare(a.academicYear || '') ||
  (b.term || '').localeCompare(a.term || '')

/**
 * Tableau de bord personnalisé du parent (rôle PARENT).
 * Synthèse multi-enfants : moyennes, assiduité, soldes et accès rapide à chaque espace.
 * L'accès à chaque enfant reste contrôlé côté backend (ownedStudent).
 */
export default function ParentDashboard() {
  const navigate = useNavigate()
  const theme = useTheme()
  const user = useSelector((state) => state.auth.user)

  const [children, setChildren] = useState([])
  const [summaries, setSummaries] = useState([])
  const [loading, setLoading] = useState(true)

  // 1) Liste des enfants rattachés au compte
  useEffect(() => {
    let mounted = true
    parentApi.children()
      .then((res) => {
        if (!mounted) return
        const kids = res.data.data || []
        setChildren(kids)
        if (kids.length === 0) setLoading(false)
      })
      .catch(() => { if (mounted) setLoading(false) })
    return () => { mounted = false }
  }, [])

  // 2) Synthèse par enfant (bulletins, présences, factures) en parallèle
  useEffect(() => {
    if (children.length === 0) return
    let mounted = true
    Promise.all(children.map(async (child) => {
      const [b, a, i] = await Promise.all([
        parentApi.childBulletins(child.id).catch(() => null),
        parentApi.childAttendances(child.id).catch(() => null),
        parentApi.childInvoices(child.id).catch(() => null),
      ])
      const bulletins = b?.data?.data || []
      const attendances = a?.data?.data || []
      const invoices = i?.data?.data || []
      const latest = [...bulletins].sort(byPeriodDesc)[0] || null
      const due = invoices.filter((inv) => inv.status !== 'PAID')
      return {
        child,
        bulletins,
        latest,
        absences: attendances.filter((x) => x.status === 'ABSENT').length,
        lates: attendances.filter((x) => x.status === 'LATE').length,
        dueCount: due.length,
        soldeDu: due.reduce((sum, inv) => sum + Number(inv.remainingAmount || 0), 0),
      }
    })).then((rows) => {
      if (!mounted) return
      setSummaries(rows)
      setLoading(false)
    })
    return () => { mounted = false }
  }, [children])

  const totals = useMemo(() => {
    const withAverage = summaries.filter((s) => s.latest?.average != null)
    const globalAverage = withAverage.length
      ? withAverage.reduce((sum, s) => sum + Number(s.latest.average), 0) / withAverage.length
      : null
    return {
      globalAverage,
      absences: summaries.reduce((sum, s) => sum + s.absences, 0),
      lates: summaries.reduce((sum, s) => sum + s.lates, 0),
      soldeDu: summaries.reduce((sum, s) => sum + s.soldeDu, 0),
      dueCount: summaries.reduce((sum, s) => sum + s.dueCount, 0),
    }
  }, [summaries])

  const comparisonData = useMemo(
    () => summaries
      .filter((s) => s.latest?.average != null)
      .map((s) => ({
        name: s.child.firstName || s.child.lastName || '—',
        moyenne: Number(s.latest.average),
      })),
    [summaries],
  )

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
  const firstName = (user?.firstName || 'cher parent').replace(/^./, (c) => c.toUpperCase())

  const openChild = (child) => navigate('/my-children', { state: { studentId: child.id, tab: 0 } })

  if (loading) return <Loader />

  if (children.length === 0) {
    return (
      <>
        <PortalHero title={`Bonjour, ${firstName} 👋`} subtitle={today} />
        <EmptyState message="Aucun enfant n'est lié à votre compte. Contactez l'administration." />
      </>
    )
  }

  const pills = [
    { icon: <ChildrenIcon sx={{ fontSize: 15 }} />, label: `${children.length} enfant(s) suivi(s)` },
    {
      icon: <TrendIcon sx={{ fontSize: 15 }} />,
      label: totals.globalAverage != null ? `Moyenne ${totals.globalAverage.toFixed(2)}/20` : 'Moyenne —',
    },
    {
      icon: <PaymentsIcon sx={{ fontSize: 15 }} />,
      label: totals.soldeDu > 0 ? `Reste à payer ${formatCurrency(totals.soldeDu)}` : 'Aucun impayé',
    },
  ]

  return (
    <>
      <PortalHero
        title={`Bonjour, ${firstName} 👋`}
        subtitle={`${today} — suivi de la scolarité de vos enfants.`}
        pills={pills}
        actions={[
          { label: 'Mes enfants', icon: <ChildrenIcon sx={{ fontSize: 17 }} />, onClick: () => navigate('/my-children') },
          { label: 'Documents', icon: <GradesIcon sx={{ fontSize: 17 }} />, onClick: () => navigate('/my-children', { state: { tab: 4 } }) },
          { label: 'Paiements', icon: <PaymentsIcon sx={{ fontSize: 17 }} />, onClick: () => navigate('/my-children', { state: { tab: 5 } }) },
        ]}
      />

      {/* Indicateurs consolidés */}
      <Grid container spacing={3}>
        <Grid item xs={12} sm={6} md={3} className="stagger-1">
          <StatCard title="Enfants suivis" value={children.length} icon={ChildrenIcon} color="primary" subtitle="Tous cycles confondus" />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-2">
          <StatCard
            title="Moyenne générale"
            value={totals.globalAverage != null ? totals.globalAverage.toFixed(2) : '—'}
            suffix="/20"
            icon={TrendIcon}
            color="secondary"
            subtitle="Moyenne des derniers bulletins"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-3">
          <StatCard
            title="Absences cumulées"
            value={totals.absences}
            icon={AttendanceIcon}
            color={totals.absences > 0 ? 'danger' : 'success'}
            subtitle={`${totals.lates} retard(s) enregistré(s)`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-4">
          <StatCard
            title="Solde à payer"
            value={formatCurrency(totals.soldeDu)}
            icon={PaymentsIcon}
            color={totals.soldeDu > 0 ? 'warning' : 'success'}
            subtitle={`${totals.dueCount} facture(s) en attente`}
          />
        </Grid>
      </Grid>

      {/* Fiches par enfant */}
      <Grid container spacing={3} mt={0.5}>
        {summaries.map((row, i) => (
          <Grid item xs={12} md={6} xl={4} key={row.child.id} className={`stagger-${(i % 8) + 1}`}>
            <Card className="chart-card animate-fade-in-up" sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flex: 1 }}>
                <Box display="flex" alignItems="center" gap={1.6} mb={2}>
                  <Avatar src={row.child.photo} sx={{ width: 48, height: 48, bgcolor: CHILD_COLORS[i % CHILD_COLORS.length] }}>
                    {initials(row.child.firstName, row.child.lastName)}
                  </Avatar>
                  <Box minWidth={0} flex={1}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap>
                      {row.child.firstName} {row.child.lastName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" noWrap display="block">
                      {row.child.className || 'Université'} · {row.child.matricule}
                    </Typography>
                  </Box>
                  <Chip
                    size="small"
                    variant="outlined"
                    color="primary"
                    label={CYCLE_LABELS[row.child.educationCycle] || '—'}
                  />
                </Box>

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
                      Moyenne
                    </Typography>
                    <Typography variant="h5" fontWeight={800}>
                      {row.latest?.average != null ? Number(row.latest.average).toFixed(2) : '—'}
                      <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.4 }}>/20</Typography>
                    </Typography>
                    <Typography variant="caption" color="text.disabled">
                      {row.latest ? `${TERM_LABEL[row.latest.term] || row.latest.term} · ${row.latest.academicYear || ''}` : 'Aucun bulletin'}
                    </Typography>
                  </Box>
                  <Divider orientation="vertical" flexItem />
                  <Box flex={1}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Rang
                    </Typography>
                    <Typography variant="h5" fontWeight={800}>
                      {row.latest?.rank != null ? row.latest.rank : '—'}
                    </Typography>
                    <Typography variant="caption" color="text.disabled">
                      {row.latest?.mention || '—'}
                    </Typography>
                  </Box>
                </Box>

                <Box display="grid" gridTemplateColumns="1fr 1fr" gap={1.4}>
                  <MiniMetric
                    icon={<AttendanceIcon />}
                    color={row.absences > 0 ? 'error' : 'success'}
                    label="Absences"
                    value={row.absences}
                  />
                  <MiniMetric
                    icon={<PaymentsIcon />}
                    color={row.soldeDu > 0 ? 'warning' : 'success'}
                    label="Reste à payer"
                    value={formatCurrency(row.soldeDu)}
                  />
                </Box>
              </CardContent>

              <Box px={2} pb={2}>
                <Button
                  fullWidth
                  size="small"
                  variant="outlined"
                  endIcon={<ArrowIcon sx={{ fontSize: 16 }} />}
                  onClick={() => openChild(row.child)}
                  sx={{ borderRadius: '10px' }}
                >
                  Ouvrir l'espace de {row.child.firstName}
                </Button>
              </Box>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Comparaison des moyennes */}
      {comparisonData.length > 1 && (
        <Grid container spacing={3} mt={0.5}>
          <Grid item xs={12}>
            <Card className="chart-card animate-fade-in-up" sx={{ p: { xs: 2, md: 2.5 } }}>
              <Box display="flex" alignItems="center" gap={1.2} mb={2}>
                <Box sx={{ width: 34, height: 34, borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'secondary.light', color: 'secondary.main' }}>
                  <RankIcon fontSize="small" />
                </Box>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>Comparaison des moyennes</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Dernier bulletin publié de chaque enfant (sur 20)
                  </Typography>
                </Box>
              </Box>
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={comparisonData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={gridColor} vertical={false} />
                  <XAxis dataKey="name" tick={{ fontSize: 11.5, fill: axisColor }} axisLine={false} tickLine={false} />
                  <YAxis domain={[0, 20]} tick={{ fontSize: 11.5, fill: axisColor }} axisLine={false} tickLine={false} width={36} />
                  <Tooltip
                    formatter={(v) => [`${Number(v).toFixed(2)} / 20`, 'Moyenne']}
                    contentStyle={tooltipStyle}
                    cursor={{ fill: dark ? 'rgba(59,130,246,0.08)' : 'rgba(37,99,235,0.06)' }}
                  />
                  <Bar dataKey="moyenne" radius={[6, 6, 0, 0]} maxBarSize={52}>
                    {comparisonData.map((_, i) => (
                      <Cell key={i} fill={CHILD_COLORS[i % CHILD_COLORS.length]} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Rappel des espaces */}
      <Grid container spacing={3} mt={0.5}>
        <Grid item xs={12}>
          <Card className="animate-fade-in-up" sx={{ p: 2.5, borderRadius: '16px' }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }} mb={2}>Accès rapides</Typography>
            <Grid container spacing={2}>
              {[
                { label: 'Bulletins & notes', icon: <GradesIcon />, color: 'primary', tab: 0 },
                { label: 'Assiduité', icon: <AttendanceIcon />, color: 'info', tab: 1 },
                { label: 'Documents officiels', icon: <SchoolIcon />, color: 'secondary', tab: 4 },
                { label: 'Paiements', icon: <PaymentsIcon />, color: 'warning', tab: 5 },
              ].map((item) => (
                <Grid item xs={12} sm={6} md={3} key={item.label}>
                  <Card variant="outlined" sx={{ borderRadius: '14px' }}>
                    <CardActionArea onClick={() => navigate('/my-children', { state: { tab: item.tab } })} sx={{ p: 2 }}>
                      <Box display="flex" alignItems="center" gap={1.4}>
                        <Box sx={{ width: 38, height: 38, borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: `${item.color}.light`, color: `${item.color}.dark` }}>
                          {item.icon}
                        </Box>
                        <Typography variant="body2" fontWeight={600}>{item.label}</Typography>
                      </Box>
                    </CardActionArea>
                  </Card>
                </Grid>
              ))}
            </Grid>
            <Typography variant="caption" color="text.disabled" display="block" mt={2}>
              Dernière actualisation : {formatDate(new Date().toISOString())}
            </Typography>
          </Card>
        </Grid>
      </Grid>
    </>
  )
}
