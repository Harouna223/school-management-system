import { useState, useEffect } from 'react'
import { Grid, TextField, MenuItem, Button, Card, CardContent, Typography, Box, Chip, Dialog, DialogTitle, DialogContent, DialogActions, Tabs, Tab } from '@mui/material'
import { Send } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import { useToast } from '../../hooks/useToast'
import { communicationApi, userApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDateTime } from '../../utils/format'

/**
 * Messagerie interne : boîte de réception, messages envoyés, rédaction.
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
    userApi.search({ page: 0, size: 500 }).then((r) => setUsers(r.data.data.content)).catch(() => {})
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

  const columns = [
    { key: 'subject', label: 'Objet', render: (r) => <b>{r.subject}</b> },
    { key: 'senderName', label: 'De' },
    { key: 'recipientName', label: 'À' },
    { key: 'content', label: 'Aperçu', render: (r) => <Typography variant="body2" noWrap sx={{ maxWidth: 300 }}>{r.content}</Typography> },
    { key: 'createdAt', label: 'Date', render: (r) => formatDateTime(r.createdAt) },
    { key: 'read', label: 'Lu', render: (r) => (r.read ? <Chip size="small" label="Lu" /> : <Chip size="small" color="primary" label="Non lu" />) },
  ]

  return (
    <>
      <PageHeader title="Messagerie" subtitle="Communication interne entre utilisateurs" actionLabel="Nouveau message" onAction={() => setComposeOpen(true)} />

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
    </>
  )
}