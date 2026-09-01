import { useState, useEffect } from 'react'
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import {
  Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions,
  Card, CardContent, Typography, Box, Chip,
} from '@mui/material'
import PageHeader from '../../components/PageHeader'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { subjectApi, teacherApi, classApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

const empty = { name: '', code: '', coefficient: 1, description: '' }

/**
 * Gestion des matières et affectations aux enseignants.
 */
export default function SubjectsPage() {
  const { success, error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [assignments, setAssignments] = useState([])
  const [teachers, setTeachers] = useState([])
  const [classes, setClasses] = useState([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [assignOpen, setAssignOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [toDelete, setToDelete] = useState(null)
  const [assignForm, setAssignForm] = useState({ teacherId: '', subjectId: '', classId: '' })

  const load = async () => {
    try {
      const { data } = await subjectApi.all()
      setRows(data.data)
      const res = await subjectApi.assignments()
      setAssignments(res.data.data)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    load()
    teacherApi.search({ page: 0, size: 500 }).then((r) => setTeachers(r.data.data.content)).catch(() => {})
    classApi.all().then((r) => setClasses(r.data.data)).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSubmit = async (values) => {
    try {
      if (editing) {
        await subjectApi.update(editing.id, values)
        success('Matière modifiée')
      } else {
        await subjectApi.create(values)
        success('Matière créée')
      }
      setDialogOpen(false)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleAssign = async () => {
    try {
      await subjectApi.assign({
        teacherId: Number(assignForm.teacherId),
        subjectId: Number(assignForm.subjectId),
        classId: Number(assignForm.classId),
      })
      success('Affectation créée')
      setAssignOpen(false)
      setAssignForm({ teacherId: '', subjectId: '', classId: '' })
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleUnassign = async (assignment) => {
    try {
      await subjectApi.unassign(assignment.id)
      success('Affectation retirée')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await subjectApi.remove(toDelete.id)
      success('Matière supprimée')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title="Matières"
        subtitle="Coefficients et affectations des enseignants"
        actionLabel="Nouvelle matière"
        onAction={() => { setEditing(null); setDialogOpen(true) }}
      />

      <Grid container spacing={3}>
        {rows.map((row) => (
          <Grid item xs={12} sm={6} md={4} key={row.id}>
            <Card>
              <CardContent>
                <Box display="flex" justifyContent="space-between">
                  <Box>
                    <Typography variant="h6">{row.name}</Typography>
                    <Typography variant="caption" color="text.secondary">{row.code}</Typography>
                  </Box>
                  <Chip label={`Coef ${row.coefficient}`} color="primary" size="small" />
                </Box>
                <Typography variant="body2" color="text.secondary" mt={1}>
                  {row.description || '—'}
                </Typography>
                <Box mt={2} display="flex" gap={1}>
                  <Button size="small" onClick={() => { setEditing(row); setDialogOpen(true) }}>Modifier</Button>
                  <Button size="small" color="error" onClick={() => setToDelete(row)}>Supprimer</Button>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <PageHeader title="Affectations" subtitle="Enseignant → Matière → Classe" actionLabel="Affecter" onAction={() => setAssignOpen(true)} />
      <Grid container spacing={2}>
        {assignments.map((a) => (
          <Grid item xs={12} md={6} key={a.id}>
            <Card variant="outlined">
              <CardContent sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Box>
                  <Typography variant="body1" fontWeight={600}>{a.teacherName}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {a.subjectName} • {a.className}
                  </Typography>
                </Box>
                <Button size="small" color="error" onClick={() => handleUnassign(a)}>Retirer</Button>
              </CardContent>
            </Card>
          </Grid>
        ))}
        {assignments.length === 0 && (
          <Grid item xs={12}>
            <Typography color="text.secondary" align="center" py={3}>
              Aucune affectation — attribuez des matières aux enseignants.
            </Typography>
          </Grid>
        )}
      </Grid>

      {/* Dialog matière */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <Formik
          initialValues={editing || empty}
          validationSchema={Yup.object({
            name: Yup.string().required('Le nom est requis'),
            code: Yup.string().required('Le code est requis'),
            coefficient: Yup.number().min(1).max(10).required('Coefficient requis'),
          })}
          onSubmit={handleSubmit}
          enableReinitialize
        >
          {({ values, errors, touched, handleChange }) => (
            <Form>
              <DialogTitle>{editing ? 'Modifier la matière' : 'Nouvelle matière'}</DialogTitle>
              <DialogContent>
                <Grid container spacing={2} mt={0.5}>
                  <Grid item xs={12} sm={6}>
                    <TextField fullWidth label="Nom" name="name" value={values.name} onChange={handleChange}
                      error={touched.name && Boolean(errors.name)} helperText={touched.name && errors.name} />
                  </Grid>
                  <Grid item xs={12} sm={3}>
                    <TextField fullWidth label="Code" name="code" value={values.code} onChange={handleChange} />
                  </Grid>
                  <Grid item xs={12} sm={3}>
                    <TextField fullWidth type="number" label="Coefficient" name="coefficient" value={values.coefficient} onChange={handleChange} />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField fullWidth label="Description" name="description" value={values.description} onChange={handleChange} />
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

      {/* Dialog affectation */}
      <Dialog open={assignOpen} onClose={() => setAssignOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Affecter un enseignant</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Enseignant" value={assignForm.teacherId}
                onChange={(e) => setAssignForm({ ...assignForm, teacherId: e.target.value })}>
                {teachers.map((t) => <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField select fullWidth label="Matière" value={assignForm.subjectId}
                onChange={(e) => setAssignForm({ ...assignForm, subjectId: e.target.value })}>
                {rows.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField select fullWidth label="Classe" value={assignForm.classId}
                onChange={(e) => setAssignForm({ ...assignForm, classId: e.target.value })}>
                {classes.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>)}
              </TextField>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAssignOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleAssign}>Affecter</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer la matière"
        message={`Supprimer ${toDelete?.name} ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}