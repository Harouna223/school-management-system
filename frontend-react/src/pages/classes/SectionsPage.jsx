import { useState, useEffect } from 'react'
import { Grid, TextField, Button, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material'
import { Add } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { classApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

const empty = { name: '', description: '' }

/**
 * Gestion des sections pédagogiques.
 */
export default function SectionsPage() {
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
      const res = await classApi.sections()
      setRows(res.data.data || [])
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const openNew = () => { setEditing(null); setForm(empty); setDialogOpen(true) }
  const openEdit = (row) => { setEditing(row); setForm({ name: row.name, description: row.description || '' }); setDialogOpen(true) }

  const handleSave = async () => {
    try {
      if (editing) {
        await classApi.updateSection(editing.id, form)
        success('Section modifiée')
      } else {
        await classApi.createSection(form)
        success('Section créée')
      }
      setDialogOpen(false)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await classApi.deleteSection(toDelete.id)
      success('Section supprimée')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    { key: 'name', label: 'Nom', render: (r) => <b>{r.name}</b> },
    { key: 'description', label: 'Description', render: (r) => r.description || '—' },
  ]

  return (
    <>
      <PageHeader
        title="Sections"
        subtitle="Référentiel des sections pédagogiques"
        actionLabel="Nouvelle section"
        onAction={openNew}
      />
      <DataTable
        title="Liste des sections"
        columns={columns}
        rows={rows}
        loading={loading}
        onEdit={openEdit}
        onDelete={setToDelete}
      />

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={800}>{editing ? 'Modifier la section' : 'Nouvelle section'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField fullWidth label="Nom" value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth multiline minRows={2} label="Description (optionnel)" value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })} />
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
        title="Supprimer la section"
        message={`Supprimer la section « ${toDelete?.name} » ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}
