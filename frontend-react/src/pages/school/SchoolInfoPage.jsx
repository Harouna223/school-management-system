import { useCallback, useEffect, useState } from 'react'
import {
  Grid, Card, CardContent, CardActions, Box, Typography, TextField, MenuItem,
  Button, Stack, Divider, Alert, Chip, Dialog, DialogTitle, DialogContent,
  DialogActions, CircularProgress, Avatar,
} from '@mui/material'
import Business from '@mui/icons-material/Business'
import ContactPhone from '@mui/icons-material/ContactPhone'
import Badge from '@mui/icons-material/Badge'
import ImageIcon from '@mui/icons-material/Image'
import Notes from '@mui/icons-material/Notes'
import Save from '@mui/icons-material/Save'
import RestartAlt from '@mui/icons-material/RestartAlt'
import Visibility from '@mui/icons-material/Visibility'
import CloudUpload from '@mui/icons-material/CloudUpload'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import SchoolIcon from '@mui/icons-material/School'
import Download from '@mui/icons-material/Download'
import PageHeader from '../../components/PageHeader'
import ConfirmDialog from '../../components/ConfirmDialog'
import FileImage from '../../components/FileImage'
import defaultLogo from '../../assets/logo.svg'
import { useToast } from '../../hooks/useToast'
import { settingsApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { downloadBlob } from '../../utils/format'

const TYPES = [
  'École maternelle', 'École fondamentale', 'Lycée',
  'Établissement technique et professionnel', 'Université', 'Autre',
]
const STATUSES = ['Public', 'Privé', 'Communautaire', 'Confessionnel', 'Autre']
const MAX_LOGO_BYTES = 5 * 1024 * 1024
const ACCEPTED_LOGO = ['image/png', 'image/jpeg']

const EMPTY = {
  name: '', shortName: '', type: '', status: '', identificationNumber: '',
  motto: '', creationYear: '',
  address: '', district: '', city: '', region: '', country: 'Mali',
  phoneCountryCode: '', phone: '', phoneAlt: '', email: '', website: '',
  directorName: '', directorRole: 'Directeur', directorPhone: '', directorEmail: '',
  accreditationNumber: '', additionalInfo: '', logoUrl: '',
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
        <Grid container spacing={2}>{children}</Grid>
      </CardContent>
    </Card>
  )
}

/**
 * « Informations de l'établissement ».
 *
 * S'appuie sur la table `settings` existante (clés SCHOOL_*) : les mêmes
 * valeurs alimentent les en-têtes des bulletins et des reçus PDF. Aucune
 * duplication de modèle, aucune modification de code nécessaire pour changer
 * l'identité de l'école.
 */
export default function SchoolInfoPage() {
  const { success, error: toastError } = useToast()
  const [saved, setSaved] = useState(null)
  const [form, setForm] = useState(EMPTY)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [logoFile, setLogoFile] = useState(null)
  const [logoPreviewUrl, setLogoPreviewUrl] = useState(null)
  const [logoBusy, setLogoBusy] = useState(false)
  const [logoDownloading, setLogoDownloading] = useState(false)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [confirmReset, setConfirmReset] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await settingsApi.schoolInfo()
      const value = { ...EMPTY, ...data.data, creationYear: data.data?.creationYear ?? '' }
      setSaved(value)
      setForm(value)
      setError('')
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }, [toastError])

  useEffect(() => { load() }, [load])

  const set = (field, value) => setForm((prev) => ({ ...prev, [field]: value }))
  const dirty = saved !== null
    && JSON.stringify({ ...form, logoUrl: saved.logoUrl }) !== JSON.stringify(saved)

  const localValidate = () => {
    if (!form.name?.trim()) return "Le nom officiel de l'établissement est obligatoire."
    // Même règle que la validation serveur : 1 à 4 chiffres, « + » toléré.
    if (form.phoneCountryCode && !/^\+?\d{1,4}$/.test(form.phoneCountryCode.trim())) {
      return "L'indicatif du pays doit comporter 1 à 4 chiffres (ex. 237)."
    }
    const emailPattern = /^[^@\s]+@[^@\s]+\.[A-Za-z]{2,}$/
    if (form.email && !emailPattern.test(form.email)) return "L'adresse e-mail de l'établissement est invalide."
    if (form.directorEmail && !emailPattern.test(form.directorEmail)) return "L'adresse e-mail du responsable est invalide."
    if (form.creationYear !== '' && Number(form.creationYear) > new Date().getFullYear() + 1) {
      return "L'année de création ne peut pas être dans le futur."
    }
    return ''
  }

  const save = async () => {
    const problem = localValidate()
    if (problem) { setError(problem); return }
    setSaving(true)
    setError('')
    try {
      const payload = {
        ...form,
        creationYear: form.creationYear === '' ? null : Number(form.creationYear),
        name: form.name.trim(),
      }
      const { data } = await settingsApi.updateSchoolInfo(payload)
      const value = { ...EMPTY, ...data.data, creationYear: data.data?.creationYear ?? '' }
      setSaved(value)
      setForm(value)
      success('Informations de l\'établissement enregistrées')
    } catch (err) {
      setError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  const reset = () => {
    setForm(saved)
    setLogoFile(null)
    setLogoPreviewUrl(null)
    setError('')
    setConfirmReset(false)
    success('Formulaire réinitialisé aux dernières valeurs enregistrées')
  }

  const pickLogo = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    setError('')
    if (!ACCEPTED_LOGO.includes(file.type)) {
      setError('Le logo doit être une image PNG ou JPEG.')
      event.target.value = ''
      return
    }
    if (file.size > MAX_LOGO_BYTES) {
      setError('Le logo ne doit pas dépasser 5 Mo.')
      event.target.value = ''
      return
    }
    setLogoFile(file)
    setLogoPreviewUrl(URL.createObjectURL(file))
  }

  const uploadLogo = async () => {
    if (!logoFile) return
    setLogoBusy(true)
    setError('')
    try {
      const { data } = await settingsApi.uploadLogo(logoFile)
      const url = data.data
      setForm((prev) => ({ ...prev, logoUrl: url }))
      setSaved((prev) => (prev ? { ...prev, logoUrl: url } : prev))
      setLogoFile(null)
      setLogoPreviewUrl(null)
      success('Logo enregistré')
    } catch (err) {
      setError(extractError(err))
    } finally {
      setLogoBusy(false)
    }
  }

  const removeLogo = async () => {
    setLogoBusy(true)
    setError('')
    try {
      await settingsApi.deleteLogo()
      setForm((prev) => ({ ...prev, logoUrl: '' }))
      setSaved((prev) => (prev ? { ...prev, logoUrl: '' } : prev))
      success('Logo supprimé')
    } catch (err) {
      setError(extractError(err))
    } finally {
      setLogoBusy(false)
    }
  }

  /**
   * Télécharge le fichier du logo tel qu'il est utilisé dans les documents PDF
   * et sur la carte scolaire.
   *
   * Le serveur répond par un flux binaire : en cas d'erreur, le corps est
   * aussi un blob, que `extractError` ne sait pas lire. On le décode donc
   * nous-mêmes pour afficher le vrai motif (logo absent, fichier supprimé du
   * disque) plutôt qu'un message générique.
   */
  const downloadLogo = async () => {
    setLogoDownloading(true)
    setError('')
    try {
      const { data, headers } = await settingsApi.downloadLogo()
      const disposition = headers?.['content-disposition'] || ''
      const match = /filename="([^"]+)"/.exec(disposition)
      downloadBlob(data, match ? match[1] : 'logo-etablissement')
      success('Logo téléchargé')
    } catch (err) {
      const body = err?.response?.data
      if (body instanceof Blob) {
        try {
          const text = await body.text()
          const parsed = JSON.parse(text)
          setError(parsed?.message || 'Le logo ne peut pas être téléchargé.')
        } catch {
          setError('Le logo ne peut pas être téléchargé.')
        }
      } else {
        setError(extractError(err))
      }
    } finally {
      setLogoDownloading(false)
    }
  }

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" py={10}><CircularProgress /></Box>
    )
  }

  const currentLogo = logoPreviewUrl || form.logoUrl

  return (
    <>
      <PageHeader
        title="Informations de l'établissement"
        subtitle="Identité, coordonnées, administration et logo — utilisés dans tous les documents"
        badge={dirty ? 'Modifications non enregistrées' : undefined}
        actions={[
          { label: 'Prévisualiser', icon: <Visibility />, onClick: () => setPreviewOpen(true) },
          { label: 'Réinitialiser', icon: <RestartAlt />, onClick: () => setConfirmReset(true) },
        ]}
      />

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Section icon={<Business fontSize="small" />} title="Identité de l'établissement"
        subtitle="Nom officiel, sigle, type et statut">
        <Grid item xs={12} md={6}>
          <TextField fullWidth required label="Nom officiel" value={form.name}
            onChange={(e) => set('name', e.target.value)}
            helperText="Obligatoire — apparaît sur tous les documents" />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth label="Sigle" value={form.shortName}
            onChange={(e) => set('shortName', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth type="number" label="Année de création" value={form.creationYear}
            onChange={(e) => set('creationYear', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField select fullWidth label="Type d'établissement" value={form.type}
            onChange={(e) => set('type', e.target.value)}>
            <MenuItem value="">Non précisé</MenuItem>
            {TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField select fullWidth label="Statut" value={form.status}
            onChange={(e) => set('status', e.target.value)}>
            <MenuItem value="">Non précisé</MenuItem>
            {STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField fullWidth label="N° d'identification" value={form.identificationNumber}
            onChange={(e) => set('identificationNumber', e.target.value)} />
        </Grid>
        <Grid item xs={12}>
          <TextField fullWidth label="Devise / slogan" value={form.motto}
            onChange={(e) => set('motto', e.target.value)} />
        </Grid>
      </Section>

      <Section icon={<ContactPhone fontSize="small" />} title="Coordonnées"
        subtitle="Adresse, contacts et présence en ligne">
        <Grid item xs={12} md={6}>
          <TextField fullWidth label="Adresse complète" value={form.address}
            onChange={(e) => set('address', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth label="Quartier" value={form.district}
            onChange={(e) => set('district', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth label="Ville / commune" value={form.city}
            onChange={(e) => set('city', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth label="Région" value={form.region}
            onChange={(e) => set('region', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth label="Pays" value={form.country}
            onChange={(e) => set('country', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth label="Indicatif du pays" value={form.phoneCountryCode}
            onChange={(e) => set('phoneCountryCode', e.target.value)}
            inputProps={{ inputMode: 'numeric' }}
            helperText="Ex. 237 — appliqué à tous les numéros sans indicatif" />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth label="Téléphone principal" value={form.phone}
            onChange={(e) => set('phone', e.target.value)}
            helperText="Formats locaux et internationaux acceptés" />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField fullWidth label="Téléphone secondaire" value={form.phoneAlt}
            onChange={(e) => set('phoneAlt', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField fullWidth type="email" label="E-mail officiel" value={form.email}
            onChange={(e) => set('email', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField fullWidth label="Site web" value={form.website}
            onChange={(e) => set('website', e.target.value)} />
        </Grid>
      </Section>

      <Section icon={<Badge fontSize="small" />} title="Informations administratives"
        subtitle="Responsable et agrément">
        <Grid item xs={12} md={4}>
          <TextField fullWidth label="Nom du directeur / de la directrice" value={form.directorName}
            onChange={(e) => set('directorName', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField fullWidth label="Fonction du responsable" value={form.directorRole}
            onChange={(e) => set('directorRole', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField fullWidth label="N° d'autorisation / agrément" value={form.accreditationNumber}
            onChange={(e) => set('accreditationNumber', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField fullWidth label="Téléphone du responsable" value={form.directorPhone}
            onChange={(e) => set('directorPhone', e.target.value)} />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField fullWidth type="email" label="E-mail du responsable" value={form.directorEmail}
            onChange={(e) => set('directorEmail', e.target.value)} />
        </Grid>
      </Section>

      <Section icon={<ImageIcon fontSize="small" />} title="Logo de l'établissement"
        subtitle="PNG ou JPEG, 5 Mo maximum — affiché en tête des documents PDF">
        <Grid item xs={12} md={4}>
          <Box sx={{ p: 2, border: '1px dashed', borderColor: 'divider', borderRadius: 3,
            display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 140 }}>
            {currentLogo ? (
              <FileImage src={currentLogo} alt="Logo" sx={{ maxWidth: '100%', maxHeight: 110 }} />
            ) : (
              <Stack alignItems="center" spacing={1}>
                <SchoolIcon sx={{ fontSize: 40, color: 'text.disabled' }} />
                <Typography variant="caption" color="text.secondary">Aucun logo</Typography>
              </Stack>
            )}
          </Box>
        </Grid>
        <Grid item xs={12} md={8}>
          <Stack spacing={1.5}>
            <Typography variant="body2" color="text.secondary">
              Le format réel du fichier est vérifié côté serveur (signature binaire), pas seulement
              l'extension. L'ancien logo n'est supprimé qu'une fois le nouveau enregistré.
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Button component="label" variant="outlined" startIcon={<CloudUpload />} disabled={logoBusy}>
                Choisir un fichier
                <input hidden type="file" accept="image/png,image/jpeg" onChange={pickLogo} />
              </Button>
              <Button variant="contained" onClick={uploadLogo} disabled={!logoFile || logoBusy}>
                {logoBusy ? 'Enregistrement…' : 'Enregistrer le logo'}
              </Button>
              {/* Toujours actif : sans logo personnalisé, c'est le logo par
                  défaut qui est imprimé sur les documents — et c'est donc lui
                  que l'utilisateur doit pouvoir récupérer. */}
              <Button variant="outlined" startIcon={<Download />}
                onClick={downloadLogo}
                disabled={logoDownloading}
                title={form.logoUrl
                  ? 'Enregistrer sur votre poste le fichier utilisé dans les documents et la carte'
                  : "Aucun logo personnalisé : c'est le logo par défaut qui est imprimé, et c'est lui qui sera téléchargé"}>
                {logoDownloading ? 'Téléchargement…' : 'Télécharger le logo'}
              </Button>
              {form.logoUrl && (
                <Button color="error" variant="outlined" startIcon={<DeleteOutline />}
                  onClick={removeLogo} disabled={logoBusy}>
                  Supprimer le logo
                </Button>
              )}
            </Stack>
            {logoFile && (
              <Chip size="small" color="info" variant="outlined"
                label={`${logoFile.name} — ${Math.round(logoFile.size / 1024)} Ko`} />
            )}
          </Stack>
        </Grid>
      </Section>

      <Section icon={<Notes fontSize="small" />} title="Informations complémentaires"
        subtitle="Mentions utiles sur les documents officiels">
        <Grid item xs={12}>
          <TextField fullWidth multiline minRows={3} label="Informations complémentaires"
            value={form.additionalInfo}
            onChange={(e) => set('additionalInfo', e.target.value)}
            helperText={`${(form.additionalInfo || '').length}/1000`} />
        </Grid>
      </Section>

      <Card sx={{ position: 'sticky', bottom: 0, zIndex: 2 }}>
        <CardActions sx={{ p: 2, justifyContent: 'space-between', flexWrap: 'wrap', gap: 1 }}>
          <Typography variant="caption" color="text.secondary">
            {dirty ? 'Des modifications ne sont pas encore enregistrées.' : 'Toutes les valeurs sont à jour.'}
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button onClick={() => setPreviewOpen(true)} startIcon={<Visibility />}>Prévisualiser</Button>
            <Button variant="outlined" onClick={() => setConfirmReset(true)}
              startIcon={<RestartAlt />} disabled={!dirty}>Annuler les modifications</Button>
            <Button variant="contained" onClick={save} disabled={saving}
              startIcon={saving ? <CircularProgress size={16} color="inherit" /> : <Save />}>
              {saving ? 'Enregistrement…' : 'Enregistrer'}
            </Button>
          </Stack>
        </CardActions>
      </Card>

      {/* Prévisualisation */}
      <Dialog open={previewOpen} onClose={() => setPreviewOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Prévisualisation de la fiche</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={1.5}>
            <Stack direction="row" spacing={2} alignItems="center">
              {form.logoUrl
                ? <FileImage src={form.logoUrl} alt="Logo" sx={{ width: 64, height: 64, objectFit: 'contain' }} />
                : <img src={defaultLogo} alt="Logo par défaut" style={{ width: 56, height: 56 }} />}
              <Box>
                <Typography variant="h6" fontWeight={800}>{form.name || '(nom non renseigné)'}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {[form.shortName, form.type, form.status].filter(Boolean).join(' · ') || '—'}
                </Typography>
              </Box>
            </Stack>
            <Divider />
            <Typography variant="body2">
              {[form.address, form.district, form.city, form.region, form.country]
                .filter(Boolean).join(', ') || 'Adresse non renseignée'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {[form.phone && `Tél. ${form.phone}`, form.phoneAlt, form.email, form.website]
                .filter(Boolean).join(' · ') || 'Aucun contact renseigné'}
            </Typography>
            <Divider />
            <Typography variant="body2">
              <b>{form.directorRole || 'Responsable'} :</b> {form.directorName || '—'}
            </Typography>
            {form.accreditationNumber && (
              <Typography variant="body2"><b>Agrément :</b> {form.accreditationNumber}</Typography>
            )}
            {form.motto && (
              <Typography variant="body2" fontStyle="italic" color="text.secondary">« {form.motto} »</Typography>
            )}
            {form.additionalInfo && (
              <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'pre-line' }}>
                {form.additionalInfo}
              </Typography>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewOpen(false)}>Fermer</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmReset}
        title="Annuler les modifications"
        message="Revenir aux dernières valeurs enregistrées ? Les saisies non enregistrées seront perdues."
        onConfirm={reset}
        onClose={() => setConfirmReset(false)}
      />
    </>
  )
}
