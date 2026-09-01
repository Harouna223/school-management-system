import { useRef, useState } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Alert,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
} from '@mui/material'
import { UploadFile, Download, CheckCircle, ErrorOutline, Close } from '@mui/icons-material'
import { studentApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { downloadBlob } from '../../utils/format'
import { useToast } from '../../hooks/useToast'

const STEPS = ['Choisir le fichier', 'Vérification', 'Résultat']

/**
 * Import Excel d'élèves en 3 étapes : téléchargement du modèle, upload,
 * rapport de validation (lignes importées / rejetées avec erreurs).
 */
export default function StudentImportDialog({ open, onClose, onImported }) {
  const { success, error: toastError } = useToast()
  const inputRef = useRef(null)
  const [step, setStep] = useState(0)
  const [file, setFile] = useState(null)
  const [fileName, setFileName] = useState('')
  const [importing, setImporting] = useState(false)
  const [result, setResult] = useState(null)

  const reset = () => {
    setStep(0)
    setFile(null)
    setFileName('')
    setResult(null)
  }

  const handleClose = () => {
    if (importing) return
    reset()
    onClose()
  }

  const handleTemplate = async () => {
    try {
      const res = await studentApi.importTemplate()
      downloadBlob(res.data, 'modele-import-eleves.xlsx')
      success('Modèle téléchargé')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  const handleFile = (event) => {
    const f = event.target.files?.[0]
    if (!f) return
    if (!f.name.toLowerCase().endsWith('.xlsx')) {
      toastError('Seuls les fichiers .xlsx sont acceptés')
      return
    }
    setFile(f)
    setFileName(f.name)
    setStep(1)
  }

  const handleImport = async () => {
    setImporting(true)
    try {
      if (!file) {
        toastError('Veuillez choisir un fichier')
        return
      }
      const res = await studentApi.importExcel(file)
      setResult(res.data.data)
      setStep(2)
      onImported?.()
      success(`Import terminé : ${res.data.data.imported} élève(s) importé(s)`)
    } catch (err) {
      toastError(extractError(err))
    } finally {
      setImporting(false)
    }
  }

  const hasErrors = result && result.errors?.length > 0

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <UploadFile color="primary" />
        <Box>
          <Typography variant="subtitle1" fontWeight={700}>Importer des élèves</Typography>
          <Typography variant="caption" color="text.secondary">
            {STEPS[step]} — étape {step + 1}/3
          </Typography>
        </Box>
      </DialogTitle>

      <DialogContent dividers>
        {step === 0 && (
          <Box>
            <Alert severity="info" sx={{ mb: 2 }}>
              Le fichier doit suivre le modèle fourni (13 colonnes : prénom, nom, genre, date de
              naissance, classe, téléphone, email, adresse, parent prénom, parent nom, parent
              téléphone, parent email, parent profession). Les doublons et lignes invalides sont
              rejetés avec le détail des erreurs.
            </Alert>
            <Box display="flex" gap={2}>
              <Button variant="outlined" startIcon={<Download />} onClick={handleTemplate}>
                Télécharger le modèle
              </Button>
              <Button variant="contained" component="label" startIcon={<UploadFile />}>
                Choisir le fichier .xlsx
                <input ref={inputRef} type="file" accept=".xlsx" hidden onChange={handleFile} />
              </Button>
            </Box>
          </Box>
        )}

        {step === 1 && (
          <Box>
            <Alert severity="success" sx={{ mb: 2 }}>
              Fichier sélectionné : <strong>{fileName}</strong>
            </Alert>
            <Typography variant="body2" color="text.secondary">
              Chaque ligne est vérifiée : champs obligatoires, format de la date, classe existante,
              doublons (email ou nom + date de naissance) dans le fichier et en base. Cliquez sur
              « Importer » pour lancer l'analyse.
            </Typography>
          </Box>
        )}

        {step === 2 && result && (
          <Box>
            <Box display="flex" gap={2} mb={2}>
              <Chip icon={<CheckCircle sx={{ fontSize: 17 }} />} color="success"
                label={`${result.imported} importé(s)`} variant="outlined" />
              <Chip icon={<ErrorOutline sx={{ fontSize: 17 }} />} color={hasErrors ? 'error' : 'default'}
                label={`${result.skipped} rejeté(s)`} variant="outlined" />
              <Chip label={`${result.totalRows} ligne(s) analysée(s)`} variant="outlined" />
            </Box>
            {!hasErrors ? (
              <Alert severity="success">Toutes les lignes valides ont été importées.</Alert>
            ) : (
              <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 320 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }} width={80}>Ligne</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Erreur</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {result.errors.map((e, i) => (
                      <TableRow key={i}>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{e.row}</TableCell>
                        <TableCell>{e.message}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>
        )}
      </DialogContent>

      <DialogActions>
        <Button startIcon={<Close />} onClick={handleClose} disabled={importing}>
          Fermer
        </Button>
        {step === 1 && (
          <Button variant="contained" startIcon={<UploadFile />} onClick={handleImport} disabled={importing}>
            {importing ? 'Import en cours...' : 'Importer'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}