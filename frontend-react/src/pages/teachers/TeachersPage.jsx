import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Grid, TextField, MenuItem, Button, Box, Typography, Avatar } from '@mui/material'
import { School, PersonAddAlt1, TableView } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import StatusChip from '../../components/StatusChip'
import StatCard from '../../components/StatCard'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { useFetch } from '../../hooks/useFetch'
import { teacherApi, dashboardApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate, initials, formatCurrency, downloadBlob } from '../../utils/format'
import { hasPermission } from '../../utils/auth'

/**
 * Liste des enseignants avec recherche et filtres.
 */
export default function TeachersPage() {
  const navigate = useNavigate()
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [toDelete, setToDelete] = useState(null)

  const { data: stats, loading: statsLoading } = useFetch(() => dashboardApi.stats())
  const canWrite = hasPermission('TEACHER_WRITE')

  const load = async () => {
    setLoading(true)
    try {
      const { data } = await teacherApi.search({
        search: search || undefined,
        status: status || undefined,
        page,
        size,
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
  }, [page, size, status])

  const handleDelete = async () => {
    try {
      await teacherApi.remove(toDelete.id)
      success('Enseignant supprimé')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleExportExcel = async () => {
    try {
      const res = await teacherApi.exportExcel({ search: search || undefined, status: status || undefined })
      downloadBlob(res.data, 'enseignants.xlsx')
      success('Export Excel généré')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    {
      key: 'teacher',
      label: 'Enseignant',
      render: (row) => (
        <Box display="flex" alignItems="center" gap={1.5}>
          <Avatar src={row.photo} sx={{ width: 34, height: 34 }}>
            {initials(row.firstName, row.lastName)}
          </Avatar>
          <Box>
            <Typography variant="body2" fontWeight={600}>
              {row.firstName} {row.lastName}
            </Typography>
            <Typography variant="caption" color="text.secondary">{row.employeeNo}</Typography>
          </Box>
        </Box>
      ),
    },
    { key: 'gender', label: 'Genre' },
    { key: 'phone', label: 'Téléphone', render: (r) => r.phone || '—' },
    { key: 'contractType', label: 'Contrat' },
    { key: 'salary', label: 'Salaire', render: (r) => formatCurrency(r.salary) },
    { key: 'hireDate', label: 'Embauche', render: (r) => formatDate(r.hireDate) },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
  ]

  return (
    <>
      <PageHeader
        title="Enseignants"
        subtitle="Gestion des profils, contrats et salaires"
        badge={total ? `${total} enseignant(s)` : undefined}
        actionLabel={canWrite ? 'Nouvel enseignant' : undefined}
        onAction={() => navigate('/teachers/new')}
        actions={[
          { label: 'Export Excel', icon: <TableView sx={{ fontSize: 18 }} />, onClick: handleExportExcel },
        ]}
      />

      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={4} className="stagger-1">
          <StatCard title="Enseignants" value={stats?.totalTeachers ?? 0} icon={School} color="secondary" loading={statsLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={4} className="stagger-2">
          <StatCard title="Dossiers dans la liste" value={total ?? 0} icon={PersonAddAlt1} color="primary" loading={loading} />
        </Grid>
      </Grid>

      <FilterCard mb={3}>
        <Grid container spacing={2} alignItems="flex-end">
          <Grid item xs={12} md={6}>
            <TextField
              size="small"
              fullWidth
              label="Rechercher (nom, matricule...)"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
            />
          </Grid>
          <Grid item xs={12} md={3}>
            <TextField
              select size="small" fullWidth label="Statut" value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <MenuItem value="">Tous</MenuItem>
              <MenuItem value="ACTIVE">Actif</MenuItem>
              <MenuItem value="INACTIVE">Inactif</MenuItem>
              <MenuItem value="ON_LEAVE">En congé</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12} md={3}>
            <Button variant="contained" size="medium" fullWidth onClick={load}>Rechercher</Button>
          </Grid>
        </Grid>
      </FilterCard>

      <DataTable
        title="Liste des enseignants"
        columns={columns}
        rows={rows}
        loading={loading}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={(s) => { setSize(s); setPage(0) }}
        onEdit={canWrite ? (row) => navigate(`/teachers/${row.id}/edit`) : undefined}
        onDelete={canWrite ? setToDelete : undefined}
      />

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer l'enseignant"
        message={`Supprimer ${toDelete?.firstName} ${toDelete?.lastName} ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}