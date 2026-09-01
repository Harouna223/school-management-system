import { useState, useEffect } from 'react'
import { Grid, TextField, Button, MenuItem } from '@mui/material'
import { Search, FilterAlt } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import StatusChip from '../../components/StatusChip'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { dashboardApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDateTime } from '../../utils/format'

const ACTIONS = ['LOGIN', 'CREATE', 'UPDATE', 'DELETE', 'PAYMENT', 'EXPENSE', 'SAVE_GRADE', 'BULLETIN', 'DELIBERATE', 'ROLES_UPDATE', 'TOGGLE_ENABLED', 'PASSWORD_RESET', 'PASSWORD_CHANGE', 'TRANSFER', 'RADIATE', 'REINSCRIBE', 'LEAVE_REQUEST', 'LEAVE_DECIDE', 'CONTRACT_CREATE', 'CONTRACT_UPDATE', 'CONTRACT_DELETE', 'PAYROLL', 'BOOK_CREATE', 'BOOK_UPDATE', 'BOOK_DELETE', 'BORROW', 'RETURN', 'MESSAGE_SEND', 'ANNOUNCEMENT_CREATE', 'ANNOUNCEMENT_DELETE']

const ENTITIES = ['Student', 'Teacher', 'Class', 'Subject', 'Schedule', 'Exam', 'Grade', 'Bulletin', 'Invoice', 'Payment', 'Expense', 'User', 'Leave', 'Contract', 'Payroll', 'Book', 'Borrowing', 'Message', 'Announcement', 'Attendance']

/**
 * Journal d'activité : traçabilité complète des actions utilisateurs.
 */
export default function AuditPage() {
  const { error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [username, setUsername] = useState('')
  const [action, setAction] = useState('')
  const [entity, setEntity] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const load = async () => {
    setLoading(true)
    try {
      const { data } = await dashboardApi.auditLogs({
        username: username || undefined,
        action: action || undefined,
        entity: entity || undefined,
        from: from || undefined,
        to: to || undefined,
        page, size,
      })
      setRows(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size])

  const applyFilters = () => {
    setPage(0)
    load()
  }

  const columns = [
    { key: 'username', label: 'Utilisateur', render: (r) => <b>{r.username}</b> },
    {
      key: 'action',
      label: 'Action',
      render: (r) => <StatusChip status={r.action} />,
    },
    { key: 'entity', label: 'Entité', render: (r) => r.entity || '—' },
    { key: 'details', label: 'Détails' },
    { key: 'ipAddress', label: 'IP', render: (r) => r.ipAddress || '—' },
    { key: 'createdAt', label: 'Date', render: (r) => formatDateTime(r.createdAt) },
  ]

  return (
    <>
      <PageHeader
        title="Journal d'activité"
        subtitle="Traçabilité complète des actions utilisateurs"
        badge={total ? `${total} entrée(s)` : undefined}
      />

      <FilterCard mb={3} title="Filtres avancés" actions={
        <Button size="small" variant="contained" startIcon={<FilterAlt />} onClick={applyFilters}>
          Appliquer
        </Button>
      }>
        <Grid container spacing={2} alignItems="flex-end">
          <Grid item xs={12} md={4}>
            <TextField size="small" fullWidth label="Utilisateur" value={username}
              onChange={(e) => setUsername(e.target.value)} />
          </Grid>
          <Grid item xs={12} md={2.5}>
            <TextField select size="small" fullWidth label="Action" value={action}
              onChange={(e) => setAction(e.target.value)}>
              <MenuItem value="">Toutes</MenuItem>
              {ACTIONS.map((a) => <MenuItem key={a} value={a}>{a}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={2.5}>
            <TextField select size="small" fullWidth label="Entité" value={entity}
              onChange={(e) => setEntity(e.target.value)}>
              <MenuItem value="">Toutes</MenuItem>
              {ENTITIES.map((e) => <MenuItem key={e} value={e}>{e}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={6} md={1.5}>
            <TextField size="small" fullWidth type="date" label="Du" value={from}
              onChange={(e) => setFrom(e.target.value)} InputLabelProps={{ shrink: true }} />
          </Grid>
          <Grid item xs={6} md={1.5}>
            <TextField size="small" fullWidth type="date" label="Au" value={to}
              onChange={(e) => setTo(e.target.value)} InputLabelProps={{ shrink: true }} />
          </Grid>
        </Grid>
      </FilterCard>

      <DataTable
        title="Historique des actions"
        columns={columns}
        rows={rows}
        loading={loading}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={(s) => { setSize(s); setPage(0) }}
        searchable={false}
        actions={false}
      />
    </>
  )
}