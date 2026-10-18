import { useEffect, useState } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, Typography,
  CircularProgress, Alert, Chip, Stack, IconButton,
} from '@mui/material'
import { Close, Download, PictureAsPdf, Videocam } from '@mui/icons-material'
import {
  courseApi, courseDownloadUrl, coursePreviewUrl, courseStreamUrl,
} from '../api/endpoints'
import { extractError } from '../api/axios'
import { formatDate } from '../utils/format'

function humanSize(bytes) {
  if (!bytes && bytes !== 0) return '—'
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} Ko`
  return `${bytes} o`
}

/**
 * Lecture d'un cours : lecteur vidéo intégré (HTTP Range, pas d'auto-play avec
 * son) ou visionneuse PDF en ligne.
 *
 * Le fichier n'est jamais exposé par une URL publique : l'ouverture du dialogue
 * demande un jeton court au serveur, puis construit l'URL de lecture.
 */
export default function CourseViewerDialog({ open, resource, onClose }) {
  const [token, setToken] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [downloading, setDownloading] = useState(false)

  useEffect(() => {
    if (!open || !resource) {
      setToken(null)
      setError('')
      return undefined
    }
    let cancelled = false
    setLoading(true)
    setError('')
    setToken(null)
    const purpose = resource.type === 'VIDEO' ? 'stream' : 'preview'
    courseApi.token(resource.id, purpose)
      .then(({ data }) => {
        if (!cancelled) setToken(data.data.token)
      })
      .catch((err) => {
        if (!cancelled) setError(extractError(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => { cancelled = true }
  }, [open, resource])

  const handleDownload = async () => {
    if (!resource) return
    setDownloading(true)
    setError('')
    try {
      const { data } = await courseApi.token(resource.id, 'download')
      const link = document.createElement('a')
      link.href = courseDownloadUrl(resource.id, data.data.token)
      link.download = resource.originalFilename || `cours-${resource.id}`
      document.body.appendChild(link)
      link.click()
      link.remove()
    } catch (err) {
      setError(extractError(err))
    } finally {
      setDownloading(false)
    }
  }

  const isVideo = resource?.type === 'VIDEO'
  const src = token && resource
    ? (isVideo ? courseStreamUrl(resource.id, token) : coursePreviewUrl(resource.id, token))
    : null

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ pr: 6 }}>
        <Stack direction="row" spacing={1} alignItems="center">
          {isVideo ? <Videocam color="primary" /> : <PictureAsPdf color="error" />}
          <Box minWidth={0}>
            <Typography variant="h6" noWrap fontWeight={700}>{resource?.title || 'Cours'}</Typography>
            <Typography variant="caption" color="text.secondary" noWrap display="block">
              {[resource?.subjectName, resource?.chapter, resource?.authorName && `Par ${resource.authorName}`]
                .filter(Boolean).join(' · ')}
            </Typography>
          </Box>
        </Stack>
        <IconButton onClick={onClose} sx={{ position: 'absolute', right: 8, top: 8 }} aria-label="Fermer">
          <Close />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers sx={{ bgcolor: 'background.default' }}>
        {loading && (
          <Box display="flex" justifyContent="center" py={8}><CircularProgress /></Box>
        )}

        {!loading && error && <Alert severity="error">{error}</Alert>}

        {!loading && !error && src && isVideo && (
          <Box
            component="video"
            src={src}
            controls
            preload="metadata"
            playsInline
            sx={{ width: '100%', maxHeight: '68vh', bgcolor: 'black', borderRadius: 2 }}
          >
            Votre navigateur ne prend pas en charge la lecture vidéo intégrée.
          </Box>
        )}

        {!loading && !error && src && !isVideo && (
          <Box
            component="iframe"
            src={src}
            title={resource?.title || 'Document PDF'}
            sx={{ width: '100%', height: '68vh', border: 0, borderRadius: 2, bgcolor: 'white' }}
          />
        )}

        {!loading && !error && resource?.description && (
          <Box mt={2}>
            <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-line' }}>
              {resource.description}
            </Typography>
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2, flexWrap: 'wrap', gap: 1 }}>
        <Stack direction="row" spacing={1} sx={{ mr: 'auto' }} alignItems="center">
          <Chip size="small" label={isVideo ? 'Vidéo' : 'PDF'} color={isVideo ? 'primary' : 'error'} variant="outlined" />
          <Typography variant="caption" color="text.secondary">
            {humanSize(resource?.sizeBytes)}
            {resource?.publishedAt ? ` · publié le ${formatDate(resource.publishedAt)}` : ''}
          </Typography>
        </Stack>
        {resource?.hasFile && resource?.downloadable && (
          <Button
            variant="outlined"
            startIcon={downloading ? <CircularProgress size={16} /> : <Download />}
            onClick={handleDownload}
            disabled={downloading}
          >
            Télécharger
          </Button>
        )}
        {resource?.hasFile && !resource?.downloadable && (
          <Typography variant="caption" color="text.secondary">
            Téléchargement non autorisé
          </Typography>
        )}
        <Button onClick={onClose}>Fermer</Button>
      </DialogActions>
    </Dialog>
  )
}

export { humanSize }
