import { useState, useEffect } from 'react'
import { Grid, TextField, Button, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material'
import { Add } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { classApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

const empty = { name: '', capacity: 30, location: '' }

/**
 * Gestion des salles de classe.
 */
export default function RoomsPage() {
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
      const res = await classApi.rooms()
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
    setForm({ name: row.name, capacity: row.capacity ?? 30, location: row.location || '' })
    setDialogOpen(true)
  }

  const handleSave = async () => {
    try {
      const payload = { ...form, capacity: Number(form.capacity) || 30 }
      if (editing) {
        await classApi.updateRoom(editing.id, payload)
        success('Salle modifiée')
      } else {
        await classApi.createRoom(payload)
        success('Salle créée')
      }
      setDialogOpen(false)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await classApi.deleteRoom(toDelete.id)
      success('Salle supprimée')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    { key: 'name', label: 'Nom', render: (r) => <b>{r.name}</b> },
    { key: 'capacity', label: 'Capacité', render: (r) => `${r.capacity ?? 30} places` },
    { key: 'location', label: 'Localisation', render: (r) => r.location || '—' },
  ]

  return (
    <>
      <PageHeader
        title="Salles"
        subtitle="Référentiel des salles de classe"
        actionLabel="Nouvelle salle"
        onAction={openNew}
      />
      <DataTable
        title="Liste des salles"
        columns={columns}
        rows={rows}
        loading={loading}
        onEdit={openEdit}
        onDelete={setToDelete}
      />

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={800}>{editing ? 'Modifier la salle' : 'Nouvelle salle'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField fullWidth label="Nom" value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="number" label="Capacité" value={form.capacity}
                onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth label="Localisation (bâtiment, étage...)" value={form.location}
                onChange={(e) => setForm({ ...form, location: e.target.value })} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDialogOpen(false)} color="inherit">Annuler</Button>
          <Button variant="contained" startIcon={<Add />} onClick={handleSave} disabled={!form.name}>
            {editing ? 'Enregistrer' : 'Créer'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer la salle"
        message={`Supprimer la salle « ${toDelete?.name} » ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}
