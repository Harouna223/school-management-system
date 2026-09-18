import { useEffect, useState } from 'react'
import {
  Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions,
  Box, Typography, Tabs, Tab, Chip, IconButton, Tooltip, Alert,
} from '@mui/material'
import {
  Schedule as HoursIcon, History as HistoryIcon, Payments as PayIcon,
  Paid as RatesIcon, Lock, LockOpen,
  FileDownload as DownloadIcon, Edit as EditIcon, Delete as DeleteIcon,
  ReceiptLong as ReceiptIcon, TableChart as ExcelIcon,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { teacherHoursApi, teacherApi, classApi, subjectApi, academicYearApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { hasPermission, hasRole } from '../../utils/auth'
import { formatCurrency, formatDate, downloadBlob } from '../../utils/format'

const METHODS = ['CASH', 'MOBILE_MONEY', 'BANK_TRANSFER', 'CHECK', 'CARD']
const METHOD_LABEL = {
  CASH: 'Espèces',
  MOBILE_MONEY: 'Mobile Money (Orange/Moov)',
  BANK_TRANSFER: 'Virement bancaire',
  CHECK: 'Chèque',
  CARD: 'Carte',
}
const STATUS_LABEL = { PENDING: 'Non payé', PARTIAL: 'Partiellement payé', PAID: 'Payé' }
const MONTHS_FR = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet',
  'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre']

/**
 * Paie des enseignants à l'heure : tarifs horaires, saisie quotidienne des
 * heures, historique, calcul mensuel (heures × tarif), paiements totaux ou
 * partiels, reçus PDF, rapports et clôture mensuelle.
 */
export default function TeacherPayrollPage({ initialTab = 'monthly' }) {
  const { success, error: toastError } = useToast()
  const [tab, setTab] = useState(initialTab)
  const [teachers, setTeachers] = useState([])
  const [classes, setClasses] = useState([])
  const [subjects, setSubjects] = useState([])
  const [years, setYears] = useState([])
  const [loading, setLoading] = useState(false)

  // Permissions alignées sur SecurityConfig :
  // HOURS_WRITE = saisie/correction des heures, HOURS_PAY = paiement,
  // clôture/réouverture réservée à la direction.
  const canWrite = hasPermission('HOURS_WRITE')
  const canPay = hasPermission('HOURS_PAY')
  const canCloseMonth = hasRole('SUPER_ADMIN') || hasRole('DIRECTEUR')

  // Tarifs horaires
  const [rates, setRates] = useState([])
  const [rateDialog, setRateDialog] = useState(false)
  const [rateForm, setRateForm] = useState({ teacherId: '', hourlyRate: '', startDate: '', endDate: '' })
  const [toToggleRate, setToToggleRate] = useState(null)

  // Saisie quotidienne
  const [entryDate, setEntryDate] = useState(new Date().toISOString().slice(0, 10))
  const [entryForm, setEntryForm] = useState({ teacherId: '', subjectId: '', classId: '', hours: '', observation: '' })
  const [dayEntries, setDayEntries] = useState([])
  const [editingEntry, setEditingEntry] = useState(null)
  const [toDeleteEntry, setToDeleteEntry] = useState(null)

  // Historique
  const [history, setHistory] = useState([])
  const [historyFilters, setHistoryFilters] = useState({ teacherId: '', from: '', to: '' })
  const [historyPage, setHistoryPage] = useState(0)
  const [historySize, setHistorySize] = useState(10)
  const [historyTotal, setHistoryTotal] = useState(0)

  // Paie mensuelle
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7))
  const [rows, setRows] = useState([])
  const [monthClosed, setMonthClosed] = useState(false)
  const [payRow, setPayRow] = useState(null)
  const [payForm, setPayForm] = useState({ amount: '', method: 'CASH', reference: '', observation: '' })

  // Paiements
  const [txs, setTxs] = useState([])
  const [txFilters, setTxFilters] = useState({ teacherId: '', month: '' })

  useEffect(() => {
    teacherApi.search({ page: 0, size: 500 }).then((r) => setTeachers(r.data.data.content)).catch(() => {})
    classApi.all().then((r) => setClasses(r.data.data || [])).catch(() => {})
    subjectApi.all().then((r) => setSubjects(r.data.data || [])).catch(() => {})
    academicYearApi.all().then((r) => setYears(r.data.data || [])).catch(() => {})
  }, [])

  useEffect(() => {
    teacherHoursApi.monthStatus(`${month}-01`)
      .then((r) => setMonthClosed(Boolean(r.data.data))).catch(() => {})
  }, [month])

  // ---------- Chargements ----------

  const loadRates = async () => {
    try {
      const { data } = await teacherHoursApi.rates({})
      setRates(data.data)
    } catch (err) { toastError(extractError(err)) }
  }

  const loadDay = async () => {
    if (!entryForm.teacherId) { setDayEntries([]); return }
    try {
      const { data } = await teacherHoursApi.workHoursOfDay({
        teacherId: entryForm.teacherId, date: entryDate,
      })
      setDayEntries(data.data)
    } catch (err) { toastError(extractError(err)) }
  }

  const loadHistory = async (p = 0, s = historySize) => {
    setLoading(true)
    try {
      const params = { page: p, size: s }
      if (historyFilters.teacherId) params.teacherId = historyFilters.teacherId
      if (historyFilters.from) params.from = historyFilters.from
      if (historyFilters.to) params.to = historyFilters.to
      const { data } = await teacherHoursApi.workHours(params)
      setHistory(data.data.content)
      setHistoryTotal(data.data.totalElements)
      setHistoryPage(p)
    } catch (err) { toastError(extractError(err)) } finally { setLoading(false) }
  }

  const loadMonthly = async () => {
    try {
      const { data } = await teacherHoursApi.monthly({ month: `${month}-01` })
      setRows(data.data)
    } catch (err) { toastError(extractError(err)) }
  }

  const loadTxs = async () => {
    try {
      const params = {}
      if (txFilters.teacherId) params.teacherId = txFilters.teacherId
      if (txFilters.month) params.month = `${txFilters.month}-01`
      const { data } = await teacherHoursApi.transactions(params)
      setTxs(data.data)
    } catch (err) { toastError(extractError(err)) }
  }

  useEffect(() => { if (tab === 'rates') loadRates() }, [tab])
  useEffect(() => { if (tab === 'history') loadHistory(0) }, [tab, historyFilters])
  useEffect(() => { loadDay() }, [entryForm.teacherId, entryDate])
  useEffect(() => { if (tab === 'monthly') loadMonthly() }, [tab, month])
  useEffect(() => { if (tab === 'payments') loadTxs() }, [tab, txFilters])

  // ---------- Actions ----------

  const saveRate = async () => {
    try {
      await teacherHoursApi.createRate({
        teacherId: Number(rateForm.teacherId),
        hourlyRate: Number(rateForm.hourlyRate),
        startDate: rateForm.startDate,
        endDate: rateForm.endDate || null,
      })
      success('Tarif horaire enregistré')
      setRateDialog(false)
      setRateForm({ teacherId: '', hourlyRate: '', startDate: '', endDate: '' })
      loadRates()
    } catch (err) { toastError(extractError(err)) }
  }

  const toggleRate = async () => {
    try {
      await teacherHoursApi.toggleRate(toToggleRate.id, !toToggleRate.active)
      success(toToggleRate.active ? 'Tarif désactivé' : 'Tarif activé')
      setToToggleRate(null)
      loadRates()
    } catch (err) { toastError(extractError(err)) }
  }

  const saveEntry = async () => {
    const payload = {
      teacherId: Number(entryForm.teacherId),
      date: entryDate,
      hours: Number(entryForm.hours),
      subjectId: entryForm.subjectId || null,
      classId: entryForm.classId || null,
      observation: entryForm.observation,
    }
    try {
      if (editingEntry) {
        await teacherHoursApi.updateHours(editingEntry.id, payload)
        success('Saisie corrigée')
      } else {
        await teacherHoursApi.recordHours(payload)
        success('Heures enregistrées')
      }
      setEditingEntry(null)
      setEntryForm({ ...entryForm, hours: '', observation: '' })
      loadDay()
    } catch (err) { toastError(extractError(err)) }
  }

  const deleteEntry = async () => {
    try {
      await teacherHoursApi.deleteHours(toDeleteEntry.id)
      success('Saisie supprimée')
      setToDeleteEntry(null)
      loadDay()
    } catch (err) { toastError(extractError(err)) }
  }

  const doPay = async () => {
    try {
      await teacherHoursApi.pay({
        teacherId: payRow.teacherId,
        monthDate: `${month}-01`,
        amount: Number(payForm.amount),
        method: payForm.method,
        reference: payForm.reference,
        observation: payForm.observation,
      })
      success('Paiement enregistré — le reçu est disponible dans « Paiements »')
      setPayRow(null)
      setPayForm({ amount: '', method: 'CASH', reference: '', observation: '' })
      loadMonthly()
    } catch (err) { toastError(extractError(err)) }
  }

  const download = async (fn, filename) => {
    try {
      const r = await fn()
      downloadBlob(r.data, filename)
    } catch (err) { toastError(extractError(err)) }
  }

  const monthLabel = (m) => {
    if (!m) return '—'
    const [y, mo] = m.split('-')
    return `${MONTHS_FR[Number(mo) - 1]} ${y}`
  }

  // ---------- Colonnes ----------

  const rateColumns = [
    { key: 'teacherName', label: 'Enseignant', render: (r) => <b>{r.teacherName}</b> },
    { key: 'hourlyRate', label: 'Tarif horaire', render: (r) => <b>{formatCurrency(r.hourlyRate)}</b> },
    { key: 'startDate', label: 'Début de validité', render: (r) => formatDate(r.startDate) },
    { key: 'endDate', label: 'Fin de validité', render: (r) => (r.endDate ? formatDate(r.endDate) : '—') },
    { key: 'academicYearLabel', label: 'Année scolaire', render: (r) => r.academicYearLabel || '—' },
    {
      key: 'active', label: 'Statut',
      render: (r) => (
        <Chip size="small" color={r.active ? 'success' : 'default'}
          label={r.active ? 'Actif' : 'Inactif'} />
      ),
    },
    {
      key: 'actions2', label: '',
      render: (r) => (
        <Tooltip title={r.active ? 'Désactiver' : 'Réactiver'}>
          <IconButton size="small" color={r.active ? 'error' : 'success'}
            onClick={() => setToToggleRate(r)}>
            {r.active ? <DeleteIcon fontSize="small" /> : <RatesIcon fontSize="small" />}
          </IconButton>
        </Tooltip>
      ),
    },
  ]

  const historyColumns = [
    { key: 'date', label: 'Date', render: (r) => formatDate(r.date) },
    { key: 'teacherName', label: 'Professeur', render: (r) => <b>{r.teacherName}</b> },
    { key: 'subjectName', label: 'Matière', render: (r) => r.subjectName || '—' },
    { key: 'className', label: 'Classe', render: (r) => r.className || '—' },
    { key: 'hours', label: 'Heures', render: (r) => `${Number(r.hours)} h` },
    { key: 'hourlyRateApplied', label: 'Tarif horaire', render: (r) => formatCurrency(r.hourlyRateApplied) },
    { key: 'amount', label: 'Montant', render: (r) => <b>{formatCurrency(r.amount)}</b> },
  ]

  const monthlyColumns = [
    { key: 'teacherName', label: 'Professeur', render: (r) => <b>{r.teacherName}</b> },
    { key: 'totalHours', label: 'Heures', render: (r) => `${Number(r.totalHours)} h` },
    { key: 'hourlyRate', label: 'Tarif/h', render: (r) => formatCurrency(r.hourlyRate) },
    { key: 'totalAmount', label: 'Salaire brut', render: (r) => <b>{formatCurrency(r.totalAmount)}</b> },
    { key: 'amountPaid', label: 'Déjà payé', render: (r) => formatCurrency(r.amountPaid) },
    { key: 'remainingAmount', label: 'Reste à payer', render: (r) => formatCurrency(r.remainingAmount) },
    {
      key: 'status', label: 'Statut',
      render: (r) => (
        <Chip size="small" color={r.status === 'PAID' ? 'success' : r.status === 'PARTIAL' ? 'warning' : 'error'}
          label={STATUS_LABEL[r.status] || r.status} />
      ),
    },
    {
      key: 'actions2', label: '',
      render: (r) =>
        canPay && r.status !== 'PAID' && r.totalAmount > 0 ? (
          <Button size="small" color="success" startIcon={<PayIcon />}
            onClick={() => { setPayRow(r); setPayForm({ ...payForm, amount: String(r.remainingAmount) }) }}>
            Payer
          </Button>
        ) : null,
    },
  ]

  const txColumns = [
    { key: 'receiptNo', label: 'N° reçu', render: (r) => <b>{r.receiptNo}</b> },
    { key: 'teacherName', label: 'Professeur' },
    { key: 'monthDate', label: 'Mois', render: (r) => monthLabel(String(r.monthDate).slice(0, 7)) },
    { key: 'amount', label: 'Montant payé', render: (r) => <b>{formatCurrency(r.amount)}</b> },
    { key: 'method', label: 'Mode', render: (r) => METHOD_LABEL[r.method] || r.method },
    { key: 'paymentDate', label: 'Date', render: (r) => formatDate(r.paymentDate) },
    { key: 'reference', label: 'Référence', render: (r) => r.reference || '—' },
    {
      key: 'receipt', label: '',
      render: (r) => (
        <Tooltip title="Télécharger le reçu PDF">
          <IconButton size="small" color="primary"
            onClick={() => download(() => teacherHoursApi.receipt(r.id), `recu-${r.receiptNo}.pdf`)}>
            <ReceiptIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      ),
    },
  ]

  const dayColumns = [
    { key: 'subjectName', label: 'Matière', render: (r) => r.subjectName || '—' },
    { key: 'className', label: 'Classe', render: (r) => r.className || '—' },
    { key: 'hours', label: 'Heures', render: (r) => `${Number(r.hours)} h` },
    { key: 'amount', label: 'Montant', render: (r) => formatCurrency(r.amount) },
    {
      key: 'actions2', label: '',
      render: (r) => (
        <>
          <Tooltip title="Corriger">
            <IconButton size="small" onClick={() => {
              setEditingEntry(r)
              setEntryForm({
                teacherId: String(r.teacherId),
                subjectId: r.subjectId ? String(r.subjectId) : '',
                classId: r.classId ? String(r.classId) : '',
                hours: String(Number(r.hours)),
                observation: r.observation || '',
              })
              setEntryDate(r.date)
            }}>
              <EditIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Supprimer">
            <IconButton size="small" color="error" onClick={() => setToDeleteEntry(r)}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Paie des enseignants"
        subtitle="Heures enseignées, calcul mensuel (heures × tarif) et paiements"
        actionLabel={tab === 'rates' && canWrite ? 'Nouveau tarif horaire' : undefined}
        onAction={() => setRateDialog(true)}
        actions={tab === 'monthly' ? [
          canCloseMonth && {
            label: monthClosed ? 'Réouvrir le mois' : 'Clôturer le mois',
            icon: monthClosed ? <LockOpen /> : <Lock />,
            color: monthClosed ? 'warning' : 'inherit',
            onClick: async () => {
              try {
                if (monthClosed) {
                  await teacherHoursApi.reopenMonth(`${month}-01`)
                  success('Mois réouvert')
                } else {
                  await teacherHoursApi.closeMonth(`${month}-01`)
                  success('Mois clôturé : les heures sont verrouillées')
                }
                setMonthClosed(!monthClosed)
                loadMonthly()
              } catch (err) { toastError(extractError(err)) }
            },
          },
          {
            label: 'PDF',
            icon: <DownloadIcon />,
            onClick: () => download(() => teacherHoursApi.reportPdf({ month: `${month}-01` }),
              `paie-enseignants-${month}.pdf`),
          },
          {
            label: 'Excel',
            icon: <ExcelIcon />,
            onClick: () => download(() => teacherHoursApi.reportExcel({ month: `${month}-01` }),
              `paie-enseignants-${month}.xlsx`),
          },
        ].filter(Boolean) : []}
      />

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2.5 }} variant="scrollable" allowScrollButtonsMobile>
        <Tab icon={<RatesIcon fontSize="small" />} iconPosition="start" label="Tarifs horaires" value="rates" />
        {canWrite && (
          <Tab icon={<HoursIcon fontSize="small" />} iconPosition="start" label="Saisie quotidienne" value="entry" />
        )}
        <Tab icon={<HistoryIcon fontSize="small" />} iconPosition="start" label="Historique des heures" value="history" />
        <Tab icon={<PayIcon fontSize="small" />} iconPosition="start" label="Paie mensuelle" value="monthly" />
        {canPay && (
          <Tab icon={<ReceiptIcon fontSize="small" />} iconPosition="start" label="Paiements" value="payments" />
        )}
      </Tabs>

      {/* ---- Tarifs horaires ---- */}
      {tab === 'rates' && (
        <DataTable columns={rateColumns} rows={rates} searchable title="Tarifs horaires des enseignants" />
      )}

      {/* ---- Saisie quotidienne ---- */}
      {tab === 'entry' && (
        <Grid container spacing={2.5}>
          <Grid item xs={12} md={5}>
            <FilterCard title="Enregistrer les heures du jour">
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth type="date" label="Date" value={entryDate}
                    onChange={(e) => setEntryDate(e.target.value)} InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth select label="Professeur" value={entryForm.teacherId}
                    onChange={(e) => setEntryForm({ ...entryForm, teacherId: e.target.value })}>
                    {teachers.map((t) => (
                      <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth select label="Matière" value={entryForm.subjectId}
                    onChange={(e) => setEntryForm({ ...entryForm, subjectId: e.target.value })}>
                    <MenuItem value="">—</MenuItem>
                    {subjects.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.name}</MenuItem>)}
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth select label="Classe" value={entryForm.classId}
                    onChange={(e) => setEntryForm({ ...entryForm, classId: e.target.value })}>
                    <MenuItem value="">—</MenuItem>
                    {classes.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>)}
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth type="number" label="Nombre d'heures" value={entryForm.hours}
                    inputProps={{ min: 0.5, max: 24, step: 0.5 }}
                    onChange={(e) => setEntryForm({ ...entryForm, hours: e.target.value })} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth label="Observation" value={entryForm.observation}
                    onChange={(e) => setEntryForm({ ...entryForm, observation: e.target.value })} />
                </Grid>
                <Grid item xs={12} display="flex" gap={1.5}>
                  <Button variant="contained"
                    disabled={monthClosed || !entryForm.teacherId || !entryForm.hours}
                    onClick={saveEntry}>
                    {editingEntry ? 'Enregistrer la correction' : 'Enregistrer les heures'}
                  </Button>
                  {editingEntry && (
                    <Button onClick={() => {
                      setEditingEntry(null)
                      setEntryForm({ ...entryForm, hours: '', observation: '' })
                    }}>
                      Annuler
                    </Button>
                  )}
                </Grid>
                {monthClosed && (
                  <Grid item xs={12}>
                    <Alert severity="warning">{monthLabel(month)} est clôturé : saisie verrouillée.</Alert>
                  </Grid>
                )}
              </Grid>
            </FilterCard>
          </Grid>
          <Grid item xs={12} md={7}>
            <DataTable columns={dayColumns} rows={dayEntries} searchable={false} title="Saisies du jour" />
          </Grid>
        </Grid>
      )}

      {/* ---- Historique des heures ---- */}
      {tab === 'history' && (
        <>
          <FilterCard title="Filtres">
            <Grid container spacing={2}>
              <Grid item xs={12} sm={4}>
                <TextField fullWidth select label="Professeur" value={historyFilters.teacherId}
                  onChange={(e) => setHistoryFilters({ ...historyFilters, teacherId: e.target.value })}>
                  <MenuItem value="">Tous</MenuItem>
                  {teachers.map((t) => (
                    <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField fullWidth type="date" label="Du" value={historyFilters.from}
                  onChange={(e) => setHistoryFilters({ ...historyFilters, from: e.target.value })}
                  InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField fullWidth type="date" label="Au" value={historyFilters.to}
                  onChange={(e) => setHistoryFilters({ ...historyFilters, to: e.target.value })}
                  InputLabelProps={{ shrink: true }} />
              </Grid>
            </Grid>
          </FilterCard>
          <DataTable
            columns={historyColumns}
            rows={history}
            loading={loading}
            searchable={false}
            actions={false}
            title="Historique des heures enseignées"
            page={historyPage}
            size={historySize}
            total={historyTotal}
            onPageChange={(p) => loadHistory(p, historySize)}
            onSizeChange={(s) => loadHistory(0, s)}
          />
        </>
      )}

      {/* ---- Paie mensuelle ---- */}
      {tab === 'monthly' && (
        <>
          <FilterCard title="Paramètres du mois" actions={
            <Chip size="small" color={monthClosed ? 'error' : 'success'}
              icon={monthClosed ? <Lock /> : <LockOpen />}
              label={monthClosed ? 'Mois clôturé' : 'Mois ouvert'} />
          }>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={4}>
                <TextField fullWidth type="month" label="Mois" value={month}
                  onChange={(e) => setMonth(e.target.value)} InputLabelProps={{ shrink: true }} />
              </Grid>
            </Grid>
          </FilterCard>
          <Box mt={2}>
            <DataTable columns={monthlyColumns} rows={rows} searchable={false}
              title={`Calcul des salaires — ${monthLabel(month)}`} />
          </Box>
        </>
      )}

      {/* ---- Paiements ---- */}
      {tab === 'payments' && (
        <>
          <FilterCard title="Filtres">
            <Grid container spacing={2}>
              <Grid item xs={12} sm={4}>
                <TextField fullWidth select label="Professeur" value={txFilters.teacherId}
                  onChange={(e) => setTxFilters({ ...txFilters, teacherId: e.target.value })}>
                  <MenuItem value="">Tous</MenuItem>
                  {teachers.map((t) => (
                    <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField fullWidth type="month" label="Mois" value={txFilters.month}
                  onChange={(e) => setTxFilters({ ...txFilters, month: e.target.value })}
                  InputLabelProps={{ shrink: true }} />
              </Grid>
            </Grid>
          </FilterCard>
          <Box mt={2}>
            <DataTable columns={txColumns} rows={txs} searchable={false}
              title="Historique des paiements des enseignants" />
          </Box>
        </>
      )}
    {/* ---- Dialogue : nouveau tarif horaire ---- */}
      <Dialog open={rateDialog} onClose={() => setRateDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Nouveau tarif horaire</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth select label="Enseignant" value={rateForm.teacherId}
                onChange={(e) => setRateForm({ ...rateForm, teacherId: e.target.value })}>
                {teachers.map((t) => (
                  <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="number" label="Montant par heure (FCFA)" value={rateForm.hourlyRate}
                onChange={(e) => setRateForm({ ...rateForm, hourlyRate: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="date" label="Début de validité" value={rateForm.startDate}
                onChange={(e) => setRateForm({ ...rateForm, startDate: e.target.value })}
                InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="date" label="Fin de validité (facultatif)" value={rateForm.endDate}
                onChange={(e) => setRateForm({ ...rateForm, endDate: e.target.value })}
                InputLabelProps={{ shrink: true }} />
            </Grid>
          </Grid>
          <Alert severity="info" sx={{ mt: 2 }}>
            Le tarif est historisé : les heures déjà saisies conservent le tarif appliqué au moment
            de leur enregistrement.
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRateDialog(false)}>Annuler</Button>
          <Button variant="contained" onClick={saveRate}
            disabled={!rateForm.teacherId || !rateForm.hourlyRate || !rateForm.startDate}>
            Enregistrer
          </Button>
        </DialogActions>
      </Dialog>

      {/* ---- Dialogue : paiement total ou partiel ---- */}
      <Dialog open={Boolean(payRow)} onClose={() => setPayRow(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Paiement du salaire — {payRow?.teacherName}</DialogTitle>
        <DialogContent>
          {payRow && (
            <>
              <Typography variant="body2" color="text.secondary" mb={2}>
                {Number(payRow.totalHours)} h × {formatCurrency(payRow.hourlyRate)} ={' '}
                <b>{formatCurrency(payRow.totalAmount)}</b> · reste à payer :{' '}
                <b>{formatCurrency(payRow.remainingAmount)}</b>
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth type="number" label="Montant payé (FCFA)" value={payForm.amount}
                    onChange={(e) => setPayForm({ ...payForm, amount: e.target.value })} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth select label="Mode de paiement" value={payForm.method}
                    onChange={(e) => setPayForm({ ...payForm, method: e.target.value })}>
                    {METHODS.map((m) => (
                      <MenuItem key={m} value={m}>{METHOD_LABEL[m]}</MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth label="Référence (facultatif)" value={payForm.reference}
                    onChange={(e) => setPayForm({ ...payForm, reference: e.target.value })} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth label="Observation (facultatif)" value={payForm.observation}
                    onChange={(e) => setPayForm({ ...payForm, observation: e.target.value })} />
                </Grid>
              </Grid>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPayRow(null)}>Annuler</Button>
          <Button variant="contained" color="success" onClick={doPay}
            disabled={!payForm.amount || Number(payForm.amount) <= 0}>
            Enregistrer le paiement
          </Button>
        </DialogActions>
      </Dialog>

      {/* ---- Confirmations ---- */}
      <ConfirmDialog
        open={Boolean(toToggleRate)}
        title={toToggleRate?.active ? 'Désactiver le tarif' : 'Réactiver le tarif'}
        message={toToggleRate
          ? `Confirmez-vous cette opération sur le tarif horaire de ${toToggleRate.teacherName} ?`
          : ''}
        confirmLabel={toToggleRate?.active ? 'Désactiver' : 'Réactiver'}
        onClose={() => setToToggleRate(null)}
        onConfirm={toggleRate}
      />
      <ConfirmDialog
        open={Boolean(toDeleteEntry)}
        title="Supprimer la saisie"
        message={toDeleteEntry
          ? `Supprimer la saisie de ${Number(toDeleteEntry.hours)} h du ${formatDate(toDeleteEntry.date)} ?`
          : ''}
        confirmLabel="Supprimer"
        onClose={() => setToDeleteEntry(null)}
        onConfirm={deleteEntry}
      />

    </>
  )
}
