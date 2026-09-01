import { useState, useEffect } from 'react'
import { Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material'
import { AccountBalanceWallet, Payments, ReceiptLong, AddCard, TrendingDown, TableView, PictureAsPdf } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import StatCard from '../../components/StatCard'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { financeApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatCurrency, formatDate, downloadBlob } from '../../utils/format'

/**
 * Comptabilité : dépenses, catégories, synthèse financière.
 */
export default function ExpensesPage() {
  const { success, error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [categories, setCategories] = useState([])
  const [summary, setSummary] = useState(null)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [categoryFilter, setCategoryFilter] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [form, setForm] = useState({ description: '', amount: '', categoryId: '', expenseDate: '' })
  const [toDelete, setToDelete] = useState(null)

  const load = async () => {
    try {
      const { data } = await financeApi.expenses({
        categoryId: categoryFilter || undefined,
        page, size,
      })
      setRows(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    financeApi.categories().then((r) => setCategories(r.data.data)).catch(() => {})
    financeApi.summary().then((r) => setSummary(r.data.data)).catch(() => {})
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, categoryFilter])

  const handleCreate = async () => {
    try {
      await financeApi.createExpense({
        description: form.description,
        amount: Number(form.amount),
        categoryId: Number(form.categoryId),
        expenseDate: form.expenseDate,
      })
      success('Dépense enregistrée')
      setDialogOpen(false)
      setForm({ description: '', amount: '', categoryId: '', expenseDate: '' })
      load()
      financeApi.summary().then((r) => setSummary(r.data.data))
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await financeApi.deleteExpense(toDelete.id)
      success('Dépense supprimée')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleExportExcel = async () => {
    try {
      const res = await financeApi.exportExpensesExcel({ categoryId: categoryFilter || undefined })
      downloadBlob(res.data, 'depenses.xlsx')
      success('Export Excel généré')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleExportPdf = async () => {
    try {
      const res = await financeApi.exportExpensesPdf({ categoryId: categoryFilter || undefined })
      downloadBlob(res.data, 'depenses.pdf')
      success('Export PDF généré (avec total)')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    { key: 'description', label: 'Description', render: (r) => <b>{r.description}</b> },
    { key: 'categoryName', label: 'Catégorie' },
    { key: 'amount', label: 'Montant', render: (r) => formatCurrency(r.amount) },
    { key: 'expenseDate', label: 'Date', render: (r) => formatDate(r.expenseDate) },
    { key: 'paidByName', label: 'Enregistré par', render: (r) => r.paidByName || '—' },
  ]

  return (
    <>
      <PageHeader
        title="Dépenses"
        subtitle="Comptabilité et rapports financiers"
        badge={total ? `${total} dépense(s)` : undefined}
        actionLabel="Nouvelle dépense"
        onAction={() => setDialogOpen(true)}
        actions={[
          { label: 'Export PDF', icon: <PictureAsPdf sx={{ fontSize: 18 }} />, onClick: handleExportPdf },
          { label: 'Export Excel', icon: <TableView sx={{ fontSize: 18 }} />, onClick: handleExportExcel },
        ]}
      />

      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3} className="stagger-1">
          <StatCard title="Dépenses du mois" value={formatCurrency(summary?.monthlyExpenses)} icon={TrendingDown} color="danger" loading={!summary} />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-2">
          <StatCard title="Recettes du mois" value={formatCurrency(summary?.monthlyRevenue)} icon={Payments} color="success" loading={!summary} />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-3">
          <StatCard title="Dépenses totales" value={formatCurrency(summary?.totalExpenses)} icon={AccountBalanceWallet} color="warning" loading={!summary} />
        </Grid>
        <Grid item xs={12} sm={6} md={3} className="stagger-4">
          <StatCard title="Factures impayées" value={summary?.unpaidInvoices ?? 0} icon={ReceiptLong} color="info" loading={!summary} />
        </Grid>
      </Grid>

      <FilterCard mb={3} actions={
        <Button variant="contained" size="small" startIcon={<AddCard sx={{ fontSize: 17 }} />} onClick={() => setDialogOpen(true)}>
          Nouvelle dépense
        </Button>
      }>
        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <TextField select size="small" fullWidth label="Catégorie" value={categoryFilter}
              onChange={(e) => { setCategoryFilter(e.target.value); setPage(0) }}>
              <MenuItem value="">Toutes</MenuItem>
              {categories.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
            </TextField>
          </Grid>
        </Grid>
      </FilterCard>

      <DataTable
        title="Liste des dépenses"
        columns={columns}
        rows={rows}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={(s) => { setSize(s); setPage(0) }}
        searchable={false}
        onDelete={setToDelete}
      />

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={800}>Nouvelle dépense</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField fullWidth label="Description" value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField select fullWidth label="Catégorie" value={form.categoryId}
                onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
                {categories.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="number" label="Montant" value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth type="date" label="Date" value={form.expenseDate}
                onChange={(e) => setForm({ ...form, expenseDate: e.target.value })} InputLabelProps={{ shrink: true }} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDialogOpen(false)} color="inherit">Annuler</Button>
          <Button variant="contained" onClick={handleCreate} disabled={!form.description || !form.amount || !form.categoryId || !form.expenseDate}>
            Enregistrer
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer la dépense"
        message={`Supprimer la dépense « ${toDelete?.description} » ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}