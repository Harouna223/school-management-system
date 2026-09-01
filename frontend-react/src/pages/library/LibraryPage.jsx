import { useState, useEffect } from 'react'
import { Grid, TextField, MenuItem, Button, Dialog, DialogTitle, DialogContent, DialogActions, Chip, Box, Typography, Card, CardContent, Tabs, Tab } from '@mui/material'
import PageHeader from '../../components/PageHeader'
import DataTable from '../../components/DataTable'
import ConfirmDialog from '../../components/ConfirmDialog'
import FilterCard from '../../components/FilterCard'
import { useToast } from '../../hooks/useToast'
import { libraryApi, studentApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate } from '../../utils/format'
import StatusChip from '../../components/StatusChip'

/**
 * Module bibliothèque : livres, emprunts, retours.
 */
export default function LibraryPage() {
  const { success, error: toastError } = useToast()
  const [tab, setTab] = useState('books')
  const [books, setBooks] = useState([])
  const [borrowings, setBorrowings] = useState([])
  const [students, setStudents] = useState([])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [search, setSearch] = useState('')
  const [bookDialog, setBookDialog] = useState(false)
  const [borrowDialog, setBorrowDialog] = useState(false)
  const [toDelete, setToDelete] = useState(null)
  const [bookForm, setBookForm] = useState({ title: '', author: '', isbn: '', category: '', quantity: 1, publisher: '', publicationYear: '' })
  const [borrowForm, setBorrowForm] = useState({ bookId: '', studentId: '', borrowDate: '', dueDate: '' })
  const [allBooks, setAllBooks] = useState([])

  useEffect(() => {
    if (borrowDialog) {
      libraryApi.books({ page: 0, size: 10000 })
        .then((r) => setAllBooks(r.data.data.content || []))
        .catch(() => {})
    }
  }, [borrowDialog])

  const loadBooks = async () => {
    try {
      const { data } = await libraryApi.books({ search: search || undefined, page, size })
      setBooks(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const loadBorrowings = async () => {
    try {
      const { data } = await libraryApi.borrowings({ page, size })
      setBorrowings(data.data.content)
      setTotal(data.data.totalElements)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    studentApi.search({ page: 0, size: 500 }).then((r) => setStudents(r.data.data.content)).catch(() => {})
  }, [])

  useEffect(() => {
    if (tab === 'books') loadBooks()
    else loadBorrowings()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, page, size, search])

  const createBook = async () => {
    try {
      await libraryApi.createBook({ ...bookForm, quantity: Number(bookForm.quantity), publicationYear: bookForm.publicationYear ? Number(bookForm.publicationYear) : undefined })
      success('Livre ajouté à l\'inventaire')
      setBookDialog(false)
      setBookForm({ title: '', author: '', isbn: '', category: '', quantity: 1, publisher: '', publicationYear: '' })
      loadBooks()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const borrow = async () => {
    try {
      await libraryApi.borrow({
        bookId: Number(borrowForm.bookId),
        studentId: Number(borrowForm.studentId),
        borrowDate: borrowForm.borrowDate,
        dueDate: borrowForm.dueDate,
      })
      success('Emprunt enregistré')
      setBorrowDialog(false)
      setBorrowForm({ bookId: '', studentId: '', borrowDate: '', dueDate: '' })
      loadBorrowings()
      loadBooks()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const returnBook = async (b) => {
    try {
      await libraryApi.returnBook(b.id)
      success('Retour enregistré')
      loadBorrowings()
      loadBooks()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const markOverdue = async () => {
    try {
      const { data } = await libraryApi.markOverdue()
      success(`${data.data} emprunt(s) marqué(s) en retard`)
      loadBorrowings()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleDelete = async () => {
    try {
      await libraryApi.deleteBook(toDelete.id)
      success('Livre supprimé')
      setToDelete(null)
      loadBooks()
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const bookColumns = [
    { key: 'title', label: 'Titre', render: (r) => <b>{r.title}</b> },
    { key: 'author', label: 'Auteur', render: (r) => r.author || '—' },
    { key: 'isbn', label: 'ISBN', render: (r) => r.isbn || '—' },
    { key: 'category', label: 'Catégorie' },
    { key: 'quantity', label: 'Exemplaires' },
    { key: 'available', label: 'Disponibles', render: (r) => (
      <Chip size="small" color={r.available > 0 ? 'success' : 'error'} label={r.available} />
    ) },
  ]

  const borrowColumns = [
    { key: 'bookTitle', label: 'Livre', render: (r) => <b>{r.bookTitle}</b> },
    { key: 'student', label: 'Élève', render: (r) => `${r.studentName} (${r.matricule})` },
    { key: 'borrowDate', label: 'Emprunt', render: (r) => formatDate(r.borrowDate) },
    { key: 'dueDate', label: 'Retour prévu', render: (r) => formatDate(r.dueDate) },
    { key: 'status', label: 'Statut', render: (r) => <StatusChip status={r.status} /> },
    {
      key: 'action',
      label: 'Action',
      render: (r) =>
        r.status !== 'RETURNED' ? (
          <Button size="small" variant="outlined" onClick={() => returnBook(r)}>Retourner</Button>
        ) : (
          <Typography variant="caption" color="text.secondary">{formatDate(r.returnDate)}</Typography>
        ),
    },
  ]

  return (
    <>
      <PageHeader title="Bibliothèque" subtitle="Inventaire des livres et gestion des emprunts" actionLabel="Ajouter un livre" onAction={() => setBookDialog(true)} />

      <Box display="flex" alignItems="center" gap={1} mb={3} flexWrap="wrap">
        <Tabs value={tab} onChange={(_, v) => { setTab(v); setPage(0) }}>
          <Tab label="Livres" value="books" />
          <Tab label="Emprunts" value="borrowings" />
        </Tabs>
        <Box flexGrow={1} />
        {tab === 'books' ? (
          <Button variant="outlined" color="warning" onClick={markOverdue}>Marquer les retards</Button>
        ) : (
          <Button variant="contained" color="secondary" onClick={() => setBorrowDialog(true)}>Nouvel emprunt</Button>
        )}
      </Box>

      {tab === 'books' && (
        <FilterCard mb={3}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <TextField size="small" fullWidth label="Rechercher un livre..." value={search}
                onChange={(e) => setSearch(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && loadBooks()} />
            </Grid>
          </Grid>
        </FilterCard>
      )}

      <DataTable
        columns={tab === 'books' ? bookColumns : borrowColumns}
        rows={tab === 'books' ? books : borrowings}
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={(s) => { setSize(s); setPage(0) }}
        searchable={false}
        onDelete={tab === 'books' ? setToDelete : undefined}
      />

      {/* Dialog livre */}
      <Dialog open={bookDialog} onClose={() => setBookDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Ajouter un livre</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12} sm={8}>
              <TextField fullWidth label="Titre" value={bookForm.title} onChange={(e) => setBookForm({ ...bookForm, title: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField fullWidth type="number" label="Quantité" value={bookForm.quantity} onChange={(e) => setBookForm({ ...bookForm, quantity: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth label="Auteur" value={bookForm.author} onChange={(e) => setBookForm({ ...bookForm, author: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth label="ISBN" value={bookForm.isbn} onChange={(e) => setBookForm({ ...bookForm, isbn: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth label="Catégorie" value={bookForm.category} onChange={(e) => setBookForm({ ...bookForm, category: e.target.value })} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth label="Éditeur" value={bookForm.publisher} onChange={(e) => setBookForm({ ...bookForm, publisher: e.target.value })} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBookDialog(false)}>Annuler</Button>
          <Button variant="contained" onClick={createBook} disabled={!bookForm.title}>Ajouter</Button>
        </DialogActions>
      </Dialog>

      {/* Dialog emprunt */}
      <Dialog open={borrowDialog} onClose={() => setBorrowDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Nouvel emprunt</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} mt={0.5}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Livre" value={borrowForm.bookId} onChange={(e) => setBorrowForm({ ...borrowForm, bookId: e.target.value })}>
                {allBooks.filter((b) => b.available > 0).map((b) => (
                  <MenuItem key={b.id} value={String(b.id)}>{b.title} ({b.available} dispo)</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <TextField select fullWidth label="Élève" value={borrowForm.studentId} onChange={(e) => setBorrowForm({ ...borrowForm, studentId: e.target.value })}>
                {students.map((s) => <MenuItem key={s.id} value={String(s.id)}>{s.firstName} {s.lastName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth type="date" label="Date d'emprunt" value={borrowForm.borrowDate}
                onChange={(e) => setBorrowForm({ ...borrowForm, borrowDate: e.target.value })} InputLabelProps={{ shrink: true }} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth type="date" label="Retour prévu" value={borrowForm.dueDate}
                onChange={(e) => setBorrowForm({ ...borrowForm, dueDate: e.target.value })} InputLabelProps={{ shrink: true }} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBorrowDialog(false)}>Annuler</Button>
          <Button variant="contained" onClick={borrow} disabled={!borrowForm.bookId || !borrowForm.studentId || !borrowForm.borrowDate || !borrowForm.dueDate}>
            Enregistrer
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(toDelete)}
        title="Supprimer le livre"
        message={`Supprimer « ${toDelete?.title} » de l'inventaire ?`}
        onConfirm={handleDelete}
        onClose={() => setToDelete(null)}
      />
    </>
  )
}