import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Box, Grid, TextField, MenuItem, Button, Avatar, Chip, Typography,
  IconButton, Menu, Dialog, DialogTitle, DialogContent, DialogActions, Tooltip,
} from '@mui/material'
import {
  QrCode2, PictureAsPdf, TableView, People, PersonAddAlt1, CheckCircle, UploadFile,
  MoreVert, SwapHoriz, PersonOff, RestartAlt, History, WhatsApp as WhatsAppIcon,
  CreditCard, FactCheck,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import StatusChip from '../../components/StatusChip'
import StatCard from '../../components/StatCard'
import FilterCard from '../../components/FilterCard'
import StudentImportDialog from './StudentImportDialog'
import { useToast } from '../../hooks/useToast'
import { useFetch } from '../../hooks/useFetch'
import { studentApi, classApi, dashboardApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate, formatDateTime, initials, downloadBlob } from '../../utils/format'
import { hasPermission } from '../../utils/auth'

const STATUSES = ['', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'GRADUATED', 'TRANSFERRED', 'RADIATED']
const CYCLES = ['', 'JARDIN', 'PRIMAIRE', 'COLLEGE', 'LYCEE', 'UNIVERSITE']

const CYCLE_LABELS = {
  JARDIN: 'Jardin',
  PRIMAIRE: 'Primaire',
  COLLEGE: 'Collège',
  LYCEE: 'Lycée',
  UNIVERSITE: 'Université',
}

/**
 * Liste des élèves / étudiants : recherche avancée, pagination, exports, QR.
 */
export default function StudentsPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { success, error: toastError } = useToast()

  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState(searchParams.get('search') || '')
  const [classId, setClassId] = useState('')
  const [status, setStatus] = useState('')
  const [cycle, setCycle] = useState('')
  const [classes, setClasses] = useState([])
  const [toDelete, setToDelete] = useState(null)
  const [importOpen, setImportOpen] = useState(false)
  const [menuAnchor, setMenuAnchor] = useState(null)
  const [menuRow, setMenuRow] = useState(null)
  const [transferTarget, setTransferTarget] = useState(null)
  const [transferClass, setTransferClass] = useState('')
  const [transferReason, setTransferReason] = useState('')

  useEffect(() => {
    const s = searchParams.get('search')
    if (s !== null) {
      setSearch(s)
      setPage(0)
    }
  }, [searchParams])
  const [reasonTarget, setReasonTarget] = useState(null)
  const [reason, setReason] = useState('')
  const [historyTarget, setHistoryTarget] = useState(null)
  const [historyRows, setHistoryRows] = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)

  const { data: stats, loading: statsLoading } = useFetch(() => dashboardApi.stats())
  const canWrite = hasPermission('STUDENT_WRITE')

  const load = async () => {
    setLoading(true)
    try {
      const { data } = await studentApi.search({
        search: search || undefined,
        classId: classId || undefined,
        status: status || undefined,
        cycle: cycle || undefined,
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
    classApi.all().then((res) => setClasses(res.data.data)).catch(() => {})
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, classId, status, cycle])

  const handleExport = async (type) => {
    try {
      const res =
        type === 'pdf'
          ? await studentApi.exportPdf({ search: search || undefined, classId: classId || undefined })
          : await studentApi.exportExcel({ search: search || undefined, classId: classId || undefined })
      const ext = type === 'pdf' ? 'pdf' : 'xlsx'
      downloadBlob(res.data, `eleves.${ext}`)
      success(`Export ${type.toUpperCase()} généré`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleQr = async (student) => {
    try {
      const res = await studentApi.qr(student.id)
      downloadBlob(res.data, `carte-${student.matricule}.png`)
      success(`Carte QR de ${student.firstName} ${student.lastName} téléchargée`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await studentApi.remove(toDelete.id)
      success('Élève supprimé')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const openMenu = (event, row) => {
    setMenuAnchor(event.currentTarget)
    setMenuRow(row)
  }

  const closeMenu = () => {
    setMenuAnchor(null)
    setMenuRow(null)
  }

  const doTransfer = async () => {
    if (!transferTarget) return
    try {
      await studentApi.transfer(transferTarget.id, {
        newClassId: Number(transferClass),
        reason: transferReason || undefined,
      })
      success(`${transferTarget.firstName} transféré(e)`)
      setTransferTarget(null)
      setTransferClass('')
      setTransferReason('')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const doRadiation = async () => {
    if (!reasonTarget) return
    try {
      await studentApi.radiate(reasonTarget.id, reason || undefined)
      success(`${reasonTarget.firstName} ${reasonTarget.lastName} radié(e)`)
      setReasonTarget(null)
      setReason('')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const doReinscription = async () => {
    if (!reasonTarget) return
    try {
      await studentApi.reinscribe(reasonTarget.id, { reason: reason || undefined })
      success(`${reasonTarget.firstName} ${reasonTarget.lastName} réinscrit(e)`)
      setReasonTarget(null)
      setReason('')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const openHistory = async (student) => {
    setHistoryTarget(student)
    setHistoryRows([])
    setHistoryLoading(true)
    try {
      const res = await studentApi.history(student.id)
      setHistoryRows(res.data.data)
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setHistoryLoading(false)
    }
  }

  const actionLabel = (a) =>
    ({ TRANSFER: 'Transfert', RADIATION: 'Radiation', REINSCRIPTION: 'Réinscription' }[a] || a)

  const downloadCard = async (row) => {
    try {
      const res = await studentApi.card(row.id)
      downloadBlob(res.data, `carte-${row.matricule}.pdf`)
      success(`Carte scolaire de ${row.firstName} téléchargée`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const downloadCertificate = async (row) => {
    try {
      const res = await studentApi.attendanceCertificate(row.id)
      downloadBlob(res.data, `certificat_${row.matricule}.pdf`)
      success(`Certificat de fréquentation de ${row.firstName} téléchargé`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    {
      key: 'student',
      label: 'Élève',
      render: (row) => (
        <Box display="flex" alignItems="center" gap={1.5}>
          <Avatar src={row.photo} sx={{ width: 36, height: 36, borderRadius: '10px' }}>
            {initials(row.firstName, row.lastName)}
          </Avatar>
          <Box>
            <Typography variant="body2" fontWeight={600}>
              {row.firstName} {row.lastName}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace', fontSize: 11 }}>
              {row.matricule}
            </Typography>
          </Box>
        </Box>
      ),
    },
    { key: 'className', label: 'Classe', render: (r) => r.className || '—' },
    { key: 'gender', label: 'Genre' },
    { key: 'birthDate', label: 'Naissance', render: (r) => formatDate(r.birthDate) },
    { key: 'enrollmentDate', label: 'Inscription', render: (r) => formatDate(r.enrollmentDate) },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'parent',
      label: 'Parent',
      render: (r) =>
        r.parent ? `${r.parent.firstName} ${r.parent.lastName}` : (
          <Chip label="Non renseigné" size="small" variant="outlined" sx={{ fontSize: 11.5, fontWeight: 600 }} />
        ),
    },
    ...(canWrite
      ? [{
          key: 'parcours',
          label: 'Parcours',
          render: (r) => (
            <IconButton size="small" onClick={(e) => openMenu(e, r)}>
              <MoreVert fontSize="small" />
            </IconButton>
          ),
        }]
      : []),
    {
      key: 'documents',
      label: 'Documents',
      render: (r) => (
        <Box display="flex" gap={0.5}>
          <Tooltip title="Carte scolaire PDF">
            <IconButton size="small" color="primary" onClick={() => downloadCard(r)}>
              <CreditCard fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Certificat de fréquentation PDF">
            <IconButton size="small" color="secondary" onClick={() => downloadCertificate(r)}>
              <FactCheck fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>
      ),
    },
    {
      key: 'contact',
      label: 'Contact',
      render: (r) => {
        const phone = r.phone || r.parent?.phone
        if (!phone) return <Chip label="—" size="small" variant="outlined" sx={{ fontSize: 11.5 }} />
        const digits = phone.replace(/\D/g, '')
        const international = digits.startsWith('0') ? `237${digits.slice(1)}` : digits
        return (
          <Tooltip title={`WhatsApp ${phone}`}>
            <IconButton size="small" color="success" component="a"
              href={`https://wa.me/${international}`} target="_blank" rel="noopener noreferrer">
              <WhatsAppIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )
      },
    },
  ]

  return (
    <>
      <PageHeader
        title="Élèves"
        subtitle="Gestion des dossiers scolaires et inscriptions"
        badge={`${total} dossier(s)`}
        actionLabel={canWrite ? 'Nouvel élève' : undefined}
        onAction={() => navigate('/students/new')}
        actions={[
          { label: 'Importer (Excel)', icon: <UploadFile sx={{ fontSize: 18 }} />, onClick: () => setImportOpen(true) },
          { label: 'Export PDF', icon: <PictureAsPdf sx={{ fontSize: 18 }} />, onClick: () => handleExport('pdf') },
          { label: 'Export Excel', icon: <TableView sx={{ fontSize: 18 }} />, onClick: () => handleExport('excel') },
        ]}
      />

      {/* Statistiques rapides */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={4} className="stagger-1">
          <StatCard title="Élèves inscrits" value={stats?.totalStudents ?? 0} icon={People} color="primary" loading={statsLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={4} className="stagger-2">
          <StatCard title="Élèves actifs" value={stats?.activeStudents ?? 0} icon={CheckCircle} color="success" loading={statsLoading} />
        </Grid>
        <Grid item xs={12} sm={6} md={4} className="stagger-3">
          <StatCard title="Moyenne / classe" value={stats?.studentsPerClassAvg ?? 0} icon={PersonAddAlt1} color="info" loading={statsLoading} />
        </Grid>
      </Grid>

      {/* Filtres */}
      <FilterCard mb={3} actions={
        <Button variant="contained" size="small" startIcon={<QrCode2 sx={{ fontSize: 17 }} />} onClick={() => rows[0] && handleQr(rows[0])} disabled={!rows[0]}>
          QR premier résultat
        </Button>
      }>
        <Grid container spacing={2} alignItems="flex-end">
          <Grid item xs={12} md={4}>
            <TextField
              size="small"
              fullWidth
              label="Rechercher (nom, matricule...)"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              select
              size="small"
              fullWidth
              label="Classe"
              value={classId}
              onChange={(e) => setClassId(e.target.value)}
            >
              <MenuItem value="">Toutes</MenuItem>
              {classes.map((c) => (
                <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              select
              size="small"
              fullWidth
              label="Cycle"
              value={cycle}
              onChange={(e) => setCycle(e.target.value)}
            >
              <MenuItem value="">Tous</MenuItem>
              {CYCLES.filter((c) => c).map((c) => (
                <MenuItem key={c} value={c}>{CYCLE_LABELS[c] || c}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              select
              size="small"
              fullWidth
              label="Statut"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              {STATUSES.map((s) => (
                <MenuItem key={s} value={s}>{s === '' ? 'Tous' : s}</MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <Button variant="contained" size="medium" fullWidth onClick={load}>
              Rechercher
            </Button>
          </Grid>
        </Grid>
      </FilterCard>

      <DataTable
        title="Liste des élèves"
        columns={columns}
        rows={rows}
        loading={loading}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={(s) => { setSize(s); setPage(0) }}
        onEdit={canWrite ? (row) => navigate(`/students/${row.id}/edit`) : undefined}
        onDelete={canWrite ? setToDelete : undefined}
        onDocs={(row) => navigate(`/students/${row.id}/documents`)}
      />

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer l'élève"
        message={`Voulez-vous vraiment supprimer ${toDelete?.firstName} ${toDelete?.lastName} ? Cette action est irréversible.`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />

      <StudentImportDialog open={importOpen} onClose={() => setImportOpen(false)} onImported={load} />

      {/* Menu actions parcours */}
      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={closeMenu}>
        <MenuItem
          onClick={() => { setTransferTarget(menuRow); setTransferClass(''); setTransferReason(''); closeMenu() }}
          disabled={menuRow?.status === 'RADIATED'}
        >
          <SwapHoriz sx={{ mr: 1, fontSize: 18 }} /> Transférer vers une classe
        </MenuItem>
        <MenuItem onClick={() => { setReasonTarget(menuRow); setReason(''); closeMenu() }}>
          <PersonOff sx={{ mr: 1, fontSize: 18 }} /> Radier
        </MenuItem>
        {(menuRow?.status === 'RADIATED' || menuRow?.status === 'INACTIVE') && (
          <MenuItem onClick={() => { setReasonTarget(menuRow); setReason(''); closeMenu() }}>
            <RestartAlt sx={{ mr: 1, fontSize: 18 }} /> Réinscrire
          </MenuItem>
        )}
        <MenuItem onClick={() => { openHistory(menuRow); closeMenu() }}>
          <History sx={{ mr: 1, fontSize: 18 }} /> Historique du parcours
        </MenuItem>
        <MenuItem onClick={() => { downloadCard(menuRow); closeMenu() }}>
          <CreditCard sx={{ mr: 1, fontSize: 18 }} /> Carte scolaire
        </MenuItem>
        <MenuItem onClick={() => { downloadCertificate(menuRow); closeMenu() }}>
          <FactCheck sx={{ mr: 1, fontSize: 18 }} /> Certificat de fréquentation
        </MenuItem>
      </Menu>

      {/* Dialog transfert */}
      <Dialog open={Boolean(transferTarget)} onClose={() => setTransferTarget(null)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={800}>
          Transférer {transferTarget?.firstName} {transferTarget?.lastName}
        </DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Classe de destination" value={transferClass}
                onChange={(e) => setTransferClass(e.target.value)}>
                {classes
                  .filter((c) => c.name !== transferTarget?.className)
                  .map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth label="Motif (optionnel)" value={transferReason}
                onChange={(e) => setTransferReason(e.target.value)} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setTransferTarget(null)} color="inherit">Annuler</Button>
          <Button variant="contained" onClick={doTransfer} disabled={!transferClass}>Transférer</Button>
        </DialogActions>
      </Dialog>

      {/* Dialog radiation / réinscription */}
      <Dialog open={Boolean(reasonTarget)} onClose={() => setReasonTarget(null)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={800}>
          {reasonTarget?.status === 'RADIATED' || reasonTarget?.status === 'INACTIVE'
            ? `Réinscrire ${reasonTarget?.firstName} ${reasonTarget?.lastName}`
            : `Radier ${reasonTarget?.firstName} ${reasonTarget?.lastName}`}
        </DialogTitle>
        <DialogContent>
          <TextField fullWidth multiline minRows={2} label="Motif (optionnel)" value={reason}
            onChange={(e) => setReason(e.target.value)} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setReasonTarget(null)} color="inherit">Annuler</Button>
          <Button variant="contained" color={reasonTarget?.status === 'RADIATED' || reasonTarget?.status === 'INACTIVE' ? 'success' : 'error'}
            onClick={reasonTarget?.status === 'RADIATED' || reasonTarget?.status === 'INACTIVE' ? doReinscription : doRadiation}>
            {reasonTarget?.status === 'RADIATED' || reasonTarget?.status === 'INACTIVE' ? 'Réinscrire' : 'Radier'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog historique */}
      <Dialog open={Boolean(historyTarget)} onClose={() => setHistoryTarget(null)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={800}>
          Parcours de {historyTarget?.firstName} {historyTarget?.lastName}
        </DialogTitle>
        <DialogContent>
          {historyLoading ? (
            <Typography variant="body2" color="text.secondary">Chargement…</Typography>
          ) : historyRows.length === 0 ? (
            <Typography variant="body2" color="text.secondary">Aucun changement de parcours enregistré.</Typography>
          ) : (
            historyRows.map((h) => (
              <Box key={h.id} sx={{ display: 'flex', gap: 1.5, py: 1, borderBottom: '1px dashed', borderColor: 'divider' }}>
                <StatusChip status={h.action} />
                <Box>
                  <Typography variant="body2">
                    {h.fromClass && <b>{h.fromClass}</b>}
                    {h.fromClass && h.toClass && <span> → </span>}
                    {h.toClass && <b>{h.toClass}</b>}
                    {!h.fromClass && !h.toClass && actionLabel(h.action)}
                  </Typography>
                  {h.reason && <Typography variant="caption" color="text.secondary">Motif : {h.reason}</Typography>}
                  <Typography variant="caption" color="text.secondary" display="block">
                    {formatDateTime(h.createdAt)} — par {h.recordedBy}
                  </Typography>
                </Box>
              </Box>
            ))
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setHistoryTarget(null)} color="inherit">Fermer</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}