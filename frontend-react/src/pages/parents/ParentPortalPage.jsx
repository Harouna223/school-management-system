import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  Grid, Card, CardContent, CardActionArea, Typography, Avatar, Box,
  Tabs, Tab, Chip, Button, Dialog, DialogTitle, DialogContent,
  DialogActions, Table, TableHead, TableRow, TableCell, TableBody, Divider,
} from '@mui/material'
import {
  PictureAsPdf, Print, MenuBook, FactCheck, Assignment, Timeline as TimelineIcon,
  Description, School, Receipt, Download,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import StatusChip from '../../components/StatusChip'
import Loader from '../../components/Loader'
import EmptyState from '../../components/EmptyState'
import { parentApi, examApi } from '../../api/endpoints'
import { downloadResponse } from '../../services/exportService'
import { useToast } from '../../hooks/useToast'
import { extractError } from '../../api/axios'
import { primaryRole } from '../../utils/auth'
import { initials, formatDate, formatCurrency } from '../../utils/format'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts'

const TERM_LABEL = { T1: '1er Trimestre', T2: '2e Trimestre', T3: '3e Trimestre' }
const CYCLE_LABELS = {
  JARDIN: 'Jardin', PRIMAIRE: 'Primaire', COLLEGE: 'Collège',
  LYCEE: 'Lycée', UNIVERSITE: 'Université',
}

/**
 * Espace parent unifié : enfants (tous cycles), bulletins, présences, notes,
 * parcours (timeline) et centre documentaire (téléchargements autorisés).
 * L'accès à chaque enfant est contrôlé côté backend (ownedStudent).
 */
export default function ParentPortalPage() {
  const role = primaryRole()
  const { success, error: toastError } = useToast()

  const [children, setChildren] = useState([])
  const [selected, setSelected] = useState(null)
  const [tab, setTab] = useState(0)
  const [bulletins, setBulletins] = useState([])
  const [attendances, setAttendances] = useState([])
  const [grades, setGrades] = useState([])
  const [timeline, setTimeline] = useState([])
  const [invoices, setInvoices] = useState([])
  const [univEnrollments, setUnivEnrollments] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    parentApi.children()
      .then((res) => {
        setChildren(res.data.data || [])
        if (res.data.data?.length) setSelected(res.data.data[0])
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!selected) return
    setBulletins([])
    setAttendances([])
    setGrades([])
    setTimeline([])
    setInvoices([])
    setUnivEnrollments([])
    parentApi.childBulletins(selected.id).then((res) => setBulletins(res.data.data || [])).catch(() => {})
    parentApi.childAttendances(selected.id).then((res) => setAttendances(res.data.data || [])).catch(() => {})
    parentApi.childGrades(selected.id).then((res) => setGrades(res.data.data || [])).catch(() => {})
    parentApi.childTimeline(selected.id).then((res) => setTimeline(res.data.data || [])).catch(() => {})
    parentApi.childInvoices(selected.id).then((res) => setInvoices(res.data.data || [])).catch(() => {})
    parentApi.childUniversityEnrollments(selected.id).then((res) => setUnivEnrollments(res.data.data || [])).catch(() => {})
  }, [selected])

  if (role !== 'PARENT') {
    return <Navigate to="/dashboard" replace />
  }

  const downloadBulletin = async (bulletin) => {
    try {
      const res = await examApi.bulletinPdf(bulletin.id)
      await downloadResponse(res, `bulletin-${selected.matricule}-${bulletin.term}.pdf`)
      success('Bulletin téléchargé')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const downloadDoc = async (promise, filename) => {
    try {
      const res = await promise
      await downloadResponse(res, filename)
      success('Document téléchargé')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const downloadUnivDoc = async (type, enr) => {
    const params = { studentId: selected.id, fieldId: enr.field.id, semester: enr.currentSemester, session: 1 }
    const promise = type === 'releve' ? parentApi.childReleve(params) : parentApi.childAttestation(params)
    await downloadDoc(promise, `${type}-${selected.matricule}-${enr.currentSemester}.pdf`)
  }

  const summary = bulletins.map((b) => ({
    ...b,
    absentCount: attendances.filter((a) => a.status === 'ABSENT').length,
  }))

  const progressionData = bulletins
    .slice()
    .sort((a, b) => (a.academicYear || '').localeCompare(b.academicYear || '') || (a.term || '').localeCompare(b.term || ''))
    .map((b) => ({ name: `${TERM_LABEL[b.term] || b.term} ${b.academicYear || ''}`, moyenne: b.average ?? 0 }))

  return (
    <>
      <PageHeader
        title="Mes enfants"
        subtitle="Tous les cycles — bulletins, présences, parcours, documents"
      />

      {loading ? (
        <Loader />
      ) : children.length === 0 ? (
        <EmptyState message="Aucun enfant n'est lié à votre compte. Contactez l'administration." />
      ) : (
        <Grid container spacing={3}>
          {/* Liste des enfants (tous cycles) */}
          <Grid item xs={12} md={4}>
            <Grid container spacing={2}>
              {children.map((child) => (
                <Grid item xs={12} key={child.id}>
                  <Card sx={{ border: selected?.id === child.id ? '2px solid' : '1px solid', borderColor: selected?.id === child.id ? 'primary.main' : 'divider' }}>
                    <CardActionArea onClick={() => setSelected(child)}>
                      <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <Avatar src={child.photo} sx={{ width: 48, height: 48 }}>
                          {initials(child.firstName, child.lastName)}
                        </Avatar>
                        <Box minWidth={0} flex={1}>
                          <Typography variant="subtitle1" fontWeight={600} noWrap>
                            {child.firstName} {child.lastName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {child.matricule}
                          </Typography>
                        </Box>
                        <Box textAlign="right">
                          <Chip size="small" color="primary" variant="outlined"
                            label={CYCLE_LABELS[child.educationCycle] || '—'} />
                          <Typography variant="caption" display="block" color="text.secondary" mt={0.5}>
                            {child.className || (child.educationCycle === 'UNIVERSITE' ? 'Université' : '—')}
                          </Typography>
                        </Box>
                      </CardContent>
                    </CardActionArea>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Grid>

          {/* Détails de l'enfant sélectionné */}
          <Grid item xs={12} md={8}>
            <Card>
              <CardContent>
                <Box display="flex" alignItems="center" gap={2} mb={1} flexWrap="wrap">
                  <Avatar src={selected.photo} sx={{ width: 56, height: 56 }}>
                    {initials(selected.firstName, selected.lastName)}
                  </Avatar>
                  <Box flex={1} minWidth={200}>
                    <Typography variant="h6">{selected.firstName} {selected.lastName}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {selected.className || 'Université'} · Matricule {selected.matricule} · {formatDate(selected.enrollmentDate)}
                    </Typography>
                  </Box>
                  <StatusChip status={selected.status} />
                  {selected.educationCycle && (
                    <Chip size="small" color="primary" label={CYCLE_LABELS[selected.educationCycle]} />
                  )}
                </Box>

                <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
                  <Tab icon={<MenuBook />} iconPosition="start" label="Bulletins" />
                  <Tab icon={<FactCheck />} iconPosition="start" label="Présences" />
                  <Tab icon={<Assignment />} iconPosition="start" label="Notes" />
                  <Tab icon={<TimelineIcon />} iconPosition="start" label="Parcours" />
                  <Tab icon={<Description />} iconPosition="start" label="Documents" />
                  <Tab icon={<Receipt />} iconPosition="start" label="Paiements" />
                </Tabs>

                {/* Bulletins */}
                {tab === 0 && (
                  bulletins.length === 0 ? (
                    <EmptyState message="Aucun bulletin disponible pour le moment." />
                  ) : (
                    <>
                      {progressionData.length > 1 && (
                        <Box mb={2.5}>
                          <Typography variant="subtitle2" fontWeight={700} mb={1}>Évolution de la moyenne</Typography>
                          <ResponsiveContainer width="100%" height={200}>
                            <LineChart data={progressionData} margin={{ top: 8, right: 16, left: -8, bottom: 0 }}>
                              <CartesianGrid strokeDasharray="3 3" stroke="rgba(128,128,128,0.25)" />
                              <XAxis dataKey="name" tick={{ fontSize: 10 }} />
                              <YAxis domain={[0, 20]} tick={{ fontSize: 10 }} />
                              <Tooltip />
                              <Legend />
                              <Line type="monotone" dataKey="moyenne" name="Moyenne /20" stroke="#2563eb" strokeWidth={2.5} dot={{ r: 4 }} />
                            </LineChart>
                          </ResponsiveContainer>
                        </Box>
                      )}
                      <Grid container spacing={2}>
                        {summary.map((b) => (
                          <Grid item xs={12} sm={6} key={b.id}>
                            <Card variant="outlined">
                              <CardContent>
                                <Typography variant="subtitle2" color="primary" fontWeight={700}>
                                  {TERM_LABEL[b.term] || b.term} · {b.academicYear}
                                </Typography>
                                <Box my={1}>
                                  <Typography variant="h4" fontWeight={800}>
                                    {b.average != null ? b.average.toFixed(2) : '—'}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">Moyenne / 20</Typography>
                                </Box>
                                <Box display="flex" gap={1} flexWrap="wrap">
                                  <Chip size="small" label={`Rang : ${b.rank || '—'}`} variant="outlined" />
                                  <Chip size="small" color={b.average >= 10 ? 'success' : 'error'} label={b.mention} variant="outlined" />
                                </Box>
                                <Button size="small" sx={{ mt: 2 }} startIcon={<PictureAsPdf />} onClick={() => downloadBulletin(b)}>
                                  Bulletin PDF
                                </Button>
                              </CardContent>
                            </Card>
                          </Grid>
                        ))}
                      </Grid>
                    </>
                  )
                )}

                {/* Présences */}
                {tab === 1 && (
                  attendances.length === 0 ? (
                    <EmptyState message="Aucune présence enregistrée pour cet enfant." />
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Date</TableCell>
                          <TableCell>Statut</TableCell>
                          <TableCell>Justification</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {attendances.slice(0, 30).map((a) => (
                          <TableRow key={a.id}>
                            <TableCell>{formatDate(a.date)}</TableCell>
                            <TableCell><StatusChip status={a.status} /></TableCell>
                            <TableCell>{a.justification || '—'}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )
                )}

                {/* Notes */}
                {tab === 2 && (
                  grades.length === 0 ? (
                    <EmptyState message="Aucune note saisie pour cet enfant." />
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Matière</TableCell>
                          <TableCell>Évaluation</TableCell>
                          <TableCell>Note</TableCell>
                          <TableCell>Appréciation</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {grades.map((g) => (
                          <TableRow key={g.id}>
                            <TableCell>{g.subjectName}</TableCell>
                            <TableCell>{g.examName}</TableCell>
                            <TableCell fontWeight={600}>{g.value} / {g.maxValue}</TableCell>
                            <TableCell>{g.appreciation || '—'}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )
                )}

                {/* Parcours (timeline) */}
                {tab === 3 && (
                  timeline.length === 0 ? (
                    <EmptyState message="Aucun parcours enregistré pour cet enfant." />
                  ) : (
                    <Box display="flex" flexDirection="column" gap={1.5}>
                      {timeline.map((entry, i) => (
                        <Box key={`${entry.context}-${entry.id}`} display="flex" gap={1.5} alignItems="flex-start">
                          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', minWidth: 28 }}>
                            <Avatar sx={{ width: 26, height: 26, bgcolor: entry.context === 'UNIVERSITY' ? 'secondary.main' : 'primary.main', fontSize: 12 }}>
                              {entry.context === 'UNIVERSITY' ? <School sx={{ fontSize: 14 }} /> : <MenuBook sx={{ fontSize: 14 }} />}
                            </Avatar>
                            {i < timeline.length - 1 && <Box sx={{ width: 2, height: '100%', bgcolor: 'divider' }} />}
                          </Box>
                          <Box mb={1} flex={1}>
                            <Typography variant="body2" fontWeight={600}>
                              {entry.action} {entry.academicYear ? `· ${entry.academicYear}` : ''}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {entry.fromLabel ? `${entry.fromLabel} → ` : ''}{entry.toLabel || ''}
                              {entry.status ? ` · ${entry.status}` : ''} · {entry.date ? formatDate(entry.date) : ''}
                            </Typography>
                            {entry.reason && (
                              <Typography variant="caption" display="block" color="text.secondary">
                                {entry.reason}
                              </Typography>
                            )}
                          </Box>
                        </Box>
                      ))}
                    </Box>
                  )
                )}

                {/* Centre documentaire */}
                {tab === 4 && (
                  <Grid container spacing={2}>
                    <Grid item xs={12}>
                      <Typography variant="subtitle2" color="primary" fontWeight={700} mb={1}>Documents scolaires</Typography>
                    </Grid>
                    {[
                      { label: 'Certificat de scolarité', action: () => downloadDoc(parentApi.childCertificate(selected.id), `certificat-${selected.matricule}.pdf`) },
                      { label: 'Carte scolaire', action: () => downloadDoc(parentApi.childCard(selected.id), `carte-${selected.matricule}.pdf`) },
                      { label: 'Certificat de fréquentation', action: () => downloadDoc(parentApi.childAttendanceCertificate(selected.id), `frequentation-${selected.matricule}.pdf`) },
                      { label: 'QR code carte scolaire', action: () => downloadDoc(parentApi.childQr(selected.id), `qr-${selected.matricule}.png`) },
                    ].map((doc) => (
                      <Grid item xs={12} sm={6} md={4} key={doc.label}>
                        <Card variant="outlined">
                          <CardActionArea onClick={doc.action} sx={{ p: 2 }}>
                            <Box display="flex" alignItems="center" gap={1.5}>
                              <PictureAsPdf color="error" />
                              <Typography variant="body2" fontWeight={600}>{doc.label}</Typography>
                            </Box>
                          </CardActionArea>
                        </Card>
                      </Grid>
                    ))}

                    {bulletins.length > 0 && (
                      <>
                        <Grid item xs={12}>
                          <Typography variant="subtitle2" color="primary" fontWeight={700} mb={1} mt={1}>
                            Bulletins de notes
                          </Typography>
                        </Grid>
                        {bulletins.map((b) => (
                          <Grid item xs={12} sm={6} md={4} key={`b-${b.id}`}>
                            <Card variant="outlined">
                              <CardActionArea onClick={() => downloadBulletin(b)} sx={{ p: 2 }}>
                                <Box display="flex" alignItems="center" gap={1.5}>
                                  <PictureAsPdf color="primary" />
                                  <Box>
                                    <Typography variant="body2" fontWeight={600}>{TERM_LABEL[b.term] || b.term}</Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {b.academicYear} · Moyenne {b.average != null ? b.average.toFixed(2) : '—'}
                                    </Typography>
                                  </Box>
                                </Box>
                              </CardActionArea>
                            </Card>
                          </Grid>
                        ))}
                      </>
                    )}

                    {univEnrollments.length > 0 && (
                      <>
                        <Grid item xs={12}>
                          <Typography variant="subtitle2" color="secondary" fontWeight={700} mb={1} mt={1}>
                            Documents universitaires
                          </Typography>
                        </Grid>
                        {univEnrollments.map((enr) => (
                          <Grid item xs={12} md={6} key={enr.id}>
                            <Card variant="outlined" sx={{ p: 2 }}>
                              <Typography variant="body2" fontWeight={600}>
                                {enr.field?.name} — Semestre {enr.currentSemester}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" display="block" mb={1}>
                                {enr.level ? `${enr.level} · ` : ''}{enr.academicYear || ''}
                              </Typography>
                              <Box display="flex" gap={1} flexWrap="wrap">
                                <Button size="small" variant="outlined" startIcon={<PictureAsPdf />}
                                  onClick={() => downloadUnivDoc('releve', enr)}>
                                  Relevé
                                </Button>
                                <Button size="small" variant="outlined" startIcon={<PictureAsPdf />}
                                  onClick={() => downloadUnivDoc('attestation', enr)}>
                                  Attestation
                                </Button>
                              </Box>
                            </Card>
                          </Grid>
                        ))}
                      </>
                    )}
                  </Grid>
                )}

                {/* Paiements */}
                {tab === 5 && (
                  invoices.length === 0 ? (
                    <EmptyState message="Aucune facture pour cet enfant." />
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Facture</TableCell>
                          <TableCell>Frais</TableCell>
                          <TableCell align="right">Montant</TableCell>
                          <TableCell align="right">Payé</TableCell>
                          <TableCell>Statut</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {invoices.slice(0, 20).map((inv) => (
                          <TableRow key={inv.id}>
                            <TableCell>{inv.invoiceNo}</TableCell>
                            <TableCell>{inv.feeTypeName}</TableCell>
                            <TableCell align="right">{formatCurrency(inv.amount)}</TableCell>
                            <TableCell align="right">{formatCurrency(inv.paidAmount)}</TableCell>
                            <TableCell><Chip size="small" label={inv.status} color={inv.status === 'PAID' ? 'success' : inv.status === 'PARTIAL' ? 'warning' : 'error'} /></TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}
    </>
  )
}