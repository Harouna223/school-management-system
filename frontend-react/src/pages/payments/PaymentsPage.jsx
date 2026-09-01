import { useState, useEffect } from 'react'
import {
  Grid, TextField, MenuItem, Button, Box,
  Dialog, DialogTitle, DialogContent, DialogActions, Tabs, Tab,
} from '@mui/material'
import { PictureAsPdf, Receipt, AddCard, Payments as PaymentsIcon, Payments, AccountBalanceWallet, TableView, WarningAmber, NotificationsActive, WhatsApp as WhatsAppIcon } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import StatCard from '../../components/StatCard'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { useFetch } from '../../hooks/useFetch'
import { paymentApi, studentApi, financeApi, classApi, dashboardApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatCurrency, formatDate, formatDateTime, downloadBlob } from '../../utils/format'
import { buildWhatsAppLink, getDefaultCountryCode } from '../../utils/whatsapp'
import StatusChip from '../../components/StatusChip'

/**
 * Module paiements : factures, enregistrement des paiements, reçus PDF.
 */
export default function PaymentsPage() {
  const { success, error: toastError } = useToast()
  const [tab, setTab] = useState('invoices')
  const [invoices, setInvoices] = useState([])
  const [payments, setPayments] = useState([])
  const [students, setStudents] = useState([])
  const [feeTypes, setFeeTypes] = useState([])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [statusFilter, setStatusFilter] = useState('')
  const [studentFilter, setStudentFilter] = useState('')
  const [invoiceDialog, setInvoiceDialog] = useState(false)
  const [paymentDialog, setPaymentDialog] = useState(false)
  const [invoiceForm, setInvoiceForm] = useState({ studentId: '', feeTypeId: '', amount: '', discount: '', dueDate: '' })
  const [paymentForm, setPaymentForm] = useState({ invoiceId: '', amount: '', method: 'CASH', note: '' })
  const [classes, setClasses] = useState([])
  const [classFilter, setClassFilter] = useState('')

  const { data: stats, loading: statsLoading } = useFetch(() => dashboardApi.stats())

  const loadInvoices = async () => {
    try {
      const { data } = await paymentApi.searchInvoices({
        studentId: studentFilter || undefined,
        status: statusFilter || undefined,
        page, size,
      })
      setInvoices(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const loadPayments = async () => {
    try {
      const { data } = await paymentApi.search({ page, size })
      setPayments(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    studentApi.search({ page: 0, size: 500 }).then((r) => setStudents(r.data.data.content)).catch(() => {})
    financeApi.feeTypes().then((r) => setFeeTypes(r.data.data)).catch(() => {})
    classApi.all().then((r) => setClasses(r.data.data)).catch(() => {})
    getDefaultCountryCode().catch(() => {})
  }, [])

  const filteredStudents = classFilter
    ? students.filter((s) => String(s.classId) === String(classFilter))
    : students

  useEffect(() => {
    if (tab === 'invoices') loadInvoices()
    else loadPayments()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, page, size, statusFilter, studentFilter])

  const createInvoice = async () => {
    try {
      await paymentApi.createInvoice({
        studentId: Number(invoiceForm.studentId),
        feeTypeId: Number(invoiceForm.feeTypeId),
        amount: invoiceForm.amount ? Number(invoiceForm.amount) : undefined,
        discount: invoiceForm.discount ? Number(invoiceForm.discount) : undefined,
        dueDate: invoiceForm.dueDate,
      })
      success('Facture créée')
      setInvoiceDialog(false)
      setInvoiceForm({ studentId: '', feeTypeId: '', amount: '', discount: '', dueDate: '' })
      loadInvoices()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const recordPayment = async () => {
    try {
      const res = await paymentApi.record({
        invoiceId: Number(paymentForm.invoiceId),
        amount: Number(paymentForm.amount),
        method: paymentForm.method,
        note: paymentForm.note,
      })
      success(`Paiement enregistré — Reçu ${res.data.data.receiptNo}`)
      setPaymentDialog(false)
      setPaymentForm({ invoiceId: '', amount: '', method: 'CASH', note: '' })
      loadInvoices()
      loadPayments()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const downloadReceipt = async (payment) => {
    try {
      const res = await paymentApi.receiptPdf(payment.id)
      downloadBlob(res.data, `recu-${payment.receiptNo}.pdf`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleExportExcel = async () => {
    try {
      const res = tab === 'invoices'
        ? await paymentApi.exportInvoicesExcel({ studentId: studentFilter || undefined, status: statusFilter || undefined })
        : await paymentApi.exportExcel({ studentId: studentFilter || undefined })
      downloadBlob(res.data, tab === 'invoices' ? 'factures.xlsx' : 'paiements.xlsx')
      success('Export Excel généré')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleExportPdf = async () => {
    try {
      const res = tab === 'invoices'
        ? await paymentApi.exportInvoicesPdf({ studentId: studentFilter || undefined, status: statusFilter || undefined })
        : await paymentApi.exportPdf({ studentId: studentFilter || undefined })
      downloadBlob(res.data, tab === 'invoices' ? 'factures.pdf' : 'paiements.pdf')
      success('Export PDF généré (avec total)')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleMarkOverdue = async () => {
    try {
      const res = await financeApi.markOverdue()
      success(res.data.message)
      loadInvoices()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleRemind = async () => {
    try {
      const res = await financeApi.remind()
      success(res.data.message)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleWhatsAppReminder = async (invoice) => {
    const phone = invoice.parentPhone || invoice.studentPhone
    if (!phone) {
      toastError('Aucun numéro de téléphone disponible pour cette facture')
      return
    }
    const link = buildWhatsAppLink(phone, `Bonjour, rappel : la facture ${invoice.invoiceNo} présente un solde restant de ${formatCurrency(invoice.remainingAmount)}. Merci de régulariser.`)
    if (link) window.open(link, '_blank')
  }

  const invoiceColumns = [
    { key: 'invoiceNo', label: 'Facture', render: (r) => <b>{r.invoiceNo}</b> },
    { key: 'student', label: 'Élève', render: (r) => `${r.studentName} (${r.matricule})` },
    { key: 'feeTypeName', label: 'Frais' },
    { key: 'amount', label: 'Montant', render: (r) => formatCurrency(r.amount) },
    { key: 'discount', label: 'Remise', render: (r) => (r.discount && Number(r.discount) > 0 ? `-${formatCurrency(r.discount)}` : '—') },
    { key: 'paidAmount', label: 'Payé', render: (r) => formatCurrency(r.paidAmount) },
    { key: 'remaining', label: 'Restant', render: (r) => formatCurrency(r.remainingAmount) },
    { key: 'dueDate', label: 'Échéance', render: (r) => formatDate(r.dueDate) },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'whatsapp',
      label: 'Relance WhatsApp',
      render: (r) => (
        <Button size="small" color="success" startIcon={<WhatsAppIcon />} disabled={!r.parentPhone && !r.studentPhone}
          onClick={() => handleWhatsAppReminder(r)}>
          WhatsApp
        </Button>
      ),
    },
  ]

  const paymentColumns = [
    { key: 'receiptNo', label: 'Reçu', render: (r) => <b>{r.receiptNo}</b> },
    { key: 'student', label: 'Élève', render: (r) => `${r.studentName} (${r.matricule})` },
    { key: 'invoiceNo', label: 'Facture' },
    { key: 'amount', label: 'Montant', render: (r) => formatCurrency(r.amount) },
    { key: 'method', label: 'Mode' },
    { key: 'paymentDate', label: 'Date', render: (r) => formatDateTime(r.paymentDate) },
    {
      key: 'receipt',
      label: 'Reçu PDF',
      render: (r) => (
        <Button size="small" startIcon={<PictureAsPdf />} onClick={() => downloadReceipt(r)}>
          PDF
        </Button>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Paiements & Reçus"
        subtitle="Facturation, encaissements et documents"
        badge={`${total} facture(s)`}
        actionLabel="Nouvelle facture"
        onAction={() => setInvoiceDialog(true)}
        actions={[
          { label: 'Passer en retard', icon: <WarningAmber />, onClick: handleMarkOverdue, color: 'warning' },
          { label: 'Relancer', icon: <NotificationsActive />, onClick: handleRemind, color: 'info' },
        ]}
      />

      {/* KPIs finance */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={4} className="stagger-1">
          <StatCard title="Recettes du mois" value={formatCurrency(stats?.monthlyRevenue)} icon={PaymentsIcon} color="success" loading={statsLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={4} className="stagger-2">
          <StatCard title="Factures impayées" value={stats?.unpaidInvoices ?? 0} icon={AccountBalanceWallet} color="danger" loading={statsLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={4} className="stagger-3">
          <StatCard title="Paiements enregistrés" value={stats?.totalPaymentsCount ?? 0} icon={Payments} color="info" loading={statsLoading} />
        </Grid>
      </Grid>

      {/* Onglets + actions */}
      <Box display="flex" alignItems="center" gap={1} mb={3} flexWrap="wrap">
        <Tabs
          value={tab}
          onChange={(_, v) => { setTab(v); setPage(0) }}
          sx={{ bgcolor: 'action.hover', borderRadius: '12px', p: 0.4, minHeight: 44, '& .MuiTabs-indicator': { display: 'none' } }}
        >
          <Tab
            label="Factures"
            value="invoices"
            sx={{
              borderRadius: '9px',
              minHeight: 36,
              '&.Mui-selected': {
                bgcolor: 'background.paper',
                boxShadow: '0 1px 3px rgba(15,23,42,0.08)',
                borderRadius: '9px',
                fontWeight: 700,
              },
            }}
          />
          <Tab
            label="Paiements"
            value="payments"
            sx={{
              borderRadius: '9px',
              minHeight: 36,
              '&.Mui-selected': {
                bgcolor: 'background.paper',
                boxShadow: '0 1px 3px rgba(15,23,42,0.08)',
                borderRadius: '9px',
                fontWeight: 700,
              },
            }}
          />
        </Tabs>
        <Box flexGrow={1} />
        <Button variant="outlined" startIcon={<PictureAsPdf />} onClick={handleExportPdf}>
          Export PDF
        </Button>
        <Button variant="outlined" startIcon={<TableView />} onClick={handleExportExcel}>
          Export Excel
        </Button>
        {tab === 'invoices' ? (
          <>
            <Button variant="outlined" color="warning" startIcon={<WarningAmber />} onClick={handleMarkOverdue}>
              Passer en retard
            </Button>
            <Button variant="contained" onClick={() => setInvoiceDialog(true)} startIcon={<AddCard />}>
              Nouvelle facture
            </Button>
          </>
        ) : null}
        <Button variant="contained" color="success" onClick={() => setPaymentDialog(true)} startIcon={<Receipt />}>
          Enregistrer un paiement
        </Button>
      </Box>

      {tab === 'invoices' && (
        <FilterCard mb={3}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <TextField select size="small" fullWidth label="Élève" value={studentFilter}
                onChange={(e) => { setStudentFilter(e.target.value); setPage(0) }}>
                <MenuItem value="">Tous</MenuItem>
                {students.map((s) => <MenuItem key={s.id} value={s.id}>{s.firstName} {s.lastName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField select size="small" fullWidth label="Statut" value={statusFilter}
                onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}>
                <MenuItem value="">Tous</MenuItem>
                <MenuItem value="UNPAID">Impayée</MenuItem>
                <MenuItem value="PARTIAL">Partielle</MenuItem>
                <MenuItem value="PAID">Payée</MenuItem>
                <MenuItem value="OVERDUE">En retard</MenuItem>
              </TextField>
            </Grid>
          </Grid>
        </FilterCard>
      )}

      {tab === 'invoices' && (
        <DataTable
          title="Liste des factures"
          columns={invoiceColumns}
          rows={invoices}
          page={page}
          size={size}
          total={total}
          onPageChange={setPage}
          onSizeChange={(s) => { setSize(s); setPage(0) }}
          searchable={false}
          actions={false}
        />
      )}

      {tab === 'payments' && (
        <DataTable
          title="Historique des paiements"
          columns={paymentColumns}
          rows={payments}
          page={page}
          size={size}
          total={total}
          onPageChange={setPage}
          onSizeChange={(s) => { setSize(s); setPage(0) }}
          searchable={false}
          actions={false}
        />
      )}

      {/* Dialog facture */}
      <Dialog open={invoiceDialog} onClose={() => setInvoiceDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={800}>Nouvelle facture</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Classe" value={classFilter}
                onChange={(e) => { setClassFilter(e.target.value); setInvoiceForm({ ...invoiceForm, studentId: '' }) }}>
                <MenuItem value="">Toutes les classes</MenuItem>
                {classes.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField select fullWidth label="Élève" value={invoiceForm.studentId}
                onChange={(e) => setInvoiceForm({ ...invoiceForm, studentId: e.target.value })}>
                {filteredStudents.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.firstName} {s.lastName} ({s.matricule})</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField select fullWidth label="Type de frais" value={invoiceForm.feeTypeId}
                onChange={(e) => setInvoiceForm({ ...invoiceForm, feeTypeId: e.target.value })}>
                {feeTypes.map((f) => <MenuItem key={f.id} value={String(f.id)}>{f.name} — {formatCurrency(f.amount)}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="date" label="Échéance" value={invoiceForm.dueDate}
                onChange={(e) => setInvoiceForm({ ...invoiceForm, dueDate: e.target.value })} InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth type="number" label="Montant (vide = tarif par défaut)" value={invoiceForm.amount}
                onChange={(e) => setInvoiceForm({ ...invoiceForm, amount: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth type="number" label="Remise (optionnel)" value={invoiceForm.discount}
                onChange={(e) => setInvoiceForm({ ...invoiceForm, discount: e.target.value })} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setInvoiceDialog(false)} color="inherit">Annuler</Button>
          <Button variant="contained" onClick={createInvoice} disabled={!invoiceForm.studentId || !invoiceForm.feeTypeId}>Créer</Button>
        </DialogActions>
      </Dialog>

      {/* Dialog paiement */}
      <Dialog open={paymentDialog} onClose={() => setPaymentDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={800}>Enregistrer un paiement</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Facture" value={paymentForm.invoiceId}
                onChange={(e) => setPaymentForm({ ...paymentForm, invoiceId: e.target.value })}>
                {invoices
                  .filter((i) => i.status !== 'PAID')
                  .map((i) => (
                    <MenuItem key={i.id} value={String(i.id)}>
                      {i.invoiceNo} — {i.studentName} — restant {formatCurrency(i.remainingAmount)}
                    </MenuItem>
                  ))}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="number" label="Montant" value={paymentForm.amount}
                onChange={(e) => setPaymentForm({ ...paymentForm, amount: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField select fullWidth label="Mode" value={paymentForm.method}
                onChange={(e) => setPaymentForm({ ...paymentForm, method: e.target.value })}>
                <MenuItem value="CASH">Espèces</MenuItem>
                <MenuItem value="CARD">Carte</MenuItem>
                <MenuItem value="BANK_TRANSFER">Virement</MenuItem>
                <MenuItem value="MOBILE_MONEY">Mobile Money</MenuItem>
                <MenuItem value="CHECK">Chèque</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth label="Note" value={paymentForm.note}
                onChange={(e) => setPaymentForm({ ...paymentForm, note: e.target.value })} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setPaymentDialog(false)} color="inherit">Annuler</Button>
          <Button variant="contained" onClick={recordPayment} disabled={!paymentForm.invoiceId || !paymentForm.amount}>
            Encaisser
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}