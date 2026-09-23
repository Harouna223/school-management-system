import { useEffect, useState, useRef } from 'react'
import {
  Card, CardContent, Grid, TextField, Typography, Button, Box, Switch, FormControlLabel, Divider,
  Dialog, DialogTitle, DialogContent, DialogActions, Chip, IconButton,
} from '@mui/material'
import { Save, School, Receipt, WhatsApp, CalendarMonth, Add, Edit, Delete, Star, StarBorder, Backup, CloudDownload, CloudUpload, AccountTree } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { settingsApi, academicYearApi, backupApi, communicationApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate } from '../../utils/format'
import defaultLogo from '../../assets/logo.svg'

const KEYS = [
  'SCHOOL_NAME', 'SCHOOL_ADDRESS', 'SCHOOL_PHONE', 'SCHOOL_EMAIL',
  'RECEIPT_FOOTER', 'WHATSAPP_ENABLED', 'WHATSAPP_DEFAULT_NUMBER',
]

const LABELS = {
  SCHOOL_NAME: 'Nom de l\'établissement',
  SCHOOL_ADDRESS: 'Adresse',
  SCHOOL_PHONE: 'Téléphone',
  SCHOOL_EMAIL: 'Email',
  RECEIPT_FOOTER: 'Pied de page des reçus',
  WHATSAPP_ENABLED: 'Activer WhatsApp',
  WHATSAPP_DEFAULT_NUMBER: 'Indicatif par défaut',
}

/**
 * Paramètres de l'établissement (identité, reçus, WhatsApp).
 */
export default function SettingsPage() {
  const { success, error: toastError } = useToast()
  const [values, setValues] = useState({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [years, setYears] = useState([])
  const [yearDialog, setYearDialog] = useState(false)
  const [yearForm, setYearForm] = useState({ label: '', startDate: '', endDate: '' })
  const [editingYear, setEditingYear] = useState(null)
  const logoRef = useRef(null)
  const [logoFileName, setLogoFileName] = useState(null)
  const [messageLogs, setMessageLogs] = useState([])
  const [cycles, setCycles] = useState([])

  const ALL_CYCLES = [
    { value: 'JARDIN', label: 'Jardin' },
    { value: 'PRIMAIRE', label: 'Primaire' },
    { value: 'COLLEGE', label: 'Collège' },
    { value: 'LYCEE', label: 'Lycée' },
    { value: 'UNIVERSITE', label: 'Université' },
  ]

  useEffect(() => {
    Promise.all([settingsApi.all(), settingsApi.defaults()])
      .then(([savedRes, defaultsRes]) => {
        const defaults = defaultsRes.data.data || {}
        const saved = {}
        ;(savedRes.data.data || []).forEach((s) => { saved[s.key] = s.value })
        const merged = {}
        KEYS.forEach((k) => { merged[k] = saved[k] ?? defaults[k] ?? '' })
        merged.SCHOOL_LOGO = saved.SCHOOL_LOGO ?? ''
        setValues(merged)
      })
      .catch((err) => toastError(extractError(err)))
      .finally(() => setLoading(false))
    loadYears()
    loadMessageLogs()
    loadCycles()
  }, [])

  const loadCycles = async () => {
    try {
      const { data } = await settingsApi.cycles()
      setCycles(data.data || [])
    } catch { setCycles(ALL_CYCLES.map((c) => c.value)) }
  }

  const loadMessageLogs = async () => {
    try {
      const res = await communicationApi.messageLogs({ page: 0, size: 50 })
      setMessageLogs(res.data.data?.content || [])
    } catch { /* historique optionnel */ }
  }

  const set = (key, value) => setValues((v) => ({ ...v, [key]: value }))

  const saveCycles = async () => {
    try {
      await settingsApi.updateCycles(cycles)
      success('Cycles d\'enseignement mis à jour')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const save = async () => {
    setSaving(true)
    try {
      await settingsApi.update(values)
      success('Paramètres enregistrés')
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  const uploadLogo = async () => {
    const file = logoRef.current
    if (!file) return
    setSaving(true)
    try {
      const res = await settingsApi.uploadLogo(file)
      setValues((v) => ({ ...v, SCHOOL_LOGO: res.data.data }))
      logoRef.current = null
      setLogoFileName(null)
      success('Logo mis à jour')
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  const loadYears = async () => {
    try {
      const { data } = await academicYearApi.all()
      setYears(data.data || [])
    } catch { /* ignore */ }
  }

  const openYearDialog = (year) => {
    setEditingYear(year)
    setYearForm(year ? { label: year.label, startDate: year.startDate || '', endDate: year.endDate || '' } : { label: '', startDate: '', endDate: '' })
    setYearDialog(true)
  }

  const saveYear = async () => {
    try {
      if (editingYear) {
        await academicYearApi.update(editingYear.id, yearForm)
        success('Année scolaire modifiée')
      } else {
        await academicYearApi.create(yearForm)
        success('Année scolaire créée')
      }
      setYearDialog(false)
      loadYears()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const setCurrentYear = async (id) => {
    try {
      await academicYearApi.setCurrent(id)
      success('Année scolaire active mise à jour')
      loadYears()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const deleteYear = async (id) => {
    try {
      await academicYearApi.remove(id)
      success('Année scolaire supprimée')
      loadYears()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const backupRef = useRef(null)
  const [backupFileName, setBackupFileName] = useState(null)
  const [restoreOpen, setRestoreOpen] = useState(false)

  const exportBackup = async () => {
    try {
      const res = await backupApi.export()
      const blob = new Blob([res.data], { type: 'application/sql' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `sms-backup-${new Date().toISOString().slice(0, 10)}.sql`
      a.click()
      URL.revokeObjectURL(url)
      success('Sauvegarde téléchargée')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const restoreBackup = async () => {
    const file = backupRef.current
    if (!file) return
    try {
      const res = await backupApi.restore(file)
      success(res.data.message)
      setRestoreOpen(false)
      backupRef.current = null
      setBackupFileName(null)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const section = (icon, title, children) => (
    <Card sx={{ mb: 3, borderRadius: '16px', border: '1px solid', borderColor: 'divider' }}>
      <CardContent>
        <Box display="flex" alignItems="center" gap={1.5} mb={2.5}>
          {icon}
          <Typography variant="subtitle1" fontWeight={700}>{title}</Typography>
        </Box>
        <Grid container spacing={2.5}>{children}</Grid>
      </CardContent>
    </Card>
  )

  return (
    <>
      <PageHeader
        title="Paramètres"
        subtitle="Configuration de l'établissement"
        actions={[
          { label: 'Enregistrer', icon: <Save sx={{ fontSize: 18 }} />, onClick: save, disabled: saving || loading },
        ]}
      />

      {loading ? (
        <Typography variant="body2" color="text.secondary">Chargement…</Typography>
      ) : (
        <>
          {section(<School color="primary" />, 'Établissement', (
            <>
              <Grid item xs={12} md={6}>
                <TextField fullWidth label={LABELS.SCHOOL_NAME} value={values.SCHOOL_NAME ?? ''}
                  onChange={(e) => set('SCHOOL_NAME', e.target.value)} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField fullWidth label={LABELS.SCHOOL_ADDRESS} value={values.SCHOOL_ADDRESS ?? ''}
                  onChange={(e) => set('SCHOOL_ADDRESS', e.target.value)} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField fullWidth label={LABELS.SCHOOL_PHONE} value={values.SCHOOL_PHONE ?? ''}
                  onChange={(e) => set('SCHOOL_PHONE', e.target.value)} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField fullWidth label={LABELS.SCHOOL_EMAIL} value={values.SCHOOL_EMAIL ?? ''}
                  onChange={(e) => set('SCHOOL_EMAIL', e.target.value)} />
              </Grid>
            </>
          ))}

          {section(<School color="primary" />, 'Logo de l\'établissement', (
            <Grid item xs={12}>
              <Box display="flex" alignItems="center" gap={2} flexWrap="wrap">
                <Box
                  component="img"
                  src={values.SCHOOL_LOGO || defaultLogo}
                  alt="Logo"
                  sx={{ width: 72, height: 72, objectFit: 'contain', border: '1px dashed', borderColor: 'divider', borderRadius: 2, p: 0.5, bgcolor: '#fff' }}
                />
                <Box>
                  <Button variant="outlined" component="label" startIcon={<CloudUpload />}>
                    Choisir un logo
                    <input type="file" hidden accept="image/*" onChange={(e) => { const f = e.target.files?.[0] || null; logoRef.current = f; setLogoFileName(f ? f.name : null) }} />
                  </Button>
                  <Button variant="contained" onClick={uploadLogo} disabled={!logoFileName || saving} sx={{ ml: 1 }}>
                    Envoyer
                  </Button>
                  {logoFileName && (
                    <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
                      {logoFileName}
                    </Typography>
                  )}
                </Box>
              </Box>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
                Le logo apparaît en entête des documents officiels (carte scolaire, certificat de fréquentation).
              </Typography>
            </Grid>
          ))}

          {section(<Receipt color="primary" />, 'Reçus de paiement', (
            <Grid item xs={12}>
              <TextField fullWidth multiline minRows={2} label={LABELS.RECEIPT_FOOTER}
                value={values.RECEIPT_FOOTER ?? ''}
                onChange={(e) => set('RECEIPT_FOOTER', e.target.value)} />
            </Grid>
          ))}

          {section(<AccountTree color="primary" />, 'Cycles d\'enseignement', (
            <Grid item xs={12}>
              <Typography variant="body2" color="text.secondary" mb={2}>
                Activez ou désactivez les cycles gérés par l'établissement. Le menu et les fonctionnalités
                s'adaptent automatiquement (scolaire vs université).
              </Typography>
              <Grid container spacing={1.5}>
                {ALL_CYCLES.map((c) => (
                  <Grid item xs={12} sm={6} md={4} key={c.value}>
                    <Box sx={{ p: 1.5, borderRadius: '12px', border: '1px solid', borderColor: 'divider' }}>
                      <FormControlLabel
                        control={<Switch checked={cycles.includes(c.value)} onChange={() => setCycles((prev) => prev.includes(c.value) ? prev.filter((x) => x !== c.value) : [...prev, c.value])} />}
                        label={<Typography fontWeight={600}>{c.label}</Typography>}
                      />
                    </Box>
                  </Grid>
                ))}
              </Grid>
              <Box mt={2}>
                <Button variant="contained" startIcon={<Save />} onClick={saveCycles} disabled={cycles.length === 0}>
                  Enregistrer les cycles
                </Button>
              </Box>
            </Grid>
          ))}

          {section(<WhatsApp color="success" />, 'Historique des notifications', (
            <Grid item xs={12}>
              {messageLogs.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Aucune notification envoyée pour le moment. Les absences et retards signalés déclenchent
                  automatiquement un message WhatsApp, SMS et e-mail au parent concerné.
                </Typography>
              ) : (
                <Box sx={{ maxHeight: 360, overflowY: 'auto', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5 }}>
                    <thead>
                      <tr style={{ textAlign: 'left', background: 'rgba(241,245,249,0.7)' }}>
                        <th style={{ padding: '8px 10px' }}>Date</th>
                        <th style={{ padding: '8px 10px' }}>Canal</th>
                        <th style={{ padding: '8px 10px' }}>Destinataire</th>
                        <th style={{ padding: '8px 10px' }}>Téléphone</th>
                        <th style={{ padding: '8px 10px' }}>Statut</th>
                        <th style={{ padding: '8px 10px' }}>Message</th>
                      </tr>
                    </thead>
                    <tbody>
                      {messageLogs.map((log) => (
                        <tr key={log.id} style={{ borderTop: '1px solid rgba(226,232,240,0.8)' }}>
                          <td style={{ padding: '8px 10px', whiteSpace: 'nowrap' }}>{log.createdAt ? new Date(log.createdAt).toLocaleString() : '—'}</td>
                          <td style={{ padding: '8px 10px' }}>
                            <Chip size="small" label={log.channel}
                              color={log.channel === 'WHATSAPP' ? 'success' : log.channel === 'EMAIL' ? 'primary' : 'default'} />
                          </td>
                          <td style={{ padding: '8px 10px' }}>{log.recipientName}</td>
                          <td style={{ padding: '8px 10px' }}>{log.phone || '—'}</td>
                          <td style={{ padding: '8px 10px' }}>
                            <Chip size="small" label={log.status}
                              color={log.status === 'SENT' ? 'success' : log.status === 'FAILED' ? 'error' : 'warning'} />
                          </td>
                          <td style={{ padding: '8px 10px', maxWidth: 320 }}>
                            <Typography variant="caption" component="div" noWrap title={log.message}>
                              {log.message}
                            </Typography>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </Box>
              )}
            </Grid>
          ))}
          {section(<WhatsApp color="success" />, 'WhatsApp', (
            <>
              <Grid item xs={12}>
                <FormControlLabel
                  control={<Switch checked={values.WHATSAPP_ENABLED === 'true'} color="success"
                    onChange={(e) => set('WHATSAPP_ENABLED', String(e.target.checked))} />}
                  label={LABELS.WHATSAPP_ENABLED}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField fullWidth label={LABELS.WHATSAPP_DEFAULT_NUMBER} value={values.WHATSAPP_DEFAULT_NUMBER ?? ''}
                  onChange={(e) => set('WHATSAPP_DEFAULT_NUMBER', e.target.value)} />
              </Grid>
            </>
          ))}

          {section(<CalendarMonth color="primary" />, 'Années scolaires', (
            <>
              <Grid item xs={12}>
                <Box display="flex" justifyContent="flex-end" mb={2}>
                  <Button size="small" variant="outlined" startIcon={<Add />} onClick={() => openYearDialog(null)}>
                    Ajouter une année
                  </Button>
                </Box>
                {years.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">Aucune année scolaire définie.</Typography>
                ) : (
                  <Box display="flex" flexDirection="column" gap={1}>
                    {years.map((y) => (
                      <Box key={y.id} display="flex" alignItems="center" gap={1.5}
                        sx={{ p: 1.5, borderRadius: '12px', border: '1px solid', borderColor: 'divider' }}>
                        <Chip size="small" color={y.current ? 'primary' : 'default'}
                          icon={y.current ? <Star /> : <StarBorder />}
                          label={y.current ? 'Active' : 'Inactive'} />
                        <Box flex={1}>
                          <Typography variant="body2" fontWeight={600}>{y.label}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {y.startDate ? formatDate(y.startDate) : '—'} → {y.endDate ? formatDate(y.endDate) : '—'}
                          </Typography>
                        </Box>
                        {!y.current && (
                          <Button size="small" onClick={() => setCurrentYear(y.id)}>Activer</Button>
                        )}
                        <IconButton size="small" onClick={() => openYearDialog(y)}><Edit fontSize="small" /></IconButton>
                        {!y.current && (
                          <IconButton size="small" color="error" onClick={() => deleteYear(y.id)}><Delete fontSize="small" /></IconButton>
                        )}
                      </Box>
                    ))}
                  </Box>
                )}
              </Grid>
            </>
          ))}

          {section(<Backup color="error" />, 'Sauvegarde & Restauration', (
            <Grid item xs={12}>
              <Box display="flex" flexDirection="column" gap={1.5}>
                <Typography variant="body2" color="text.secondary">
                  La sauvegarde télécharge un dump SQL complet. La restauration écrase la base actuelle :
                  une sauvegarde préalable est fortement recommandée avant toute restauration.
                </Typography>
                <Box display="flex" gap={1.5} flexWrap="wrap">
                  <Button variant="contained" color="error" startIcon={<CloudDownload />} onClick={exportBackup}>
                    Télécharger la sauvegarde
                  </Button>
                  <Button variant="outlined" color="error" startIcon={<CloudUpload />} onClick={() => setRestoreOpen(true)}>
                    Restaurer une sauvegarde
                  </Button>
                </Box>
              </Box>
            </Grid>
          ))}

          <Divider sx={{ my: 1 }} />
          <Box display="flex" justifyContent="flex-end" gap={1}>
            <Button variant="contained" startIcon={<Save />} onClick={save} disabled={saving}>
              {saving ? 'Enregistrement…' : 'Enregistrer'}
            </Button>
          </Box>
        </>
      )}

      <Dialog open={yearDialog} onClose={() => setYearDialog(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{editingYear ? 'Modifier l\'année scolaire' : 'Nouvelle année scolaire'}</DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={2} mt={1}>
            <TextField fullWidth label="Libellé (ex. 2026-2027)" value={yearForm.label}
              onChange={(e) => setYearForm((f) => ({ ...f, label: e.target.value }))} />
            <TextField fullWidth type="date" label="Début" value={yearForm.startDate}
              InputLabelProps={{ shrink: true }}
              onChange={(e) => setYearForm((f) => ({ ...f, startDate: e.target.value }))} />
            <TextField fullWidth type="date" label="Fin" value={yearForm.endDate}
              InputLabelProps={{ shrink: true }}
              onChange={(e) => setYearForm((f) => ({ ...f, endDate: e.target.value }))} />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setYearDialog(false)}>Annuler</Button>
          <Button variant="contained" onClick={saveYear} disabled={!yearForm.label}>Enregistrer</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={restoreOpen} onClose={() => setRestoreOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Restaurer une sauvegarde</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="error" sx={{ mb: 2 }}>
            Attention : la restauration remplace les données actuelles de la base. Action irréversible.
          </Typography>
          <Button variant="outlined" component="label" startIcon={<CloudUpload />}>
            Choisir un fichier .sql
            <input type="file" hidden accept=".sql" onChange={(e) => { const f = e.target.files?.[0] || null; backupRef.current = f; setBackupFileName(f ? f.name : null) }} />
          </Button>
          {backupFileName && <Typography variant="caption" display="block" sx={{ mt: 1 }}>{backupFileName}</Typography>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRestoreOpen(false)}>Annuler</Button>
          <Button variant="contained" color="error" disabled={!backupFileName} onClick={restoreBackup}>
            Restaurer
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}