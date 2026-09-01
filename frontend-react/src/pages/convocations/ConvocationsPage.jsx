import { useEffect, useState } from 'react'
import {
  Grid, Card, CardContent, Typography, Box, Chip, Button, TextField, MenuItem,
  Dialog, DialogTitle, DialogContent, DialogActions, IconButton, Avatar, TablePagination,
} from '@mui/material'
import { Add, Delete, PictureAsPdf, Event, Place, Person } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { convocationApi, studentApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate, formatDateTime } from '../../utils/format'

const CONTEXTS = ['SCHOOL', 'UNIVERSITY']
const CONTEXT_LABELS = { SCHOOL: 'Scolaire', UNIVERSITY: 'Universitaire' }

/**
 * Module convocations : scolaires et universitaires.
 */
export default function ConvocationsPage() {
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [students, setStudents] = useState([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [studentId, setStudentId] = useState('')
  const [context, setContext] = useState('SCHOOL')
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [time, setTime] = useState('10:00')
  const [location, setLocation] = useState('')
  const [filter, setFilter] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)

  const load = async () => {
    setLoading(true)
    try {
      const params = {}
      if (filter) params.studentId = filter
      const { data } = await convocationApi.search(params)
      setRows(data.data.content || [])
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    studentApi.search({ page: 0, size: 500 })
      .then((r) => setStudents(r.data.data.content || []))
      .catch(() => {})
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter, page, size])

  const handleCreate = async () => {
    if (!studentId || !subject || !location) {
      toastError('Élève, motif et lieu sont obligatoires')
      return
    }
    try {
      await convocationApi.create({
        studentId: Number(studentId),
        context,
        subject,
        message,
        date,
        time: `${date}T${time}:00`,
        location,
      })
      success('Convocation créée et notifiée au parent')
      setDialogOpen(false)
      resetForm()
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async (id) => {
    try {
      await convocationApi.delete(id)
      success('Convocation supprimée')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const resetForm = () => {
    setStudentId('')
    setContext('SCHOOL')
    setSubject('')
    setMessage('')
    setDate(new Date().toISOString().slice(0, 10))
    setTime('10:00')
    setLocation('')
  }

  return (
    <>
      <PageHeader
        title="Convocations"
        subtitle="Scolaires et universitaires — notification automatique au parent"
        actions={[{ label: 'Nouvelle convocation', icon: <Add />, onClick: () => setDialogOpen(true) }]}
      />

      <Card sx={{ p: 2, borderRadius: '16px', mb: 3 }}>
        <TextField
          select size="small" label="Filtrer par élève" value={filter}
          onChange={(e) => { setFilter(e.target.value); setPage(0) }} sx={{ minWidth: 260 }}
        >
          <MenuItem value="">Tous</MenuItem>
          {students.map((s) => (
            <MenuItem key={s.id} value={String(s.id)}>{s.firstName} {s.lastName} — {s.matricule}</MenuItem>
          ))}
        </TextField>
      </Card>

      <Grid container spacing={2.5}>
        {rows.map((c) => (
          <Grid item xs={12} md={6} lg={4} key={c.id}>
            <Card sx={{ p: 2.5, borderRadius: '16px', border: '1px solid', borderColor: 'divider' }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Chip size="small" color={c.context === 'UNIVERSITY' ? 'secondary' : 'primary'}
                  label={CONTEXT_LABELS[c.context] || c.context} />
                <Chip size="small" variant="outlined" label={c.status} />
              </Box>
              <Typography variant="h6" fontWeight={700}>{c.subject}</Typography>
              <Typography variant="caption" color="text.secondary">Réf. {c.reference}</Typography>

              <Box display="flex" alignItems="center" gap={1} mt={1.5}>
                <Avatar sx={{ width: 28, height: 28, bgcolor: 'primary.main' }}><Person sx={{ fontSize: 16 }} /></Avatar>
                <Typography variant="body2" fontWeight={600}>{c.studentName}</Typography>
                <Typography variant="caption" color="text.secondary">({c.matricule})</Typography>
              </Box>

              <Box display="flex" alignItems="center" gap={1} mt={1}>
                <Event color="action" sx={{ fontSize: 18 }} />
                <Typography variant="body2">{formatDate(c.date)} — {formatDateTime(c.time)}</Typography>
              </Box>
              <Box display="flex" alignItems="center" gap={1} mt={0.5}>
                <Place color="action" sx={{ fontSize: 18 }} />
                <Typography variant="body2">{c.location}</Typography>
              </Box>

              {c.message && (
                <Typography variant="body2" color="text.secondary" mt={1} sx={{ fontStyle: 'italic' }}>
                  {c.message}
                </Typography>
              )}

              <Box display="flex" justifyContent="flex-end" mt={1.5}>
                <IconButton size="small" color="error" onClick={() => handleDelete(c.id)} title="Supprimer">
                  <Delete fontSize="small" />
                </IconButton>
              </Box>
            </Card>
          </Grid>
        ))}
        {rows.length === 0 && !loading && (
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary" textAlign="center" py={4}>
              Aucune convocation enregistrée.
            </Typography>
          </Grid>
        )}
      </Grid>

      {/* Dialogue création */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Nouvelle convocation</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={12}>
              <TextField select fullWidth size="small" label="Élève / Étudiant" value={studentId}
                onChange={(e) => setStudentId(e.target.value)}>
                {students.map((s) => (
                  <MenuItem key={s.id} value={String(s.id)}>{s.firstName} {s.lastName} — {s.matricule}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField select fullWidth size="small" label="Contexte" value={context}
                onChange={(e) => setContext(e.target.value)}>
                {CONTEXTS.map((c) => <MenuItem key={c} value={c}>{CONTEXT_LABELS[c]}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" label="Motif" value={subject}
                onChange={(e) => setSubject(e.target.value)} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Message" multiline minRows={2} value={message}
                onChange={(e) => setMessage(e.target.value)} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" type="date" label="Date" value={date}
                onChange={(e) => setDate(e.target.value)} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth size="small" type="time" label="Heure" value={time}
                onChange={(e) => setTime(e.target.value)} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Lieu" value={location}
                onChange={(e) => setLocation(e.target.value)} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleCreate}>Créer et notifier</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}