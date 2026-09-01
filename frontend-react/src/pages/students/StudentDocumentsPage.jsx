import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Box, Button, Card, Chip, Divider, Typography } from '@mui/material'
import { ArrowBack, Print, Badge, PictureAsPdf } from '@mui/icons-material'
import { studentApi, attendanceApi } from '../../api/endpoints'
import { extractError } from '../../api/axios'
import { formatDate, initials, downloadBlob } from '../../utils/format'
import { useToast } from '../../hooks/useToast'
import logo from '../../assets/logo.svg'

const SCHOOL_NAME = 'School Manager'
const SCHOOL_SLOGAN = 'Établissement d’enseignement et de formation'

function schoolYear() {
  const now = new Date()
  const year = now.getFullYear()
  return now.getMonth() >= 8 ? `${year}-${year + 1}` : `${year - 1}-${year}`
}

/**
 * Carte scolaire et certificat de fréquentation de l'élève (imprimables).
 */
export default function StudentDocumentsPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { error: toastError } = useToast()

  const [student, setStudent] = useState(null)
  const [stats, setStats] = useState(null)
  const [qrUrl, setQrUrl] = useState(null)

  useEffect(() => {
    let cancelled = false
    studentApi
      .get(id)
      .then((res) => { if (!cancelled) setStudent(res.data.data) })
      .catch((err) => toastError(extractError(err)))
    attendanceApi
      .stats(id)
      .then((res) => { if (!cancelled) setStats(res.data.data) })
      .catch(() => {})
    studentApi
      .qr(id)
      .then((res) => { if (!cancelled) setQrUrl(URL.createObjectURL(res.data)) })
      .catch(() => {})
    return () => { cancelled = true }
  }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  const print = (kind) => {
    document.body.classList.add('printing', kind)
    window.print()
    setTimeout(() => document.body.classList.remove('printing', kind), 500)
  }

  const downloadCertificate = async () => {
    try {
      const res = await studentApi.attendanceCertificate(id)
      downloadBlob(res.data, `certificat-${student.matricule}.pdf`)
    } catch (err) {
      toastError(extractError(err))
    }
  }

  if (!student) {
    return (
      <Box display="flex" justifyContent="center" py={10}>
        <Typography color="text.secondary">Chargement des documents…</Typography>
      </Box>
    )
  }

  const fullName = `${student.lastName.toUpperCase()} ${student.firstName}`
  const genderLabel = student.gender === 'MALE' ? 'Masculin' : 'Féminin'

  return (
    <Box className="print-area">
      {/* Barre d'actions (masquée à l'impression) */}
      <Box className="doc-toolbar" display="flex" alignItems="center" gap={1.5} mb={3} flexWrap="wrap">
        <Button variant="outlined" startIcon={<ArrowBack />} onClick={() => navigate('/students')}>
          Retour
        </Button>
        <Box flex={1} />
        <Button variant="outlined" color="inherit" startIcon={<Badge />} onClick={() => print('print-card')}>
          Imprimer la carte
        </Button>
        <Button variant="outlined" startIcon={<Print />} onClick={() => print('print-cert')}>
          Imprimer le certificat
        </Button>
        <Button variant="contained" startIcon={<PictureAsPdf />} onClick={downloadCertificate}>
          Certificat PDF
        </Button>
      </Box>

      {/* ------- CARTE SCOLAIRE ------- */}
      <Box className="card-area" display="flex" justifyContent="center" mb={5}>
        <Card
          sx={{
            width: 380,
            borderRadius: '18px',
            overflow: 'hidden',
            boxShadow: '0 14px 34px rgba(15, 23, 42, 0.18)',
          }}
        >
          <Box
            sx={{
              background: 'linear-gradient(135deg, #1d4ed8 0%, #2563eb 45%, #3b82f6 100%)',
              color: 'white',
              px: 2.5,
              py: 1.5,
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
            }}
          >
            <Box component="img" src={logo} alt="logo" sx={{ width: 34, height: 34, filter: 'brightness(0) invert(1)' }} />
            <Box flex={1}>
              <Typography variant="subtitle1" fontWeight={800} lineHeight={1.2}>
                {SCHOOL_NAME}
              </Typography>
              <Typography variant="caption" sx={{ opacity: 0.9 }}>
                CARTE SCOLAIRE · {schoolYear()}
              </Typography>
            </Box>
          </Box>

          <Box display="flex" gap={2} p={2.5}>
            <Box
              sx={{
                width: 96,
                height: 116,
                borderRadius: '10px',
                border: '2px solid',
                borderColor: 'primary.main',
                overflow: 'hidden',
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: '#f1f5f9',
              }}
            >
              {student.photo ? (
                <Box component="img" src={student.photo} alt={fullName} sx={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <Typography variant="h5" fontWeight={800} color="primary.main">
                  {initials(student.firstName, student.lastName)}
                </Typography>
              )}
            </Box>
            <Box flex={1} minWidth={0}>
              <Typography variant="h6" fontWeight={800} noWrap>
                {fullName}
              </Typography>
              <Divider sx={{ my: 1 }} />
              {[
                ['Matricule', student.matricule],
                ['Classe', student.className || '—'],
                ['Né(e) le', formatDate(student.birthDate)],
                ['Sexe', genderLabel],
              ].map(([label, value]) => (
                <Box key={label} display="flex" justifyContent="space-between" gap={1}>
                  <Typography variant="caption" color="text.secondary">{label}</Typography>
                  <Typography variant="body2" fontWeight={600} noWrap>{value}</Typography>
                </Box>
              ))}
            </Box>
          </Box>

          <Box
            display="flex"
            alignItems="center"
            justifyContent="space-between"
            px={2.5}
            pb={2}
          >
            <Chip
              size="small"
              label={student.status}
              sx={{ fontWeight: 700, borderRadius: '999px', bgcolor: '#dbeafe', color: '#1d4ed8' }}
            />
            {qrUrl && (
              <Box component="img" src={qrUrl} alt="QR" sx={{ width: 56, height: 56 }} />
            )}
          </Box>
        </Card>
      </Box>

      {/* ------- CERTIFICAT DE FRÉQUENTATION ------- */}
      <Box className="cert-area" display="flex" justifyContent="center" mb={5}>
        <Box
          sx={{
            width: '100%',
            maxWidth: 760,
            bgcolor: '#ffffff',
            border: '2px solid #1d4ed8',
            borderRadius: '6px',
            p: 4,
          }}
        >
          {/* En-tête officiel */}
          <Box display="flex" alignItems="center" gap={2} mb={1}>
            <Box component="img" src={logo} alt="logo" sx={{ width: 56, height: 56 }} />
            <Box flex={1} textAlign="center">
              <Typography variant="h6" fontWeight={800} color="#1d4ed8">
                {SCHOOL_NAME}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {SCHOOL_SLOGAN} · Année scolaire {schoolYear()}
              </Typography>
            </Box>
            {student.photo && (
              <Box
                component="img"
                src={student.photo}
                alt={fullName}
                sx={{ width: 72, height: 88, objectFit: 'cover', borderRadius: '6px', border: '1px solid #cbd5e1' }}
              />
            )}
          </Box>
          <Divider sx={{ borderColor: '#1d4ed8', borderBottomWidth: 2, mb: 3 }} />

          <Typography variant="h5" fontWeight={800} align="center" mb={3}>
            CERTIFICAT DE FRÉQUENTATION
          </Typography>

          <Typography paragraph sx={{ textIndent: 24, lineHeight: 1.9 }}>
            Nous, {SCHOOL_NAME}, certifions que l'élève{' '}
            <b>{fullName}</b>, né(e) le <b>{formatDate(student.birthDate)}</b>, de sexe{' '}
            <b>{genderLabel}</b>, portant la matricule <b>{student.matricule}</b>, est
            régulièrement inscrit(e) en <b>{student.className || '—'}</b> pour l'année
            scolaire <b>{schoolYear()}</b>.
          </Typography>

          <Typography paragraph sx={{ textIndent: 24, lineHeight: 1.9 }}>
            Au cours de cette période, la fréquentation de l'élève se présente comme suit :
          </Typography>

          {stats && (
            <Box display="flex" justifyContent="center" mb={3}>
              <Box sx={{ border: '1px solid #cbd5e1', borderRadius: '8px', overflow: 'hidden' }}>
                {[
                  ['Jours de classe', stats.total],
                  ['Présences', stats.presences],
                  ['Absences', stats.absences],
                  ['Retards', stats.retards],
                ].map(([label, value]) => (
                  <Box key={label} display="flex" sx={{ '& + &': { borderTop: '1px solid #e2e8f0' } }}>
                    <Box px={4} py={1} sx={{ bgcolor: '#eff6ff', fontWeight: 600, minWidth: 200 }}>
                      {label}
                    </Box>
                    <Box px={4} py={1} sx={{ minWidth: 100, textAlign: 'right', fontWeight: 700 }}>
                      {Number(value)}
                    </Box>
                  </Box>
                ))}
              </Box>
            </Box>
          )}

          <Typography paragraph sx={{ textIndent: 24, lineHeight: 1.9 }}>
            En foi de quoi, le présent certificat est délivré pour servir et valoir ce que
            de droit.
          </Typography>

          <Box display="flex" justifyContent="flex-end" mt={5}>
            <Box textAlign="center">
              <Typography variant="body2" fontWeight={600}>
                Fait à ..., le {formatDate(new Date().toISOString())}
              </Typography>
              <Box height={70} />
              <Divider sx={{ width: 240, borderColor: '#334155' }} />
              <Typography variant="body2" fontWeight={700} mt={0.5}>
                Le Chef d'Établissement
              </Typography>
            </Box>
          </Box>
        </Box>
      </Box>
    </Box>
  )
}