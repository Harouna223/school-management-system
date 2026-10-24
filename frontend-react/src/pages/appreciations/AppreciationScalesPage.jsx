import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Grid, Card, CardContent, CardActions, Box, Typography, Chip, Button, Stack,
  TextField, MenuItem, Dialog, DialogTitle, DialogContent, DialogActions,
  IconButton, Divider, Alert, Table, TableBody, TableCell, TableHead, TableRow,
  Tooltip, Paper,
} from '@mui/material'
import {
  Add, ContentCopy, DeleteOutline, Edit, PlayArrow, StopCircle, Visibility,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import FilterCard from '../../components/FilterCard'
import EmptyState from '../../components/EmptyState'
import { useToast } from '../../hooks/useToast'
import { appreciationScaleApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'

const CYCLES = [
  { value: 'JARDIN', label: 'Jardin' },
  { value: 'PRIMAIRE', label: 'Primaire' },
  { value: 'COLLEGE', label: 'Collège' },
  { value: 'LYCEE', label: 'Lycée' },
  { value: 'UNIVERSITE', label: 'Université' },
]
const CYCLE_LABEL = Object.fromEntries(CYCLES.map((c) => [c.value, c.label]))

const DEFAULT_BANDS = [
  { lowerBound: '0', upperBound: '8', label: 'Très insuffisant', observation: '' },
  { lowerBound: '8', upperBound: '10', label: 'Insuffisant', observation: '' },
  { lowerBound: '10', upperBound: '12', label: 'Passable', observation: '' },
  { lowerBound: '12', upperBound: '14', label: 'Assez bien', observation: '' },
  { lowerBound: '14', upperBound: '16', label: 'Bien', observation: '' },
  { lowerBound: '16', upperBound: '18', label: 'Très bien', observation: '' },
  { lowerBound: '18', upperBound: '20', label: 'Excellent', observation: '' },
]

const emptyScale = () => ({
  name: '',
  cycle: 'COLLEGE',
  minNote: '0',
  maxNote: '20',
  bands: DEFAULT_BANDS.map((b) => ({ ...b })),
})

/**
 * Barèmes d'appréciation : configuration par cycle scolaire.
 *
 * Les seuils ne sont plus dans le code : ce que l'administrateur enregistre ici
 * est utilisé tel quel par les bulletins (écrans et PDF).
 */
export default function AppreciationScalesPage() {
  const { success, error: toastError } = useToast()
  const [scales, setScales] = useState([])
  const [loading, setLoading] = useState(true)
  const [cycle, setCycle] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await appreciationScaleApi.list({
        cycle: cycle || undefined,
        active: activeFilter === '' ? undefined : activeFilter === 'true',
      })
      setScales(data.data || [])
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }, [cycle, activeFilter, toastError])

  useEffect(() => { load() }, [load])

  const activate = async (scale) => {
    try {
      await appreciationScaleApi.activate(scale.id)
      success(`« ${scale.name} » est désormais le barème actif du cycle ${CYCLE_LABEL[scale.cycle]}`)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const deactivate = async (scale) => {
    try {
      await appreciationScaleApi.deactivate(scale.id)
      success('Barème désactivé')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const duplicate = async (scale) => {
    try {
      await appreciationScaleApi.duplicate(scale.id)
      success('Barème dupliqué en brouillon')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const openCreate = () => { setEditing(null); setDialogOpen(true) }
  const openEdit = (scale) => { setEditing(scale); setDialogOpen(true) }

  return (
    <>
      <PageHeader
        title="Barèmes d'appréciation"
        subtitle="Configurez les appréciations attribuées aux notes, cycle par cycle"
        actionLabel="Nouveau barème"
        onAction={openCreate}
        badge={`${scales.length} barème${scales.length > 1 ? 's' : ''}`}
      />

      <FilterCard mb={3}>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={4}>
            <TextField select size="small" fullWidth label="Cycle" value={cycle}
              onChange={(e) => setCycle(e.target.value)}>
              <MenuItem value="">Tous les cycles</MenuItem>
              {CYCLES.map((c) => <MenuItem key={c.value} value={c.value}>{c.label}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} sm={6} md={4}>
            <TextField select size="small" fullWidth label="Statut" value={activeFilter}
              onChange={(e) => setActiveFilter(e.target.value)}>
              <MenuItem value="">Tous</MenuItem>
              <MenuItem value="true">Actifs</MenuItem>
              <MenuItem value="false">Inactifs</MenuItem>
            </TextField>
          </Grid>
        </Grid>
      </FilterCard>

      {!loading && scales.length === 0 && (
        <EmptyState message="Aucun barème configuré"
          hint="Créez un barème pour que les bulletins affichent des appréciations." />
      )}

      <Grid container spacing={2.5}>
        {scales.map((scale) => (
          <Grid item xs={12} md={6} lg={4} key={scale.id}>
            <Card sx={{
              height: '100%', display: 'flex', flexDirection: 'column',
              border: scale.active ? '2px solid' : '1px solid',
              borderColor: scale.active ? 'success.main' : 'divider',
            }}>
              <CardContent sx={{ flexGrow: 1 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                  <Box minWidth={0}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap>{scale.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Échelle {Number(scale.minNote)} – {Number(scale.maxNote)} · version {scale.version}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Chip size="small" label={CYCLE_LABEL[scale.cycle] || scale.cycle} variant="outlined" />
                    <Chip size="small" color={scale.active ? 'success' : 'default'}
                      label={scale.active ? 'Actif' : 'Inactif'} />
                  </Stack>
                </Stack>

                <Divider sx={{ my: 1.5 }} />

                <Stack spacing={0.6}>
                  {(scale.bands || []).map((band) => (
                    <Stack key={band.id} direction="row" justifyContent="space-between" gap={1}>
                      <Typography variant="caption" color="text.secondary">
                        [{Number(band.lowerBound)} – {Number(band.upperBound)}{Number(band.upperBound) === Number(scale.maxNote) ? ']' : '['}
                      </Typography>
                      <Typography variant="body2" fontWeight={600} noWrap>{band.label}</Typography>
                    </Stack>
                  ))}
                </Stack>
              </CardContent>

              <CardActions sx={{ px: 2, pb: 1.5, flexWrap: 'wrap', gap: 0.5 }}>
                <Button size="small" startIcon={<Edit />} onClick={() => openEdit(scale)}>Modifier</Button>
                {scale.active ? (
                  <Button size="small" color="warning" startIcon={<StopCircle />}
                    onClick={() => deactivate(scale)}>Désactiver</Button>
                ) : (
                  <Button size="small" color="success" startIcon={<PlayArrow />}
                    onClick={() => activate(scale)}>Activer</Button>
                )}
                <Button size="small" startIcon={<ContentCopy />} onClick={() => duplicate(scale)}>Dupliquer</Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      <ScaleDialog
        open={dialogOpen}
        initial={editing}
        onClose={() => setDialogOpen(false)}
        onSaved={() => { setDialogOpen(false); success(editing ? 'Barème enregistré' : 'Barème créé'); load() }}
      />
    </>
  )
}

/* ============================================================
   Éditeur
   ============================================================ */
function ScaleDialog({ open, initial, onClose, onSaved }) {
  const { error: toastError } = useToast()
  const [form, setForm] = useState(emptyScale())
  const [preview, setPreview] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const isEdit = Boolean(initial?.id)

  useEffect(() => {
    if (!open) return
    setError('')
    setPreview(null)
    if (initial) {
      setForm({
        name: initial.name || '',
        cycle: initial.cycle || 'COLLEGE',
        minNote: String(initial.minNote ?? 0),
        maxNote: String(initial.maxNote ?? 20),
        bands: (initial.bands || []).map((b) => ({
          lowerBound: String(b.lowerBound),
          upperBound: String(b.upperBound),
          label: b.label,
          observation: b.observation || '',
        })),
      })
    } else {
      setForm(emptyScale())
    }
  }, [open, initial])

  const updateBand = (index, patch) => {
    setForm((prev) => {
      const bands = prev.bands.map((band, i) => (i === index ? { ...band, ...patch } : band))
      return { ...prev, bands }
    })
    setPreview(null)
  }

  const addBand = () => {
    setForm((prev) => {
      const last = prev.bands[prev.bands.length - 1]
      const start = last ? last.upperBound : prev.minNote
      return { ...prev, bands: [...prev.bands, { lowerBound: start, upperBound: prev.maxNote, label: '', observation: '' }] }
    })
    setPreview(null)
  }

  const removeBand = (index) => {
    setForm((prev) => ({ ...prev, bands: prev.bands.filter((_, i) => i !== index) }))
    setPreview(null)
  }

  const payload = useMemo(() => ({
    name: form.name.trim(),
    cycle: form.cycle,
    minNote: Number(form.minNote),
    maxNote: Number(form.maxNote),
    bands: form.bands.map((band) => ({
      lowerBound: Number(band.lowerBound),
      upperBound: Number(band.upperBound),
      label: band.label.trim(),
      observation: band.observation?.trim() || null,
    })),
  }), [form])

  const localCheck = () => {
    if (!payload.name) return 'Le nom du barème est obligatoire.'
    if (form.bands.some((b) => !b.label.trim())) return 'Chaque intervalle doit avoir un libellé.'
    if (form.bands.some((b) => Number(b.lowerBound) >= Number(b.upperBound))) {
      return 'Chaque borne basse doit être inférieure à la borne haute.'
    }
    return ''
  }

  const runPreview = async () => {
    const problem = localCheck()
    if (problem) { setError(problem); return }
    setError('')
    try {
      const { data } = await appreciationScaleApi.preview(payload)
      setPreview(data.data)
    } catch (err) {
      toastError(extractError(err))
      setError(extractError(err))
    }
  }

  const save = async () => {
    const problem = localCheck()
    if (problem) { setError(problem); return }
    setSaving(true)
    setError('')
    try {
      if (isEdit) await appreciationScaleApi.update(initial.id, payload)
      else await appreciationScaleApi.create(payload)
      onSaved()
    } catch (err) {
      setError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {isEdit ? `Modifier « ${initial?.name} »` : 'Nouveau barème'}
        <Typography variant="caption" display="block" color="text.secondary">
          Bornes incluses en bas, exclues en haut ; le dernier intervalle inclut la note maximale.
        </Typography>
      </DialogTitle>

      <DialogContent dividers>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <Grid container spacing={2}>
          <Grid item xs={12} sm={5}>
            <TextField fullWidth label="Nom du barème" value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Grid>
          <Grid item xs={12} sm={3}>
            <TextField select fullWidth label="Cycle" value={form.cycle} disabled={isEdit}
              onChange={(e) => setForm({ ...form, cycle: e.target.value })}>
              {CYCLES.map((c) => <MenuItem key={c.value} value={c.value}>{c.label}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={6} sm={2}>
            <TextField fullWidth type="number" label="Note min." value={form.minNote}
              onChange={(e) => setForm({ ...form, minNote: e.target.value })} />
          </Grid>
          <Grid item xs={6} sm={2}>
            <TextField fullWidth type="number" label="Note max." value={form.maxNote}
              onChange={(e) => setForm({ ...form, maxNote: e.target.value })} />
          </Grid>

          <Grid item xs={12}>
            <Stack direction="row" alignItems="center" justifyContent="space-between" mt={1}>
              <Typography variant="subtitle2">Intervalles et appréciations</Typography>
              <Button size="small" startIcon={<Add />} onClick={addBand}>Ajouter un intervalle</Button>
            </Stack>
          </Grid>

          {form.bands.map((band, index) => (
            <Grid item xs={12} key={index}>
              <Paper variant="outlined" sx={{ p: 1.5 }}>
                <Grid container spacing={1.5} alignItems="center">
                  <Grid item xs={6} sm={2}>
                    <TextField fullWidth size="small" type="number" label="De" value={band.lowerBound}
                      onChange={(e) => updateBand(index, { lowerBound: e.target.value })} />
                  </Grid>
                  <Grid item xs={6} sm={2}>
                    <TextField fullWidth size="small" type="number" label="À" value={band.upperBound}
                      onChange={(e) => updateBand(index, { upperBound: e.target.value })} />
                  </Grid>
                  <Grid item xs={12} sm={3}>
                    <TextField fullWidth size="small" label="Appréciation" value={band.label}
                      onChange={(e) => updateBand(index, { label: e.target.value })} />
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <TextField fullWidth size="small" label="Observation (facultatif)" value={band.observation}
                      onChange={(e) => updateBand(index, { observation: e.target.value })} />
                  </Grid>
                  <Grid item xs={12} sm={1}>
                    <Tooltip title="Retirer cet intervalle">
                      <span>
                        <IconButton size="small" color="error" onClick={() => removeBand(index)}
                          disabled={form.bands.length <= 1}>
                          <DeleteOutline fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </Grid>
                </Grid>
              </Paper>
            </Grid>
          ))}

          <Grid item xs={12}>
            <Button variant="outlined" size="small" startIcon={<Visibility />} onClick={runPreview}>
              Prévisualiser
            </Button>
          </Grid>

          {preview && (
            <Grid item xs={12}>
              <Typography variant="subtitle2" gutterBottom>Prévisualisation</Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Note</TableCell>
                    <TableCell>Appréciation</TableCell>
                    <TableCell>Observation</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {preview.samples.map((sample) => (
                    <TableRow key={sample.note}>
                      <TableCell>{Number(sample.note).toFixed(2)}</TableCell>
                      <TableCell><b>{sample.label}</b></TableCell>
                      <TableCell>{sample.observation || '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Grid>
          )}
        </Grid>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={saving}>Annuler</Button>
        <Button variant="contained" onClick={save} disabled={saving}>
          {saving ? 'Enregistrement…' : (isEdit ? 'Enregistrer' : 'Créer le barème')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
