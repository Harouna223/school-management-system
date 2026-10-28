import { useCallback, useEffect, useState } from 'react'
import {
  Grid, TextField, MenuItem, Button, Box, Chip, Tooltip, Stack, Typography,
} from '@mui/material'
import Archive from '@mui/icons-material/Archive'
import Publish from '@mui/icons-material/Publish'
import Unpublished from '@mui/icons-material/Unpublished'
import Videocam from '@mui/icons-material/Videocam'
import PictureAsPdf from '@mui/icons-material/PictureAsPdf'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import FilterCard from '../../components/FilterCard'
import StatusChip from '../../components/StatusChip'
import CourseViewerDialog from '../../components/CourseViewerDialog'
import CourseFormDialog from './CourseFormDialog'
import { humanSize } from '../../components/CourseViewerDialog'
import { useToast } from '../../hooks/useToast'
import { courseApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate } from '../../utils/format'

/**
 * Gestion des cours vidéo et PDF (administration et enseignants).
 *
 * L'enseignant ne voit ici que ce que le serveur lui renvoie : le catalogue
 * publié, plus ses propres brouillons. Les actions de gestion sont donc
 * toujours doublées d'un contrôle serveur.
 */
export default function CoursesPage() {
  const { success, error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [type, setType] = useState('')
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(false)

  const [options, setOptions] = useState(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [viewing, setViewing] = useState(null)
  const [toDelete, setToDelete] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await courseApi.search({
        search: search || undefined,
        type: type || undefined,
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
  }, [search, type, status, page, size, toastError])

  useEffect(() => { load() }, [load])

  useEffect(() => {
    courseApi.options()
      .then(({ data }) => setOptions(data.data))
      .catch(() => setOptions({ restricted: false, classes: [], subjects: [], assignments: [] }))
  }, [])

  const changeStatus = async (row, nextStatus) => {
    try {
      await courseApi.changeStatus(row.id, nextStatus)
      success(nextStatus === 'PUBLISHED' ? 'Cours publié' : nextStatus === 'ARCHIVED' ? 'Cours archivé' : 'Cours repassé en brouillon')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await courseApi.remove(toDelete.id)
      success('Cours supprimé')
      setToDelete(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    {
      key: 'title',
      label: 'Cours',
      render: (r) => (
        <Box display="flex" alignItems="center" gap={1}>
          {r.type === 'VIDEO'
            ? <Videocam fontSize="small" color="primary" />
            : <PictureAsPdf fontSize="small" color="error" />}
          <Box minWidth={0}>
            <Typography variant="body2" fontWeight={700} noWrap>{r.title}</Typography>
            <Typography variant="caption" color="text.secondary" noWrap display="block">
              {[r.chapter, r.subjectName].filter(Boolean).join(' · ')}
            </Typography>
          </Box>
        </Box>
      ),
    },
    { key: 'type', label: 'Type', render: (r) => <Chip size="small" label={r.type} variant="outlined" /> },
    {
      key: 'classNames',
      label: 'Classes',
      render: (r) => (
        <Typography variant="caption" color="text.secondary">
          {(r.classNames || []).join(', ') || '—'}
        </Typography>
      ),
    },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'sizeBytes',
      label: 'Fichier',
      render: (r) => (r.hasFile
        ? <Typography variant="caption">{humanSize(r.sizeBytes)}</Typography>
        : <Typography variant="caption" color="warning.main">Aucun fichier</Typography>),
    },
    { key: 'authorName', label: 'Auteur', render: (r) => r.authorName || '—' },
    { key: 'createdAt', label: 'Créé le', render: (r) => formatDate(r.createdAt) },
    {
      key: 'publication',
      label: 'Publication',
      render: (r) => (
        <Stack direction="row" spacing={0.5}>
          {r.status === 'PUBLISHED' ? (
            <Tooltip title="Repasser en brouillon">
              <span>
                <Button size="small" variant="outlined" startIcon={<Unpublished />}
                  onClick={() => changeStatus(r, 'DRAFT')}>
                  Dépublier
                </Button>
              </span>
            </Tooltip>
          ) : (
            <Tooltip title={r.hasFile ? 'Publier' : 'Téléversez d’abord un fichier'}>
              <span>
                <Button size="small" variant="contained" startIcon={<Publish />}
                  disabled={!r.hasFile}
                  onClick={() => changeStatus(r, 'PUBLISHED')}>
                  Publier
                </Button>
              </span>
            </Tooltip>
          )}
          {r.status !== 'ARCHIVED' && (
            <Tooltip title="Archiver">
              <Button size="small" color="warning" onClick={() => changeStatus(r, 'ARCHIVED')} aria-label="Archiver">
                <Archive fontSize="small" />
              </Button>
            </Tooltip>
          )}
        </Stack>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Cours et ressources"
        subtitle="Publiez des vidéos et des supports PDF à destination de vos classes"
        actionLabel="Nouveau cours"
        onAction={() => { setEditing(null); setFormOpen(true) }}
      />

      <FilterCard mb={3}>
        <Grid container spacing={2}>
          <Grid item xs={12} md={5}>
            <TextField
              size="small" fullWidth label="Rechercher un cours…"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0) }}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <TextField
              select size="small" fullWidth label="Type"
              value={type}
              onChange={(e) => { setType(e.target.value); setPage(0) }}
            >
              <MenuItem value="">Tous</MenuItem>
              <MenuItem value="VIDEO">Vidéo</MenuItem>
              <MenuItem value="PDF">PDF</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={6} md={4}>
            <TextField
              select size="small" fullWidth label="Statut"
              value={status}
              onChange={(e) => { setStatus(e.target.value); setPage(0) }}
            >
              <MenuItem value="">Tous</MenuItem>
              <MenuItem value="DRAFT">Brouillon</MenuItem>
              <MenuItem value="PUBLISHED">Publié</MenuItem>
              <MenuItem value="ARCHIVED">Archivé</MenuItem>
            </TextField>
          </Grid>
        </Grid>
      </FilterCard>

      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={(s) => { setSize(s); setPage(0) }}
        searchable={false}
        onView={(row) => setViewing(row)}
        onEdit={(row) => { setEditing(row); setFormOpen(true) }}
        onDelete={(row) => setToDelete(row)}
      />

      <CourseFormDialog
        open={formOpen}
        initial={editing}
        options={options}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false)
          success(editing ? 'Cours modifié' : 'Cours créé')
          load()
        }}
      />

      <CourseViewerDialog
        open={Boolean(viewing)}
        resource={viewing}
        onClose={() => setViewing(null)}
      />

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer le cours"
        message={`Supprimer définitivement « ${toDelete?.title} » et son fichier ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}
