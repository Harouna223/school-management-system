import { useState, useEffect } from 'react'
import { Grid, TextField, MenuItem, Button, Box, Chip, Dialog, DialogTitle, DialogContent, DialogActions, Typography } from '@mui/material'
import { AdminPanelSettings, Key, Search, LockOpen, Lock } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { userApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate, initials } from '../../utils/format'

/**
 * Administration des utilisateurs : rôles, activation, mots de passe.
 */
export default function UsersPage() {
  const { success, error: toastError } = useToast()
  const [rows, setRows] = useState([])
  const [roles, setRoles] = useState([])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [search, setSearch] = useState('')
  const [rolesDialog, setRolesDialog] = useState(null)
  const [selectedRoles, setSelectedRoles] = useState([])
  const [passwordDialog, setPasswordDialog] = useState(null)
  const [newPassword, setNewPassword] = useState('')

  const load = async () => {
    try {
      const { data } = await userApi.search({ search: search || undefined, page, size })
      setRows(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    userApi.roles().then((r) => setRoles(r.data.data)).catch(() => {})
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size])

  const openRoles = (user) => {
    setRolesDialog(user)
    setSelectedRoles(user.roles)
  }

  const saveRoles = async () => {
    try {
      await userApi.updateRoles(rolesDialog.id, selectedRoles)
      success('Rôles mis à jour')
      setRolesDialog(null)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const toggleEnabled = async (user) => {
    try {
      await userApi.toggleEnabled(user.id, !user.enabled)
      success(user.enabled ? 'Compte désactivé' : 'Compte activé')
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const isLocked = (r) => r.lockedUntil && new Date(r.lockedUntil) > new Date()

  const unlock = async (user) => {
    try {
      await userApi.unlock(user.id)
      success(`Compte ${user.username} déverrouillé`)
      load()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const resetPassword = async () => {
    try {
      await userApi.resetPassword(passwordDialog.id, newPassword)
      success('Mot de passe réinitialisé')
      setPasswordDialog(null)
      setNewPassword('')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const columns = [
    {
      key: 'user',
      label: 'Utilisateur',
      render: (r) => (
        <Box display="flex" alignItems="center" gap={1.5}>
          <Box sx={{ width: 34, height: 34, borderRadius: '50%', bgcolor: 'primary.main', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700 }}>
            {initials(r.firstName, r.lastName)}
          </Box>
          <Box>
            <Typography variant="body2" fontWeight={600}>{r.firstName} {r.lastName}</Typography>
            <Typography variant="caption" color="text.secondary">@{r.username}</Typography>
          </Box>
        </Box>
      ),
    },
    { key: 'email', label: 'Email' },
    { key: 'roles', label: 'Rôles', render: (r) => r.roles?.map((role) => <Chip key={role} size="small" label={role} sx={{ mr: 0.5 }} />) },
    { key: 'enabled', label: 'Statut', render: (r) => (
      <Box display="flex" gap={0.5} flexWrap="wrap">
        <Chip size="small" color={r.enabled ? 'success' : 'error'} label={r.enabled ? 'Actif' : 'Désactivé'} />
        {isLocked(r) && <Chip size="small" color="warning" icon={<Lock sx={{ fontSize: 14 }} />} label="Verrouillé" />}
      </Box>
    ) },
    { key: 'lastLogin', label: 'Dernière connexion', render: (r) => r.lastLogin ? formatDate(r.lastLogin) : '—' },
    { key: 'createdAt', label: 'Créé le', render: (r) => formatDate(r.createdAt) },
    {
      key: 'actions',
      label: 'Actions',
      render: (r) => (
        <Box display="flex" gap={1}>
          <Button size="small" startIcon={<AdminPanelSettings />} onClick={() => openRoles(r)}>Rôles</Button>
          <Button size="small" startIcon={<Key />} onClick={() => setPasswordDialog(r)}>MDP</Button>
          {isLocked(r) && (
            <Button size="small" color="warning" startIcon={<LockOpen />} onClick={() => unlock(r)}>Déverrouiller</Button>
          )}
          <Button size="small" color={r.enabled ? 'error' : 'success'} onClick={() => toggleEnabled(r)}>
            {r.enabled ? 'Désactiver' : 'Activer'}
          </Button>
        </Box>
      ),
    },
  ]

  return (
    <>
      <PageHeader title="Utilisateurs & Rôles" subtitle="Gestion des comptes et permissions" badge={total ? `${total} compte(s)` : undefined} />

      <FilterCard mb={3}>
        <Grid container spacing={2} alignItems="flex-end">
          <Grid item xs={12} md={4}>
            <TextField size="small" fullWidth label="Rechercher..." value={search}
              onChange={(e) => setSearch(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && load()} />
          </Grid>
          <Grid item xs={12} md={3}>
            <Button variant="contained" size="medium" fullWidth startIcon={<Search />} onClick={load}>
              Rechercher
            </Button>
          </Grid>
        </Grid>
      </FilterCard>

      <DataTable
        title="Comptes utilisateurs"
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

      {/* Dialog rôles */}
      <Dialog open={Boolean(rolesDialog)} onClose={() => setRolesDialog(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Rôles de {rolesDialog?.firstName} {rolesDialog?.lastName}</DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={1} mt={1}>
            {roles.map((role) => (
              <Chip
                key={role.id}
                label={role.name}
                color={selectedRoles.includes(role.name) ? 'primary' : 'default'}
                onClick={() => setSelectedRoles((prev) =>
                  prev.includes(role.name)
                    ? prev.filter((r) => r !== role.name)
                    : [...prev, role.name],
                )}
                sx={{ width: 'fit-content' }}
              />
            ))}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRolesDialog(null)}>Annuler</Button>
          <Button variant="contained" onClick={saveRoles}>Enregistrer</Button>
        </DialogActions>
      </Dialog>

      {/* Dialog mot de passe */}
      <Dialog open={Boolean(passwordDialog)} onClose={() => setPasswordDialog(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Réinitialiser le mot de passe</DialogTitle>
        <DialogContent>
          <TextField fullWidth type="password" label="Nouveau mot de passe"
            value={newPassword} onChange={(e) => setNewPassword(e.target.value)} sx={{ mt: 1 }}
            helperText="8 caractères minimum, avec des lettres et des chiffres"
            error={newPassword.length > 0 && !(newPassword.length >= 8 && /[A-Za-z]/.test(newPassword) && /[0-9]/.test(newPassword))}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPasswordDialog(null)}>Annuler</Button>
          <Button variant="contained" onClick={resetPassword}
            disabled={!(newPassword.length >= 8 && /[A-Za-z]/.test(newPassword) && /[0-9]/.test(newPassword))}>
            Réinitialiser
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}