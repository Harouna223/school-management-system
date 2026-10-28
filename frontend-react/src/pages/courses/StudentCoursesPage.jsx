import { useCallback, useEffect, useState } from 'react'
import {
  Grid, Card, CardActionArea, CardContent, CardActions, Box, Typography,
  TextField, MenuItem, Button, Chip, Stack, Skeleton, Pagination,
} from '@mui/material'
import Videocam from '@mui/icons-material/Videocam'
import PictureAsPdf from '@mui/icons-material/PictureAsPdf'
import PlayArrow from '@mui/icons-material/PlayArrow'
import Visibility from '@mui/icons-material/Visibility'
import PageHeader from '../../components/PageHeader'
import EmptyState from '../../components/EmptyState'
import FilterCard from '../../components/FilterCard'
import CourseViewerDialog from '../../components/CourseViewerDialog'
import { humanSize } from '../../components/CourseViewerDialog'
import { useToast } from '../../hooks/useToast'
import { courseApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate } from '../../utils/format'

const PAGE_SIZE = 12

/**
 * Catalogue des cours destinés à l'élève connecté.
 *
 * Le serveur ne renvoie que les ressources PUBLIÉES visant SA classe : cet
 * écran ne filtre donc rien lui-même, il affiche ce qu'il reçoit.
 */
export default function StudentCoursesPage() {
  const { error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [type, setType] = useState('')
  const [loading, setLoading] = useState(true)
  const [viewing, setViewing] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await courseApi.search({
        search: search || undefined,
        type: type || undefined,
        page: page - 1,
        size: PAGE_SIZE,
      })
      setRows(data.data.content)
      setTotal(data.data.totalElements)
      setTotalPages(data.data.totalPages)
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setLoading(false)
    }
  }, [search, type, page, toastError])

  useEffect(() => { load() }, [load])

  return (
    <>
      <PageHeader
        title="Cours et ressources"
        subtitle="Vidéos et supports PDF publiés pour votre classe"
        badge={total > 0 ? `${total} ressource${total > 1 ? 's' : ''}` : undefined}
      />

      <FilterCard mb={3}>
        <Grid container spacing={2}>
          <Grid item xs={12} md={8}>
            <TextField
              size="small" fullWidth label="Rechercher un cours, un chapitre…"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(1) }}
            />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select size="small" fullWidth label="Type de ressource"
              value={type}
              onChange={(e) => { setType(e.target.value); setPage(1) }}
            >
              <MenuItem value="">Tous les types</MenuItem>
              <MenuItem value="VIDEO">Vidéos</MenuItem>
              <MenuItem value="PDF">Documents PDF</MenuItem>
            </TextField>
          </Grid>
        </Grid>
      </FilterCard>

      {loading && (
        <Grid container spacing={2.5}>
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Grid item xs={12} sm={6} lg={4} key={i}>
              <Skeleton variant="rounded" height={190} />
            </Grid>
          ))}
        </Grid>
      )}

      {!loading && rows.length === 0 && (
        <EmptyState
          message="Aucun cours disponible pour le moment"
          hint="Les cours publiés pour votre classe apparaîtront ici."
        />
      )}

      {!loading && rows.length > 0 && (
        <Grid container spacing={2.5}>
          {rows.map((course) => (
            <Grid item xs={12} sm={6} lg={4} key={course.id}>
              <Card
                sx={{
                  height: '100%', display: 'flex', flexDirection: 'column',
                  transition: 'transform 160ms ease, box-shadow 160ms ease',
                  '&:hover': { transform: 'translateY(-2px)', boxShadow: 6 },
                }}
              >
                <CardActionArea onClick={() => setViewing(course)} sx={{ flexGrow: 1 }}>
                  <Box
                    sx={{
                      height: 96,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      bgcolor: course.type === 'VIDEO' ? 'rgba(37,99,235,0.08)' : 'rgba(220,38,38,0.08)',
                    }}
                  >
                    {course.type === 'VIDEO'
                      ? <Videocam sx={{ fontSize: 44, color: 'primary.main' }} />
                      : <PictureAsPdf sx={{ fontSize: 44, color: 'error.main' }} />}
                  </Box>
                  <CardContent sx={{ pb: 0 }}>
                    <Stack direction="row" spacing={0.7} alignItems="center" mb={1} flexWrap="wrap">
                      <Chip
                        size="small"
                        label={course.type === 'VIDEO' ? 'Vidéo' : 'PDF'}
                        color={course.type === 'VIDEO' ? 'primary' : 'error'}
                        variant="outlined"
                      />
                      {course.chapter && <Chip size="small" label={course.chapter} variant="outlined" />}
                    </Stack>
                    <Typography variant="subtitle1" fontWeight={700} gutterBottom noWrap>
                      {course.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" noWrap>
                      {course.subjectName}
                      {course.classNames?.length ? ` · ${course.classNames.join(', ')}` : ''}
                    </Typography>
                    {course.description && (
                      <Typography
                        variant="caption" color="text.secondary" display="block" mt={1}
                        sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}
                      >
                        {course.description}
                      </Typography>
                    )}
                  </CardContent>
                </CardActionArea>
                <CardActions sx={{ px: 2, pb: 1.5, justifyContent: 'space-between' }}>
                  <Typography variant="caption" color="text.secondary">
                    {[course.authorName && `Par ${course.authorName}`, formatDate(course.publishedAt || course.createdAt)]
                      .filter(Boolean).join(' · ')}
                  </Typography>
                  <Button size="small" startIcon={<PlayArrow />} onClick={() => setViewing(course)}>
                    {course.type === 'VIDEO' ? 'Lire' : 'Ouvrir'}
                  </Button>
                </CardActions>
                <Box px={2} pb={1.5} display="flex" justifyContent="space-between">
                  <Typography variant="caption" color="text.disabled">
                    {humanSize(course.sizeBytes)}
                  </Typography>
                  <Typography variant="caption" color="text.disabled" display="flex" alignItems="center" gap={0.4}>
                    <Visibility sx={{ fontSize: 13 }} />
                    {course.downloadable ? 'Téléchargement autorisé' : 'Consultation seule'}
                  </Typography>
                </Box>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {totalPages > 1 && (
        <Box display="flex" justifyContent="center" mt={4}>
          <Pagination
            color="primary"
            page={page}
            count={totalPages}
            onChange={(_, value) => setPage(value)}
          />
        </Box>
      )}

      <CourseViewerDialog
        open={Boolean(viewing)}
        resource={viewing}
        onClose={() => setViewing(null)}
      />
    </>
  )
}
