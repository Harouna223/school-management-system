import { useState, useEffect } from 'react'
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import {
  Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions,
  Card, CardContent, Typography, Box, Skeleton,
} from '@mui/material'
import PageHeader from '../../components/PageHeader'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { classApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

const validationSchema = Yup.object({
  name: Yup.string().required('Le nom est requis'),
  code: Yup.string().required('Le code est requis'),
  levelId: Yup.string().required('Le niveau est requis'),
})

const empty = { name: '', code: '', levelId: '', sectionId: '', roomId: '', capacity: 40 }

/**
 * Gestion des classes : niveaux, sections, salles.
 */
export default function ClassesPage() {
  const { success, error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [levels, setLevels] = useState([])
  const [sections, setSections] = useState([])
  const [rooms, setRooms] = useState([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [toDelete, setToDelete] = useState(null)

  const load = async () => {
    setLoading(true)
    try {
      const { data } = await classApi.all()
      setRows(data.data)
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    classApi.levels().then((r) => setLevels(r.data.data)).catch(() => {})
    classApi.sections().then((r) => setSections(r.data.data)).catch(() => {})
    classApi.rooms().then((r) => setRooms(r.data.data)).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    setEditing(null)
    setDialogOpen(true)
  }

  const openEdit = (row) => {
    setEditing(row)
    setDialogOpen(true)
  }

  const handleSubmit = async (values) => {
    const payload = {
      ...values,
      levelId: Number(values.levelId),
      sectionId: values.sectionId ? Number(values.sectionId) : null,
      roomId: values.roomId ? Number(values.roomId) : null,
      capacity: Number(values.capacity),
    }
    try {
      if (editing) {
        await classApi.update(editing.id, payload)
        success('Classe modifiée')
      } else {
        await classApi.create(payload)
        success('Classe créée')
      }
      setDialogOpen(false)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await classApi.remove(toDelete.id)
      success('Classe supprimée')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader title="Classes & Salles" subtitle="Niveaux, sections, salles et capacités" actionLabel="Nouvelle classe" onAction={openCreate} />

      <Grid container spacing={3}>
        {loading ? (
          Array.from({ length: 3 }).map((_, i) => (
            <Grid item xs={12} sm={6} md={4} key={`sk-${i}`}>
              <Card sx={{ p: 2.5, borderRadius: '16px' }}>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                  <Skeleton width={130} height={30} />
                  <Skeleton width={44} height={30} />
                </Box>
                <Skeleton height={16} sx={{ mb: 1 }} />
                <Skeleton height={16} sx={{ mb: 1 }} />
                <Skeleton height={16} width="60%" />
              </Card>
            </Grid>
          ))
        ) : (
          rows.map((row) => (
            <Grid item xs={12} sm={6} md={4} key={row.id}>
              <Card className="card-lift" sx={{ borderRadius: '16px', height: '100%' }}>
                <CardContent>
                  <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                    <Box>
                      <Typography variant="h6">{row.name}</Typography>
                      <Typography variant="caption" color="text.secondary">Code : {row.code}</Typography>
                    </Box>
                    <Typography variant="h6" color="primary">
                      {row.studentCount}<Typography component="span" variant="caption" color="text.secondary">/{row.capacity}</Typography>
                    </Typography>
                  </Box>
                  <Box mt={2} display="flex" flexDirection="column" gap={0.5}>
                    <Typography variant="body2">Niveau : {row.levelName}</Typography>
                    <Typography variant="body2">Section : {row.sectionName || '—'}</Typography>
                    <Typography variant="body2">Salle : {row.roomName || '—'}</Typography>
                  </Box>
                  <Box mt={2} display="flex" justifyContent="flex-end" gap={1}>
                    <Button size="small" onClick={() => openEdit(row)}>Modifier</Button>
                    <Button size="small" color="error" onClick={() => setToDelete(row)}>Supprimer</Button>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))
        )}
      </Grid>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <Formik
          initialValues={
            editing
              ? {
                  name: editing.name, code: editing.code, levelId: String(editing.levelId),
                  sectionId: editing.sectionId ? String(editing.sectionId) : '',
                  roomId: editing.roomId ? String(editing.roomId) : '',
                  capacity: editing.capacity,
                }
              : empty
          }
          validationSchema={validationSchema}
          onSubmit={handleSubmit}
          enableReinitialize
        >
          {({ values, errors, touched, handleChange }) => (
            <Form>
              <DialogTitle>{editing ? 'Modifier la classe' : 'Nouvelle classe'}</DialogTitle>
              <DialogContent>
                <Grid container spacing={2} mt={0.5}>
                  <Grid item xs={12} sm={6}>
                    <TextField fullWidth label="Nom" name="name" value={values.name} onChange={handleChange}
                      error={touched.name && Boolean(errors.name)} helperText={touched.name && errors.name} />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField fullWidth label="Code" name="code" value={values.code} onChange={handleChange}
                      error={touched.code && Boolean(errors.code)} helperText={touched.code && errors.code} />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Niveau" name="levelId" value={values.levelId} onChange={handleChange}
                      error={touched.levelId && Boolean(errors.levelId)}>
                      {levels.map((l) => <MenuItem key={l.id} value={String(l.id)}>{l.name}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Section" name="sectionId" value={values.sectionId} onChange={handleChange}>
                      <MenuItem value="">Aucune</MenuItem>
                      {sections.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.name}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Salle" name="roomId" value={values.roomId} onChange={handleChange}>
                      <MenuItem value="">Aucune</MenuItem>
                      {rooms.map((r) => <MenuItem key={r.id} value={String(r.id)}>{r.name}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField fullWidth type="number" label="Capacité" name="capacity" value={values.capacity} onChange={handleChange} />
                  </Grid>
                </Grid>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
                <Button type="submit" variant="contained">{editing ? 'Enregistrer' : 'Créer'}</Button>
              </DialogActions>
            </Form>
          )}
        </Formik>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer la classe"
        message={`Supprimer la classe ${toDelete?.name} ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}