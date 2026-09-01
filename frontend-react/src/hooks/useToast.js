import { useDispatch } from 'react-redux'
import { showToast, removeToast } from '../redux/slices/toastSlice'

/**
 * Hook utilitaire pour afficher des toasts depuis n'importe quel composant.
 */
export function useToast() {
  const dispatch = useDispatch()

  const notify = (message, type = 'success') => {
    const id = Date.now() + Math.random()
    dispatch(showToast({ message, type, id }))
    setTimeout(() => dispatch(removeToast(id)), 4500)
  }

  return {
    success: (msg) => notify(msg, 'success'),
    error: (msg) => notify(msg, 'error'),
    warning: (msg) => notify(msg, 'warning'),
    info: (msg) => notify(msg, 'info'),
  }
}