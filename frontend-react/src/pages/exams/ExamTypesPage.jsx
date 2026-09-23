import { useNavigate } from 'react-router-dom'
import {
  Grid,
  Card,
  CardActionArea,
  Typography,
  Box
} from '@mui/material'
import {
  Quiz, Assignment, School, WorkspacePremium, Add,
} from '@mui/icons-material'
import PageHeader from '../../components/PageHeader'

const TYPES = {
  CONTROLE: { icon: <Quiz sx={{ fontSize: 32 }} />, color: '#2563eb', description: 'Évaluation rapide en cours de séquence, généralement sans préparation longue.' },
  DEVOIR: { icon: <Assignment sx={{ fontSize: 32 }} />, color: '#7c3aed', description: 'Travail écrit noté, portant sur une ou plusieurs séquences.' },
  EXAMEN: { icon: <School sx={{ fontSize: 32 }} />, color: '#d97706', description: 'Évaluation bilan de fin de trimestre ou de semestre, coefficient élevé.' },
  BACCALAUREAT: { icon: <WorkspacePremium sx={{ fontSize: 32 }} />, color: '#dc2626', description: 'Examen national officiel, sanctionnant la fin du cycle secondaire.' },
}

/**
 * Page de gestion des types d'évaluation.
 * Affiche les 4 types disponibles avec leur description.
 */
export default function ExamTypesPage() {
  const navigate = useNavigate()

  return (
    <>
      <PageHeader
        title="Types d'évaluation"
        subtitle="Contrôle, Devoir, Examen, Baccalauréat — configurez et planifiez vos évaluations"
        actions={[
          { label: 'Planifier une évaluation', icon: <Add />, onClick: () => navigate('/exams') },
        ]}
      />

      <Grid container spacing={3}>
        {Object.entries(TYPES).map(([key, type]) => (
          <Grid item xs={12} sm={6} md={3} key={key}>
            <Card
              sx={{
                borderRadius: '16px',
                border: '1px solid',
                borderColor: 'divider',
                transition: 'all 0.2s',
                '&:hover': { transform: 'translateY(-2px)', boxShadow: '0 8px 24px rgba(0,0,0,0.1)' },
              }}
            >
              <CardActionArea onClick={() => navigate(`/exams?type=${key}`)} sx={{ p: 2.5 }}>
                <Box
                  sx={{
                    width: 56, height: 56, borderRadius: '14px', mb: 2,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    bgcolor: `${type.color}18`, color: type.color,
                  }}
                >
                  {type.icon}
                </Box>
                <Typography variant="h6" fontWeight={700}>{key}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1, minHeight: 40 }}>
                  {type.description}
                </Typography>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>
    </>
  )
}