import { useDispatch, useSelector } from 'react-redux'
import { Formik, Form, Field } from 'formik'
import * as Yup from 'yup'
import {
  TextField,
  Button,
  Alert,
  Box,
  InputAdornment,
  IconButton,
  Typography,
  CircularProgress,
  Divider,
} from '@mui/material'
import { Visibility, VisibilityOff, Lock, Person, ShieldOutlined } from '@mui/icons-material'
import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { login } from '../../redux/slices/authSlice'
import { useToast } from '../../hooks/useToast'
import { useI18n } from '../../i18n/I18nContext'

const makeValidationSchema = (t) => Yup.object({
  username: Yup.string().required(t('login.usernameRequired')),
  password: Yup.string().required(t('login.passwordRequired')),
})

/**
 * Page de connexion sécurisée.
 */
export default function LoginPage() {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const { success } = useToast()
  const { t } = useI18n()
  const { loading, error } = useSelector((state) => state.auth)
  const [showPassword, setShowPassword] = useState(false)

  const handleSubmit = async (values) => {
    const result = await dispatch(login(values))
    if (login.fulfilled.match(result)) {
      success(t('login.success'))
      navigate(location.state?.from?.pathname || '/dashboard')
    }
  }

  return (
    <Formik
      initialValues={{ username: '', password: '' }}
      validationSchema={makeValidationSchema(t)}
      onSubmit={handleSubmit}
    >
      {({ values, errors, touched, handleChange, handleBlur }) => (
        <Form>
          <Typography variant="body2" color="text.secondary" mb={2.5} align="center">
            {t('login.subtitle')}
          </Typography>

          {error && (
            <Alert severity="error" sx={{ mb: 2.5 }}>
              {error}
            </Alert>
          )}

          <Field name="username">
            {() => (
              <TextField
                fullWidth
                label={t('login.username')}
                name="username"
                value={values.username}
                onChange={handleChange}
                onBlur={handleBlur}
                error={touched.username && Boolean(errors.username)}
                helperText={touched.username && errors.username}
                margin="normal"
                autoComplete="username"
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Person fontSize="small" sx={{ color: 'text.secondary' }} />
                    </InputAdornment>
                  ),
                }}
              />
            )}
          </Field>

          <Field name="password">
            {() => (
              <TextField
                fullWidth
                label={t('login.password')}
                type={showPassword ? 'text' : 'password'}
                name="password"
                value={values.password}
                onChange={handleChange}
                onBlur={handleBlur}
                error={touched.password && Boolean(errors.password)}
                helperText={touched.password && errors.password}
                margin="normal"
                autoComplete="current-password"
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Lock fontSize="small" sx={{ color: 'text.secondary' }} />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <IconButton onClick={() => setShowPassword(!showPassword)} size="small" aria-label={t('login.showPassword')}>
                      {showPassword ? <VisibilityOff /> : <Visibility />}
                    </IconButton>
                  ),
                }}
              />
            )}
          </Field>

          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            disabled={loading}
            sx={{ mt: 3, py: 1.3 }}
          >
            {loading ? <CircularProgress size={22} color="inherit" /> : t('login.submit')}
          </Button>

          <Divider sx={{ my: 2.5 }}>
            <Typography variant="caption" color="text.disabled" px={1}>
              {t('login.secureArea')}
            </Typography>
          </Divider>

          <Box display="flex" alignItems="center" justifyContent="center" gap={0.8}>
            <ShieldOutlined sx={{ fontSize: 15, color: 'success.main' }} />
            <Typography variant="caption" color="text.secondary">
              {t('login.encrypted')}
            </Typography>
          </Box>
        </Form>
      )}
    </Formik>
  )
}