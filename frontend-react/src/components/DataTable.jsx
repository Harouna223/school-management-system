import { useState } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Paper,
  TableSortLabel,
  Box,
  TextField,
  InputAdornment,
  IconButton,
  Tooltip,
  Skeleton,
  Typography,
  Chip,
} from '@mui/material'
import { Search, Edit, Delete, Visibility, Badge, KeyboardArrowDown, Download } from '@mui/icons-material'
import EmptyState from './EmptyState'
import { useI18n } from '../i18n/I18nContext'

/**
 * Tableau moderne générique : colonnes, tri, pagination, recherche locale,
 * actions par icônes, compteur de résultats et états chargement/vide.
 */
export default function DataTable({
  columns,
  rows = [],
  loading,
  onEdit,
  onDelete,
  onView,
  onDocs,
  searchable = true,
  actions = true,
  onExportCsv,
  page = 0,
  size = 10,
  total = rows.length,
  onPageChange,
  onSizeChange,
  title,
}) {
  const [order, setOrder] = useState('asc')
  const [orderBy, setOrderBy] = useState(null)
  const [query, setQuery] = useState('')
  const { t } = useI18n()

  const handleSort = (key) => {
    const isAsc = orderBy === key && order === 'asc'
    setOrder(isAsc ? 'desc' : 'asc')
    setOrderBy(key)
  }

  const handleExportCsv = () => {
    const headers = columns.map((c) => c.label).join(';')
    const lines = displayRows.map((row) =>
      columns.map((col) => {
        const v = col.render ? col.render(row) : row[col.key]
        const s = typeof v === 'object' && v !== null ? '' : String(v ?? '')
        return `"${s.replace(/"/g, '""')}"`
      }).join(';'),
    )
    const blob = new Blob(['\uFEFF' + [headers, ...lines].join('\n')], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.setAttribute('download', `${title || 'export'}.csv`)
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  let displayRows = [...rows]
  if (query.trim()) {
    displayRows = displayRows.filter((row) =>
      columns.some((col) =>
        String(row[col.key] ?? '').toLowerCase().includes(query.toLowerCase()),
      ),
    )
  }
  if (orderBy) {
    displayRows.sort((a, b) => {
      const va = a[orderBy]
      const vb = b[orderBy]
      if (typeof va === 'number' && typeof vb === 'number') {
        return order === 'asc' ? va - vb : vb - va
      }
      return order === 'asc'
        ? String(va ?? '').localeCompare(String(vb ?? ''))
        : String(vb ?? '').localeCompare(String(va ?? ''))
    })
  }

  const hasActions = actions && (onEdit || onDelete || onView || onDocs)

  return (
    <Paper
      className="animate-fade-in-up"
      sx={{
        width: '100%',
        overflow: 'hidden',
        borderRadius: '16px',
        border: '1px solid',
        borderColor: 'divider',
      }}
    >
      {(searchable || title) && (
        <Box p={2} pb={1.5} display="flex" alignItems="center" gap={1.5} flexWrap="wrap">
          {title && (
            <Box mr="auto">
              <Typography variant="subtitle1" fontWeight={700} sx={{ fontSize: 15 }}>
                {title}
              </Typography>
            </Box>
          )}
          {searchable && (
            <Box display="flex" alignItems="center" gap={1} ml={title ? 'auto' : 0} flexGrow={title ? 0 : 1}>
              <TextField
                size="small"
                fullWidth
                placeholder={t('common.searchTable')}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search fontSize="small" sx={{ color: 'text.secondary' }} />
                    </InputAdornment>
                  ),
                }}
                sx={{
                  maxWidth: 320,
                  '& .MuiOutlinedInput-root': {
                    background: (t) => (t.palette.mode === 'dark' ? 'rgba(30,41,59,0.5)' : '#f8fafc'),
                    borderRadius: '10px',
                    '&:hover': { background: (th) => (th.palette.mode === 'dark' ? '#1e293b' : '#f1f5f9') },
                  },
                }}
              />
              {!loading && displayRows.length > 0 && (
                <Chip
                  label={`${displayRows.length} ${t('common.results')}${displayRows.length > 1 ? 's' : ''}`}
                  size="small"
                  variant="outlined"
                  sx={{ fontWeight: 600, height: 28, borderRadius: '8px' }}
                />
              )}
              {onExportCsv !== false && !loading && displayRows.length > 0 && (
                <Tooltip title="Exporter en CSV">
                  <IconButton size="small" onClick={handleExportCsv} sx={{ color: 'primary.main' }}>
                    <Download fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
            </Box>
          )}
        </Box>
      )}
      <TableContainer sx={{ maxHeight: 640 }}>
        <Table size="medium" stickyHeader>
          <TableHead>
            <TableRow>
              {columns.map((col) => (
                <TableCell key={col.key} sortDirection={orderBy === col.key ? order : false}>
                  {col.sortable ? (
                    <TableSortLabel
                      active={orderBy === col.key}
                      direction={orderBy === col.key ? order : 'asc'}
                      onClick={() => handleSort(col.key)}
                      IconComponent={KeyboardArrowDown}
                    >
                      {col.label}
                    </TableSortLabel>
                  ) : (
                    col.label
                  )}
                </TableCell>
              ))}
              {hasActions && <TableCell align="right" sx={{ pr: 2.5 }}>{t('common.actions')}</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {loading && rows.length === 0 && (
              Array.from({ length: 4 }).map((_, i) => (
                <TableRow key={`sk-${i}`}>
                  <TableCell colSpan={columns.length + (hasActions ? 1 : 0)}>
                    <Skeleton height={30} />
                  </TableCell>
                </TableRow>
              ))
            )}
            {!loading && displayRows.length === 0 && (
              <TableRow>
                <TableCell colSpan={columns.length + (hasActions ? 1 : 0)}>
                  <EmptyState hint={query ? t('common.noMatch') : undefined} />
                </TableCell>
              </TableRow>
            )}
            {displayRows.map((row, idx) => (
              <TableRow key={row.id ?? idx} hover>
                {columns.map((col) => (
                  <TableCell key={col.key}>
                    {col.render ? col.render(row) : row[col.key]}
                  </TableCell>
                ))}
                {hasActions && (
                  <TableCell align="right" sx={{ pr: 2.5, whiteSpace: 'nowrap' }}>
                    <Box display="flex" justifyContent="flex-end" gap={0.2}>
                      {onView && (
                        <Tooltip title={t('common.view')}>
                          <IconButton size="small" onClick={() => onView(row)}>
                            <Visibility fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {onDocs && (
                        <Tooltip title={t('common.docs')}>
                          <IconButton size="small" color="primary" onClick={() => onDocs(row)}>
                            <Badge fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {onEdit && (
                        <Tooltip title={t('common.edit')}>
                          <IconButton size="small" onClick={() => onEdit(row)}>
                            <Edit fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {onDelete && (
                        <Tooltip title={t('common.delete')}>
                          <IconButton size="small" color="error" onClick={() => onDelete(row)}>
                            <Delete fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Box>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {onPageChange && (
        <TablePagination
          component="div"
          count={total}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={[5, 10, 25, 50]}
          onPageChange={(_, p) => onPageChange(p)}
          onRowsPerPageChange={(e) => onSizeChange(Number(e.target.value))}
          labelRowsPerPage={t('common.rowsPerPage')}
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} ${t('common.of')} ${count}`}
          sx={{
            '& .MuiTablePagination-toolbar': { minHeight: 52 },
          }}
        />
      )}
    </Paper>
  )
}