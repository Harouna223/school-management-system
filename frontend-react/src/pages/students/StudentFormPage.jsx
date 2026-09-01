import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import {
  Grid,
  TextField,
  MenuItem,
  Button,
  Card,
  CardContent,
  Typography,
  Divider,
  FormControlLabel,
  Switch,
  Box,
  Avatar,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material'
import { ArrowBack, Save, Upload, School, PictureAsPdf } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { studentApi, classApi, examApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate, formatGrade, initials, downloadBlob } from '../../utils/format'

const CYCLES = [
  { value: '', label: '— Aucun / Déduit de la classe —' },
  { value: 'JARDIN', label: 'Jardin' },
  { value: 'PRIMAIRE', label: 'Primaire' },
  { value: 'COLLEGE', label: 'Collège' },
  { value: 'LYCEE', label: 'Lycée' },
  { value: 'UNIVERSITE', label: 'Université' },
]

const validationSchema = Yup.object({
  firstName: Yup.string().required('Le prénom est requis'),
  lastName: Yup.string().required('Le nom est requis'),
  gender: Yup.string().required('Le genre est requis'),
  email: Yup.string().email('Email invalide'),
})

/**
 * Formulaire d'inscription / modification d'un élève ou étudiant.
 */
export default function StudentFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()
  const { success, error: toastError } = useToast()

  const [classes, setClasses] = useState([])
  const [initial, setInitial] = useState({
    firstName: '',
    lastName: '',
    birthDate: '',
    birthPlace: '',
    gender: '',
    address: '',
    phone: '',
    email: '',
    classId: '',
    educationCycle: '',
    parentFirstName: '',
    parentLastName: '',
    parentPhone: '',
    parentEmail: '',
    parentProfession: '',
    createUserAccount: false,
    username: '',
    password: '',
    createParentAccount: false,
    parentUsername: '',
    parentPassword: '',
  })
  const [photo, setPhoto] = useState(null)
  const [preview, setPreview] = useState(null)
  const [parentHasAccount, setParentHasAccount] = useState(false)
const [history, setHistory] = useState({ bulletins: [], grades: [] })

  useEffect(() => {
    if (!isEdit) return
    examApi.bulletinsByStudent(id).then((r) => setHistory((h) => ({ ...h, bulletins: r.data.data }))).catch(() => {})
    examApi.gradesByStudent(id).then((r) => setHistory((h) => ({ ...h, grades: r.data.data }))).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const downloadBulletin = async (bulletin) => {
    try {
      const res = await examApi.bulletinPdf(bulletin.id)
      downloadBlob(res.data, `bulletin-${bulletin.matricule}-${bulletin.term}.pdf`)
      success('Bulletin téléchargé')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  useEffect(() => {
    classApi.all().then((res) => setClasses(res.data.data)).catch(() => {})
    if (isEdit) {
      studentApi.get(id).then((res) => {
        const s = res.data.data
        setParentHasAccount(Boolean(s.parent?.hasAccount))
        setInitial({
          firstName: s.firstName,
          lastName: s.lastName,
          birthDate: s.birthDate || '',
          birthPlace: s.birthPlace || '',
          gender: s.gender || '',
          address: s.address || '',
          phone: s.phone || '',
          email: s.email || '',
          classId: s.classId ? String(s.classId) : '',
          educationCycle: s.educationCycle || '',
          parentFirstName: s.parent?.firstName || '',
          parentLastName: s.parent?.lastName || '',
          parentPhone: s.parent?.phone || '',
          parentEmail: s.parent?.email || '',
          parentProfession: s.parent?.profession || '',
          createUserAccount: false,
          username: '',
          password: '',
          createParentAccount: false,
          parentUsername: '',
          parentPassword: '',
        })
        if (s.photo) setPreview(s.photo)
      })
    }
  }, [id])

  const handlePhoto = (e) => {
    const file = e.target.files?.[0]
    if (file) {
      setPhoto(file)
      setPreview(URL.createObjectURL(file))
    }
  }

  const handleSubmit = async (values) => {
    const payload = {
      ...values,
      classId: values.classId ? Number(values.classId) : null,
      educationCycle: values.educationCycle || undefined,
      enrollmentDate: isEdit ? undefined : new Date().toISOString().slice(0, 10),
    }
    try {
      let saved
      if (isEdit) {
        saved = await studentApi.update(id, payload)
        success('Élève modifié avec succès')
      } else {
        saved = await studentApi.create(payload)
        success(`Élève inscrit : ${saved.data.data.matricule}`)
      }
      if (photo && saved.data.data.id) {
        await studentApi.uploadPhoto(saved.data.data.id, photo)
      }
      navigate('/students')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title={isEdit ? 'Modifier l\'élève' : 'Nouvel élève'}
        subtitle={isEdit ? 'Mise à jour du dossier scolaire' : 'Inscription avec matricule automatique'}
      />

      <Formik initialValues={initial} validationSchema={validationSchema} onSubmit={handleSubmit} enableReinitialize>
        {({ values, errors, touched, handleChange, handleBlur }) => (
          <Form>
            <Grid container spacing={3}>
              <Grid item xs={12} md={4}>
                <Card>
                  <CardContent sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
                    <Avatar src={preview} sx={{ width: 140, height: 140, fontSize: 48 }}>
                      {initials(values.firstName, values.lastName)}
                    </Avatar>
                    <Button component="label" variant="outlined" startIcon={<Upload />}>
                      Photo de profil
                      <input type="file" hidden accept="image/*" onChange={handlePhoto} />
                    </Button>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={8}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" mb={2}>Informations personnelles</Typography>
                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth label="Prénom" name="firstName" value={values.firstName}
                          onChange={handleChange} onBlur={handleBlur}
                          error={touched.firstName && Boolean(errors.firstName)}
                          helperText={touched.firstName && errors.firstName} />
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth label="Nom" name="lastName" value={values.lastName}
                          onChange={handleChange} onBlur={handleBlur}
                          error={touched.lastName && Boolean(errors.lastName)}
                          helperText={touched.lastName && errors.lastName} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField select fullWidth label="Genre" name="gender" value={values.gender}
                          onChange={handleChange} error={touched.gender && Boolean(errors.gender)}>
                          <MenuItem value="MALE">Masculin</MenuItem>
                          <MenuItem value="FEMALE">Féminin</MenuItem>
                        </TextField>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth type="date" label="Date de naissance" name="birthDate"
                          value={values.birthDate} onChange={handleChange} InputLabelProps={{ shrink: true }} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth label="Lieu de naissance" name="birthPlace"
                          value={values.birthPlace} onChange={handleChange} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField select fullWidth label="Classe" name="classId" value={values.classId}
                          onChange={handleChange} error={touched.classId && Boolean(errors.classId)}>
                          <MenuItem value="">— Aucune (université) —</MenuItem>
                          {classes.map((c) => (
                            <MenuItem key={c.id} value={String(c.id)}>{c.name}</MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField select fullWidth label="Cycle" name="educationCycle" value={values.educationCycle}
                          onChange={handleChange}>
                          {CYCLES.map((c) => (
                            <MenuItem key={c.value} value={c.value}>{c.label}</MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth label="Téléphone" name="phone" value={values.phone} onChange={handleChange} />
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth label="Email" name="email" value={values.email} onChange={handleChange}
                          error={touched.email && Boolean(errors.email)} helperText={touched.email && errors.email} />
                      </Grid>
                      <Grid item xs={12}>
                        <TextField fullWidth label="Adresse" name="address" value={values.address} onChange={handleChange} />
                      </Grid>
                    </Grid>

                    <Divider sx={{ my: 3 }} />
                    <Typography variant="h6" mb={2}>Parent / Tuteur</Typography>
                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth label="Prénom du parent" name="parentFirstName" value={values.parentFirstName} onChange={handleChange} />
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth label="Nom du parent" name="parentLastName" value={values.parentLastName} onChange={handleChange} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth label="Téléphone" name="parentPhone" value={values.parentPhone} onChange={handleChange} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth label="Email" name="parentEmail" value={values.parentEmail} onChange={handleChange} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth label="Profession" name="parentProfession" value={values.parentProfession} onChange={handleChange} />
                      </Grid>
                    </Grid>

                    {!isEdit && (
                      <>
                        <Divider sx={{ my: 3 }} />
                        <FormControlLabel
                          control={
                            <Switch
                              checked={values.createUserAccount}
                              onChange={handleChange}
                              name="createUserAccount"
                            />
                          }
                          label="Créer un compte utilisateur (espace élève)"
                        />
                        {values.createUserAccount && (
                          <Grid container spacing={2} mt={1}>
                            <Grid item xs={12} sm={6}>
                              <TextField fullWidth label="Nom d'utilisateur" name="username" value={values.username} onChange={handleChange} />
                            </Grid>
                            <Grid item xs={12} sm={6}>
                              <TextField fullWidth label="Mot de passe" type="password" name="password" value={values.password} onChange={handleChange} />
                            </Grid>
                          </Grid>
                        )}

                        <Divider sx={{ my: 3 }} />
                        <FormControlLabel
                          control={
                            <Switch
                              checked={values.createParentAccount}
                              onChange={handleChange}
                              name="createParentAccount"
                            />
                          }
                          label="Créer un compte parent (espace parent : bulletins et absences)"
                        />
                        {values.createParentAccount && (
                          <Grid container spacing={2} mt={1}>
                            <Grid item xs={12} sm={6}>
                              <TextField fullWidth label="Nom d'utilisateur parent" name="parentUsername" value={values.parentUsername} onChange={handleChange} />
                            </Grid>
                            <Grid item xs={12} sm={6}>
                              <TextField fullWidth label="Mot de passe parent" type="password" name="parentPassword" value={values.parentPassword} onChange={handleChange} />
                            </Grid>
                          </Grid>
                        )}
                      </>
                    )}

                    {isEdit && (
                      <>
                        <Divider sx={{ my: 3 }} />
                        <FormControlLabel
                          control={
                            <Switch
                              checked={values.createParentAccount}
                              onChange={handleChange}
                              name="createParentAccount"
                              disabled={parentHasAccount}
                            />
                          }
                          label={
                            parentHasAccount
                              ? 'Le parent dispose déjà d\'un compte (espace parent)'
                              : 'Créer un compte parent (espace parent : bulletins et absences)'
                          }
                        />
                        {values.createParentAccount && !parentHasAccount && (
                          <Grid container spacing={2} mt={1}>
                            <Grid item xs={12} sm={6}>
                              <TextField fullWidth label="Nom d'utilisateur parent" name="parentUsername" value={values.parentUsername} onChange={handleChange} />
                            </Grid>
                            <Grid item xs={12} sm={6}>
                              <TextField fullWidth label="Mot de passe parent" type="password" name="parentPassword" value={values.parentPassword} onChange={handleChange} />
                            </Grid>
                          </Grid>
                        )}
                      </>
                    )}

                    <Box mt={3} display="flex" gap={2} justifyContent="flex-end">
                      <Button startIcon={<ArrowBack />} onClick={() => navigate('/students')}>
                        Retour
                      </Button>
                      <Button type="submit" variant="contained" startIcon={<Save />}>
                        {isEdit ? 'Enregistrer' : 'Inscrire'}
                      </Button>
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          </Form>
        )}
      </Formik>

      {/* Historique académique (mode édition) */}
      {isEdit && (
        <Card sx={{ mt: 3, overflow: 'hidden' }} className="animate-fade-in-up">
          <Box p={2.5} pb={1.5} display="flex" alignItems="center" gap={1.5}>
            <Box sx={{ width: 36, height: 36, borderRadius: 2.5, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'primary.light', color: 'primary.main' }}>
              <School fontSize="small" />
            </Box>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Historique académique</Typography>
              <Typography variant="caption" color="text.secondary">Bulletins et notes de l'élève</Typography>
            </Box>
          </Box>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Année</TableCell>
                  <TableCell>Trimestre</TableCell>
                  <TableCell align="center">Moyenne</TableCell>
                  <TableCell>Mention</TableCell>
                  <TableCell align="center">Rang</TableCell>
                  <TableCell align="center">Décision</TableCell>
                  <TableCell align="center">PDF</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {history.bulletins.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 4 }} color="text.secondary">
                      Aucun bulletin pour le moment
                    </TableCell>
                  </TableRow>
                )}
                {history.bulletins.map((b) => (
                  <TableRow key={b.id}>
                    <TableCell sx={{ color: 'text.secondary' }}>{b.academicYear}</TableCell>
                    <TableCell>
                      <Chip label={b.term} size="small" variant="outlined" sx={{ fontWeight: 700 }} />
                    </TableCell>
                    <TableCell align="center">
                      <Typography variant="body2" fontWeight={800} color="primary.main">
                        {formatGrade(b.average)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">{b.mention || '—'}</Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Chip label={`#${b.rank ?? '-'}`} size="small" variant="outlined" sx={{ fontWeight: 600 }} />
                    </TableCell>
                    <TableCell align="center">
                      <Chip
                        label={{ ADMIS: 'Admis', AJOURNE: 'Ajourné', REDOUBLE: 'Redouble' }[b.decision] || '—'}
                        color={{ ADMIS: 'success', AJOURNE: 'warning', REDOUBLE: 'error' }[b.decision] || 'default'}
                        size="small"
                        sx={{ fontWeight: 700 }}
                      />
                    </TableCell>
                    <TableCell align="center">
                      <Button size="small" variant="outlined" startIcon={<PictureAsPdf />} onClick={() => downloadBulletin(b)}>
                        PDF
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {history.grades.length > 0 && (
            <Box p={2.5} pt={1}>
              <Typography variant="subtitle2" fontWeight={700} mb={1}>
                Dernières notes ({history.grades.length})
              </Typography>
              <Box display="flex" flexWrap="wrap" gap={1}>
                {history.grades.slice(0, 12).map((g) => (
                  <Chip
                    key={g.id}
                    size="small"
                    label={`${g.examName} : ${formatGrade(g.value)}`}
                    color={g.value >= 10 ? 'success' : 'error'}
                    variant="outlined"
                  />
                ))}
              </Box>
            </Box>
          )}
        </Card>
      )}
    </>
  )
}