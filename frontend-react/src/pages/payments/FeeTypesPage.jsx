import { useState, useEffect } from 'react'
import { Grid, TextField, Button, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material'
import { Add } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { financeApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatCurrency } from '../../utils/format'

const empty = { name: '', amount: '', description: '' }

/**
 * Gestion des types de frais scolaires.
 */
export default function FeeTypesPage() {
  const { success, error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(empty)
  const [toDelete, setToDelete] = useState(null)

  const load = async () => {
    setLoading(true)
    try {
      const res = await financeApi.feeTypes()
      setRows(res.data.data || [])
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const openNew = () => { setEditing(null); setForm(empty); setDialogOpen(true) }
  const openEdit = (row) => {
    setEditing(row)
    setForm({ name: row.name, amount: String(row.amount ?? ''), description: row.description || '' })
    setDialogOpen(true)
  }

  const handleSave = async () => {
    try {
      const payload = { name: form.name, amount: Number(form.amount), description: form.description }
      if (editing) {
        await financeApi.updateFeeType(editing.id, payload)
        success('Type de frais modifié')
      } else {
        await financeApi.createFeeType(payload)
        success('Type de frais créé')
      }
      setDialogOpen(false)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await financeApi.deleteFeeType(toDelete.id)
      success('Type de frais supprimé')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    { key: 'name', label: 'Nom', render: (r) => <b>{r.name}</b> },
    { key: 'amount', label: 'Montant', render: (r) => formatCurrency(r.amount) },
    { key: 'description', label: 'Description', render: (r) => r.description || '—' },
  ]

  return (
    <>
      <PageHeader
        title="Types de frais"
        subtitle="Référentiel des frais scolaires (scolarité, cantine, transport...)"
        actionLabel="Nouveau type de frais"
        onAction={openNew}
      />
      <DataTable
        title="Liste des types de frais"
        columns={columns}
        rows={rows}
        loading={loading}
        onEdit={openEdit}
        onDelete={setToDelete}
      />

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={800}>{editing ? 'Modifier le type de frais' : 'Nouveau type de frais'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField fullWidth label="Nom" value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth type="number" label="Montant" value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth multiline minRows={2} label="Description (optionnel)" value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDialogOpen(false)} color="inherit">Annuler</Button>
          <Button variant="contained" startIcon={<Add />} onClick={handleSave} disabled={!form.name || !form.amount}>
            {editing ? 'Enregistrer' : 'Créer'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer le type de frais"
        message={`Supprimer le type de frais « ${toDelete?.name} » ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}
