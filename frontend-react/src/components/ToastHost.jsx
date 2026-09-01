import { useSelector } from 'react-redux'
import { Snackbar, Alert } from '@mui/material'

/**
 * Hôte global des toasts (Snackbars) alimenté par le store Redux.
 */
export default function ToastHost() {
  const items = useSelector((state) => state.toast.items)

  return (
    <>
      {items.map((toast) => (
        <Snackbar
          key={toast.id}
          open
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          autoHideDuration={4500}
        >
          <Alert severity={toast.type} variant="filled" sx={{ width: '100%' }}>
            {toast.message}
          </Alert>
        </Snackbar>
      ))}
    </>
  )
}