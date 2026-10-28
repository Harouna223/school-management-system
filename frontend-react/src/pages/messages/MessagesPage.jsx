import { useState, useEffect } from 'react'
import { Grid, TextField, MenuItem, Button, Typography, Chip, Box, Divider, Dialog, DialogTitle, DialogContent, DialogActions, Tabs, Tab } from '@mui/material'
import Send from '@mui/icons-material/Send'
import MarkEmailRead from '@mui/icons-material/MarkEmailRead'
import Visibility from '@mui/icons-material/Visibility'
import Reply from '@mui/icons-material/Reply'
import Delete from '@mui/icons-material/Delete'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import { useToast } from '../../hooks/useToast'
import { communicationApi, userApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDateTime } from '../../utils/format'

/**
 * Messagerie interne : boîte de réception, messages envoyés, rédaction,
 * consultation d'une conversation, réponse et suppression.
 *
 * Ouvrir un message le marque lu côté serveur (l'expéditeur voit alors « Lu »).
 */
export default function MessagesPage() {
  const { success, error: toastError } = useToast()
  const [tab, setTab] = useState('inbox')
  const [rows, setRows] = useState([])
  const [users, setUsers] = useState([])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [composeOpen, setComposeOpen] = useState(false)
  const [form, setForm] = useState({ recipientId: '', subject: '', content: '' })
  const [detail, setDetail] = useState(null)
  const [reply, setReply] = useState('')
  const [replyBusy, setReplyBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(null)

  const load = async () => {
    try {
      const { data } = tab === 'inbox'
        ? await communicationApi.inbox({ page, size })
        : await communicationApi.sent({ page, size })
      setRows(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    userApi.search({ page: 0, size: 100 }).then((r) => setUsers(r.data.data.content)).catch(() => {})
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, page, size])

  const send = async () => {
    try {
      await communicationApi.send({
        recipientId: Number(form.recipientId),
        subject: form.subject,
        content: form.content,
      })
      success('Message envoyé')
      setComposeOpen(false)
      setForm({ recipientId: '', subject: '', content: '' })
      if (tab === 'sent') load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  /** Ouvre la conversation : le serveur marque le message lu s'il est reçu. */
  const openMessage = async (row) => {
    try {
      const { data } = await communicationApi.open(row.id)
      setDetail(data.data)
      setReply('')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const sendReply = async () => {
    if (!reply.trim()) return
    setReplyBusy(true)
    try {
      await communicationApi.reply(detail.id, reply.trim())
      success('Réponse envoyée')
      setDetail(null)
      setReply('')
      load()
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setReplyBusy(false)
    }
  }

  const removeMessage = async () => {
    const id = confirmDelete
    setConfirmDelete(null)
    try {
      await communicationApi.remove(id)
      success('Message supprimé')
      if (detail?.id === id) setDetail(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const markAllRead = async () => {
    try {
      await communicationApi.markMessagesRead()
      success('Messages marqués comme lus')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    { key: 'subject', label: 'Objet', render: (r) => <b>{r.subject}</b> },
    { key: 'senderName', label: 'De' },
    { key: 'recipientName', label: 'À' },
    { key: 'content', label: 'Aperçu', render: (r) => <Typography variant="body2" noWrap sx={{ maxWidth: 280 }}>{r.content}</Typography> },
    { key: 'createdAt', label: 'Date', render: (r) => formatDateTime(r.createdAt) },
    { key: 'read', label: 'Lu', render: (r) => (r.read ? <Chip size="small" label="Lu" /> : <Chip size="small" color="primary" label="Non lu" />) },
    {
      key: 'actions',
      label: 'Actions',
      render: (r) => (
        <Box display="flex" gap={0.5}>
          <Button size="small" startIcon={<Visibility />} onClick={() => openMessage(r)}>Ouvrir</Button>
          <Button size="small" color="error" onClick={() => setConfirmDelete(r.id)} title="Supprimer">
            <Delete fontSize="small" />
          </Button>
        </Box>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Messagerie"
        subtitle="Communication interne entre utilisateurs"
        actionLabel="Nouveau message"
        onAction={() => setComposeOpen(true)}
        actions={[{ label: 'Tout marquer comme lu', icon: <MarkEmailRead />, onClick: markAllRead }]}
      />

      <Tabs value={tab} onChange={(_, v) => { setTab(v); setPage(0) }} sx={{ mb: 3 }}>
        <Tab label="Réception" value="inbox" />
        <Tab label="Envoyés" value="sent" />
      </Tabs>

      <DataTable
        columns={columns}
        rows={rows}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={(s) => { setSize(s); setPage(0) }}
        searchable={false}
        actions={false}
      />

      {/* Rédaction */}
      <Dialog open={composeOpen} onClose={() => setComposeOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Nouveau message</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Destinataire" value={form.recipientId}
                onChange={(e) => setForm({ ...form, recipientId: e.target.value })}>
                {users.map((u) => <MenuItem key={u.id} value={String(u.id)}>{u.firstName} {u.lastName} ({u.username})</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth label="Objet" value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth multiline rows={4} label="Contenu" value={form.content}
                onChange={(e) => setForm({ ...form, content: e.target.value })} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setComposeOpen(false)}>Annuler</Button>
          <Button variant="contained" startIcon={<Send />} onClick={send} disabled={!form.recipientId || !form.subject || !form.content}>
            Envoyer
          </Button>
        </DialogActions>
      </Dialog>

      {/* Conversation */}
      <Dialog open={!!detail} onClose={() => setDetail(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{detail?.subject}</DialogTitle>
        <DialogContent dividers>
          {detail && (
            <>
              <Typography variant="caption" color="text.secondary">
                De {detail.senderName} — à {detail.recipientName} — {formatDateTime(detail.createdAt)}
              </Typography>
              <Typography variant="body2" sx={{ mt: 2, whiteSpace: 'pre-line' }}>{detail.content}</Typography>
              <Divider sx={{ my: 2 }} />
              <TextField
                fullWidth multiline minRows={3} label="Votre réponse"
                value={reply} onChange={(e) => setReply(e.target.value)}
                helperText="L'objet devient « Re: … » et la réponse part vers l'autre participant."
              />
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button color="error" onClick={() => setConfirmDelete(detail?.id)}>Supprimer</Button>
          <Box flexGrow={1} />
          <Button onClick={() => setDetail(null)}>Fermer</Button>
          <Button variant="contained" startIcon={<Reply />} onClick={sendReply} disabled={!reply.trim() || replyBusy}>
            Répondre
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmDelete != null}
        title="Supprimer le message"
        message="Le message sera supprimé pour vous comme pour l'autre participant. Continuer ?"
        onConfirm={removeMessage}
        onClose={() => setConfirmDelete(null)}
      />
    </>
  )
}
