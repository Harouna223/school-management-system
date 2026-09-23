import { useState, useEffect, useMemo } from 'react'
import {
  Grid, Card, CardContent, Typography, Box, Chip, Button, Avatar, Skeleton, Divider,
  Tabs, Tab, Table, TableHead, TableRow, TableCell, TableBody, LinearProgress,
} from '@mui/material'
import {
  School, PictureAsPdf, TableView, MenuBook, Star, WorkspacePremium, History, Description,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { myApi, lmdApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { downloadBlob } from '../../utils/format'
import EmptyState from '../../components/EmptyState'

const SESSION_LABEL = { 1: 'Session 1 (normale)', 2: 'Session 2 (rattrapage)' }

export default function UniversityPortalPage() {
  const { success, error: toastError } = useToast()
  const [profile, setProfile] = useState(null)
  const [enrollments, setEnrollments] = useState([])
  const [history, setHistory] = useState([])
  const [ueEnrollments, setUeEnrollments] = useState([])
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState(0)
  const [selectedEnr, setSelectedEnr] = useState(null)
  const [releve, setReleve] = useState(null)
  const [releveSession, setReleveSession] = useState(1)
  const [loadingReleve, setLoadingReleve] = useState(false)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      myApi.profile().then((r) => setProfile(r.data.data)).catch(() => {}),
      myApi.university().then((r) => setEnrollments(r.data.data || [])).catch(() => {}),
      myApi.universityHistory().then((r) => setHistory(r.data.data || [])).catch(() => {}),
    ]).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (enrollments.length > 0) {
      setSelectedEnr(enrollments[0])
      loadUeEnrollments(enrollments[0])
    }
  }, [enrollments])

  useEffect(() => {
    if (!selectedEnr) return
    loadReleve(selectedEnr, releveSession)
    loadUeEnrollments(selectedEnr)
  }, [selectedEnr, releveSession])

  const loadReleve = async (enr, session = 1) => {
    if (!enr?.field?.id) return
    setLoadingReleve(true)
    try {
      const { data } = await myApi.universityReleve({
        fieldId: enr.field.id, semester: enr.currentSemester, session,
      })
      setReleve(data.data)
    } catch { setReleve(null) }
    finally { setLoadingReleve(false) }
  }

  const loadUeEnrollments = async (enr) => {
    if (!enr?.student?.id) return
    try {
      const { data } = await lmdApi.ueEnrollments(enr.student.id)
      setUeEnrollments(data.data || [])
    } catch { setUeEnrollments([]) }
  }

  const creditsObtained = releve?.creditsObtained ?? 0
  const creditsFailed = releve?.creditsFailed ?? 0
  const totalCreditsSemester = releve?.totalCredits ?? 0
  const debts = useMemo(() => (releve?.uesToRetake || []), [releve])
  const totalCreditsAll = useMemo(() => enrollments.reduce((acc, e) => acc + (e.program?.totalCredits ?? 0), 0), [enrollments])
  const creditsCumulated = useMemo(() => enrollments.reduce((acc, e) => acc + (e.program?.totalCredits ?? 0), 0), [enrollments])

  const downloadDoc = async (type, enr, session = 1) => {
    try {
      const params = { studentId: enr.student.id, fieldId: enr.field.id, semester: enr.currentSemester, session }
      const resp = type === 'releve' ? await lmdApi.relevePdf(params)
        : type === 'attestation' ? await lmdApi.attestationPdf(params)
        : await lmdApi.releveExcel(params)
      const ext = type === 'releve-excel' ? 'xlsx' : 'pdf'
      downloadBlob(resp.data, `${type}-${enr.student.matricule}-${enr.currentSemester}.${ext}`)
      success(`${type === 'releve' ? 'Relevé' : type === 'attestation' ? 'Attestation' : 'Relevé Excel'} téléchargé`)
    } catch (err) { toastError(extractError(err)) }
  }

  if (loading) return <Box p={4}><Skeleton height={200} /><Skeleton height={300} sx={{ mt: 2 }} /></Box>

  return (
    <>
      <PageHeader title="Mon espace universitaire" subtitle="Inscriptions LMD, résultats, crédits et documents" />

      <Card sx={{ p: 3, borderRadius: '16px', mb: 3, bgcolor: 'primary.main', color: '#fff' }}>
        <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={2}>
          <Box display="flex" alignItems="center" gap={2.5}>
            <Avatar src={profile?.photo} sx={{ width: 64, height: 64, border: '3px solid rgba(255,255,255,0.3)' }}>
              {profile?.firstName?.[0]}{profile?.lastName?.[0]}
            </Avatar>
            <Box>
              <Typography variant="h5" fontWeight={800}>{profile?.firstName} {profile?.lastName}</Typography>
              <Typography variant="body2" sx={{ opacity: 0.85 }}>{profile?.matricule}</Typography>
            </Box>
          </Box>
          <Box display="flex" gap={3} textAlign="center">
            <Box>
              <Typography variant="h5" fontWeight={800}>{enrollments.length}</Typography>
              <Typography variant="caption" sx={{ opacity: 0.85 }}>Inscriptions</Typography>
            </Box>
            <Box>
              <Typography variant="h5" fontWeight={800}>{creditsObtained}</Typography>
              <Typography variant="caption" sx={{ opacity: 0.85 }}>Crédits acquis</Typography>
            </Box>
            <Box>
              <Typography variant="h5" fontWeight={800}>{releve?.average ?? '—'}</Typography>
              <Typography variant="caption" sx={{ opacity: 0.85 }}>Moyenne</Typography>
            </Box>
          </Box>
        </Box>
      </Card>

      {enrollments.length === 0 ? (
        <Card sx={{ p: 4, borderRadius: '16px', textAlign: 'center' }}>
          <MenuBook sx={{ fontSize: 48, color: 'text.disabled' }} />
          <Typography variant="body1" color="text.secondary" mt={1}>Aucune inscription universitaire pour votre compte. Contactez la scolarité.</Typography>
        </Card>
      ) : (
        <Card sx={{ borderRadius: '16px' }}>
          <CardContent>
            <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }} variant="scrollable">
              <Tab icon={<School />} iconPosition="start" label="Inscriptions" />
              <Tab icon={<WorkspacePremium />} iconPosition="start" label="Résultats" />
              <Tab icon={<Star />} iconPosition="start" label="Crédits" />
              <Tab icon={<Description />} iconPosition="start" label="Documents" />
            </Tabs>

            {/* ======= Inscriptions LMD ======= */}
            {tab === 0 && (
              <Grid container spacing={2.5}>
                {enrollments.map((enr) => (
                  <Grid item xs={12} md={6} key={enr.id}>
                    <Card variant="outlined" onClick={() => setSelectedEnr(enr)} sx={{ cursor: 'pointer', border: selectedEnr?.id === enr.id ? '2px solid' : '1px solid', borderColor: selectedEnr?.id === enr.id ? 'primary.main' : 'divider', borderRadius: '14px' }}>
                      <CardContent>
                        <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
                          <Typography variant="subtitle1" fontWeight={800}>{enr.field?.name}</Typography>
                          <Chip size="small" color={enr.active ? 'success' : 'default'} label={enr.enrollmentStatus || (enr.active ? 'INSCRIT' : 'INACTIF')} />
                        </Box>
                        <Typography variant="body2" color="text.secondary" mb={1}>{enr.field?.code} — Semestre {enr.currentSemester}</Typography>
                        <Box display="flex" gap={1} flexWrap="wrap">
                          {enr.level && <Chip size="small" icon={<Star sx={{ fontSize: 14 }} />} label={enr.level} />}
                          {enr.program && <Chip size="small" label={enr.program?.name} />}
                          {enr.academicYear && <Chip size="small" label={enr.academicYear} />}
                        </Box>
                        <Divider sx={{ my: 1.5 }} />
                        <Typography variant="body2" fontWeight={600} color="text.secondary">UE suivies ({ueEnrollments.length})</Typography>
                        <Box display="flex" flexDirection="column" gap={0.5} mt={0.5}>
                          {ueEnrollments.filter((ue) => ue.field?.id === enr.field?.id).slice(0, 6).map((ue) => (
                            <Box key={ue.id} display="flex" alignItems="center" gap={1}>
                              <MenuBook sx={{ fontSize: 14, color: 'text.disabled' }} />
                              <Typography variant="caption">{ue.code} — {ue.name}</Typography>
                            </Box>
                          ))}
                          {ueEnrollments.filter((ue) => ue.field?.id === enr.field?.id).length === 0 && (
                            <Typography variant="caption" color="text.secondary">Aucune UE inscrite pour ce semestre.</Typography>
                          )}
                        </Box>
                        <Divider sx={{ my: 1.5 }} />
                        <Typography variant="body2" fontWeight={600} color="text.secondary">Historique du parcours</Typography>
                        <Box display="flex" flexDirection="column" gap={0.5} mt={0.5}>
                          {history.filter((h) => h.field?.id === enr.field?.id).length === 0 && <Typography variant="caption" color="text.secondary">Aucun événement.</Typography>}
                          {history.filter((h) => h.field?.id === enr.field?.id).slice(0, 6).map((h) => (
                            <Box key={h.id} display="flex" alignItems="center" gap={1}>
                              <History sx={{ fontSize: 14, color: 'text.disabled' }} />
                              <Typography variant="caption">{h.fromLevel ? `${h.fromLevel} → ${h.toLevel || '—'}` : ''}{h.fromSemester ? ` · ${h.fromSemester} → ${h.toSemester || '—'}` : ''}{h.academicYear ? ` · ${h.academicYear}` : ''}{h.enrollmentStatus ? ` · ${h.enrollmentStatus}` : ''}</Typography>
                            </Box>
                          ))}
                        </Box>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            )}

            {/* ======= Résultats détaillés ======= */}
            {tab === 1 && (
              <>
                <Box display="flex" gap={1} flexWrap="wrap" mb={2}>
                  {enrollments.map((enr) => (
                    <Chip key={enr.id} label={`${enr.field?.name} — ${enr.currentSemester}`} color={selectedEnr?.id === enr.id ? 'primary' : 'default'} onClick={() => setSelectedEnr(enr)} sx={{ fontWeight: 600 }} />
                  ))}
                </Box>
                <Box display="flex" gap={1} mb={2}>
                  <Chip size="small" label="Session 1 (normale)" color={releveSession === 1 ? 'primary' : 'default'} onClick={() => setReleveSession(1)} clickable />
                  <Chip size="small" label="Session 2 (rattrapage)" color={releveSession === 2 ? 'primary' : 'default'} onClick={() => setReleveSession(2)} clickable />
                </Box>

                {loadingReleve ? (<Box p={3}><Skeleton height={250} /></Box>
                ) : !releve ? (<EmptyState message="Résultats indisponibles pour ce semestre et cette session." />
                ) : (
                  <Grid container spacing={3}>
                    <Grid item xs={12} md={4}>
                      <Card variant="outlined" sx={{ p: 2.5, borderRadius: '14px' }}>
                        <Typography variant="subtitle2" color="text.secondary" fontWeight={600}>Moyenne du semestre</Typography>
                        <Typography variant="h3" fontWeight={800} color="primary.main">{releve.average ?? '—'}/20</Typography>
                        <Divider sx={{ my: 1.5 }} />
                        <Box display="flex" justifyContent="space-between" mb={1}><Typography variant="body2">Décision</Typography><Chip size="small" label={releve.decision} color={releve.decision === 'ADMIS' || releve.decision === 'PASSAGE_AVEC_DETTES' ? 'success' : 'warning'} /></Box>
                        <Box display="flex" justifyContent="space-between" mb={1}><Typography variant="body2">Mention</Typography><Chip size="small" label={releve.mention || '—'} color={releve.mention ? 'secondary' : 'default'} /></Box>
                        <Divider sx={{ my: 1.5 }} />
                        <Box display="flex" justifyContent="space-between"><Typography variant="body2">Crédits acquis</Typography><Typography variant="body2" fontWeight={700} color="success.main">{releve.creditsObtained ?? 0}</Typography></Box>
                        <Box display="flex" justifyContent="space-between" mt={0.5}><Typography variant="body2">Crédits échoués</Typography><Typography variant="body2" fontWeight={700} color="error.main">{releve.creditsFailed ?? 0}</Typography></Box>
                        <Box display="flex" justifyContent="space-between" mt={0.5}><Typography variant="body2">Crédits du semestre</Typography><Typography variant="body2" fontWeight={700}>{releve.totalCredits ?? 0}</Typography></Box>
                      </Card>
                    </Grid>
                    <Grid item xs={12} md={8}>
                      <Card variant="outlined" sx={{ p: 2.5, borderRadius: '14px' }}>
                        <Typography variant="subtitle1" fontWeight={700} mb={1.5}>Unités d'enseignement ({releve.ues?.length ?? 0}) · {SESSION_LABEL[releveSession] || `Session ${releveSession}`}</Typography>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>UE</TableCell>
                              <TableCell align="center">Coef.</TableCell>
                              <TableCell align="center">Crédits</TableCell>
                              <TableCell align="center">Note /20</TableCell>
                              <TableCell>Statut</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {(releve.ues || []).map((ue) => (
                              <TableRow key={ue.code}>
                                <TableCell><Typography variant="body2" fontWeight={600}>{ue.name}</Typography><Typography variant="caption" color="text.secondary">{ue.code}</Typography></TableCell>
                                <TableCell align="center">{ue.coefficient}</TableCell>
                                <TableCell align="center">{ue.credits}</TableCell>
                                <TableCell align="center"><Typography variant="body2" fontWeight={700} color={ue.note != null && ue.note >= 10 ? 'success.main' : 'error.main'}>{ue.note ?? '—'}</Typography></TableCell>
                                <TableCell>{ue.note == null ? <Chip size="small" label="Non noté" variant="outlined" /> : ue.note >= 10 ? <Chip size="small" color="success" label="Validée" /> : <Chip size="small" color="error" label="À repasser" />}</TableCell>
                              </TableRow>
                            ))}
                            {(releve.ues || []).length === 0 && <TableRow><TableCell colSpan={5} align="center" sx={{ py: 4 }} color="text.secondary">Aucune UE évaluée.</TableCell></TableRow>}
                          </TableBody>
                        </Table>
                        {releve.uesToRetake?.length > 0 && <Box mt={2}><Typography variant="body2" color="error" fontWeight={600}>UE à repasser : {releve.uesToRetake.join(', ')}</Typography></Box>}
                      </Card>
                    </Grid>
                  </Grid>
                )}
              </>
            )}

            {/* ======= Crédits ======= */}
            {tab === 2 && (
              <Grid container spacing={3}>
                <Grid item xs={12} md={6}>
                  <Card variant="outlined" sx={{ p: 2.5, borderRadius: '14px' }}>
                    <Typography variant="subtitle1" fontWeight={700} mb={2}>Bilan des crédits</Typography>
                    <Box mb={2}>
                      <Typography variant="body2" color="text.secondary">Crédits obtenus ce semestre</Typography>
                      <Box display="flex" alignItems="center" gap={2}>
                        <Typography variant="h4" fontWeight={800} color="success.main">{creditsObtained}</Typography>
                        <Typography variant="body2" color="text.secondary">/ {totalCreditsSemester}</Typography>
                      </Box>
                      <LinearProgress variant="determinate" value={totalCreditsSemester > 0 ? (creditsObtained / totalCreditsSemester) * 100 : 0} sx={{ mt: 1, height: 8, borderRadius: 4 }} />
                    </Box>
                    <Box mb={2}>
                      <Typography variant="body2" color="text.secondary">Crédits échoués</Typography>
                      <Typography variant="h5" fontWeight={700} color="error.main">{creditsFailed}</Typography>
                    </Box>
                    <Divider sx={{ my: 2 }} />
                    <Box display="flex" justifyContent="space-between" mb={1}>
                      <Typography variant="body2">Total programme</Typography>
                      <Typography variant="body2" fontWeight={700}>{totalCreditsAll}</Typography>
                    </Box>
                    <Box display="flex" justifyContent="space-between" mb={1}>
                      <Typography variant="body2">Crédits validés (cumul)</Typography>
                      <Typography variant="body2" fontWeight={700} color="success.main">{creditsCumulated}</Typography>
                    </Box>
                    {debts.length > 0 && (
                      <Box mt={2}>
                        <Typography variant="body2" color="error" fontWeight={600}>UE à valider (dettes) : {debts.join(', ')}</Typography>
                      </Box>
                    )}
                  </Card>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Card variant="outlined" sx={{ p: 2.5, borderRadius: '14px' }}>
                    <Typography variant="subtitle1" fontWeight={700} mb={2}>Crédits par UE — {selectedEnr?.currentSemester || ''}</Typography>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>UE</TableCell>
                          <TableCell align="center">Crédits</TableCell>
                          <TableCell align="center">Note</TableCell>
                          <TableCell>Statut</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {(releve?.ues || []).map((ue) => (
                          <TableRow key={ue.code}>
                            <TableCell><Typography variant="body2" fontWeight={600}>{ue.code}</Typography></TableCell>
                            <TableCell align="center">{ue.credits}</TableCell>
                            <TableCell align="center"><Typography variant="body2" fontWeight={700} color={ue.note != null && ue.note >= 10 ? 'success.main' : 'error.main'}>{ue.note ?? '—'}</Typography></TableCell>
                            <TableCell>{ue.note == null ? <Chip size="small" label="En cours" variant="outlined" /> : ue.note >= 10 ? <Chip size="small" color="success" label="Acquis" /> : <Chip size="small" color="error" label="Non acquis" />}</TableCell>
                          </TableRow>
                        ))}
                        {(releve?.ues || []).length === 0 && <TableRow><TableCell colSpan={4} align="center" sx={{ py: 4 }} color="text.secondary">Aucune UE pour ce semestre.</TableCell></TableRow>}
                      </TableBody>
                    </Table>
                  </Card>
                </Grid>
              </Grid>
            )}

            {/* ======= Documents ======= */}
            {tab === 3 && (
              <Grid container spacing={2.5}>
                {enrollments.map((enr) => (
                  <Grid item xs={12} md={6} key={enr.id}>
                    <Card variant="outlined" sx={{ p: 2.5, borderRadius: '14px' }}>
                      <Typography variant="subtitle1" fontWeight={700}>{enr.field?.name} — Semestre {enr.currentSemester}</Typography>
                      <Typography variant="body2" color="text.secondary" mb={2}>{enr.level ? `${enr.level} · ` : ''}{enr.program?.name || ''} {enr.academicYear ? ` · ${enr.academicYear}` : ''}</Typography>
                      <Box display="flex" gap={1} flexWrap="wrap">
                        <Button size="small" variant="contained" startIcon={<PictureAsPdf />} onClick={() => downloadDoc('releve', enr)}>Relevé PDF</Button>
                        <Button size="small" variant="outlined" startIcon={<TableView />} onClick={() => downloadDoc('releve-excel', enr)}>Relevé Excel</Button>
                        <Button size="small" variant="outlined" startIcon={<PictureAsPdf />} onClick={() => downloadDoc('attestation', enr)}>Attestation</Button>
                      </Box>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            )}
          </CardContent>
        </Card>
      )}
    </>
  )
}