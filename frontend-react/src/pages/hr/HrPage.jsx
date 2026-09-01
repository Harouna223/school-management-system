import { useState, useEffect } from 'react'
import {
  Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions,
  Box, Chip, Typography, Tabs, Tab, Card,
} from '@mui/material'
import { EventBusy, Description, Payments as PaymentsIcon } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { hrApi, teacherApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate, formatCurrency } from '../../utils/format'
import StatusChip from '../../components/StatusChip'

const EMPTY_CONTRACT = {
  teacherId: '', type: 'CDI', startDate: '', endDate: '', baseSalary: '', description: '',
}
const EMPTY_PAYROLL = {
  teacherId: '', monthDate: '', allowances: '0', deductions: '0',
}

/**
 * Module RH : congés, contrats et paie du personnel.
 */
export default function HrPage() {
  const { success, error: toastError } = useToast()
  const [tab, setTab] = useState('leaves')

  // Congés
  const [leaves, setLeaves] = useState([])
  const [leaveFilter, setLeaveFilter] = useState('')
  const [leaveDialog, setLeaveDialog] = useState(false)
  const [leaveForm, setLeaveForm] = useState({ teacherId: '', type: 'ANNUAL', startDate: '', endDate: '', reason: '' })

  // Contrats
  const [contracts, setContracts] = useState([])
  const [contractDialog, setContractDialog] = useState(false)
  const [editingContract, setEditingContract] = useState(null)
  const [contractForm, setContractForm] = useState(EMPTY_CONTRACT)
  const [toDeleteContract, setToDeleteContract] = useState(null)

  // Paie
  const [payrolls, setPayrolls] = useState([])
  const [payrollDialog, setPayrollDialog] = useState(false)
  const [payrollForm, setPayrollForm] = useState(EMPTY_PAYROLL)

  const [teachers, setTeachers] = useState([])

  useEffect(() => {
    teacherApi.search({ page: 0, size: 500 }).then((r) => setTeachers(r.data.data.content)).catch(() => {})
  }, [])

  // ---------- Congés ----------
  const loadLeaves = async () => {
    try {
      const { data } = await hrApi.leaves({ status: leaveFilter || undefined })
      setLeaves(data.data)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    loadLeaves()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [leaveFilter])

  const requestLeave = async () => {
    try {
      await hrApi.requestLeave({
        teacherId: Number(leaveForm.teacherId),
        type: leaveForm.type,
        startDate: leaveForm.startDate,
        endDate: leaveForm.endDate,
        reason: leaveForm.reason,
      })
      success('Demande de congé enregistrée')
      setLeaveDialog(false)
      setLeaveForm({ teacherId: '', type: 'ANNUAL', startDate: '', endDate: '', reason: '' })
      loadLeaves()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const decide = async (leave, status) => {
    try {
      await hrApi.decide(leave.id, status)
      success(`Congé ${status === 'APPROVED' ? 'approuvé' : 'rejeté'}`)
      loadLeaves()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  // ---------- Contrats ----------
  const loadContracts = async () => {
    try {
      const { data } = await hrApi.contracts()
      setContracts(data.data)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    if (tab === 'contracts') loadContracts()
    if (tab === 'payrolls') loadPayrolls()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab])

  const saveContract = async () => {
    const payload = {
      ...contractForm,
      teacherId: Number(contractForm.teacherId),
      baseSalary: Number(contractForm.baseSalary),
      endDate: contractForm.endDate || null,
    }
    try {
      if (editingContract) {
        await hrApi.updateContract(editingContract.id, payload)
        success('Contrat modifié')
      } else {
        await hrApi.createContract(payload)
        success('Contrat créé')
      }
      setContractDialog(false)
      setContractForm(EMPTY_CONTRACT)
      loadContracts()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const deleteContract = async () => {
    try {
      await hrApi.deleteContract(toDeleteContract.id)
      success('Contrat supprimé')
      setToDeleteContract(null)
      loadContracts()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  // ---------- Paie ----------
  const loadPayrolls = async () => {
    try {
      const { data } = await hrApi.payrolls()
      setPayrolls(data.data)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const generatePayroll = async () => {
    try {
      await hrApi.generatePayroll({
        teacherId: Number(payrollForm.teacherId),
        monthDate: `${payrollForm.monthDate}-01`,
        allowances: Number(payrollForm.allowances || 0),
        deductions: Number(payrollForm.deductions || 0),
      })
      success('Bulletin de paie généré')
      setPayrollDialog(false)
      setPayrollForm(EMPTY_PAYROLL)
      loadPayrolls()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const markPaid = async (payroll) => {
    try {
      await hrApi.payPayroll(payroll.id)
      success(`Salaire de ${payroll.teacherName} marqué comme payé`)
      loadPayrolls()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  // ---------- Colonnes ----------
  const leaveColumns = [
    { key: 'teacherName', label: 'Enseignant', render: (r) => <b>{r.teacherName}</b> },
    { key: 'type', label: 'Type' },
    { key: 'startDate', label: 'Début', render: (r) => formatDate(r.startDate) },
    { key: 'endDate', label: 'Fin', render: (r) => formatDate(r.endDate) },
    { key: 'reason', label: 'Motif', render: (r) => r.reason || '—' },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'actions',
      label: 'Décision',
      render: (r) =>
        r.status === 'PENDING' ? (
          <Box display="flex" gap={1}>
            <Button size="small" color="success" onClick={() => decide(r, 'APPROVED')}>Approuver</Button>
            <Button size="small" color="error" onClick={() => decide(r, 'REJECTED')}>Rejeter</Button>
          </Box>
        ) : null,
    },
  ]

  const contractColumns = [
    { key: 'teacherName', label: 'Enseignant', render: (r) => <b>{r.teacherName}</b> },
    { key: 'type', label: 'Type', render: (r) => <Chip size="small" label={r.type} /> },
    { key: 'startDate', label: 'Début', render: (r) => formatDate(r.startDate) },
    { key: 'endDate', label: 'Fin', render: (r) => r.endDate ? formatDate(r.endDate) : '—' },
    { key: 'baseSalary', label: 'Salaire de base', render: (r) => <b>{formatCurrency(r.baseSalary)}</b> },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
  ]

  const payrollColumns = [
    { key: 'teacherName', label: 'Enseignant', render: (r) => <b>{r.teacherName}</b> },
    { key: 'monthDate', label: 'Mois', render: (r) => formatMonth(r.monthDate) },
    { key: 'baseSalary', label: 'Base', render: (r) => formatCurrency(r.baseSalary) },
    { key: 'allowances', label: 'Primes', render: (r) => formatCurrency(r.allowances) },
    { key: 'deductions', label: 'Retenues', render: (r) => formatCurrency(r.deductions) },
    { key: 'netSalary', label: 'Net à payer', render: (r) => <b>{formatCurrency(r.netSalary)}</b> },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'actions2',
      label: '',
      render: (r) =>
        r.status === 'PENDING' ? (
          <Button size="small" color="success" startIcon={<PaymentsIcon />} onClick={() => markPaid(r)}>
            Marquer payé
          </Button>
        ) : null,
    },
  ]

  const tabLabel = (icon, text) => (
    <Box display="flex" alignItems="center" gap={1}>
      {icon}
      {text}
    </Box>
  )

  return (
    <>
      <PageHeader
        title="Ressources Humaines"
        subtitle="Congés, contrats et paie du personnel"
        actionLabel={tab === 'leaves' ? 'Demander un congé' : tab === 'contracts' ? 'Nouveau contrat' : 'Générer un bulletin'}
        onAction={() => {
          if (tab === 'leaves') setLeaveDialog(true)
          else if (tab === 'contracts') { setEditingContract(null); setContractForm(EMPTY_CONTRACT); setContractDialog(true) }
          else setPayrollDialog(true)
        }}
      />

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 3 }}>
        <Tab value="leaves" label={tabLabel(<EventBusy fontSize="small" />, 'Congés')} />
        <Tab value="contracts" label={tabLabel(<Description fontSize="small" />, 'Contrats')} />
        <Tab value="payrolls" label={tabLabel(<PaymentsIcon fontSize="small" />, 'Paie')} />
      </Tabs>

      {/* ---- Onglet Congés ---- */}
      {tab === 'leaves' && (
        <>
          <FilterCard mb={3}>
            <Grid container spacing={2}>
              <Grid item xs={12} md={3}>
                <TextField select size="small" fullWidth label="Statut" value={leaveFilter} onChange={(e) => setLeaveFilter(e.target.value)}>
                  <MenuItem value="">Tous</MenuItem>
                  <MenuItem value="PENDING">En attente</MenuItem>
                  <MenuItem value="APPROVED">Approuvés</MenuItem>
                  <MenuItem value="REJECTED">Rejetés</MenuItem>
                </TextField>
              </Grid>
            </Grid>
          </FilterCard>
          <DataTable columns={leaveColumns} rows={leaves} searchable={false} actions={false} />

          <Dialog open={leaveDialog} onClose={() => setLeaveDialog(false)} maxWidth="sm" fullWidth>
            <DialogTitle>Demande de congé</DialogTitle>
            <DialogContent>
              <Grid container spacing={2} mt={0.5}>
                <Grid item xs={12}>
                  <TextField select fullWidth label="Enseignant" value={leaveForm.teacherId} onChange={(e) => setLeaveForm({ ...leaveForm, teacherId: e.target.value })}>
                    {teachers.map((t) => <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>)}
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField select fullWidth label="Type" value={leaveForm.type} onChange={(e) => setLeaveForm({ ...leaveForm, type: e.target.value })}>
                    <MenuItem value="ANNUAL">Annuel</MenuItem>
                    <MenuItem value="SICK">Maladie</MenuItem>
                    <MenuItem value="MATERNITY">Maternité</MenuItem>
                    <MenuItem value="UNPAID">Sans solde</MenuItem>
                    <MenuItem value="OTHER">Autre</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={6}>
                  <TextField fullWidth type="date" label="Début" value={leaveForm.startDate}
                    onChange={(e) => setLeaveForm({ ...leaveForm, startDate: e.target.value })} InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid item xs={6}>
                  <TextField fullWidth type="date" label="Fin" value={leaveForm.endDate}
                    onChange={(e) => setLeaveForm({ ...leaveForm, endDate: e.target.value })} InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid item xs={12}>
                  <TextField fullWidth multiline rows={2} label="Motif" value={leaveForm.reason}
                    onChange={(e) => setLeaveForm({ ...leaveForm, reason: e.target.value })} />
                </Grid>
              </Grid>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setLeaveDialog(false)}>Annuler</Button>
              <Button variant="contained" onClick={requestLeave} disabled={!leaveForm.teacherId || !leaveForm.startDate || !leaveForm.endDate}>
                Envoyer la demande
              </Button>
            </DialogActions>
          </Dialog>
        </>
      )}

      {/* ---- Onglet Contrats ---- */}
      {tab === 'contracts' && (
        <DataTable
          columns={contractColumns}
          rows={contracts}
          searchable={false}
          onEdit={(row) => {
            setEditingContract(row)
            setContractForm({
              teacherId: String(row.teacherId),
              type: row.type,
              startDate: row.startDate || '',
              endDate: row.endDate || '',
              baseSalary: String(row.baseSalary || ''),
              description: row.description || '',
            })
            setContractDialog(true)
          }}
          onDelete={setToDeleteContract}
        />
      )}

      {/* ---- Onglet Paie ---- */}
      {tab === 'payrolls' && (
        <DataTable columns={payrollColumns} rows={payrolls} searchable={false} actions={false} />
      )}

      {/* Dialog contrat */}
      <Dialog open={contractDialog} onClose={() => setContractDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingContract ? 'Modifier le contrat' : 'Nouveau contrat'}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Enseignant" value={contractForm.teacherId}
                onChange={(e) => setContractForm({ ...contractForm, teacherId: e.target.value })}>
                {teachers.map((t) => <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField select fullWidth label="Type de contrat" value={contractForm.type}
                onChange={(e) => setContractForm({ ...contractForm, type: e.target.value })}>
                <MenuItem value="CDI">CDI</MenuItem>
                <MenuItem value="CDD">CDD</MenuItem>
                <MenuItem value="VACATAIRE">Vacataire</MenuItem>
                <MenuItem value="STAGE">Stage</MenuItem>
                <MenuItem value="OTHER">Autre</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth label="Salaire de base (FCFA)" type="number" value={contractForm.baseSalary}
                onChange={(e) => setContractForm({ ...contractForm, baseSalary: e.target.value })} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth type="date" label="Début" value={contractForm.startDate}
                onChange={(e) => setContractForm({ ...contractForm, startDate: e.target.value })} InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth type="date" label="Fin (si CDD)" value={contractForm.endDate}
                onChange={(e) => setContractForm({ ...contractForm, endDate: e.target.value })} InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth multiline rows={2} label="Description" value={contractForm.description}
                onChange={(e) => setContractForm({ ...contractForm, description: e.target.value })} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setContractDialog(false)}>Annuler</Button>
          <Button variant="contained" onClick={saveContract} disabled={!contractForm.teacherId || !contractForm.startDate || !contractForm.baseSalary}>
            {editingContract ? 'Enregistrer' : 'Créer le contrat'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog paie */}
      <Dialog open={payrollDialog} onClose={() => setPayrollDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Générer un bulletin de paie</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" mb={2}>
            Le salaire de base est celui du contrat actif de l'enseignant (sinon salaire du profil).
            Net = base + primes − retenues.
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Enseignant" value={payrollForm.teacherId}
                onChange={(e) => setPayrollForm({ ...payrollForm, teacherId: e.target.value })}>
                {teachers.map((t) => <MenuItem key={t.id} value={String(t.id)}>{t.firstName} {t.lastName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="month" label="Mois" value={payrollForm.monthDate}
                onChange={(e) => setPayrollForm({ ...payrollForm, monthDate: e.target.value })} InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth type="number" label="Primes (FCFA)" value={payrollForm.allowances}
                onChange={(e) => setPayrollForm({ ...payrollForm, allowances: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth type="number" label="Retenues (FCFA)" value={payrollForm.deductions}
                onChange={(e) => setPayrollForm({ ...payrollForm, deductions: e.target.value })} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPayrollDialog(false)}>Annuler</Button>
          <Button variant="contained" onClick={generatePayroll} disabled={!payrollForm.teacherId || !payrollForm.monthDate}>
            Générer le bulletin
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDeleteContract)}
        title="Supprimer le contrat"
        message={`Supprimer le contrat de ${toDeleteContract?.teacherName} ?`}
        onConfirm={deleteContract}
        onClose={() => setToDeleteContract(null)}
      />
    </>
  )
}

function formatMonth(dateStr) {
  if (!dateStr) return '—'
  const [y, m] = dateStr.split('-')
  const MONTHS = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre']
  return `${MONTHS[Number(m) - 1]} ${y}`
}