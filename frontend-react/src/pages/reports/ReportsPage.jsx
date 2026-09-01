import { useState, useEffect } from 'react'
import {
  Box, Card, Grid, TextField, Button, Typography, Tabs, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, FormControl, InputLabel, Select, MenuItem,
} from '@mui/material'
import { Assessment, TableView, PictureAsPdf, Download } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { attendanceApi, financeApi, classApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatCurrency, downloadBlob } from '../../utils/format'

/**
 * Rapports agrégés : présences et finances.
 */
export default function ReportsPage() {
  const { success, error: toastError } = useToast()
  const [tab, setTab] = useState(0)

  // Attendance
  const [classes, setClasses] = useState([])
  const [attClassId, setAttClassId] = useState('')
  const [attFrom, setAttFrom] = useState('')
  const [attTo, setAttTo] = useState('')
  const [attRows, setAttRows] = useState([])

  // Finance
  const [finFrom, setFinFrom] = useState('')
  const [finTo, setFinTo] = useState('')
  const [finData, setFinData] = useState(null)

  useEffect(() => {
    classApi.all().then((r) => setClasses(r.data.data || [])).catch(() => {})
  }, [])

  const loadAttendance = async () => {
    if (!attClassId) return
    try {
      const { data } = await attendanceApi.report(attClassId, attFrom || undefined, attTo || undefined)
      setAttRows(data.data || [])
    } catch (err) { toastError(extractError(err)) }
  }

  const exportAttendance = async () => {
    try {
      const res = await attendanceApi.reportExcel(attClassId, attFrom || undefined, attTo || undefined)
      downloadBlob(res.data, 'rapport-presences.xlsx')
      success('Export Excel généré')
    } catch (err) { toastError(extractError(err)) }
  }

  const exportAttendancePdf = async () => {
    try {
      const res = await attendanceApi.reportPdf(attClassId, attFrom || undefined, attTo || undefined)
      downloadBlob(res.data, 'rapport-presences.pdf')
      success('Rapport PDF généré')
    } catch (err) { toastError(extractError(err)) }
  }

  const loadFinance = async () => {
    if (!finFrom || !finTo) return
    try {
      const { data } = await financeApi.report(finFrom, finTo)
      setFinData(data.data)
    } catch (err) { toastError(extractError(err)) }
  }

  const exportFinance = async () => {
    try {
      const res = await financeApi.reportExcel(finFrom, finTo)
      downloadBlob(res.data, `bilan-financier-${finFrom}-${finTo}.xlsx`)
      success('Export Excel généré')
    } catch (err) { toastError(extractError(err)) }
  }

  const exportFinancePdf = async () => {
    try {
      const res = await financeApi.reportPdf(finFrom, finTo)
      downloadBlob(res.data, `bilan-financier-${finFrom}-${finTo}.pdf`)
      success('Bilan PDF généré')
    } catch (err) { toastError(extractError(err)) }
  }

  return (
    <>
      <PageHeader title="Rapports" subtitle="Statistiques et bilans par période" />

      <Card sx={{ mb: 3, borderRadius: '16px' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ px: 2 }}>
          <Tab label="Présences" icon={<Assessment />} iconPosition="start" />
          <Tab label="Finances" icon={<TableView />} iconPosition="start" />
        </Tabs>
      </Card>

      {tab === 0 && (
        <Card sx={{ p: 2.5, borderRadius: '16px' }}>
          <Grid container spacing={2} alignItems="flex-end" mb={2}>
            <Grid item xs={12} md={3}>
              <FormControl size="small" fullWidth>
                <InputLabel>Classe</InputLabel>
                <Select label="Classe" value={attClassId} onChange={(e) => setAttClassId(e.target.value)}>
                  {classes.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={3}>
              <TextField fullWidth size="small" type="date" label="Du" InputLabelProps={{ shrink: true }}
                value={attFrom} onChange={(e) => setAttFrom(e.target.value)} />
            </Grid>
            <Grid item xs={6} md={3}>
              <TextField fullWidth size="small" type="date" label="Au" InputLabelProps={{ shrink: true }}
                value={attTo} onChange={(e) => setAttTo(e.target.value)} />
            </Grid>
            <Grid item xs={12} md={3}>
              <Button variant="contained" fullWidth disabled={!attClassId} onClick={loadAttendance}>Générer</Button>
            </Grid>
          </Grid>

          {attRows.length > 0 && (
            <>
              <Box display="flex" justifyContent="flex-end" gap={1} mb={1.5}>
                <Button size="small" startIcon={<PictureAsPdf />} onClick={exportAttendancePdf}>PDF</Button>
                <Button size="small" startIcon={<Download />} onClick={exportAttendance}>Excel</Button>
              </Box>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Matricule</TableCell>
                      <TableCell>Nom</TableCell>
                      <TableCell>Prénom</TableCell>
                      <TableCell align="right">Présents</TableCell>
                      <TableCell align="right">Absents</TableCell>
                      <TableCell align="right">Retards</TableCell>
                      <TableCell align="right">Justifiés</TableCell>
                      <TableCell align="right">Taux %</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {attRows.map((r) => (
                      <TableRow key={r.studentId}>
                        <TableCell>{r.matricule}</TableCell>
                        <TableCell>{r.lastName}</TableCell>
                        <TableCell>{r.firstName}</TableCell>
                        <TableCell align="right">{r.present}</TableCell>
                        <TableCell align="right">{r.absent}</TableCell>
                        <TableCell align="right">{r.late}</TableCell>
                        <TableCell align="right">{r.justified}</TableCell>
                        <TableCell align="right">{r.rate != null ? `${r.rate}%` : '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </>
          )}
          {attRows.length === 0 && <Typography variant="body2" color="text.secondary">Générez un rapport pour afficher les données.</Typography>}
        </Card>
      )}

      {tab === 1 && (
        <Card sx={{ p: 2.5, borderRadius: '16px' }}>
          <Grid container spacing={2} alignItems="flex-end" mb={2}>
            <Grid item xs={6} md={3}>
              <TextField fullWidth size="small" type="date" label="Du" InputLabelProps={{ shrink: true }}
                value={finFrom} onChange={(e) => setFinFrom(e.target.value)} />
            </Grid>
            <Grid item xs={6} md={3}>
              <TextField fullWidth size="small" type="date" label="Au" InputLabelProps={{ shrink: true }}
                value={finTo} onChange={(e) => setFinTo(e.target.value)} />
            </Grid>
            <Grid item xs={12} md={3}>
              <Button variant="contained" fullWidth disabled={!finFrom || !finTo} onClick={loadFinance}>Générer</Button>
            </Grid>
          </Grid>

          {finData && (
            <>
              <Box display="flex" justifyContent="flex-end" gap={1} mb={1.5}>
                <Button size="small" startIcon={<PictureAsPdf />} onClick={exportFinancePdf}>PDF</Button>
                <Button size="small" startIcon={<Download />} onClick={exportFinance}>Excel</Button>
              </Box>
              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <Card variant="outlined" sx={{ p: 2, borderRadius: '12px', textAlign: 'center' }}>
                    <Typography variant="caption" color="text.secondary">Encaissé</Typography>
                    <Typography variant="h5" fontWeight={700} color="success.main">{formatCurrency(finData.revenue)}</Typography>
                  </Card>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Card variant="outlined" sx={{ p: 2, borderRadius: '12px', textAlign: 'center' }}>
                    <Typography variant="caption" color="text.secondary">Dépenses</Typography>
                    <Typography variant="h5" fontWeight={700} color="error.main">{formatCurrency(finData.expenses)}</Typography>
                  </Card>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Card variant="outlined" sx={{ p: 2, borderRadius: '12px', textAlign: 'center' }}>
                    <Typography variant="caption" color="text.secondary">Solde</Typography>
                    <Typography variant="h5" fontWeight={700} color={Number(finData.balance) >= 0 ? 'primary.main' : 'error.main'}>{formatCurrency(finData.balance)}</Typography>
                  </Card>
                </Grid>
              </Grid>
            </>
          )}
          {!finData && <Typography variant="body2" color="text.secondary">Générez un rapport pour afficher les données.</Typography>}
        </Card>
      )}
    </>
  )
}