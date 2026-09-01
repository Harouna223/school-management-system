import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import {
  Grid, TextField, MenuItem, Button, Card, CardContent, Typography, Divider, Box, Avatar,
} from '@mui/material'
import { ArrowBack, Save, Upload } from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'
import { useToast } from '../../hooks/useToast'
import { teacherApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { initials } from '../../utils/format'

const validationSchema = Yup.object({
  firstName: Yup.string().required('Le prénom est requis'),
  lastName: Yup.string().required('Le nom est requis'),
  gender: Yup.string().required('Le genre est requis'),
  hireDate: Yup.string().required('La date d\'embauche est requise'),
  contractType: Yup.string().required('Le contrat est requis'),
  email: Yup.string().email('Email invalide'),
})

/**
 * Formulaire d'embauche / modification d'un enseignant.
 */
export default function TeacherFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const navigate = useNavigate()
  const { success, error: toastError } = useToast()

  const [initial, setInitial] = useState({
    firstName: '', lastName: '', birthDate: '', gender: '', phone: '', email: '',
    address: '', hireDate: '', contractType: 'CDD', salary: '', createUserAccount: false,
    username: '', password: '',
  })
  const [photo, setPhoto] = useState(null)
  const [preview, setPreview] = useState(null)

  useEffect(() => {
    if (isEdit) {
      teacherApi.get(id).then((res) => {
        const t = res.data.data
        setInitial({
          firstName: t.firstName, lastName: t.lastName, birthDate: t.birthDate || '',
          gender: t.gender || '', phone: t.phone || '', email: t.email || '',
          address: t.address || '', hireDate: t.hireDate || '',
          contractType: t.contractType || 'CDD', salary: t.salary ?? '',
          createUserAccount: false, username: '', password: '',
        })
        if (t.photo) setPreview(t.photo)
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
      salary: values.salary ? Number(values.salary) : undefined,
    }
    try {
      let saved
      if (isEdit) {
        saved = await teacherApi.update(id, payload)
        success('Enseignant modifié')
      } else {
        saved = await teacherApi.create(payload)
        success(`Enseignant embauché : ${saved.data.data.employeeNo}`)
      }
      if (photo && saved.data.data.id) {
        await teacherApi.uploadPhoto(saved.data.data.id, photo)
      }
      navigate('/teachers')
    } catch (err) {
      toastError(extractError(err))
    }
  }

  return (
    <>
      <PageHeader
        title={isEdit ? 'Modifier l\'enseignant' : 'Nouvel enseignant'}
        subtitle="Profil, contrat et salaire"
      />
      <Formik initialValues={initial} validationSchema={validationSchema} onSubmit={handleSubmit} enableReinitialize>
        {({ values, errors, touched, handleChange, handleBlur }) => (
          <Form>
            <Grid container spacing={3} maxWidth="lg">
              <Grid item xs={12} md={4}>
                <Card>
                  <CardContent sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
                    <Avatar src={preview} sx={{ width: 140, height: 140, fontSize: 48 }}>
                      {initials(values.firstName, values.lastName)}
                    </Avatar>
                    <Button component="label" variant="outlined" startIcon={<Upload />}>
                      Photo
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
                        <TextField fullWidth label="Prénom" name="firstName" value={values.firstName} onChange={handleChange}
                          error={touched.firstName && Boolean(errors.firstName)} helperText={touched.firstName && errors.firstName} />
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth label="Nom" name="lastName" value={values.lastName} onChange={handleChange}
                          error={touched.lastName && Boolean(errors.lastName)} helperText={touched.lastName && errors.lastName} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField select fullWidth label="Genre" name="gender" value={values.gender} onChange={handleChange}>
                          <MenuItem value="MALE">Masculin</MenuItem>
                          <MenuItem value="FEMALE">Féminin</MenuItem>
                        </TextField>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth type="date" label="Naissance" name="birthDate" value={values.birthDate}
                          onChange={handleChange} InputLabelProps={{ shrink: true }} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth type="date" label="Date d'embauche" name="hireDate" value={values.hireDate}
                          onChange={handleChange} InputLabelProps={{ shrink: true }}
                          error={touched.hireDate && Boolean(errors.hireDate)}
                          helperText={touched.hireDate && errors.hireDate} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth label="Téléphone" name="phone" value={values.phone} onChange={handleChange} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth label="Email" name="email" value={values.email} onChange={handleChange}
                          error={touched.email && Boolean(errors.email)} helperText={touched.email && errors.email} />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField select fullWidth label="Contrat" name="contractType" value={values.contractType} onChange={handleChange}>
                          <MenuItem value="CDI">CDI</MenuItem>
                          <MenuItem value="CDD">CDD</MenuItem>
                          <MenuItem value="VACATAIRE">Vacataire</MenuItem>
                        </TextField>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <TextField fullWidth label="Salaire (FCFA)" name="salary" type="number" value={values.salary} onChange={handleChange} />
                      </Grid>
                      <Grid item xs={12}>
                        <TextField fullWidth label="Adresse" name="address" value={values.address} onChange={handleChange} />
                      </Grid>
                    </Grid>

                    {!isEdit && (
                      <>
                        <Divider sx={{ my: 3 }} />
                        <Typography variant="h6" mb={2}>Compte utilisateur</Typography>
                        <Grid container spacing={2}>
                          <Grid item xs={12} sm={6}>
                            <TextField fullWidth label="Nom d'utilisateur (laisser vide = auto)" name="username" value={values.username} onChange={handleChange} />
                          </Grid>
                          <Grid item xs={12} sm={6}>
                            <TextField fullWidth label="Mot de passe" type="password" name="password" value={values.password} onChange={handleChange} />
                          </Grid>
                        </Grid>
                      </>
                    )}

                    <Box mt={3} display="flex" gap={2} justifyContent="flex-end">
                      <Button startIcon={<ArrowBack />} onClick={() => navigate('/teachers')}>Retour</Button>
                      <Button type="submit" variant="contained" startIcon={<Save />}>
                        {isEdit ? 'Enregistrer' : 'Embaucher'}
                      </Button>
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          </Form>
        )}
      </Formik>
    </>
  )
}