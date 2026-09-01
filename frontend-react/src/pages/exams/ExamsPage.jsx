import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions, Chip } from '@mui/material'
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import StatusChip from '../../components/StatusChip'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { examApi, classApi, subjectApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate } from '../../utils/format'

const empty = {
  name: '', type: 'CONTROLE', term: 'T1', academicYear: '',
  classId: '', subjectId: '', examDate: '', coefficient: 1, status: 'PLANNED',
}

/**
 * Gestion des examens : planification, statuts, délibérations.
 */
export default function ExamsPage() {
  const navigate = useNavigate()
  const { success, error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [classes, setClasses] = useState([])
  const [subjects, setSubjects] = useState([])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [classFilter, setClassFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [toDelete, setToDelete] = useState(null)

  const load = async () => {
    try {
      const { data } = await examApi.search({
        classId: classFilter || undefined,
        status: statusFilter || undefined,
        page, size,
      })
      setRows(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    classApi.all().then((r) => setClasses(r.data.data)).catch(() => {})
    subjectApi.all().then((r) => setSubjects(r.data.data)).catch(() => {})
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, classFilter, statusFilter])

  const handleSubmit = async (values) => {
    const payload = {
      ...values,
      classId: Number(values.classId),
      subjectId: Number(values.subjectId),
      coefficient: Number(values.coefficient),
      academicYear: values.academicYear || `${new Date().getFullYear()}-${new Date().getFullYear() + 1}`,
      status: values.status || 'PLANNED',
    }
    try {
      if (editing) {
        await examApi.update(editing.id, payload)
        success('Évaluation modifiée')
      } else {
        await examApi.create(payload)
        success('Évaluation planifiée')
      }
      setDialogOpen(false)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const changeStatus = async (exam, status) => {
    try {
      await examApi.changeStatus(exam.id, status)
      success(`Statut -> ${status}`)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await examApi.remove(toDelete.id)
      success('Évaluation supprimée')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    { key: 'name', label: 'Évaluation', sortable: true, render: (r) => <b>{r.name}</b> },
    { key: 'type', label: 'Type' },
    { key: 'className', label: 'Classe' },
    { key: 'subjectName', label: 'Matière' },
    { key: 'term', label: 'Trimestre', render: (r) => <Chip size="small" label={r.term} /> },
    { key: 'examDate', label: 'Date', render: (r) => formatDate(r.examDate) },
    { key: 'coefficient', label: 'Coef' },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'actions2',
      label: 'Délibération',
      render: (r) =>
        r.status !== 'DELIBERATED' ? (
          <Button size="small" color="success" onClick={() => changeStatus(r, 'DELIBERATED')}>
            Délibérer
          </Button>
        ) : null,
    },
  ]

  return (
    <>
      <PageHeader
        title="Examens & Évaluations"
        subtitle="Planification, statuts et délibérations"
        actionLabel="Planifier"
        onAction={() => { setEditing(null); setDialogOpen(true) }}
      />

      <FilterCard mb={3}>
        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <TextField select size="small" fullWidth label="Classe" value={classFilter} onChange={(e) => { setClassFilter(e.target.value); setPage(0) }}>
              <MenuItem value="">Toutes</MenuItem>
              {classes.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={3}>
            <TextField select size="small" fullWidth label="Statut" value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}>
              <MenuItem value="">Tous</MenuItem>
              <MenuItem value="PLANNED">Planifié</MenuItem>
              <MenuItem value="ONGOING">En cours</MenuItem>
              <MenuItem value="COMPLETED">Terminé</MenuItem>
              <MenuItem value="DELIBERATED">Délibéré</MenuItem>
            </TextField>
          </Grid>
        </Grid>
      </FilterCard>

      <DataTable
        columns={columns}
        rows={rows}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={(s) => { setSize(s); setPage(0) }}
        onEdit={(row) => { setEditing(row); setDialogOpen(true) }}
        onDelete={setToDelete}
        searchable={false}
      />

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <Formik
          initialValues={editing ? {
            name: editing.name, type: editing.type, term: editing.term,
            academicYear: editing.academicYear, classId: String(editing.classId),
            subjectId: String(editing.subjectId), examDate: editing.examDate || '',
            coefficient: editing.coefficient, status: editing.status,
          } : empty}
          validationSchema={Yup.object({
            name: Yup.string().required('Le nom est requis'),
            classId: Yup.string().required('La classe est requise'),
            subjectId: Yup.string().required('La matière est requise'),
          })}
          onSubmit={handleSubmit}
          enableReinitialize
        >
          {({ values, errors, touched, handleChange }) => (
            <Form>
              <DialogTitle>{editing ? 'Modifier l\'évaluation' : 'Planifier une évaluation'}</DialogTitle>
              <DialogContent>
                <Grid container spacing={2} mt={0.5}>
                  <Grid item xs={12} sm={8}>
                    <TextField fullWidth label="Nom" name="name" value={values.name} onChange={handleChange}
                      error={touched.name && Boolean(errors.name)} helperText={touched.name && errors.name} />
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <TextField select fullWidth label="Type" name="type" value={values.type} onChange={handleChange}>
                      <MenuItem value="CONTROLE">Contrôle</MenuItem>
                      <MenuItem value="DEVOIR">Devoir</MenuItem>
                      <MenuItem value="EXAMEN">Examen</MenuItem>
                      <MenuItem value="BACCALAUREAT">Baccalauréat</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Classe" name="classId" value={values.classId} onChange={handleChange}>
                      {classes.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField select fullWidth label="Matière" name="subjectId" value={values.subjectId} onChange={handleChange}>
                      {subjects.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.name}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <TextField select fullWidth label="Trimestre" name="term" value={values.term} onChange={handleChange}>
                      <MenuItem value="T1">T1</MenuItem>
                      <MenuItem value="T2">T2</MenuItem>
                      <MenuItem value="T3">T3</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <TextField fullWidth type="date" label="Date" name="examDate" value={values.examDate}
                      onChange={handleChange} InputLabelProps={{ shrink: true }} />
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <TextField fullWidth type="number" label="Coefficient" name="coefficient" value={values.coefficient} onChange={handleChange} />
                  </Grid>
                </Grid>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
                <Button type="submit" variant="contained">{editing ? 'Enregistrer' : 'Planifier'}</Button>
              </DialogActions>
            </Form>
          )}
        </Formik>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer l'évaluation"
        message={`Supprimer ${toDelete?.name} ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}