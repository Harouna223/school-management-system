import { useEffect, useMemo, useState } from 'react'
import {
  Box, Card, CardContent, Grid, Typography, Stack, Divider, Button, Chip, TextField,
  MenuItem, Switch, FormControlLabel, Slider, ToggleButtonGroup, ToggleButton, Alert,
  CircularProgress, Tooltip, Table, TableBody, TableCell, TableHead, TableRow,
  Collapse,
} from '@mui/material'
import {
  CheckCircle, RestartAlt, Save, Visibility, History, Palette, Article, Tune,
  Preview as PreviewIcon, Undo, ExpandMore, ExpandLess,
} from '@mui/icons-material'
import { documentThemeApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { useToast } from '../../hooks/useToast'
import { downloadBlob, formatDateTime } from '../../utils/format'

/* ------------------------------------------------------------------ */
/*  Aides                                                              */
/* ------------------------------------------------------------------ */

/** Palette de vignette : rend un modèle reconnaissable sans ouvrir un PDF. */
const THUMB_COLORS = {
  CLASSIC: { bar: '#1e3a8a', accent: '#3b82f6', rule: true, center: false, serif: false },
  MODERN: { bar: '#2563eb', accent: '#2563eb', rule: true, center: true, serif: false },
  PREMIUM: { bar: '#7f1d1d', accent: '#b39058', rule: true, center: true, serif: true },
  MINIMAL: { bar: '#475569', accent: '#94a3b8', rule: false, center: false, serif: false },
  CUSTOM: { bar: '#334155', accent: '#0ea5e9', rule: true, center: false, serif: false },
}

/**
 * Vignette d'un modèle : reproduction schématique de la mise en page
 * (position du logo, alignement du titre, filet, densité du tableau).
 * Ce n'est pas une image du PDF — c'est un repère de choix, le rendu exact
 * étant obtenu par « Prévisualiser ».
 */
function TemplateThumb({ code, palette }) {
  const t = THUMB_COLORS[code] || THUMB_COLORS.CUSTOM
  const bar = palette?.primary || t.bar
  const accent = palette?.accent || t.accent
  const line = '#cbd5e1'
  const text = palette?.text || '#1e293b'
  return (
    <Box
      sx={{
        position: 'relative', height: 128, borderRadius: 2, bgcolor: '#fff',
        border: '1px solid', borderColor: 'divider', overflow: 'hidden', p: 1.2,
      }}
    >
      {/* En-tête */}
      <Stack
        direction={t.center ? 'column' : 'row'}
        spacing={0.8}
        alignItems={t.center ? 'center' : 'flex-start'}
      >
        <Box sx={{ width: 18, height: 18, borderRadius: 0.5, bgcolor: bar, flexShrink: 0 }} />
        <Box sx={{ flex: 1, width: t.center ? '100%' : 'auto' }}>
          <Box sx={{ height: 5, width: t.center ? '55%' : '70%', bgcolor: text, opacity: 0.75, mx: t.center ? 'auto' : 0, borderRadius: 0.5 }} />
          <Box sx={{ height: 3, width: t.center ? '40%' : '55%', bgcolor: line, mt: 0.6, mx: t.center ? 'auto' : 0, borderRadius: 0.5 }} />
        </Box>
      </Stack>
      {t.rule && <Box sx={{ height: t.serif ? 2 : 2.5, bgcolor: bar, mt: 0.9, mb: 0.8 }} />}

      {/* Titre */}
      <Box sx={{ height: 6, width: t.center ? '45%' : '60%', bgcolor: accent, mx: t.center ? 'auto' : 0, my: 0.8, borderRadius: 0.5 }} />

      {/* Tableau */}
      <Box sx={{ border: '1px solid', borderColor: line, borderRadius: 0.5, overflow: 'hidden' }}>
        <Box sx={{ height: 6, bgcolor: t.serif ? '#e2e8f0' : bar, opacity: t.serif ? 1 : 0.9 }} />
        {[0, 1, 2].map((i) => (
          <Box
            key={i}
            sx={{
              height: 5,
              bgcolor: i % 2 === 1 ? '#f1f5f9' : 'transparent',
              borderTop: i === 0 ? 0 : `1px solid ${line}`,
            }}
          />
        ))}
      </Box>
    </Box>
  )
}

/** Vignette d'une palette : 5 pastilles, de la couleur principale au pied de page. */
function PaletteSwatch({ palette }) {
  return (
    <Stack direction="row" spacing={0.4}>
      {[palette.primary, palette.tableHeader, palette.accent, palette.border, palette.footerText]
        .map((c, i) => (
          <Box key={i} sx={{ width: 16, height: 16, borderRadius: '50%', bgcolor: c, border: '1px solid rgba(0,0,0,.08)' }} />
        ))}
    </Stack>
  )
}

const COLOR_FIELDS = [
  { key: 'primary', label: 'Couleur principale' },
  { key: 'secondary', label: 'Couleur secondaire' },
  { key: 'accent', label: 'Accentuation' },
  { key: 'heading', label: 'Titres' },
  { key: 'tableHeader', label: 'En-têtes de tableaux' },
  { key: 'headerText', label: 'Texte sur en-têtes' },
  { key: 'border', label: 'Bordures' },
  { key: 'text', label: 'Texte' },
  { key: 'sectionBackground', label: 'Fond des sections' },
  { key: 'footerText', label: 'Pied de page' },
]

/** Niveau 2 : options réellement utiles, par type de document. */
const PER_DOCUMENT_OPTIONS = {
  BULLETIN: [
    { field: 'layout.orientation', label: 'Orientation', type: 'select', options: ['PORTRAIT', 'LANDSCAPE'] },
    { field: 'typography.tableSize', label: 'Taille du tableau', type: 'slider', min: 6, max: 12, step: 0.5 },
  ],
  SCHOOL_CERTIFICATE: [
    { field: 'header.showLogo', label: 'Afficher le logo', type: 'switch' },
    { field: 'header.titleAlign', label: 'Alignement du titre', type: 'select', options: ['LEFT', 'CENTER', 'RIGHT'] },
  ],
  PAYMENT_RECEIPT: [
    { field: 'layout.orientation', label: 'Orientation', type: 'select', options: ['PORTRAIT', 'LANDSCAPE'] },
    { field: 'typography.bodySize', label: 'Taille du texte', type: 'slider', min: 7, max: 14, step: 0.5 },
  ],
  STUDENT_LIST: [
    { field: 'layout.orientation', label: 'Orientation', type: 'select', options: ['PORTRAIT', 'LANDSCAPE'] },
    { field: 'layout.zebraStripes', label: 'Lignes alternées', type: 'switch' },
  ],
  GENERIC_REPORT: [
    { field: 'layout.orientation', label: 'Orientation', type: 'select', options: ['PORTRAIT', 'LANDSCAPE'] },
    { field: 'tableBorders', label: 'Bordures des cellules', type: 'switch' },
  ],
}

const GROUP_LABELS = {
  scolarite: 'Documents scolaires',
  rapports: 'Rapports',
  finance: 'Documents financiers',
}

/**
 * « Modèles et apparence des documents ».
 *
 * Toute la configuration est un objet JSON unique, envoyé tel quel au serveur.
 * L'écran ne calcule rien : il affiche ce que le serveur renvoie et
 * prévisualise avec le même moteur PDF que les vrais documents.
 */
export default function DocumentThemePage() {
  const { success, error: toastError } = useToast()

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [meta, setMeta] = useState(null)
  const [config, setConfig] = useState(null)
  const [savedSnapshot, setSavedSnapshot] = useState(null)
  const [warnings, setWarnings] = useState([])
  const [history, setHistory] = useState([])
  const [historyOpen, setHistoryOpen] = useState(false)
  const [overrides, setOverrides] = useState({})
  const [label, setLabel] = useState('')
  const [previewType, setPreviewType] = useState('')
  const [documentType, setDocumentType] = useState('BULLETIN')

  const load = async () => {
    setLoading(true)
    try {
      const { data } = await documentThemeApi.get()
      const payload = data.data
      setMeta(payload)
      setConfig(payload.active)
      setSavedSnapshot(JSON.stringify(payload.active))
      setWarnings(payload.contrastWarnings || [])
      setLabel(payload.activeVersionLabel || '')
      const hist = await documentThemeApi.history()
      setHistory(hist.data.data || [])
      const ov = await documentThemeApi.overrides()
      setOverrides(ov.data.data || {})
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const dirty = useMemo(
    () => config !== null && JSON.stringify(config) !== savedSnapshot,
    [config, savedSnapshot],
  )

  const patch = (path, value) => setConfig((prev) => {
    const next = JSON.parse(JSON.stringify(prev))
    const keys = path.split('.')
    let cursor = next
    for (let i = 0; i < keys.length - 1; i += 1) cursor = cursor[keys[i]]
    cursor[keys[keys.length - 1]] = value
    return next
  })

  const read = (path) => path.split('.').reduce((acc, k) => (acc == null ? acc : acc[k]), config)

  /** Choix d'un modèle : le serveur renvoie l'apparence résultante. */
  const applyTemplate = async (code) => {
    try {
      const { data } = await documentThemeApi.preset(code)
      const next = data.data.config
      next.header = config.header
      next.layout.pageNumbers = config.layout.pageNumbers
      next.layout.zebraStripes = config.layout.zebraStripes
      next.layout.tableBorders = config.layout.tableBorders
      setConfig(next)
      setWarnings(data.data.contrastWarnings || [])
    } catch (err) {
      toastError(extractError(err))
    }
  }

  /** Choix d'une palette : ne touche qu'aux couleurs. */
  const applyPalette = (palette) => {
    setConfig((prev) => ({
      ...prev,
      colors: {
        primary: palette.primary,
        secondary: palette.secondary,
        accent: palette.accent,
        heading: palette.heading,
        tableHeader: palette.tableHeader,
        headerText: '#ffffff',
        border: palette.border,
        text: palette.text,
        sectionBackground: palette.sectionBackground,
        footerText: palette.footerText,
      },
    }))
  }

  const preview = async (type) => {
    setPreviewing(true)
    try {
      const { data } = await documentThemeApi.preview(type || previewType || '', config)
      downloadBlob(data, `apercu-${type || 'modele'}.pdf`)
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setPreviewing(false)
    }
  }

  const save = async (activate) => {
    setSaving(true)
    try {
      await documentThemeApi.save({
        label: label?.trim() || null,
        config,
        activate,
      })
      success(activate ? 'Configuration appliquée aux nouveaux documents.' : 'Configuration enregistrée.')
      await load()
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  const activateVersion = async (id) => {
    try {
      await documentThemeApi.activate(id)
      success('Configuration réactivée.')
      await load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const restoreDefault = async () => {
    if (!window.confirm('Restaurer la charte d\'origine ? Les configurations enregistrées sont conservées.')) return
    try {
      await documentThemeApi.restoreDefault()
      success('Charte d\'origine restaurée.')
      await load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  if (loading || !config || !meta) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  const documentsByGroup = (meta.documents || []).reduce((acc, d) => {
    acc[d.group] = acc[d.group] || []
    acc[d.group].push(d)
    return acc
  }, {})

  const currentTemplate = meta.templates?.find((t) => t.code === config.template)

  return (
    <Box>
      {/* Barre d'actions */}
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={1.5}
        alignItems={{ md: 'center' }}
        justifyContent="space-between"
        mb={2}
      >
        <Box>
          <Typography variant="h5" fontWeight={700}>Modèles et apparence des documents</Typography>
          <Typography variant="body2" color="text.secondary">
            Un seul réglage s'applique à tous les nouveaux documents PDF. Les documents déjà délivrés ne sont pas modifiés.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip
            size="small"
            color={meta.usingDefaults ? 'default' : 'success'}
            label={meta.usingDefaults ? 'Charte d\'origine' : `Actif : ${meta.activeVersionLabel}`}
          />
          <Button variant="outlined" startIcon={<Visibility />} disabled={previewing} onClick={() => preview('')}>
            Prévisualiser
          </Button>
          <Button variant="outlined" startIcon={<Save />} disabled={!dirty || saving} onClick={() => save(false)}>
            Enregistrer
          </Button>
          <Button variant="contained" startIcon={<CheckCircle />} disabled={!dirty || saving} onClick={() => save(true)}>
            Appliquer
          </Button>
          <Button variant="text" color="inherit" startIcon={<Undo />} disabled={!dirty} onClick={() => setConfig(JSON.parse(savedSnapshot))}>
            Annuler
          </Button>
          <Button variant="text" color="warning" startIcon={<RestartAlt />} onClick={restoreDefault}>
            Charte d'origine
          </Button>
        </Stack>
      </Stack>

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={8}>
          {/* 1. Modèles */}
          <Card sx={{ mb: 2.5, borderRadius: '16px' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                <Article color="primary" />
                <Typography fontWeight={700}>1. Modèle de document</Typography>
              </Stack>
              <Divider sx={{ mb: 2 }} />
              <Grid container spacing={2}>
                {(meta.templates || []).map((t) => {
                  const selected = config.template === t.code
                  const palette = meta.palettes?.find((p) => p.primary === config.colors.primary)
                  return (
                    <Grid item xs={12} sm={6} md={4} key={t.code}>
                      <Box
                        role="button"
                        tabIndex={0}
                        onClick={() => applyTemplate(t.code)}
                        onKeyDown={(e) => e.key === 'Enter' && applyTemplate(t.code)}
                        sx={{
                          cursor: 'pointer',
                          borderRadius: 3,
                          p: 1,
                          border: '2px solid',
                          borderColor: selected ? 'primary.main' : 'transparent',
                          bgcolor: selected ? 'action.hover' : 'transparent',
                          transition: 'all .15s',
                          '&:hover': { bgcolor: 'action.hover' },
                        }}
                      >
                        <TemplateThumb code={t.code} palette={{ ...config.colors, ...(palette || {}) }} />
                        <Stack direction="row" spacing={0.5} alignItems="center" mt={1}>
                          <Typography variant="subtitle2" fontWeight={700}>{t.label}</Typography>
                          {selected && <CheckCircle color="primary" sx={{ fontSize: 16 }} />}
                        </Stack>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.35 }}>
                          {t.description}
                        </Typography>
                      </Box>
                    </Grid>
                  )
                })}
              </Grid>
              {currentTemplate && (
                <Alert severity="info" sx={{ mt: 2 }} icon={false}>
                  Modèle sélectionné : <strong>{currentTemplate.label}</strong>. Changer de modèle ne modifie pas
                  les couleurs — la palette reste celle de l'établissement.
                </Alert>
              )}
            </CardContent>
          </Card>

          {/* 2. Couleurs */}
          <Card sx={{ mb: 2.5, borderRadius: '16px' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                <Palette color="primary" />
                <Typography fontWeight={700}>2. Couleurs et identité visuelle</Typography>
              </Stack>
              <Divider sx={{ mb: 2 }} />

              <Typography variant="body2" color="text.secondary" mb={1}>Palettes prédéfinies</Typography>
              <Grid container spacing={1.5} mb={2.5}>
                {(meta.palettes || []).map((p) => (
                  <Grid item xs={12} sm={6} md={4} key={p.code}>
                    <Box
                      role="button"
                      tabIndex={0}
                      onClick={() => applyPalette(p)}
                      onKeyDown={(e) => e.key === 'Enter' && applyPalette(p)}
                      sx={{
                        cursor: 'pointer', p: 1.2, borderRadius: 2, border: '1px solid',
                        borderColor: config.colors.primary === p.primary ? 'primary.main' : 'divider',
                        '&:hover': { bgcolor: 'action.hover' },
                      }}
                    >
                      <PaletteSwatch palette={p} />
                      <Typography variant="caption" fontWeight={600} display="block" mt={0.8}>
                        {p.label}
                      </Typography>
                    </Box>
                  </Grid>
                ))}
              </Grid>

              <Divider sx={{ mb: 2 }} />
              <Typography variant="body2" color="text.secondary" mb={1.5}>
                Réglage fin (modifiable après une palette)
              </Typography>
              <Grid container spacing={2}>
                {COLOR_FIELDS.map((f) => (
                  <Grid item xs={12} sm={6} md={4} key={f.key}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Box
                        component="input"
                        type="color"
                        aria-label={f.label}
                        value={config.colors[f.key] || '#000000'}
                        onChange={(e) => patch(`colors.${f.key}`, e.target.value)}
                        sx={{
                          width: 40, height: 40, p: 0, border: '1px solid', borderColor: 'divider',
                          borderRadius: 1.5, bgcolor: 'transparent', cursor: 'pointer',
                        }}
                      />
                      <TextField
                        size="small"
                        fullWidth
                        label={f.label}
                        value={config.colors[f.key] || ''}
                        onChange={(e) => patch(`colors.${f.key}`, e.target.value)}
                        error={!!(config.colors[f.key] && !/^#[0-9a-fA-F]{6}$/.test(config.colors[f.key]))}
                        helperText={config.colors[f.key] && !/^#[0-9a-fA-F]{6}$/.test(config.colors[f.key])
                          ? 'Format attendu #RRGGBB' : ' '}
                      />
                    </Stack>
                  </Grid>
                ))}
              </Grid>

              {warnings.length > 0 && (
                <Alert severity="warning" sx={{ mt: 1 }}>
                  <Typography variant="subtitle2">Contraste à vérifier</Typography>
                  {warnings.map((w) => (
                    <Typography key={w} variant="body2">• {w}</Typography>
                  ))}
                  <Typography variant="caption">
                    Vérifié en niveaux de gris : c'est ce ratio qui compte à l'impression noir et blanc.
                  </Typography>
                </Alert>
              )}
            </CardContent>
          </Card>

          {/* 3. Typographie et mise en page */}
          <Card sx={{ mb: 2.5, borderRadius: '16px' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                <Tune color="primary" />
                <Typography fontWeight={700}>3. Typographie et mise en page</Typography>
              </Stack>
              <Divider sx={{ mb: 2 }} />
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6} md={3}>
                  <TextField
                    select size="small" fullWidth label="Police"
                    value={config.typography.fontFamily}
                    onChange={(e) => patch('typography.fontFamily', e.target.value)}
                  >
                    <MenuItem value="HELVETICA">Helvetica (sans empattement)</MenuItem>
                    <MenuItem value="TIMES">Times (empattement)</MenuItem>
                    <MenuItem value="COURIER">Courier (chasse fixe)</MenuItem>
                  </TextField>
                  <Typography variant="caption" color="text.secondary">
                    Polices PDF standard : rendu garanti, sans intégration de fichier.
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <TextField
                    select size="small" fullWidth label="Format de page"
                    value={config.layout.pageFormat}
                    onChange={(e) => patch('layout.pageFormat', e.target.value)}
                  >
                    <MenuItem value="A4">A4 (210 × 297 mm)</MenuItem>
                    <MenuItem value="A5">A5 (148 × 210 mm)</MenuItem>
                    <MenuItem value="LETTER">Letter</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <Typography variant="caption" color="text.secondary">Orientation</Typography>
                  <ToggleButtonGroup
                    size="small" exclusive fullWidth
                    value={config.layout.orientation}
                    onChange={(e, v) => v && patch('layout.orientation', v)}
                  >
                    <ToggleButton value="PORTRAIT">Portrait</ToggleButton>
                    <ToggleButton value="LANDSCAPE">Paysage</ToggleButton>
                  </ToggleButtonGroup>
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <Typography variant="caption" color="text.secondary">Alignement des titres</Typography>
                  <ToggleButtonGroup
                    size="small" exclusive fullWidth
                    value={config.header.titleAlign}
                    onChange={(e, v) => v && patch('header.titleAlign', v)}
                  >
                    <ToggleButton value="LEFT">Gauche</ToggleButton>
                    <ToggleButton value="CENTER">Centré</ToggleButton>
                    <ToggleButton value="RIGHT">Droite</ToggleButton>
                  </ToggleButtonGroup>
                </Grid>

                {[
                  { path: 'typography.bodySize', label: 'Texte principal', min: 7, max: 14, step: 0.5 },
                  { path: 'typography.titleSize', label: 'Titres', min: 12, max: 26, step: 0.5 },
                  { path: 'typography.subtitleSize', label: 'Sous-titres', min: 7, max: 16, step: 0.5 },
                  { path: 'typography.tableSize', label: 'Texte des tableaux', min: 6, max: 12, step: 0.5 },
                  { path: 'layout.marginLeft', label: 'Marge gauche (pt)', min: 14, max: 90, step: 1 },
                  { path: 'layout.marginRight', label: 'Marge droite (pt)', min: 14, max: 90, step: 1 },
                  { path: 'layout.marginTop', label: 'Marge haute (pt)', min: 14, max: 90, step: 1 },
                  { path: 'layout.marginBottom', label: 'Marge basse (pt)', min: 20, max: 110, step: 1 },
                  { path: 'layout.sectionSpacing', label: 'Espacement des sections', min: 2, max: 24, step: 1 },
                ].map((s) => (
                  <Grid item xs={12} sm={6} md={4} key={s.path}>
                    <Typography variant="caption" color="text.secondary">
                      {s.label} : <strong>{read(s.path)}</strong>
                    </Typography>
                    <Slider
                      size="small" min={s.min} max={s.max} step={s.step}
                      value={Number(read(s.path)) || s.min}
                      onChange={(e, v) => patch(s.path, v)}
                    />
                  </Grid>
                ))}

                <Grid item xs={12}>
                  <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
                    <FormControlLabel
                      control={<Switch size="small" checked={!!config.layout.pageNumbers}
                        onChange={(e) => patch('layout.pageNumbers', e.target.checked)} />}
                      label="Numéros de page"
                    />
                    <FormControlLabel
                      control={<Switch size="small" checked={!!config.layout.zebraStripes}
                        onChange={(e) => patch('layout.zebraStripes', e.target.checked)} />}
                      label="Lignes alternées dans les tableaux"
                    />
                    <FormControlLabel
                      control={<Switch size="small" checked={!!config.layout.tableBorders}
                        onChange={(e) => patch('layout.tableBorders', e.target.checked)} />}
                      label="Bordures de cellules"
                    />
                  </Stack>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          {/* 4. En-tête et pied de page */}
          <Card sx={{ mb: 2.5, borderRadius: '16px' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                <PreviewIcon color="primary" />
                <Typography fontWeight={700}>4. En-tête et pied de page</Typography>
              </Stack>
              <Divider sx={{ mb: 2 }} />
              <Alert severity="info" sx={{ mb: 2 }}>
                Le nom, le logo, l'adresse et les coordonnées sont lus dans
                <strong> Informations de l'établissement</strong> — rien n'est redemandé ici.
              </Alert>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6} md={3}>
                  <FormControlLabel
                    control={<Switch size="small" checked={!!config.header.showLogo}
                      onChange={(e) => patch('header.showLogo', e.target.checked)} />}
                    label="Afficher le logo"
                  />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <FormControlLabel
                    control={<Switch size="small" checked={!!config.header.showSchoolName}
                      onChange={(e) => patch('header.showSchoolName', e.target.checked)} />}
                    label="Nom de l'établissement"
                  />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <FormControlLabel
                    control={<Switch size="small" checked={!!config.header.showAddress}
                      onChange={(e) => patch('header.showAddress', e.target.checked)} />}
                    label="Adresse"
                  />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <FormControlLabel
                    control={<Switch size="small" checked={!!config.header.showContact}
                      onChange={(e) => patch('header.showContact', e.target.checked)} />}
                    label="Téléphone et e-mail"
                  />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <FormControlLabel
                    control={<Switch size="small" checked={!!config.header.headerRule}
                      onChange={(e) => patch('header.headerRule', e.target.checked)} />}
                    label="Filet sous l'en-tête"
                  />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                  <TextField
                    select size="small" fullWidth label="Position du logo"
                    value={config.header.logoPosition}
                    onChange={(e) => patch('header.logoPosition', e.target.value)}
                  >
                    <MenuItem value="LEFT">Gauche</MenuItem>
                    <MenuItem value="CENTER">Centre (sous le nom)</MenuItem>
                    <MenuItem value="RIGHT">Droite</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    size="small" fullWidth label="Pied de page personnalisé"
                    value={config.header.footerText || ''}
                    onChange={(e) => patch('header.footerText', e.target.value)}
                    helperText="Affiché au centre du pied de page (300 caractères max.)"
                    inputProps={{ maxLength: 300 }}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    size="small" fullWidth label="Texte institutionnel (facultatif)"
                    value={config.header.institutionalText || ''}
                    onChange={(e) => patch('header.institutionalText', e.target.value)}
                    helperText="Mention ajoutée sous les signatures"
                    inputProps={{ maxLength: 300 }}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          {/* 5. Options par document */}
          <Card sx={{ mb: 2.5, borderRadius: '16px' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                <Tune color="primary" />
                <Typography fontWeight={700}>5. Options propres à un type de document</Typography>
              </Stack>
              <Divider sx={{ mb: 2 }} />
              <Typography variant="body2" color="text.secondary" mb={1.5}>
                Une option choisie ici ne remplace le thème que pour ce document. Les tableaux de notes,
                les montants et les mentions officielles ne changent jamais.
              </Typography>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems="center">
                <TextField
                  select size="small" label="Type de document" sx={{ minWidth: 240 }}
                  value={documentType}
                  onChange={(e) => { setDocumentType(e.target.value); setPreviewType(e.target.value) }}
                >
                  {(meta.documents || []).map((d) => (
                    <MenuItem key={d.code} value={d.code}>{d.label}</MenuItem>
                  ))}
                </TextField>
                <Button
                  variant="outlined" size="small" startIcon={<PreviewIcon />}
                  disabled={previewing}
                  onClick={() => preview(documentType)}
                >
                  Aperçu de ce document
                </Button>
                <Chip
                  size="small"
                  variant={overrides[documentType] ? 'filled' : 'outlined'}
                  color={overrides[documentType] ? 'info' : 'default'}
                  label={overrides[documentType] ? 'Options personnalisées' : 'Suit le thème global'}
                />
                {overrides[documentType] && (
                  <Button
                    size="small" color="inherit"
                    onClick={async () => {
                      await documentThemeApi.deleteOverride(documentType)
                      const ov = await documentThemeApi.overrides()
                      setOverrides(ov.data.data || {})
                      success('Options supprimées : le document suit à nouveau le thème global.')
                    }}
                  >
                    Réinitialiser
                  </Button>
                )}
              </Stack>

              <Box sx={{ mt: 2 }}>
                <PerDocumentEditor
                  type={documentType}
                  raw={overrides[documentType]}
                  onSave={async (json) => {
                    await documentThemeApi.saveOverride(documentType, json)
                    const ov = await documentThemeApi.overrides()
                    setOverrides(ov.data.data || {})
                    success('Options du document enregistrées.')
                  }}
                  onError={(e) => toastError(extractError(e))}
                />
              </Box>
            </CardContent>
          </Card>

          {/* 6. Historique */}
          <Card sx={{ borderRadius: '16px' }}>
            <CardContent>
              <Stack
                direction="row" spacing={1} alignItems="center"
                sx={{ cursor: 'pointer' }} onClick={() => setHistoryOpen((v) => !v)}
              >
                <History color="primary" />
                <Typography fontWeight={700} sx={{ flex: 1 }}>6. Historique des configurations</Typography>
                {historyOpen ? <ExpandLess /> : <ExpandMore />}
              </Stack>
              <Collapse in={historyOpen}>
                <Divider sx={{ my: 2 }} />
                {history.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    Aucune configuration enregistrée : la charte d'origine est utilisée.
                  </Typography>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Nom</TableCell>
                        <TableCell>Modèle</TableCell>
                        <TableCell>Enregistrée le</TableCell>
                        <TableCell>Par</TableCell>
                        <TableCell align="right">Action</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {history.map((h) => (
                        <TableRow key={h.id} selected={h.active}>
                          <TableCell>{h.label}</TableCell>
                          <TableCell>{meta.templates?.find((t) => t.code === h.template)?.label || h.template}</TableCell>
                          <TableCell>{h.createdAt ? formatDateTime(h.createdAt) : '—'}</TableCell>
                          <TableCell>{h.createdBy || '—'}</TableCell>
                          <TableCell align="right">
                            {h.active ? (
                              <Chip size="small" color="success" label="Active" />
                            ) : (
                              <Button size="small" onClick={() => activateVersion(h.id)}>Réactiver</Button>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Collapse>
            </CardContent>
          </Card>
        </Grid>

        {/* Colonne latérale : nom + prévisualisation */}
        <Grid item xs={12} lg={4}>
          <Card sx={{ borderRadius: '16px', position: { lg: 'sticky' }, top: { lg: 16 } }}>
            <CardContent>
              <Typography fontWeight={700} mb={1}>Enregistrement</Typography>
              <TextField
                size="small" fullWidth label="Nom de la configuration"
                placeholder={`Configuration du ${new Date().toLocaleDateString('fr-FR')}`}
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                inputProps={{ maxLength: 120 }}
                helperText="Sert à retrouver la version dans l'historique"
              />
              <Divider sx={{ my: 2 }} />

              <Typography fontWeight={700} mb={1}>Prévisualisation</Typography>
              <Typography variant="caption" color="text.secondary" display="block" mb={1.5}>
                PDF généré par le moteur réel, avec des <strong>données de démonstration</strong>.
                Aucune donnée de l'application n'est lue ni modifiée.
              </Typography>
              <TextField
                select size="small" fullWidth label="Type de document"
                value={previewType}
                onChange={(e) => setPreviewType(e.target.value)}
                sx={{ mb: 1.5 }}
              >
                <MenuItem value="">Aperçu complet (modèle)</MenuItem>
                {Object.entries(documentsByGroup).map(([group, docs]) => [
                  <MenuItem key={`g-${group}`} disabled divider sx={{ opacity: 0.7, fontSize: 12 }}>
                    {GROUP_LABELS[group] || group}
                  </MenuItem>,
                  ...docs.map((d) => (
                    <MenuItem key={d.code} value={d.code}>{d.label}</MenuItem>
                  )),
                ])}
              </TextField>
              <Stack direction="row" spacing={1}>
                <Button
                  fullWidth variant="outlined" startIcon={<Visibility />}
                  disabled={previewing}
                  onClick={() => preview(previewType)}
                >
                  {previewing ? 'Génération…' : 'Aperçu PDF'}
                </Button>
              </Stack>
              <Button
                fullWidth variant="outlined" color="inherit" sx={{ mt: 1 }} startIcon={<Undo />}
                disabled={!dirty}
                onClick={() => setConfig(JSON.parse(savedSnapshot))}
              >
                Annuler les modifications
              </Button>
              {dirty && (
                <Alert severity="info" sx={{ mt: 2 }}>
                  Modifications non enregistrées. « Appliquer » les rend effectives pour
                  les <strong>nouveaux</strong> documents.
                </Alert>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

/**
 * Éditeur des options niveau 2.
 *
 * Les champs proposés dépendent du type (un reçu n'a pas de tableau de
 * matières). La valeur enregistrée est un patch JSON partiel : seules les
 * clés modifiées sont transmises au serveur.
 */
function PerDocumentEditor({ type, raw, onSave, onError }) {
  const [patch, setPatch] = useState(() => (raw ? JSON.parse(raw) : {}))
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setPatch(raw ? JSON.parse(raw) : {})
  }, [type, raw])

  const options = PER_DOCUMENT_OPTIONS[type] || []
  const valueOf = (field) => {
    const [group, key] = field.split('.')
    return patch[group]?.[key]
  }
  const setValue = (field, value) => {
    const [group, key] = field.split('.')
    setPatch((prev) => ({ ...prev, [group]: { ...(prev[group] || {}), [key]: value } }))
  }

  if (options.length === 0) {
    return (
      <Alert severity="info">
        Ce document suit entièrement le thème global : sa mise en page est normalisée
        (carte scolaire au format ID-1, certificat au format A5) et ne se règle pas ici.
      </Alert>
    )
  }

  return (
    <Stack spacing={1.5}>
      <Grid container spacing={2}>
        {options.map((o) => (
          <Grid item xs={12} sm={6} md={4} key={o.field}>
            {o.type === 'switch' && (
              <FormControlLabel
                control={<Switch size="small" checked={!!valueOf(o.field)}
                  onChange={(e) => setValue(o.field, e.target.checked)} />}
                label={o.label}
              />
            )}
            {o.type === 'select' && (
              <TextField
                select size="small" fullWidth label={o.label}
                value={valueOf(o.field) || ''}
                onChange={(e) => setValue(o.field, e.target.value)}
              >
                <MenuItem value="">Thème global</MenuItem>
                {o.options.map((v) => (
                  <MenuItem key={v} value={v}>{v === 'LANDSCAPE' ? 'Paysage' : v === 'PORTRAIT' ? 'Portrait' : v}</MenuItem>
                ))}
              </TextField>
            )}
            {o.type === 'slider' && (
              <>
                <Typography variant="caption" color="text.secondary">
                  {o.label} : <strong>{valueOf(o.field) ?? 'thème global'}</strong>
                </Typography>
                <Slider
                  size="small" min={o.min} max={o.max} step={o.step}
                  value={valueOf(o.field) ?? o.min}
                  onChange={(e, v) => setValue(o.field, v)}
                />
              </>
            )}
          </Grid>
        ))}
      </Grid>
      <Stack direction="row" spacing={1}>
        <Button
          size="small" variant="contained" disabled={busy}
          onClick={async () => {
            setBusy(true)
            try { await onSave(patch) } catch (e) { onError(e) } finally { setBusy(false) }
          }}
        >
          Enregistrer ces options
        </Button>
        <Tooltip title="La suppression réactive le thème global pour ce document">
          <Button size="small" color="inherit" onClick={() => setPatch({})}>Vider</Button>
        </Tooltip>
      </Stack>
    </Stack>
  )
}
