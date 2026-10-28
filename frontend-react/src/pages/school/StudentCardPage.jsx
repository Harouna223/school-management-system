import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Grid, Card, CardContent, Box, Typography, TextField, MenuItem, Button, Stack,
  Divider, Alert, Chip, CircularProgress, Avatar, Switch, FormControlLabel, Slider,
  Tooltip, ToggleButton, ToggleButtonGroup,
} from '@mui/material'
import BadgeIcon from '@mui/icons-material/Badge'
import CloudUpload from '@mui/icons-material/CloudUpload'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import Download from '@mui/icons-material/Download'
import ImageIcon from '@mui/icons-material/Image'
import Palette from '@mui/icons-material/Palette'
import RestartAlt from '@mui/icons-material/RestartAlt'
import Save from '@mui/icons-material/Save'
import Tune from '@mui/icons-material/Tune'
import Visibility from '@mui/icons-material/Visibility'
import TextFields from '@mui/icons-material/TextFields'
import ListAlt from '@mui/icons-material/ListAlt'
import PhotoCamera from '@mui/icons-material/PhotoCamera'
import PageHeader from '../../components/PageHeader'
import ConfirmDialog from '../../components/ConfirmDialog'
import FileImage from '../../components/FileImage'
import { useToast } from '../../hooks/useToast'
import { studentCardApi, settingsApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { downloadBlob } from '../../utils/format'

const MAX_EMBLEM_BYTES = 5 * 1024 * 1024
const ACCEPTED_EMBLEM = ['image/png', 'image/jpeg']

/** Palettes proposées : elles ne changent que les couleurs, jamais la mise en page. */
const PALETTES = [
  {
    code: 'MALI',
    label: 'Mali officiel',
    colors: { headingColor: '#0f2a4e', textColor: '#28303a', borderColor: '#000000' },
  },
  {
    code: 'INSTITUTION',
    label: 'Bleu institutionnel',
    colors: { headingColor: '#1e3a8a', textColor: '#1e293b', borderColor: '#1e3a8a' },
  },
  {
    code: 'GREEN',
    label: 'Vert établissement',
    colors: { headingColor: '#14532d', textColor: '#1f2a24', borderColor: '#14532d' },
  },
  {
    code: 'MONO',
    label: 'Noir et blanc',
    colors: { headingColor: '#111111', textColor: '#333333', borderColor: '#111111' },
  },
]

/** Champ de couleur : pastille native + saisie hexadécimale. */
function ColorField({ label, value, onChange, disabled }) {
  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Box
        component="input"
        type="color"
        value={value || '#000000'}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        sx={{
          width: 42, height: 42, p: 0, border: '1px solid', borderColor: 'divider',
          borderRadius: 1.5, bgcolor: 'transparent', cursor: disabled ? 'not-allowed' : 'pointer',
        }}
      />
      <TextField
        size="small"
        fullWidth
        label={label}
        value={value || ''}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        inputProps={{ spellCheck: false, style: { fontFamily: 'monospace' } }}
        helperText="Format #RRGGBB"
      />
    </Stack>
  )
}

function Section({ icon, title, subtitle, children }) {
  return (
    <Card sx={{ mb: 3, borderRadius: '16px' }}>
      <CardContent>
        <Stack direction="row" spacing={1.2} alignItems="center" mb={0.5}>
          <Avatar sx={{ bgcolor: 'primary.main', width: 34, height: 34 }}>{icon}</Avatar>
          <Box>
            <Typography variant="subtitle1" fontWeight={700}>{title}</Typography>
            {subtitle && (
              <Typography variant="caption" color="text.secondary">{subtitle}</Typography>
            )}
          </Box>
        </Stack>
        <Divider sx={{ my: 1.5 }} />
        <Stack spacing={2}>{children}</Stack>
      </CardContent>
    </Card>
  )
}

/** Lit le message d'erreur d'une réponse binaire (le serveur répond en JSON dans un blob). */
async function blobMessage(err) {
  const body = err?.response?.data
  if (body instanceof Blob) {
    try {
      const parsed = JSON.parse(await body.text())
      if (parsed?.message) return parsed.message
    } catch {
      // corps non JSON : message générique ci-dessous
    }
  }
  return extractError(err)
}

/**
 * « Carte scolaire » — réglage des composants de la carte d'identité.
 *
 * <p>La géométrie de la carte (format CR80, position des blocs) n'est pas
 * réglable : une carte dont on déplace librement les blocs ne tient plus dans
 * une pochette ni dans un lecteur de badge. Ce qui est réglable, c'est ce qui
 * identifie l'établissement : emblème et logo, textes, couleurs, champs
 * imprimés.</p>
 *
 * <p>L'aperçu est le <b>document réel</b> produit par le serveur, pas une
 * maquette : un aperçu qui redessinerait une approximation ferait valider un
 * rendu qui n'existe pas.</p>
 */
export default function StudentCardPage() {
  const { success, error: toastError } = useToast()

  const [saved, setSaved] = useState(null)
  const [config, setConfig] = useState(null)
  const [meta, setMeta] = useState({ availableFields: [], emblemSources: [], maxFields: 7 })
  const [emblemUrl, setEmblemUrl] = useState('')
  const [emblemAvailable, setEmblemAvailable] = useState(false)
  const [usingDefaults, setUsingDefaults] = useState(true)

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const [emblemFile, setEmblemFile] = useState(null)
  const [emblemPreviewUrl, setEmblemPreviewUrl] = useState(null)
  const [emblemBusy, setEmblemBusy] = useState(false)

  const [previewUrl, setPreviewUrl] = useState(null)
  const [previewing, setPreviewing] = useState(false)
  const [previewError, setPreviewError] = useState('')
  const [confirmReset, setConfirmReset] = useState(false)

  const previewUrlRef = useRef(null)

  const applyPayload = useCallback((payload) => {
    setConfig(payload.config)
    setSaved(payload.config)
    setMeta({
      availableFields: payload.availableFields || [],
      emblemSources: payload.emblemSources || [],
      maxFields: payload.maxFields || 7,
    })
    setEmblemUrl(payload.emblemUrl || '')
    setEmblemAvailable(Boolean(payload.emblemAvailable))
    setUsingDefaults(Boolean(payload.usingDefaults))
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await studentCardApi.get()
      applyPayload(data.data)
      setError('')
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }, [applyPayload, toastError])

  useEffect(() => { load() }, [load])

  // Libère la dernière URL d'aperçu au démontage : sans cela, chaque rendu
  // laisse un blob PDF en mémoire jusqu'au rechargement de la page.
  useEffect(() => () => {
    if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current)
  }, [])

  const dirty = saved !== null && config !== null
    && JSON.stringify(config) !== JSON.stringify(saved)

  /**
   * Aperçu vivant : le serveur rend la carte réelle avec la configuration
   * affichée, y compris non enregistrée. Le délai évite une requête par
   * frappe quand on tape un titre.
   */
  const configKey = useMemo(() => (config ? JSON.stringify(config) : ''), [config])

  useEffect(() => {
    if (!configKey) return undefined
    let cancelled = false
    const timer = setTimeout(async () => {
      setPreviewing(true)
      try {
        const { data } = await studentCardApi.preview(JSON.parse(configKey))
        if (cancelled) return
        const url = URL.createObjectURL(data)
        if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current)
        previewUrlRef.current = url
        setPreviewUrl(url)
        setPreviewError('')
      } catch (err) {
        if (!cancelled) setPreviewError(await blobMessage(err))
      } finally {
        if (!cancelled) setPreviewing(false)
      }
    }, 550)
    return () => { cancelled = true; clearTimeout(timer) }
  }, [configKey])

  const set = (field, value) => setConfig((prev) => ({ ...prev, [field]: value }))

  const setHeaderLine = (index, value) => setConfig((prev) => {
    const lines = [...(prev.headerLines || ['', '', ''])]
    lines[index] = value
    return { ...prev, headerLines: lines }
  })

  const setBandColor = (key, value) => setConfig((prev) => ({
    ...prev,
    bandColors: { ...prev.bandColors, [key]: value },
  }))

  const applyPalette = (colors) => setConfig((prev) => ({ ...prev, ...colors }))

  const toggleField = (code) => setConfig((prev) => {
    const current = prev.fields || []
    const next = current.includes(code)
      ? current.filter((c) => c !== code)
      : [...current, code]
    // Au moins un champ : une carte sans identité n'identifie personne.
    if (next.length === 0) return prev
    return { ...prev, fields: next }
  })

  const save = async () => {
    setSaving(true)
    setError('')
    try {
      const { data } = await studentCardApi.save(config)
      applyPayload(data.data)
      success('Réglages de la carte enregistrés')
    } catch (err) {
      setError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  const reset = async () => {
    setConfirmReset(false)
    try {
      const { data } = await studentCardApi.reset()
      applyPayload(data.data)
      success('Modèle d\'origine restauré')
    } catch (err) {
      setError(extractError(err))
    }
  }

  const pickEmblem = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    setError('')
    if (!ACCEPTED_EMBLEM.includes(file.type)) {
      setError('L\'emblème doit être une image PNG ou JPEG.')
      event.target.value = ''
      return
    }
    if (file.size > MAX_EMBLEM_BYTES) {
      setError('L\'emblème ne doit pas dépasser 5 Mo.')
      event.target.value = ''
      return
    }
    setEmblemFile(file)
    setEmblemPreviewUrl(URL.createObjectURL(file))
  }

  const uploadEmblem = async () => {
    if (!emblemFile) return
    setEmblemBusy(true)
    setError('')
    try {
      await studentCardApi.uploadEmblem(emblemFile)
      setEmblemFile(null)
      setEmblemPreviewUrl(null)
      await load()
      success('Emblème enregistré')
    } catch (err) {
      setError(extractError(err))
    } finally {
      setEmblemBusy(false)
    }
  }

  const removeEmblem = async () => {
    setEmblemBusy(true)
    setError('')
    try {
      await studentCardApi.deleteEmblem()
      await load()
      success('Emblème supprimé')
    } catch (err) {
      setError(extractError(err))
    } finally {
      setEmblemBusy(false)
    }
  }

  const downloadLogo = async () => {
    try {
      const { data, headers } = await settingsApi.downloadLogo()
      const match = /filename="([^"]+)"/.exec(headers?.['content-disposition'] || '')
      downloadBlob(data, match ? match[1] : 'logo-etablissement')
      success('Logo téléchargé')
    } catch (err) {
      setError(await blobMessage(err))
    }
  }

  const downloadPreview = async () => {
    try {
      const { data } = await studentCardApi.preview(config)
      downloadBlob(data, 'apercu-carte-scolaire.pdf')
    } catch (err) {
      setError(await blobMessage(err))
    }
  }

  if (loading || !config) {
    return (
      <Box display="flex" justifyContent="center" py={10}><CircularProgress /></Box>
    )
  }

  const fields = config.fields || []
  const emblemNeedsUpload = config.emblemSource === 'EMBLEM' && !emblemAvailable

  return (
    <>
      <PageHeader
        title="Carte scolaire"
        subtitle="Emblème, logo, textes, couleurs et champs imprimés — format CR80 (85,6 × 54 mm)"
        badge={dirty ? 'Modifications non enregistrées' : undefined}
        actions={[
          { label: 'Aperçu PDF', icon: <Download />, onClick: downloadPreview },
          { label: 'Modèle d\'origine', icon: <RestartAlt />, onClick: () => setConfirmReset(true) },
        ]}
      />

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
      {usingDefaults && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Aucun réglage enregistré : la carte suit son modèle d&apos;origine. Modifiez un
          composant puis enregistrez pour appliquer vos choix.
        </Alert>
      )}

      <Grid container spacing={3}>
        {/* ---------------- Réglages ---------------- */}
        <Grid item xs={12} lg={7}>

          <Section icon={<ImageIcon fontSize="small" />} title="Emblème et logo"
            subtitle="Image affichée en haut à gauche de la carte">
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <TextField select fullWidth label="Provenance de l'image" value={config.emblemSource}
                  onChange={(e) => set('emblemSource', e.target.value)}>
                  {(meta.emblemSources || []).map((s) => (
                    <MenuItem key={s.code} value={s.code}>{s.label}</MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="caption" color="text.secondary">
                  Taille : {config.emblemSize} pt
                </Typography>
                <Slider size="small" min={14} max={30} step={1}
                  value={config.emblemSize}
                  onChange={(_, v) => set('emblemSize', v)}
                  disabled={config.emblemSource === 'NONE'}
                  marks={[{ value: 14, label: '14' }, { value: 30, label: '30' }]} />
              </Grid>

              {config.emblemSource === 'EMBLEM' && (
                <>
                  <Grid item xs={12} md={4}>
                    <Box sx={{
                      p: 2, border: '1px dashed', borderColor: 'divider', borderRadius: 3,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      minHeight: 120,
                    }}>
                      {(emblemPreviewUrl || emblemUrl)
                        ? <FileImage src={emblemPreviewUrl || emblemUrl} alt="Emblème"
                          sx={{ maxWidth: '100%', maxHeight: 90 }} />
                        : (
                          <Stack alignItems="center" spacing={0.5}>
                            <BadgeIcon sx={{ fontSize: 32, color: 'text.disabled' }} />
                            <Typography variant="caption" color="text.secondary">
                              Aucun emblème
                            </Typography>
                          </Stack>
                        )}
                    </Box>
                  </Grid>
                  <Grid item xs={12} md={8}>
                    <Stack spacing={1.5}>
                      {emblemNeedsUpload && (
                        <Alert severity="warning">
                          Aucun emblème n&apos;est téléversé : la carte utilise le logo de
                          l&apos;école à la place, pour ne pas laisser la pastille vide.
                        </Alert>
                      )}
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        <Button component="label" variant="outlined" startIcon={<CloudUpload />}
                          disabled={emblemBusy}>
                          Choisir une image
                          <input hidden type="file" accept="image/png,image/jpeg"
                            onChange={pickEmblem} />
                        </Button>
                        <Button variant="contained" onClick={uploadEmblem}
                          disabled={!emblemFile || emblemBusy}>
                          {emblemBusy ? 'Enregistrement…' : 'Enregistrer l\'emblème'}
                        </Button>
                        {emblemAvailable && (
                          <Button color="error" variant="outlined" startIcon={<DeleteOutline />}
                            onClick={removeEmblem} disabled={emblemBusy}>
                            Supprimer
                          </Button>
                        )}
                      </Stack>
                      {emblemFile && (
                        <Chip size="small" color="info" variant="outlined"
                          label={`${emblemFile.name} — ${Math.round(emblemFile.size / 1024)} Ko`} />
                      )}
                    </Stack>
                  </Grid>
                </>
              )}

              {config.emblemSource === 'SCHOOL_LOGO' && (
                <Grid item xs={12}>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                    <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
                      La pastille utilise le logo saisi dans « Informations de l&apos;établissement ».
                    </Typography>
                    <Button size="small" variant="outlined" startIcon={<Download />}
                      onClick={downloadLogo}>
                      Télécharger ce logo
                    </Button>
                  </Stack>
                </Grid>
              )}

              <Grid item xs={12}>
                <Divider />
              </Grid>

              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={<Switch checked={config.showSchoolLogo}
                    onChange={(e) => set('showSchoolLogo', e.target.checked)} />}
                  label="Logo de l'école en bas à droite" />
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="caption" color="text.secondary">
                  Taille du logo : {config.schoolLogoSize} pt
                  {config.showPhotoFrame && config.schoolLogoSize > 16
                    ? ' (limité à 16 pt : le cadre photo occupe le bas de la carte)' : ''}
                </Typography>
                <Slider size="small" min={10} max={24} step={1}
                  value={config.schoolLogoSize}
                  onChange={(_, v) => set('schoolLogoSize', v)}
                  disabled={!config.showSchoolLogo} />
              </Grid>
            </Grid>
          </Section>

          <Section icon={<TextFields fontSize="small" />} title="En-tête et titre"
            subtitle="Lignes institutionnelles, drapeau et nom de l'établissement">
            <FormControlLabel
              control={<Switch checked={config.showHeader}
                onChange={(e) => set('showHeader', e.target.checked)} />}
              label="Afficher l'en-tête institutionnel" />
            <Grid container spacing={2}>
              {[0, 1, 2].map((i) => (
                <Grid item xs={12} key={i}>
                  <TextField fullWidth size="small" label={`Ligne ${i + 1}`}
                    value={(config.headerLines || [])[i] || ''}
                    onChange={(e) => setHeaderLine(i, e.target.value)}
                    disabled={!config.showHeader}
                    inputProps={{ maxLength: 80 }}
                    helperText="Laisser vide pour masquer cette ligne" />
                </Grid>
              ))}
            </Grid>
            <Divider />
            <Grid container spacing={2}>
              <Grid item xs={12} md={8}>
                <TextField fullWidth label="Titre de la carte" value={config.title}
                  onChange={(e) => set('title', e.target.value)}
                  inputProps={{ maxLength: 60 }}
                  helperText="Laisser vide pour ne pas imprimer de titre" />
              </Grid>
              <Grid item xs={12} md={4}>
                <Typography variant="caption" color="text.secondary">
                  Taille du titre : {config.titleSize} pt
                </Typography>
                <Slider size="small" min={6} max={11} step={0.5}
                  value={config.titleSize}
                  onChange={(_, v) => set('titleSize', v)} />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={<Switch checked={config.showFlag}
                    onChange={(e) => set('showFlag', e.target.checked)} />}
                  label="Drapeau en haut à droite" />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={<Switch checked={config.showSchoolName}
                    onChange={(e) => set('showSchoolName', e.target.checked)} />}
                  label="Nom de l'établissement" />
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="caption" color="text.secondary">
                  Taille du nom : {config.schoolNameSize} pt
                </Typography>
                <Slider size="small" min={4.5} max={9} step={0.5}
                  value={config.schoolNameSize}
                  onChange={(_, v) => set('schoolNameSize', v)}
                  disabled={!config.showSchoolName} />
              </Grid>
            </Grid>
          </Section>

          <Section icon={<Palette fontSize="small" />} title="Couleurs"
            subtitle="Bande tricolore, titre, libellés et bordure">
            <Box>
              <Typography variant="caption" color="text.secondary" gutterBottom display="block">
                Palettes rapides
              </Typography>
              <ToggleButtonGroup exclusive size="small">
                {PALETTES.map((p) => (
                  <ToggleButton key={p.code} value={p.code}
                    onClick={() => applyPalette(p.colors)}
                    sx={{ textTransform: 'none', gap: 0.8 }}>
                    <Box sx={{ width: 12, height: 12, borderRadius: '50%',
                      bgcolor: p.colors.headingColor }} />
                    {p.label}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Box>
            <FormControlLabel
              control={<Switch checked={config.showBand}
                onChange={(e) => set('showBand', e.target.checked)} />}
              label="Bande tricolore sous le titre" />
            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <ColorField label="Bande — 1" value={config.bandColors?.green}
                  onChange={(v) => setBandColor('green', v)} disabled={!config.showBand} />
              </Grid>
              <Grid item xs={12} md={4}>
                <ColorField label="Bande — 2" value={config.bandColors?.yellow}
                  onChange={(v) => setBandColor('yellow', v)} disabled={!config.showBand} />
              </Grid>
              <Grid item xs={12} md={4}>
                <ColorField label="Bande — 3" value={config.bandColors?.red}
                  onChange={(v) => setBandColor('red', v)} disabled={!config.showBand} />
              </Grid>
              <Grid item xs={12} md={4}>
                <ColorField label="Titre et libellés" value={config.headingColor}
                  onChange={(v) => set('headingColor', v)} />
              </Grid>
              <Grid item xs={12} md={4}>
                <ColorField label="Texte" value={config.textColor}
                  onChange={(v) => set('textColor', v)} />
              </Grid>
              <Grid item xs={12} md={4}>
                <ColorField label="Bordure" value={config.borderColor}
                  onChange={(v) => set('borderColor', v)} disabled={!config.showBorder} />
              </Grid>
            </Grid>
          </Section>

          <Section icon={<PhotoCamera fontSize="small" />} title="Cadre et bordure"
            subtitle="Contour de la carte et cadre de la photo">
            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={<Switch checked={config.showBorder}
                    onChange={(e) => set('showBorder', e.target.checked)} />}
                  label="Bordure extérieure" />
                <Typography variant="caption" color="text.secondary">
                  Épaisseur : {config.borderWidth} pt
                </Typography>
                <Slider size="small" min={0.3} max={2} step={0.1}
                  value={config.borderWidth}
                  onChange={(_, v) => set('borderWidth', v)}
                  disabled={!config.showBorder} />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControlLabel
                  control={<Switch checked={config.showPhotoFrame}
                    onChange={(e) => set('showPhotoFrame', e.target.checked)} />}
                  label="Cadre de la photo" />
                <Box sx={{ mt: 1 }}>
                  <ColorField label="Couleur du cadre" value={config.photoFrameColor}
                    onChange={(v) => set('photoFrameColor', v)}
                    disabled={!config.showPhotoFrame} />
                </Box>
              </Grid>
              <Grid item xs={12}>
                <Typography variant="caption" color="text.secondary">
                  La photo de l&apos;élève reste imprimée même si le cadre est masqué :
                  le cadre est une décoration, la photo une donnée d&apos;identification.
                </Typography>
              </Grid>
            </Grid>
          </Section>

          <Section icon={<ListAlt fontSize="small" />} title="Champs imprimés"
            subtitle={`${fields.length} champ(s) sur ${meta.maxFields} maximum`}>
            <Grid container spacing={1}>
              {(meta.availableFields || []).map((f) => {
                const checked = fields.includes(f.code)
                const last = checked && fields.length === 1
                return (
                  <Grid item xs={12} sm={6} key={f.code}>
                    <FormControlLabel
                      control={(
                        <Switch size="small" checked={checked}
                          onChange={() => toggleField(f.code)}
                          disabled={last} />
                      )}
                      label={f.label}
                    />
                    {last && (
                      <Typography variant="caption" color="text.secondary" display="block">
                        Au moins un champ doit rester imprimé.
                      </Typography>
                    )}
                  </Grid>
                )
              })}
            </Grid>
            <Divider />
            <TextField fullWidth label="Pied de carte" value={config.footerText}
              onChange={(e) => set('footerText', e.target.value)}
              inputProps={{ maxLength: 80 }}
              helperText="Laisser vide pour ne rien imprimer en bas à gauche" />
          </Section>

          <Card sx={{ position: 'sticky', bottom: 0, zIndex: 2 }}>
            <Stack direction="row" spacing={1} p={2} justifyContent="space-between"
              alignItems="center" flexWrap="wrap" gap={1}>
              <Typography variant="caption" color="text.secondary">
                {dirty
                  ? 'Des modifications ne sont pas encore enregistrées.'
                  : 'Toutes les valeurs sont à jour.'}
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" onClick={downloadPreview} startIcon={<Visibility />}>
                  Aperçu PDF
                </Button>
                <Button variant="contained" onClick={save} disabled={saving || !dirty}
                  startIcon={saving ? <CircularProgress size={16} color="inherit" /> : <Save />}>
                  {saving ? 'Enregistrement…' : 'Enregistrer'}
                </Button>
              </Stack>
            </Stack>
          </Card>
        </Grid>

        {/* ---------------- Aperçu ---------------- */}
        <Grid item xs={12} lg={5}>
          <Card sx={{ position: { lg: 'sticky' }, top: { lg: 16 }, borderRadius: '16px' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                <Tune fontSize="small" color="action" />
                <Typography variant="subtitle1" fontWeight={700}>Aperçu</Typography>
                {previewing && <CircularProgress size={14} />}
                <Box sx={{ flex: 1 }} />
                <Tooltip title="La carte produite par le serveur, avec un élève de démonstration">
                  <Chip size="small" label="Document réel" variant="outlined" />
                </Tooltip>
              </Stack>
              {previewError && (
                <Alert severity="warning" sx={{ mb: 1 }}>{previewError}</Alert>
              )}
              <Box sx={{
                border: '1px solid', borderColor: 'divider', borderRadius: 2,
                overflow: 'hidden', bgcolor: 'grey.100', minHeight: 220,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                {previewUrl ? (
                  <Box
                    component="iframe"
                    title="Aperçu de la carte scolaire"
                    src={previewUrl}
                    sx={{ width: '100%', height: 260, border: 0 }}
                  />
                ) : (
                  <Typography variant="caption" color="text.secondary">
                    {previewing ? 'Génération de l\'aperçu…' : 'Aucun aperçu'}
                  </Typography>
                )}
              </Box>
              <Typography variant="caption" color="text.secondary" display="block" mt={1}>
                La carte réelle mesure 85,6 × 54 mm. L&apos;aperçu porte la mention
                « sans valeur officielle » et n&apos;utilise aucune donnée d&apos;élève réelle.
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <ConfirmDialog
        open={confirmReset}
        title="Revenir au modèle d'origine"
        message="Tous les réglages de la carte seront effacés : emblème, textes, couleurs et champs reviennent aux valeurs d'origine. L'emblème téléversé est conservé."
        onConfirm={reset}
        onClose={() => setConfirmReset(false)}
      />
    </>
  )
}
