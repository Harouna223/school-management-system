import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Grid,
  Alert,
  Box,
  Typography,
  Card,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Button,
  Avatar,
  Skeleton
} from '@mui/material'
import {
  ArrowBack,
  PictureAsPdf,
  Phone,
  Email,
  Cake
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { studentApi, attendanceApi, paymentApi, examApi } from '../../api/endpoints'
import { formatDate, formatCurrency } from '../../utils/format'
import { downloadResponse } from '../../services/exportService'

const CYCLE_LABELS = {
  JARDIN: 'Jardin',
  PRIMAIRE: 'Primaire',
  COLLEGE: 'Collège',
  LYCEE: 'Lycée',
  UNIVERSITE: 'Université',
}

const TABS = ['Informations', 'Notes', 'Absences', 'Paiements', 'Documents']

/**
 * Fiche élève 360° : informations, notes, absences, paiements, documents.
 */
export default function StudentDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [tab, setTab] = useState(0)
  const [student, setStudent] = useState(null)
  const [loading, setLoading] = useState(true)
  const [bulletins, setBulletins] = useState([])
  const [attendances, setAttendances] = useState([])
  const [invoices, setInvoices] = useState([])
  const [payments, setPayments] = useState([])
  const [loadError, setLoadError] = useState(false)
  const [notFound, setNotFound] = useState(false)
  const [dataLoadWarning, setDataLoadWarning] = useState(false)
  const [retryCount, setRetryCount] = useState(0)
  const [pdfError, setPdfError] = useState(false)
  const [downloadingBulletin, setDownloadingBulletin] = useState(null)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setLoadError(false)
    setNotFound(false)
    setDataLoadWarning(false)
    let cancelled = false
    Promise.allSettled([
      studentApi.get(id),
      examApi.bulletinsByStudent(id),
      attendanceApi.byStudent(id),
      paymentApi.invoicesByStudent(id),
      paymentApi.byStudent(id),
    ]).then((results) => {
      if (cancelled) return
      const [studentResult, bulletinResult, attendanceResult, invoiceResult, paymentResult] = results
      if (studentResult.status === 'rejected') {
        const status = studentResult.reason?.response?.status
        setNotFound(status === 404)
        setLoadError(status !== 404)
        return
      }
      setStudent(studentResult.value.data.data)
      if (bulletinResult.status === 'fulfilled') setBulletins(bulletinResult.value.data.data || [])
      else setBulletins([])
      if (attendanceResult.status === 'fulfilled') setAttendances(attendanceResult.value.data.data || [])
      else setAttendances([])
      if (invoiceResult.status === 'fulfilled') setInvoices(invoiceResult.value.data.data || [])
      else setInvoices([])
      if (paymentResult.status === 'fulfilled') setPayments(paymentResult.value.data.data || [])
      else setPayments([])
      setDataLoadWarning(results.slice(1).some((result) => result.status === 'rejected'))
    }).finally(() => {
      if (!cancelled) setLoading(false)
    })
    return () => { cancelled = true }
  }, [id, retryCount])

  const handleBulletinPdf = async (bulletinId) => {
    setPdfError(false)
    setDownloadingBulletin(bulletinId)
    try {
      const response = await examApi.bulletinPdf(bulletinId)
      await downloadResponse(response, 'bulletin-' + bulletinId + '.pdf')
    } catch {
      setPdfError(true)
    } finally {
      setDownloadingBulletin(null)
    }
  }

  if (loading) return <Box p={4}><Skeleton height={300} /><Skeleton height={200} sx={{ mt: 2 }} /></Box>
  if (loadError) {
    return <Box p={4}>
      <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => setRetryCount((count) => count + 1)}>Réessayer</Button>}>
        Le dossier de l'élève n'a pas pu être chargé. Vérifiez la connexion puis réessayez.
      </Alert>
    </Box>
  }
  if (notFound || !student) return <Box p={4}><Typography>Élève introuvable</Typography></Box>

  const s = student
  const fullName = `${s.firstName} ${s.lastName}`

  return (
    <>
      {dataLoadWarning && <Alert severity="warning" sx={{ mb: 2 }}>Certaines informations complémentaires n'ont pas pu être chargées.</Alert>}
      <PageHeader title={fullName} subtitle={`${s.matricule}  •  ${s.className || 'Sans classe'}  •  ${CYCLE_LABELS[s.educationCycle] || 'Cycle non défini'}`}
        actionLabel="Retour"
        onAction={() => navigate('/students')}
        actionIcon={<ArrowBack />}
      />

      <Card sx={{ borderRadius: '16px', overflow: 'hidden', mb: 3 }}>
        <Box sx={{ bgcolor: 'primary.main', color: '#fff', p: 3, display: 'flex', alignItems: 'center', gap: 2.5 }}>
          <Avatar src={s.photo} sx={{ width: 72, height: 72, border: '3px solid rgba(255,255,255,0.3)' }}>
            {s.firstName?.[0]}{s.lastName?.[0]}
          </Avatar>
          <Box>
            <Typography variant="h5" fontWeight={800}>{fullName}</Typography>
            <Typography variant="body2" sx={{ opacity: 0.85 }}>{s.matricule} — {s.className || 'Sans classe'}</Typography>
            <Box display="flex" gap={2} mt={0.5} flexWrap="wrap">
              {s.educationCycle && <Chip label={CYCLE_LABELS[s.educationCycle] || s.educationCycle} size="small" sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.3)' }} variant="outlined" />}
              {s.birthDate && <Chip icon={<Cake />} label={formatDate(s.birthDate)} size="small" sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.3)' }} variant="outlined" />}
              {s.gender && <Chip label={s.gender === 'MALE' ? 'Masculin' : 'Féminin'} size="small" sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.3)' }} variant="outlined" />}
              {s.status && <Chip label={s.status} size="small" color={s.status === 'ACTIVE' ? 'success' : 'warning'} />}
            </Box>
          </Box>
        </Box>
      </Card>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, '& .MuiTab-root': { fontWeight: 700, textTransform: 'none' } }}>
        {TABS.map((t) => <Tab key={t} label={t} />)}
      </Tabs>

      {tab === 0 && (
        <Grid container spacing={2.5}>
          <Grid item xs={12} md={6}>
            <Card sx={{ p: 2.5, borderRadius: '14px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Identité</Typography>
              <InfoRow label="Matricule" value={s.matricule} />
              <InfoRow label="Cycle" value={CYCLE_LABELS[s.educationCycle] || s.educationCycle || '—'} />
              <InfoRow label="Date de naissance" value={s.birthDate ? formatDate(s.birthDate) : '—'} />
              <InfoRow label="Lieu de naissance" value={s.birthPlace || '—'} />
              <InfoRow label="Sexe" value={s.gender === 'MALE' ? 'Masculin' : 'Féminin'} />
              <InfoRow label="Classe" value={s.className || '—'} />
              <InfoRow label="Adresse" value={s.address || '—'} />
              <InfoRow label="Statut" value={s.status || 'ACTIVE'} />
            </Card>
          </Grid>
          <Grid item xs={12} md={6}>
            <Card sx={{ p: 2.5, borderRadius: '14px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Parent / Tuteur</Typography>
              {s.parent ? (
                <>
                  <InfoRow label="Nom" value={`${s.parent.firstName} ${s.parent.lastName}`} />
                  {s.parent.phone && <InfoRow label="Téléphone" value={s.parent.phone} icon={<Phone sx={{ fontSize: 14, color: 'text.secondary' }} />} />}
                  {s.parent.email && <InfoRow label="Email" value={s.parent.email} icon={<Email sx={{ fontSize: 14, color: 'text.secondary' }} />} />}
                  {s.parent.profession && <InfoRow label="Profession" value={s.parent.profession} />}
                  {s.parent.address && <InfoRow label="Adresse" value={s.parent.address} />}
                </>
              ) : <Typography variant="body2" color="text.secondary">Aucun parent renseigné</Typography>}
            </Card>
          </Grid>
          <Grid item xs={12}>
            <Card sx={{ p: 2.5, borderRadius: '14px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Historique académique</Typography>
              {bulletins.length === 0 ? (
                <Typography variant="body2" color="text.secondary">Aucun bulletin disponible</Typography>
              ) : (
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Année</TableCell>
                        <TableCell>Trimestre</TableCell>
                        <TableCell align="center">Moyenne</TableCell>
                        <TableCell>Mention</TableCell>
                        <TableCell>Décision</TableCell>
                        <TableCell align="center">Rang</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {bulletins.map((b) => (
                        <TableRow key={b.id} hover>
                          <TableCell>{b.academicYear}</TableCell>
                          <TableCell>{b.term}</TableCell>
                          <TableCell align="center"><b>{b.average ?? '—'}</b></TableCell>
                          <TableCell>{b.mention || '—'}</TableCell>
                          <TableCell><Chip size="small" label={b.decision || '—'} color={b.decision === 'ADMIS' ? 'success' : 'warning'} /></TableCell>
                          <TableCell align="center">{b.rank ?? '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Card>
          </Grid>
        </Grid>
      )}

      {tab === 1 && (
        <Card sx={{ p: 2.5, borderRadius: '14px' }}>
          <Typography variant="subtitle1" fontWeight={700} mb={2}>Bulletins de notes</Typography>
          {pdfError && <Alert severity="error" sx={{ mb: 2 }}>Le téléchargement du bulletin a échoué. Réessayez.</Alert>}
          {bulletins.length === 0 ? (
            <Typography variant="body2" color="text.secondary">Aucun bulletin</Typography>
          ) : (
            <Box display="flex" flexDirection="column" gap={1.5}>
              {bulletins.map((b) => (
                <Box key={b.id} display="flex" alignItems="center" justifyContent="space-between" sx={{ p: 1.5, borderRadius: '10px', border: '1px solid', borderColor: 'divider' }}>
                  <Box>
                    <Typography variant="body2" fontWeight={700}>{b.term} — {b.academicYear}</Typography>
                    <Typography variant="caption" color="text.secondary">Moyenne : {b.average ?? '—'} / Rang : {b.rank ?? '—'} / Mention : {b.mention || '—'}</Typography>
                  </Box>
                  <Button size="small" startIcon={<PictureAsPdf />} onClick={() => handleBulletinPdf(b.id)} disabled={downloadingBulletin === b.id}>
                    {downloadingBulletin === b.id ? 'Téléchargement…' : 'PDF'}
                  </Button>
                </Box>
              ))}
            </Box>
          )}
        </Card>
      )}

      {tab === 2 && (
        <Card sx={{ p: 2.5, borderRadius: '14px' }}>
          <Typography variant="subtitle1" fontWeight={700} mb={2}>Présences ({attendances.length})</Typography>
          {attendances.length === 0 ? (
            <Typography variant="body2" color="text.secondary">Aucune présence enregistrée</Typography>
          ) : (
            <TableContainer>
              <Table size="small">
                <TableHead><TableRow><TableCell>Date</TableCell><TableCell>Statut</TableCell><TableCell>Justification</TableCell></TableRow></TableHead>
                <TableBody>
                  {attendances.map((a) => (
                    <TableRow key={a.id} hover>
                      <TableCell>{a.date}</TableCell>
                      <TableCell>
                        <Chip size="small" label={a.status} color={a.status === 'PRESENT' ? 'success' : a.status === 'ABSENT' ? 'error' : 'warning'} />
                      </TableCell>
                      <TableCell>{a.justification || '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      )}

      {tab === 3 && (
        <Grid container spacing={2.5}>
          <Grid item xs={12} md={6}>
            <Card sx={{ p: 2.5, borderRadius: '14px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Factures</Typography>
              {invoices.length === 0 ? (
                <Typography variant="body2" color="text.secondary">Aucune facture</Typography>
              ) : (
                <>
                  {invoices.map((i) => (
                    <Box key={i.id} display="flex" justifyContent="space-between" alignItems="center" sx={{ py: 1, borderBottom: '1px solid', borderColor: 'divider' }}>
                      <Box>
                        <Typography variant="body2" fontWeight={600}>{i.invoiceNo}</Typography>
                        <Typography variant="caption" color="text.secondary">{i.feeTypeName} — {i.dueDate}</Typography>
                      </Box>
                      <Box textAlign="right">
                        <Typography variant="body2" fontWeight={600}>{formatCurrency(i.amount)}</Typography>
                        <Chip size="small" label={i.status} color={i.status === 'PAID' ? 'success' : i.status === 'PARTIAL' ? 'warning' : 'error'} />
                      </Box>
                    </Box>
                  ))}
                </>
              )}
            </Card>
          </Grid>
          <Grid item xs={12} md={6}>
            <Card sx={{ p: 2.5, borderRadius: '14px' }}>
              <Typography variant="subtitle1" fontWeight={700} mb={2}>Paiements</Typography>
              {payments.length === 0 ? (
                <Typography variant="body2" color="text.secondary">Aucun paiement</Typography>
              ) : (
                <>
                  {payments.map((p) => (
                    <Box key={p.id} display="flex" justifyContent="space-between" alignItems="center" sx={{ py: 1, borderBottom: '1px solid', borderColor: 'divider' }}>
                      <Box>
                        <Typography variant="body2" fontWeight={600}>{p.receiptNo}</Typography>
                        <Typography variant="caption" color="text.secondary">{p.paymentDate} — {p.method}</Typography>
                      </Box>
                      <Typography variant="body2" fontWeight={600} color="success.main">{formatCurrency(p.amount)}</Typography>
                    </Box>
                  ))}
                </>
              )}
            </Card>
          </Grid>
        </Grid>
      )}

      {tab === 4 && (
        <Card sx={{ p: 2.5, borderRadius: '14px' }}>
          <Typography variant="subtitle1" fontWeight={700} mb={2}>Documents</Typography>
          <Box display="flex" flexDirection="column" gap={1.5}>
            <Button variant="outlined" startIcon={<PictureAsPdf />} onClick={() => window.open(`/api/students/${id}/card`, '_blank')}>
              Carte scolaire
            </Button>
            <Button variant="outlined" startIcon={<PictureAsPdf />} onClick={() => window.open(`/api/students/${id}/certificate`, '_blank')}>
              Certificat de scolarité
            </Button>
            <Button variant="outlined" startIcon={<PictureAsPdf />} onClick={() => window.open(`/api/students/${id}/attendance-certificate`, '_blank')}>
              Certificat de fréquentation
            </Button>
          </Box>
        </Card>
      )}
    </>
  )
}

function InfoRow({ label, value, icon }) {
  return (
    <Box display="flex" justifyContent="space-between" alignItems="center" sx={{ py: 0.8, borderBottom: '1px solid', borderColor: 'divider' }}>
      <Typography variant="body2" color="text.secondary" display="flex" alignItems="center" gap={0.5}>
        {icon} {label}
      </Typography>
      <Typography variant="body2" fontWeight={600}>{value}</Typography>
    </Box>
  )
}