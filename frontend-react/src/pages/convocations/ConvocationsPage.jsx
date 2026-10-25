import { useEffect, useState } from 'react'
import {
  Grid,
  Card,
  Typography,
  Box,
  Chip,
  Button,
  TextField,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Avatar,
  TablePagination
} from '@mui/material'
import { Add, Delete, Event, Place, Person } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { convocationApi, studentApi, parentApi, teacherApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate, formatDateTime } from '../../utils/format'

const CONTEXTS = ['SCHOOL', 'UNIVERSITY']
const CONTEXT_LABELS = { SCHOOL: 'Scolaire', UNIVERSITY: 'Universitaire' }

/**
 * Cibles d'une convocation. La cible décide QUI est convoqué et quel
 * identifiant est obligatoire côté serveur.
 */
const TARGETS = ['ELEVE', 'PARENT', 'ENSEIGNANT']
const TARGET_LABELS = { ELEVE: 'Élève', PARENT: 'Parent d\'élève', ENSEIGNANT: 'Enseignant' }
const TARGET_COLORS = { ELEVE: 'primary', PARENT: 'secondary', ENSEIGNANT: 'info' }

/** Nom affiché pour une convocation, quelle que soit sa cible. */
const targetName = (c) => c.studentName || c.parentName || c.teacherName || '—'

/**
 * Module convocations : l'administration convoque un élève, un parent d'élève
 * ou un enseignant. Le destinataire est notifié (cloche) et, quand un numéro
 * est disponible, par WhatsApp.
 */
export default function ConvocationsPage() {
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [students, setStudents] = useState([])
  const [parents, setParents] = useState([])
  const [teachers, setTeachers] = useState([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [target, setTarget] = useState('ELEVE')
  const [studentId, setStudentId] = useState('')
  const [parentId, setParentId] = useState('')
  const [teacherId, setTeacherId] = useState('')
  const [context, setContext] = useState('SCHOOL')
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [time, setTime] = useState('10:00')
  const [location, setLocation] = useState('')
  const [filter, setFilter] = useState('')
  const [targetFilter, setTargetFilter] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)

  const load = async () => {
    setLoading(true)
    try {
      const params = { page, size }
      if (filter) params.studentId = filter
      if (targetFilter) params.target = targetFilter
      const { data } = await convocationApi.search(params)
      setRows(data.data.content || [])
      setTotal(data.data.totalElements || 0)
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    studentApi.search({ page: 0, size: 100 })
      .then((r) => setStudents(r.data.data.content || []))
      .catch(() => {})
    teacherApi.search({ page: 0, size: 100 })
      .then((r) => setTeachers(r.data.data.content || []))
      .catch(() => {})
    // La liste des dossiers parents est réservée au personnel administratif.
    parentApi.parents()
      .then((r) => setParents(r.data.data || []))
      .catch(() => {})
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter, targetFilter, page, size])

  const handleCreate = async () => {
    if (!subject || !location) {
      toastError('Motif et lieu sont obligatoires')
      return
    }
    if (target === 'ELEVE' && !studentId) return toastError('Sélectionnez un élève')
    if (target === 'PARENT' && !parentId) return toastError('Sélectionnez un parent')
    if (target === 'ENSEIGNANT' && !teacherId) return toastError('Sélectionnez un enseignant')
    try {
      await convocationApi.create({
        target,
        studentId: target === 'ELEVE' ? Number(studentId) : null,
        parentId: target === 'PARENT' ? Number(parentId) : null,
        teacherId: target === 'ENSEIGNANT' ? Number(teacherId) : null,
        context,
        subject,
        message,
        date,
        time: `${date}T${time}:00`,
        location,
      })
      success(`${TARGET_LABELS[target]} convoqué(e) et notifié(e)`)
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
    setTarget('ELEVE')
    setStudentId('')
    setParentId('')
    setTeacherId('')
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
        subtitle="Élèves, parents d'élèves et enseignants — notification automatique du destinataire"
        actions={[{ label: 'Nouvelle convocation', icon: <Add />, onClick: () => setDialogOpen(true) }]}
      />

      <Card sx={{ p: 2, borderRadius: '16px', mb: 3, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          select size="small" label="Cible" value={targetFilter}
          onChange={(e) => { setTargetFilter(e.target.value); setPage(0) }} sx={{ minWidth: 200 }}
        >
          <MenuItem value="">Toutes les cibles</MenuItem>
          {TARGETS.map((t) => <MenuItem key={t} value={t}>{TARGET_LABELS[t]}</MenuItem>)}
        </TextField>
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
                <Box display="flex" gap={0.5} flexWrap="wrap">
                  <Chip size="small" color={TARGET_COLORS[c.target] || 'default'}
                    label={TARGET_LABELS[c.target] || c.target} />
                  <Chip size="small" variant="outlined"
                    color={c.context === 'UNIVERSITY' ? 'secondary' : 'primary'}
                    label={CONTEXT_LABELS[c.context] || c.context} />
                </Box>
                <Chip size="small" variant="outlined" label={c.status} />
              </Box>
              <Typography variant="h6" fontWeight={700}>{c.subject}</Typography>
              <Typography variant="caption" color="text.secondary">Réf. {c.reference}</Typography>

              <Box display="flex" alignItems="center" gap={1} mt={1.5}>
                <Avatar sx={{ width: 28, height: 28, bgcolor: 'primary.main' }}><Person sx={{ fontSize: 16 }} /></Avatar>
                <Typography variant="body2" fontWeight={600}>{targetName(c)}</Typography>
                {c.matricule && (
                  <Typography variant="caption" color="text.secondary">({c.matricule})</Typography>
                )}
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

      <TablePagination
        component="div"
        count={total}
        page={page}
        rowsPerPage={size}
        rowsPerPageOptions={[5, 10, 25]}
        onPageChange={(_, p) => setPage(p)}
        onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
      />

      {/* Dialogue création */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Nouvelle convocation</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid item xs={12} sm={5}>
              <TextField select fullWidth size="small" label="Convoquer" value={target}
                onChange={(e) => setTarget(e.target.value)}>
                {TARGETS.map((t) => <MenuItem key={t} value={t}>{TARGET_LABELS[t]}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={7}>
              {target === 'ELEVE' && (
                <TextField select fullWidth size="small" label="Élève / Étudiant" value={studentId}
                  onChange={(e) => setStudentId(e.target.value)}>
                  {students.map((s) => (
                    <MenuItem key={s.id} value={String(s.id)}>{s.firstName} {s.lastName} — {s.matricule}</MenuItem>
                  ))}
                </TextField>
              )}
              {target === 'PARENT' && (
                <TextField select fullWidth size="small" label="Parent d'élève" value={parentId}
                  onChange={(e) => setParentId(e.target.value)}>
                  {parents.map((p) => (
                    <MenuItem key={p.id} value={String(p.id)}>
                      {p.firstName} {p.lastName}{p.phone ? ` — ${p.phone}` : ''}
                    </MenuItem>
                  ))}
                </TextField>
              )}
              {target === 'ENSEIGNANT' && (
                <TextField select fullWidth size="small" label="Enseignant" value={teacherId}
                  onChange={(e) => setTeacherId(e.target.value)}>
                  {teachers.map((t) => (
                    <MenuItem key={t.id} value={String(t.id)}>
                      {t.firstName} {t.lastName}{t.matricule ? ` — ${t.matricule}` : ''}
                    </MenuItem>
                  ))}
                </TextField>
              )}
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
