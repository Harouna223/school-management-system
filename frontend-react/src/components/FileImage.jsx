import { useEffect, useState } from 'react'
import { Box } from '@mui/material'

/**
 * Image (MUI Box component="img") dont la source est protégée par authentification.
 *
 * Même mécanisme que `FileAvatar` : chargement via `fetch` avec le JWT car un
 * `<img>` classique n'enverrait pas l'en-tête Authorization sur /uploads/**.
 * Accepte les mêmes props que `<img>` (alt, sx, draggable…) et rend un `<img>`
 * de manière minimale.
 */
export default function FileImage({ src, alt = '', sx, ...rest }) {
  const [objectUrl, setObjectUrl] = useState(null)

  useEffect(() => {
    let cancelled = false
    if (!src || src.startsWith('blob:') || src.startsWith('data:')) {
      setObjectUrl(src || null)
      return () => { cancelled = true }
    }
    const token = localStorage.getItem('accessToken')
    if (!token) {
      setObjectUrl(null)
      return () => { cancelled = true }
    }
    fetch(src, { headers: { Authorization: `Bearer ${token}` } })
      .then((res) => (res.ok ? res.blob() : Promise.reject(new Error(`HTTP ${res.status}`))))
      .then((blob) => { if (!cancelled) setObjectUrl(URL.createObjectURL(blob)) })
      .catch(() => { if (!cancelled) setObjectUrl(null) })
    return () => { cancelled = true }
  }, [src])

  return <Box component="img" src={objectUrl || undefined} alt={alt} sx={sx} {...rest} />
}