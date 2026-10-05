import { useCallback, useMemo } from 'react'
import { useDispatch } from 'react-redux'
import { showToast, removeToast } from '../redux/slices/toastSlice'

/**
 * Hook utilitaire pour afficher des toasts depuis n'importe quel composant.
 *
 * Les fonctions retournées sont MÉMOÏSÉES : elles servent de dépendances dans
 * les `useCallback` / `useEffect` des pages. Sans cette stabilité, l'identité
 * changerait à chaque rendu et provoquerait des rechargements de données en
 * boucle.
 */
export function useToast() {
  const dispatch = useDispatch()

  const notify = useCallback((message, type = 'success') => {
    const id = Date.now() + Math.random()
    dispatch(showToast({ message, type, id }))
    setTimeout(() => dispatch(removeToast(id)), 4500)
  }, [dispatch])

  return useMemo(() => ({
    success: (msg) => notify(msg, 'success'),
    error: (msg) => notify(msg, 'error'),
    warning: (msg) => notify(msg, 'warning'),
    info: (msg) => notify(msg, 'info'),
  }), [notify])
}