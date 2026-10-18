import { useEffect, useMemo, useState } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Grid, TextField,
  MenuItem, FormControlLabel, Switch, Box, Typography, LinearProgress, Alert,
  Stack, Chip,
} from '@mui/material'
import { CloudUpload, Videocam, PictureAsPdf } from '@mui/icons-material'
import { courseApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

const EMPTY = {
  title: '',
  description: '',
  chapter: '',
  type: 'VIDEO',
  subjectId: '',
  classIds: [],
  downloadable: true,
}

const ACCEPT = {
  VIDEO: '.mp4,.webm,video/mp4,video/webm',
  PDF: '.pdf,application/pdf',
}

/**
 * Création / modification d'un cours, puis téléversement du fichier.
 *
 * <p>Deux appels distincts : les métadonnées d'abord (le brouillon existe même
 * si l'upload échoue), puis le fichier avec une barre de progression réelle
 * (événement `onUploadProgress` d'axios).</p>
 *
 * <p>Pour un enseignant, les classes/matières proposées sont déjà restreintes
 * par le serveur (`options.restricted`) — et il les revalide de toute façon.</p>
 */
export default function CourseFormDialog({ open, initial, options, onClose, onSaved }) {
  const [form, setForm] = useState(EMPTY)
  const [file, setFile] = useState(null)
  const [progress, setProgress] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const isEdit = Boolean(initial?.id)

  useEffect(() => {
    if (!open) return
    setError('')
    setFile(null)
    setProgress(null)
    if (initial) {
      setForm({
        title: initial.title || '',
        description: initial.description || '',
        chapter: initial.chapter || '',
        type: initial.type || 'VIDEO',
        subjectId: initial.subjectId ? String(initial.subjectId) : '',
        classIds: (initial.classIds || []).map(String),
        downloadable: initial.downloadable !== false,
      })
    } else {
      setForm(EMPTY)
    }
  }, [open, initial])

  // Un enseignant ne voit que les matières réellement affectées aux classes
  // qu'il a sélectionnées.
  const subjectOptions = useMemo(() => {
    if (!options) return []
    if (!options.restricted) return options.subjects
    const selectedClasses = form.classIds.map(Number)
    const allowed = new Set(
      options.assignments
        .filter((a) => selectedClasses.length === 0 || selectedClasses.includes(a.classId))
        .map((a) => a.subjectId),
    )
    return options.subjects.filter((s) => allowed.has(s.id))
  }, [options, form.classIds])

  const classOptions = options?.classes || []

  const typeLocked = isEdit && initial?.hasFile

  const update = (patch) => setForm((prev) => ({ ...prev, ...patch }))

  const onPickFile = (event) => {
    const picked = event.target.files?.[0] || null
    setError('')
    setFile(picked)
    if (picked) {
      const ext = picked.name.toLowerCase().split('.').pop()
      const expected = form.type === 'VIDEO' ? ['mp4', 'webm'] : ['pdf']
      if (!expected.includes(ext)) {
        setError(`Le fichier doit être au format ${expected.map((e) => e.toUpperCase()).join(' ou ')}.`)
        setFile(null)
        event.target.value = ''
      }
    }
  }

  const submit = async () => {
    setError('')
    if (!form.title.trim()) return setError('Le titre est obligatoire.')
    if (!form.subjectId) return setError('Choisissez une matière.')
    if (form.classIds.length === 0) return setError('Choisissez au moins une classe.')

    const payload = {
      title: form.title.trim(),
      description: form.description?.trim() || null,
      chapter: form.chapter?.trim() || null,
      type: form.type,
      subjectId: Number(form.subjectId),
      classIds: form.classIds.map(Number),
      downloadable: form.downloadable,
    }

    setSaving(true)
    try {
      let id = initial?.id
      if (isEdit) {
        await courseApi.update(id, payload)
      } else {
        const { data } = await courseApi.create(payload)
        id = data.data.id
      }
      if (file) {
        setProgress(0)
        await courseApi.upload(id, file, setProgress)
      }
      setProgress(null)
      onSaved?.(id, isEdit)
    } catch (err) {
      setError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {isEdit ? 'Modifier le cours' : 'Nouveau cours'}
        <Typography variant="caption" display="block" color="text.secondary">
          Le cours est enregistré en brouillon ; publiez-le quand le fichier est prêt.
        </Typography>
      </DialogTitle>

      <DialogContent dividers>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <Grid container spacing={2}>
          <Grid item xs={12}>
            <TextField
              label="Titre"
              fullWidth
              required
              inputProps={{ maxLength: 200 }}
              value={form.title}
              onChange={(e) => update({ title: e.target.value })}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              select
              label="Type de ressource"
              fullWidth
              disabled={typeLocked}
              value={form.type}
              onChange={(e) => update({ type: e.target.value })}
              helperText={typeLocked ? 'Le type ne peut plus changer : un fichier est déjà attaché.' : ' '}
            >
              <MenuItem value="VIDEO">Vidéo (MP4 / WebM)</MenuItem>
              <MenuItem value="PDF">Document PDF</MenuItem>
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              select
              label="Matière"
              fullWidth
              required
              value={form.subjectId}
              onChange={(e) => update({ subjectId: e.target.value })}
              helperText={options?.restricted ? 'Uniquement vos matières affectées.' : ' '}
            >
              {subjectOptions.map((s) => (
                <MenuItem key={s.id} value={String(s.id)}>{s.name}</MenuItem>
              ))}
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              select
              label="Classes destinataires"
              fullWidth
              required
              SelectProps={{ multiple: true }}
              value={form.classIds}
              onChange={(e) => {
                const classIds = typeof e.target.value === 'string' ? e.target.value.split(',') : e.target.value
                update({ classIds, subjectId: '' })
              }}
              helperText={options?.restricted ? 'Uniquement vos classes affectées.' : ' '}
            >
              {classOptions.map((c) => (
                <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>
              ))}
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              label="Chapitre (facultatif)"
              fullWidth
              inputProps={{ maxLength: 150 }}
              value={form.chapter}
              onChange={(e) => update({ chapter: e.target.value })}
            />
          </Grid>

          <Grid item xs={12}>
            <TextField
              label="Description / contenu pédagogique"
              fullWidth
              multiline
              minRows={3}
              inputProps={{ maxLength: 2000 }}
              value={form.description}
              onChange={(e) => update({ description: e.target.value })}
              helperText={`${form.description.length}/2000`}
            />
          </Grid>

          <Grid item xs={12}>
            <FormControlLabel
              control={<Switch checked={form.downloadable} onChange={(e) => update({ downloadable: e.target.checked })} />}
              label="Autoriser le téléchargement (sinon consultation seulement)"
            />
          </Grid>

          <Grid item xs={12}>
            <Box
              sx={{
                border: '1px dashed', borderColor: 'divider', borderRadius: 2,
                p: 2, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap',
              }}
            >
              {form.type === 'VIDEO' ? <Videocam color="primary" /> : <PictureAsPdf color="error" />}
              <Box flexGrow={1} minWidth={200}>
                <Typography variant="body2" fontWeight={600}>
                  {file ? file.name : (initial?.originalFilename || 'Aucun fichier sélectionné')}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {form.type === 'VIDEO'
                    ? 'MP4 ou WebM — le format et la signature réelle sont vérifiés côté serveur.'
                    : 'PDF uniquement — le contenu est vérifié avant enregistrement.'}
                </Typography>
              </Box>
              <Button component="label" variant="outlined" startIcon={<CloudUpload />} disabled={saving}>
                {isEdit && initial?.hasFile ? 'Remplacer le fichier' : 'Choisir un fichier'}
                <input hidden type="file" accept={ACCEPT[form.type]} onChange={onPickFile} />
              </Button>
            </Box>
          </Grid>

          {progress !== null && (
            <Grid item xs={12}>
              <Stack spacing={0.5}>
                <Typography variant="caption" color="text.secondary">
                  Téléversement… {progress}%
                </Typography>
                <LinearProgress variant="determinate" value={progress} />
              </Stack>
            </Grid>
          )}
        </Grid>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        {isEdit && initial?.hasFile && (
          <Chip
            size="small"
            label="Fichier présent"
            color="success"
            variant="outlined"
            sx={{ mr: 'auto' }}
          />
        )}
        <Button onClick={onClose} disabled={saving}>Annuler</Button>
        <Button variant="contained" onClick={submit} disabled={saving}>
          {saving ? 'Enregistrement…' : (isEdit ? 'Enregistrer' : 'Créer le cours')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
