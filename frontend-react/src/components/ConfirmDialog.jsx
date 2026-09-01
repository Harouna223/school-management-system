import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Box, Typography } from '@mui/material'
import { WarningAmber, DeleteOutline } from '@mui/icons-material'

/**
 * Dialogue de confirmation moderne avant une action destructive.
 */
export default function ConfirmDialog({ open, title = 'Confirmation', message, onConfirm, onClose, confirmLabel = 'Confirmer' }) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pb: 1 }}>
        <Box
          sx={{
            width: 40,
            height: 40,
            borderRadius: '14px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'error.light',
            color: 'error.main',
          }}
        >
          <WarningAmber sx={{ fontSize: 22 }} />
        </Box>
        <Typography variant="subtitle1" fontWeight={700}>{title}</Typography>
      </DialogTitle>
      <DialogContent sx={{ pt: 1 }}>
        <DialogContentText color="text.secondary">{message}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit">
          Annuler
        </Button>
        <Button
          onClick={onConfirm}
          color="error"
          variant="contained"
          startIcon={<DeleteOutline />}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}