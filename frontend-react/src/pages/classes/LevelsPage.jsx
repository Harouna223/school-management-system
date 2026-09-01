import { useState, useEffect } from 'react'
import { Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material'
import { Add } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { classApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

const CYCLES = [
  { value: 'JARDIN', label: 'Jardin' },
  { value: 'PRIMAIRE', label: 'Primaire' },
  { value: 'COLLEGE', label: 'Collège' },
  { value: 'LYCEE', label: 'Lycée' },
  { value: 'UNIVERSITE', label: 'Université' },
]

const empty = { name: '', code: '', educationCycle: '' }

/**
 * Gestion des niveaux scolaires (avec cycle d'enseignement : jardin, primaire, collège, lycée, université).
 */
export default function LevelsPage() {
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
      const res = await classApi.levels()
      setRows(res.data.data || [])
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const openNew = () => { setEditing(null); setForm(empty); setDialogOpen(true) }
  const openEdit = (row) => { setEditing(row); setForm({ name: row.name, code: row.code, educationCycle: row.educationCycle || '' }); setDialogOpen(true) }

  const handleSave = async () => {
    try {
      if (editing) {
        await classApi.updateLevel(editing.id, form)
        success('Niveau modifié')
      } else {
        await classApi.createLevel(form)
        success('Niveau créé')
      }
      setDialogOpen(false)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await classApi.deleteLevel(toDelete.id)
      success('Niveau supprimé')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const cycleLabel = (cycle) => CYCLES.find((c) => c.value === cycle)?.label || '—'

  const columns = [
    { key: 'name', label: 'Nom', render: (r) => <b>{r.name}</b> },
    { key: 'code', label: 'Code', render: (r) => <code>{r.code}</code> },
    { key: 'educationCycle', label: 'Cycle', render: (r) => r.educationCycle ? cycleLabel(r.educationCycle) : '—' },
  ]

  return (
    <>
      <PageHeader
        title="Niveaux"
        subtitle="Référentiel des niveaux scolaires"
        actionLabel="Nouveau niveau"
        onAction={openNew}
      />
      <DataTable
        title="Liste des niveaux"
        columns={columns}
        rows={rows}
        loading={loading}
        onEdit={openEdit}
        onDelete={setToDelete}
      />

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={800}>{editing ? 'Modifier le niveau' : 'Nouveau niveau'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField fullWidth label="Nom" value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth label="Code" value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField select fullWidth label="Cycle d'enseignement" value={form.educationCycle}
                onChange={(e) => setForm({ ...form, educationCycle: e.target.value })}>
                <MenuItem value=""><em>Aucun</em></MenuItem>
                {CYCLES.map((c) => <MenuItem key={c.value} value={c.value}>{c.label}</MenuItem>)}
              </TextField>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDialogOpen(false)} color="inherit">Annuler</Button>
          <Button variant="contained" startIcon={<Add />} onClick={handleSave} disabled={!form.name || !form.code}>
            {editing ? 'Enregistrer' : 'Créer'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer le niveau"
        message={`Supprimer le niveau « ${toDelete?.name} » ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}
