import { useEffect, useState } from 'react'
import { Avatar } from '@mui/material'

/**
 * Avatar dont l'image source est protégée par authentification.
 *
 * Depuis la fermeture de l'accès public aux fichiers téléversés, les photos
 * (élèves, enseignants, logo…) sont servies derrière le JWT (/uploads). Le
 * navigateur n'envoie PAS de jeton sur une balise <img> : on charge donc
 * l'image via `fetch` avec le header Authorization, puis on affiche une
 * objectURL. En cas d'échec (photo absente, session expirée), le fallback
 * (initiales fournies en children) s'affiche comme avant.
 */
export default function FileAvatar({ src, children, ...rest }) {
  const [objectUrl, setObjectUrl] = useState(null)

  useEffect(() => {
    let cancelled = false
    // Sources locales (objectURL de preview, data:…) : aucune protection requise.
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

  return (
    <Avatar src={objectUrl || undefined} {...rest}>
      {children}
    </Avatar>
  )
}